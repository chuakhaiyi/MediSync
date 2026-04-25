package com.medisyncplus.ai

import android.util.Log
import org.json.JSONObject

object ResponseValidator {
    private const val TAG = "ResponseValidator"
    private const val MIN_LENGTH = 10

    /**
     * Validates whether the AI response is usable.
     */
    fun isValid(response: String): Boolean {
        if (response.isBlank() || response.length < MIN_LENGTH) {
            Log.w(TAG, "Response failed: Too short or blank")
            return false
        }

        val hallucinations = listOf(
            "i am an ai", "as an ai", "my knowledge cutoff", 
            "i cannot fulfill this request", "unrelated advice"
        )
        if (hallucinations.any { response.lowercase().contains(it) }) {
            Log.w(TAG, "Response failed: Generic AI hallucination detected")
            return false
        }

        return true
    }

    /**
     * Checks if the response is relevant to the input context.
     */
    fun isRelevant(response: String, context: String): Boolean {
        val contextKeywords = context.lowercase().split(Regex("\\s+"))
            .filter { it.length > 4 }
            .take(10)
        
        if (contextKeywords.isEmpty()) return true // No context to validate against

        val responseLower = response.lowercase()
        // If the response doesn't share ANY meaningful keywords with the context, it might be off-topic
        val matches = contextKeywords.count { responseLower.contains(it) }
        
        return matches > 0 || responseLower.contains("symptom") || responseLower.contains("health")
    }

    /**
     * Ensures the response contains expected fields if it's JSON.
     */
    fun hasRequiredStructure(response: String, requiredFields: List<String>): Boolean {
        val json = AgentOrchestratorHelper.parseJson(response) ?: return false
        return requiredFields.all { json.has(it) }
    }

    fun isHighRisk(input: String): Boolean {
        val highRiskKeywords = listOf("chest pain", "cannot breathe", "stroke", "suicide", "bleeding", "severe", "critical")
        return highRiskKeywords.any { input.lowercase().contains(it) }
    }
}
