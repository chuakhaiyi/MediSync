package com.medisyncplus.ai

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// ─── Request / Response models ────────────────────────────────────────────────
data class LlmMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String
)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 10000,
    val temperature: Double = 0.3,
    val stream: Boolean = false
)

data class LlmChoice(
    val index: Int,
    val message: LlmMessage,
    @SerializedName("finish_reason") val finishReason: String?
)

data class LlmUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

data class LlmResponse(
    val id: String?,
    val `object`: String?,
    val model: String?,
    val choices: List<LlmChoice>,
    val usage: LlmUsage?
)

// ─── Retrofit service ─────────────────────────────────────────────────────────
interface LlmApiService {
    @POST("chat/completions")
    suspend fun complete(
        @Header("Authorization") authHeader: String,
        @Body request: LlmRequest
    ): LlmResponse
}
