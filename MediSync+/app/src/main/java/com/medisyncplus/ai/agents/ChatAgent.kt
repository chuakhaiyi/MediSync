package com.medisyncplus.ai.agents

import android.util.Log
import com.medisyncplus.ai.*
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import com.google.gson.Gson
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatAgent @Inject constructor(
    private val llmService: LlmApiService,
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository,
    private val gson: Gson
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)

    companion object { private const val TAG = "ChatAgent" }

    suspend fun respond(
        userMessage: String,
        conversationHistory: List<ChatMessageEntity>
    ): ChatResponse {
        val historyText = conversationHistory.takeLast(10).joinToString("\n") {
            "${it.role.uppercase()}: ${it.content}"
        }
        val userMsg = buildString {
            if (historyText.isNotEmpty()) {
                appendLine("CONVERSATION HISTORY:")
                appendLine(historyText)
                appendLine()
            }
            appendLine("PATIENT: $userMessage")
            appendLine()
            appendLine("Before responding, call get_patient_profile, get_medications, and get_appointments to provide accurate personalised answers.")
        }

        val rawResponse = AgentOrchestratorHelper.runAgentWithTools(
            llmService, toolRegistry, gson,
            systemPrompt = AgentPrompts.chatAgentSystem + "\n\n" + toolRegistry.toolManifest,
            userMessage = userMsg,
            maxToolRounds = 2
        )

        // Strip all agent-internal markers from the visible response
        var visibleResponse = rawResponse
            .stripToolCallBlocks()
            .replace(Regex("""TOOL_RESULTS:.*?(?=\n[A-Z]|\z)""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""SYMPTOM_RECORD:\s*\{.*?\}""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""APPOINTMENT_REQUEST:\s*\{.*?\}""", RegexOption.DOT_MATCHES_ALL), "")
            .trim()

        var recordedSymptom = false
        var requestedAppt = false
        var riskFlag = "STABLE"

// Handle SYMPTOM_RECORD (parse from rawResponse, NOT visibleResponse)
        val symptomMatch = Regex("""SYMPTOM_RECORD:\s*(\{.*?\})""", RegexOption.DOT_MATCHES_ALL).find(rawResponse)
        if (symptomMatch != null) {
            try {
                val sJson = JSONObject(symptomMatch.groupValues[1])
                val symptoms = buildList {
                    val arr = sJson.optJSONArray("symptoms")
                    if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
                }
                val severity = sJson.optInt("severity", 1)
                toolRegistry.dispatch(ToolCall("record_symptom", mapOf(
                    "symptoms" to symptoms,
                    "severity" to severity.toDouble(),
                    "note" to "Reported via chat: $userMessage",
                    "source" to "chat",
                    "riskLevel" to if (severity >= 3) "CRITICAL" else if (severity >= 2) "WARNING" else "STABLE",
                    "reason" to "Chat agent symptom detection",
                    "action" to "Monitor and alert care team",
                    "escalate" to (severity >= 3)
                )))
                recordedSymptom = true
                riskFlag = if (severity >= 3) "CRITICAL" else if (severity >= 2) "WARNING" else "STABLE"
            } catch (e: Exception) {
                Log.w(TAG, "Chat symptom parse error: ${e.message}")
            }
        }

// Handle APPOINTMENT_REQUEST (parse from rawResponse, NOT visibleResponse)
        val apptMatch = Regex("""APPOINTMENT_REQUEST:\s*(\{.*?\})""", RegexOption.DOT_MATCHES_ALL).find(rawResponse)
        if (apptMatch != null) {
            try {
                val aJson = JSONObject(apptMatch.groupValues[1])
                toolRegistry.dispatch(ToolCall("request_appointment", mapOf(
                    "reason" to aJson.optString("reason", "Patient requested via chat"),
                    "preferredDate" to LocalDate.now().plusDays(1).format(dateFmt),
                    "notes" to "Requested by chat agent",
                    "urgency" to aJson.optString("urgency", "ROUTINE")
                )))
                requestedAppt = true
            } catch (e: Exception) {
                Log.w(TAG, "Chat appointment parse error: ${e.message}")
            }
        }

        if (visibleResponse.isEmpty()) {
            visibleResponse = "I'm here for you, Margaret. For urgent concerns, please call your care team at Ward 4B (03-1234 5678) or dial 999."
        }

        return ChatResponse(
            text = visibleResponse,
            recordedSymptom = recordedSymptom,
            requestedAppointment = requestedAppt,
            riskFlag = riskFlag
        )
    }
}

/**
 * Strips all TOOL_CALL: { ... } blocks from LLM output,
 * correctly handling nested JSON braces.
 */
private fun String.stripToolCallBlocks(): String {
    val result = StringBuilder()
    var i = 0
    while (i < length) {
        // Look for the TOOL_CALL: marker
        val markerIdx = indexOf("TOOL_CALL:", i)
        if (markerIdx == -1) {
            result.append(substring(i))
            break
        }
        // Append text before the marker
        result.append(substring(i, markerIdx))

        // Find the opening brace
        val braceStart = indexOf('{', markerIdx)
        if (braceStart == -1) {
            result.append(substring(markerIdx))
            break
        }

        // Walk forward counting brace depth
        var depth = 0
        var j = braceStart
        while (j < length) {
            when (this[j]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) { j++; break }
                }
            }
            j++
        }
        // Skip the entire TOOL_CALL block (including any trailing newline)
        i = if (j < length && this[j] == '\n') j + 1 else j
    }
    return result.toString()
}