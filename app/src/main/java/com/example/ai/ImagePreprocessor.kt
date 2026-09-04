package com.example.ai

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreparedImage(val bitmap: Bitmap, val jpegBytes: ByteArray)

class ImagePreparationException(val error: AnalysisError) : Exception(error.name)

class ImagePreprocessor(
  private val contentResolver: ContentResolver,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun prepare(uri: Uri): Result<PreparedImage> = withContext(dispatcher) {
    try {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return@withContext invalidOutput()
      val emptyInput = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length == 0L } == true
      if (emptyInput || bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext invalidOutput()

      val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
      val decoded = contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
      } ?: return@withContext invalidOutput()

      val bitmap = scaleToBound(decoded)
      if (bitmap !== decoded) decoded.recycle()
      val jpegBytes = compressWithinLimit(bitmap)
        ?: return@withContext Result.failure(ImagePreparationException(AnalysisError.IMAGE_TOO_LARGE))
      Result.success(PreparedImage(bitmap, jpegBytes))
    } catch (error: ImagePreparationException) {
      Result.failure(error)
    } catch (error: Exception) {
      invalidOutput()
    }
  }

  private fun calculateSampleSize(width: Int, height: Int): Int {
    val longEdge = maxOf(width, height)
    var sampleSize = 1
    while (longEdge / (sampleSize * 2) >= MAX_LONG_EDGE) sampleSize *= 2
    return sampleSize
  }

  private fun scaleToBound(source: Bitmap): Bitmap {
    val longEdge = maxOf(source.width, source.height)
    if (longEdge <= MAX_LONG_EDGE) return source
    val scale = MAX_LONG_EDGE.toDouble() / longEdge
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, width, height, true)
  }

  private fun compressWithinLimit(bitmap: Bitmap): ByteArray? {
    for (quality in JPEG_QUALITIES) {
      val output = ByteArrayOutputStream()
      if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
      val bytes = output.toByteArray()
      if (bytes.size <= MAX_JPEG_BYTES) return bytes
    }
    return null
  }

  private fun invalidOutput(): Result<PreparedImage> =
    Result.failure(ImagePreparationException(AnalysisError.INVALID_OUTPUT))

  private companion object {
    const val MAX_LONG_EDGE = 2048
    const val MAX_JPEG_BYTES = 4 * 1024 * 1024
    val JPEG_QUALITIES = intArrayOf(85, 75, 65, 55, 45)
  }
}
