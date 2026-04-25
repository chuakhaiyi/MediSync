package com.medisyncplus.ai

import android.util.Log
import com.medisyncplus.ai.agents.*
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import com.google.gson.Gson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediSyncOrchestrator"

// ─── Agent result types ───────────────────────────────────────────────────────
data class SymptomAnalysisResult(
    val riskLevel: String,
    val riskReason: String,
    val detectedSymptoms: List<String>,
    val recommendedAction: String,
    val escalateImmediately: Boolean,
    val bookAppointment: Boolean,
    val appointmentReason: String?,
    val emrUpdateRequired: Boolean,
    val agentNotes: String
)

data class AdherenceResult(
    val adherenceScore: Int,
    val adherenceLabel: String,
    val missedMedications: List<MissedMedInfo>,
    val riskFromAdherence: String,
    val alertRequired: Boolean,
    val alertMessage: String,
    val patientMessage: String,
    val emrUpdateRequired: Boolean
)

data class MissedMedInfo(val name: String, val missedStreak: Int, val critical: Boolean)

data class ChatResponse(
    val text: String,
    val recordedSymptom: Boolean,
    val requestedAppointment: Boolean,
    val riskFlag: String
)

data class RiskTrajectory(
    val trajectory: String,
    val trajectoryReason: String,
    val predictedRiskIn48h: String,
    val keyRiskFactors: List<String>,
    val recommendedMonitoringFrequency: String,
    val interventionRequired: Boolean,
    val interventionType: String
)

// ─── Orchestrator ─────────────────────────────────────────────────────────────
@Singleton
class AgentOrchestrator @Inject constructor(
    private val llmService: LlmApiService,
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository,
    private val gson: Gson,
    private val medicineAgent: MedicineAgent,
    private val followUpAgent: FollowUpAgent,
    private val symptomAgent: SymptomAgent,
    private val chatAgent: ChatAgent,
    private val checklistAgent: ChecklistAgent,
    private val riskTrajectoryAgent: RiskTrajectoryAgent,
    private val dischargeAgent: DischargeAgent
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)

    private val authHeader get() = "Bearer ${com.medisyncplus.BuildConfig.LLM_API_KEY}"
    private val model get() = com.medisyncplus.BuildConfig.LLM_MODEL

    // ─── Delegated agent calls ────────────────────────────────────────────────
    suspend fun runMedicineAgent(): MedicineAgentResult = medicineAgent.analyse()
    suspend fun runMedicineAgentLlm(): MedicineAgentResult = medicineAgent.runLlmAnalysis()
    suspend fun runFollowUpAgent(): FollowUpResult = followUpAgent.analyse()
    suspend fun runChecklistAgent(): ChecklistAgentResult = checklistAgent.analyse()
    suspend fun runRiskTrajectoryAgent(): RiskTrajectory = riskTrajectoryAgent.analyse()
    suspend fun runDischargeAgent(note: String): DischargeInterpretation = dischargeAgent.interpretDischargeNote(note)

    // ─── Symptom Agent (delegated) ─────────────────────────────────────────────
    suspend fun runSymptomAgent(
        symptoms: List<String>,
        severity: Int,
        note: String,
        patientId: String
    ): SymptomAnalysisResult = symptomAgent.analyse(symptoms, severity, note, patientId)

    // ─── Adherence Agent (compat wrapper) ──────────────────────────────────────
    suspend fun runAdherenceAgent(): AdherenceResult {
        val medResult = medicineAgent.runLlmAnalysis()
        return AdherenceResult(
            adherenceScore = medResult.adherenceScore,
            adherenceLabel = medResult.adherenceLabel,
            missedMedications = medResult.missedMedications,
            riskFromAdherence = medResult.riskFromAdherence,
            alertRequired = medResult.alertRequired,
            alertMessage = medResult.alertMessage,
            patientMessage = medResult.patientMessage,
            emrUpdateRequired = medResult.emrUpdateRequired
        )
    }

    // ─── Chat Agent (delegated) ───────────────────────────────────────────────
    suspend fun runChatAgent(
        userMessage: String,
        conversationHistory: List<ChatMessageEntity>
    ): ChatResponse = chatAgent.respond(userMessage, conversationHistory)

    // ─── Master Orchestration ──────────────────────────────────────────────────
    suspend fun runFullMorningCheck(): MorningCheckResult {
        val medResult = runMedicineAgent()
        val followUpResult = runFollowUpAgent()
        val checklistResult = runChecklistAgent()
        val trajectoryResult = try { runRiskTrajectoryAgent() } catch (e: Exception) { riskTrajectoryAgent.defaultTrajectory() }

        val overallRisk = determineOverallRisk(medResult, followUpResult, trajectoryResult)

        return MorningCheckResult(
            medicineResult = medResult,
            followUpResult = followUpResult,
            checklistResult = checklistResult,
            riskTrajectory = trajectoryResult,
            overallRisk = overallRisk,
            summary = buildMorningSummary(medResult, followUpResult, checklistResult, trajectoryResult, overallRisk)
        )
    }

    private fun determineOverallRisk(
        med: MedicineAgentResult,
        followUp: FollowUpResult,
        trajectory: RiskTrajectory
    ): String {
        val risks = listOf(med.riskFromAdherence, trajectory.predictedRiskIn48h)
        return when {
            risks.contains("CRITICAL") -> "CRITICAL"
            risks.contains("WARNING") -> "WARNING"
            followUp.shouldAlert -> "WARNING"
            else -> "STABLE"
        }
    }

    private fun buildMorningSummary(
        med: MedicineAgentResult,
        followUp: FollowUpResult,
        checklist: ChecklistAgentResult,
        trajectory: RiskTrajectory,
        overallRisk: String
    ): String = buildString {
        append("Morning check: ")
        append("Adherence ${med.adherenceScore}% (${med.adherenceLabel}). ")
        if (med.missedMedications.isNotEmpty()) {
            append("Missed: ${med.missedMedications.joinToString { it.name }}. ")
        }
        append("${checklist.completedCount}/${checklist.totalCount} tasks done. ")
        followUp.nextAppointment?.let {
            append("Next visit: ${it.doctorName} in ${followUp.daysUntilNext} days. ")
        }
        append("Risk trajectory: ${trajectory.trajectory}. ")
        append("Overall: $overallRisk.")
    }
}

data class MorningCheckResult(
    val medicineResult: MedicineAgentResult,
    val followUpResult: FollowUpResult,
    val checklistResult: ChecklistAgentResult,
    val riskTrajectory: RiskTrajectory,
    val overallRisk: String,
    val summary: String
)
