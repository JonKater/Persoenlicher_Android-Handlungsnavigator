package com.example.ai

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class ImagePreprocessorTest {
  @get:Rule val temporaryFolder = TemporaryFolder()
  private val resolver: ContentResolver = RuntimeEnvironment.getApplication().contentResolver

  @Test
  fun `large bitmap is bounded to 2048 pixels and four MiB`() = runBlocking {
    val source = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
    val sourceBytes = ByteArrayOutputStream().also {
      source.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
    val uri = uriFor(sourceBytes)

    val prepared = ImagePreprocessor(resolver).prepare(uri).getOrThrow()

    assertEquals(2048, maxOf(prepared.bitmap.width, prepared.bitmap.height))
    assertTrue(prepared.jpegBytes.size <= 4 * 1024 * 1024)
    val decoded = BitmapFactory.decodeByteArray(prepared.jpegBytes, 0, prepared.jpegBytes.size)
    assertEquals(2048, maxOf(decoded.width, decoded.height))
  }

  @Test
  fun `corrupt stream maps to invalid output`() = runBlocking {
    val uri = uriFor(byteArrayOf())

    val error = ImagePreprocessor(resolver).prepare(uri).exceptionOrNull()

    assertEquals(AnalysisError.INVALID_OUTPUT, (error as ImagePreparationException).error)
  }

  @Test
  fun `processing dispatches to the supplied dispatcher`() = runBlocking {
    val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    val sourceBytes = ByteArrayOutputStream().also {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
    val uri = uriFor(sourceBytes)
    val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "image-worker") }
    val worker = executor.asCoroutineDispatcher()
    try {
      val recordingDispatcher = RecordingDispatcher(worker)
      val preprocessor = ImagePreprocessor(resolver, recordingDispatcher)

      preprocessor.prepare(uri).getOrThrow()

      assertTrue(recordingDispatcher.wasDispatched.get())
      assertEquals("image-worker", recordingDispatcher.executionThreadName)
    } finally {
      worker.close()
      executor.shutdownNow()
    }
  }

  private fun uriFor(bytes: ByteArray): Uri {
    val file = temporaryFolder.newFile()
    file.writeBytes(bytes)
    return Uri.fromFile(file)
  }

  private class RecordingDispatcher(
    private val delegate: CoroutineDispatcher,
  ) : CoroutineDispatcher() {
    val wasDispatched = AtomicBoolean(false)
    @Volatile var executionThreadName: String? = null

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      wasDispatched.set(true)
      delegate.dispatch(context) {
        executionThreadName = Thread.currentThread().name
        block.run()
      }
    }
  }
}
