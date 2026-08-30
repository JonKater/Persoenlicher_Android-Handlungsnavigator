package com.example.ai

import com.example.data.ActionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.example.BuildConfig

class GeminiService {
    private val apiKey = BuildConfig.GEMINI_API_KEY

    suspend fun analyzeCapture(input: String, imageBitmap: Bitmap? = null): ActionEntity? = withContext(Dispatchers.IO) {
        val parts = mutableListOf<Part>()
        parts.add(Part(text = input))
        
        if (imageBitmap != null) {
            val base64Img = imageBitmap.toBase64()
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Img)))
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                responseFormat = ResponseFormat(
                    text = ResponseFormatText(
                        mimeType = "application/json",
                        schema = buildJsonObject {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("title") { put("type", "STRING"); put("description", "A short title for the action.") }
                                putJsonObject("description") { put("type", "STRING"); put("description", "A detailed description or summary of what needs to be done.") }
                                putJsonObject("urgencyRisk") { put("type", "INTEGER"); put("description", "0-30 score.") }
                                putJsonObject("financial") { put("type", "INTEGER"); put("description", "0-25 score.") }
                                putJsonObject("goalFit") { put("type", "INTEGER"); put("description", "0-20 score.") }
                                putJsonObject("unblock") { put("type", "INTEGER"); put("description", "0-15 score.") }
                                putJsonObject("contextFit") { put("type", "INTEGER"); put("description", "0-10 score.") }
                                putJsonObject("uncertainty") { put("type", "INTEGER"); put("description", "0-20 penalty.") }
                                putJsonObject("effortMismatch") { put("type", "INTEGER"); put("description", "0-15 penalty.") }
                                putJsonObject("isHardDeadline") { put("type", "BOOLEAN") }
                            }
                            putJsonArray("required") {
                                add("title"); add("description"); add("urgencyRisk"); add("financial"); add("goalFit"); add("unblock"); add("contextFit"); add("uncertainty"); add("effortMismatch"); add("isHardDeadline")
                            }
                        }
                    )
                ),
                thinkingConfig = ThinkingConfig(thinkingLevel = "HIGH")
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = "You are the Mission Engine of a Personal Navigator. You receive unstructured input (text, speech transcript, images) and extract a reasoned, actionable next step. Evaluate the deterministic scores strictly according to the provided maximums. Return a JSON object with the requested fields."))
            )
        )

        try {
            // Using Pro if there's an image or complex reasoning needed
            val response = if (imageBitmap != null) {
                RetrofitClient.service.generateContentPro(apiKey, request)
            } else {
                RetrofitClient.service.generateContentPro(apiKey, request) // Always use pro for high thinking
            }
            
            val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonText).jsonObject
                return@withContext ActionEntity(
                    title = json["title"]?.jsonPrimitive?.content ?: "Unknown",
                    description = json["description"]?.jsonPrimitive?.content ?: "",
                    urgencyRisk = json["urgencyRisk"]?.jsonPrimitive?.int ?: 0,
                    financial = json["financial"]?.jsonPrimitive?.int ?: 0,
                    goalFit = json["goalFit"]?.jsonPrimitive?.int ?: 0,
                    unblock = json["unblock"]?.jsonPrimitive?.int ?: 0,
                    contextFit = json["contextFit"]?.jsonPrimitive?.int ?: 0,
                    uncertainty = json["uncertainty"]?.jsonPrimitive?.int ?: 0,
                    effortMismatch = json["effortMismatch"]?.jsonPrimitive?.int ?: 0,
                    isHardDeadline = json["isHardDeadline"]?.jsonPrimitive?.boolean ?: false,
                    source = if (imageBitmap != null) "Image" else "Text"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
