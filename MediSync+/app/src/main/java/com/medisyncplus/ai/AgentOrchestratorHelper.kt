package com.medisyncplus.ai

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object AgentOrchestratorHelper {
    private const val TAG = "AgentHelper"
    private const val MAX_RETRIES = 2

    suspend fun runAgentWithTools(
        llmService: LlmApiService,
        toolRegistry: AgentToolRegistry,
        gson: Gson,
        systemPrompt: String,
        userMessage: String,
        maxToolRounds: Int = 3,
        authHeader: String = "",
        model: String = "",
        requiredJsonFields: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        var retryCount = 0
        var lastResponse = ""
        
        val effectiveAuth = authHeader.ifEmpty {
            try { "Bearer ${com.medisyncplus.BuildConfig.LLM_API_KEY}" } catch (e: Exception) { "" }
        }
        val effectiveModel = model.ifEmpty {
            try { com.medisyncplus.BuildConfig.LLM_MODEL } catch (e: Exception) { "GLM-5.1" }
        }

        while (retryCount <= MAX_RETRIES) {
            val messages = mutableListOf(
                LlmMessage("system", systemPrompt),
                LlmMessage("user", if (retryCount > 0) "Your previous response was invalid. Please try again, being more specific and following the required structure exactly.\n\n$userMessage" else userMessage)
            )

            var round = 0
            while (round < maxToolRounds) {
                try {
                    val response = llmService.complete(
                        effectiveAuth,
                        LlmRequest(model = effectiveModel, messages = messages, maxTokens = 1500, temperature = 0.3 + (retryCount * 0.1))
                    )
                    lastResponse = response.choices.firstOrNull()?.message?.content ?: ""
                    Log.d(TAG, "LLM round $round response: ${lastResponse.take(200)}")
                } catch (e: Exception) {
                    Log.e(TAG, "LLM call failed: ${e.message}")
                    break
                }

                val toolCalls = parseToolCalls(lastResponse)
                if (toolCalls.isEmpty()) break

                val toolResults = StringBuilder()
                toolResults.appendLine("TOOL_RESULTS:")
                for (call in toolCalls) {
                    val result = toolRegistry.dispatch(call)
                    toolResults.appendLine("${call.name}: ${if (result.success) gson.toJson(result.data) else "ERROR: ${result.error}"}")
                }

                messages.add(LlmMessage("assistant", lastResponse))
                messages.add(LlmMessage("user", toolResults.toString() + "\n\nNow provide your final response based on the tool results above."))
                round++
            }

            // Validation
            val isValid = ResponseValidator.isValid(lastResponse) && 
                          ResponseValidator.isRelevant(lastResponse, userMessage) &&
                          (requiredJsonFields.isEmpty() || ResponseValidator.hasRequiredStructure(lastResponse, requiredJsonFields))

            if (isValid) {
                return@withContext lastResponse
            } else {
                retryCount++
                Log.w(TAG, "Validation failed for response. Retry $retryCount/$MAX_RETRIES")
            }
        }

        // Final Fallback if all retries fail
        Log.e(TAG, "All retries failed for agent call. Returning fallback.")
        if (requiredJsonFields.isNotEmpty()) {
            "{\"status\": \"fallback_triggered\", \"message\": \"We could not confidently process your input. Please try again or contact support.\", \"escalation_required\": ${ResponseValidator.isHighRisk(userMessage)}}"
        } else {
            "I'm sorry, I'm having trouble processing that right now. Please try rephrasing or contact your care team if this is urgent."
        }
    }

    fun parseToolCalls(text: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        val regex = Regex("""TOOL_CALL:\s*(\{.*\})""")
        for (match in regex.findAll(text)) {
            try {
                val json = JSONObject(match.groupValues[1])
                val name = json.getString("name")
                val params = if (json.has("parameters")) {
                    val paramsObj = json.getJSONObject("parameters")
                    val map = mutableMapOf<String, Any?>()
                    for (key in paramsObj.keys()) { map[key] = paramsObj.get(key) }
                    map
                } else emptyMap()
                calls.add(ToolCall(name, params))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool call: ${match.value}")
            }
        }
        return calls
    }

    fun parseJson(text: String): JSONObject? = try {
        val clean = text
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        val start = clean.indexOf('{')
        val end   = clean.lastIndexOf('}')
        if (start >= 0 && end > start) JSONObject(clean.substring(start, end + 1)) else null
    } catch (e: Exception) {
        Log.e(TAG, "JSON parse failed: ${e.message}")
        null
    }
}
