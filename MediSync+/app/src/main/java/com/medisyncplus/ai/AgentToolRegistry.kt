package com.medisyncplus.ai

import android.util.Log
import com.google.gson.Gson
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val data: Any?,
    val error: String? = null
)

data class ToolCall(
    val name: String,
    val parameters: Map<String, Any?> = emptyMap()
)

@Singleton
class AgentToolRegistry @Inject constructor(
    private val repo: MediSyncRepository,
    private val gson: Gson
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)
    private val patientId = "P001"

    companion object { private const val TAG = "ToolRegistry" }

    // ── Tool manifest (sent to LLM as system context) ─────────────────────────
    val toolManifest = """
AVAILABLE TOOLS (call by including a JSON block in your response):

1. get_patient_profile()
   → Returns patient demographics, condition, risk level, discharge date.

2. get_medications()
   → Returns all active prescribed medications with dosage and schedule.

3. get_medication_logs(date?: "yyyy-MM-dd")
   → Returns today's (or specified date's) medication taken/missed records.

4. get_vitals(type?: "weight|bp|blood_sugar|pulse|spo2|temperature", limit?: int)
   → Returns recent vital sign records. Omit type for all types.

5. get_symptom_reports(limit?: int)
   → Returns recent patient symptom reports with agent risk assessments.

6. get_appointments()
   → Returns upcoming confirmed and pending appointments.

7. get_checklist(date?: "yyyy-MM-dd")
   → Returns today's care checklist tasks and completion status.

8. get_hospital_history()
   → Returns past hospital admissions, diagnoses and discharge notes.

9. get_care_team_reminders()
   → Returns messages from doctors and nurses to the patient.

10. get_care_plan()
    → Returns the parsed discharge care plan (fluid restriction, salt, activity level, red flags).

11. get_next_medication()
    → Returns the next upcoming medication and its scheduled time.

12. mark_medication_taken(logId: long, medicationId: string)
    → Records that patient has taken a specific medication dose.
    → Side effect: updates adherence score, clears missed streak if any.

13. record_vital(type: string, value?: float, systolic?: int, diastolic?: int, unit: string, notes?: string)
    → Saves a new vital sign reading to database and queues hospital sync.
    → Types: weight | bp | blood_sugar | pulse | spo2 | temperature

14. record_symptom(symptoms: string[], severity: int, note: string, source: string)
    → Saves symptom report. Triggers EMR proposal if risk >= WARNING.

15. complete_checklist_task(taskId: string)
    → Marks a daily checklist task as done.

16. propose_emr_update(type: string, changes: object, justification: string, urgency: string, requiresReview: bool)
    → Submits a proposed change to the hospital EMR for clinician review.
    → NEVER writes directly to hospital — always proposes for review.
    → types: RISK_FLAG | SYMPTOM_RECORD | MEDICATION_UPDATE | CARE_PLAN_REVISION | APPOINTMENT

17. send_clinician_alert(message: string, urgency: string, alertType: string)
    → Sends an urgent notification to the attending care team.
    → urgency: ROUTINE | URGENT | IMMEDIATE
    → alertType: MEDICATION_MISS | SYMPTOM_ESCALATION | RISK_CHANGE | SOS | APPOINTMENT

18. request_appointment(reason: string, preferredDate: string, notes: string, urgency: string)
    → Creates an appointment request for care team to confirm.

19. log_agent_decision(agentId: string, action: string, input: string, output: string, riskLevel: string, tools: string[])
    → Records an agent's decision in the audit trail for transparency.

To call a tool, include this in your response (you may call multiple):
TOOL_CALL: {"name": "tool_name", "parameters": {"key": "value"}}

After tool results are provided, continue your response normally.
""".trimIndent()

    // ── Dispatcher ────────────────────────────────────────────────────────────
    suspend fun dispatch(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_patient_profile"      -> getPatientProfile()
                "get_medications"          -> getMedications()
                "get_medication_logs"      -> getMedicationLogs(call.parameters)
                "get_vitals"               -> getVitals(call.parameters)
                "get_symptom_reports"      -> getSymptomReports(call.parameters)
                "get_appointments"         -> getAppointments()
                "get_checklist"            -> getChecklist(call.parameters)
                "get_hospital_history"     -> getHospitalHistory()
                "get_care_team_reminders"  -> getCareTeamReminders()
                "get_care_plan"            -> getCarePlan()
                "get_next_medication"      -> getNextMedication()
                "mark_medication_taken"    -> markMedicationTaken(call.parameters)
                "record_vital"             -> recordVital(call.parameters)
                "record_symptom"           -> recordSymptom(call.parameters)
                "complete_checklist_task"  -> completeChecklistTask(call.parameters)
                "propose_emr_update"       -> proposeEmrUpdate(call.parameters)
                "send_clinician_alert"     -> sendClinicianAlert(call.parameters)
                "request_appointment"      -> requestAppointment(call.parameters)
                "log_agent_decision"       -> logAgentDecision(call.parameters)
                else -> ToolResult(call.name, false, null, "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            ToolResult(call.name, false, null, "Tool error: ${e.message}")
        }
    }

    // ── Read Tools ────────────────────────────────────────────────────────────
    private suspend fun getPatientProfile(): ToolResult {
        val patient = repo.getPatientOnce(patientId)
        return ToolResult("get_patient_profile", patient != null, patient)
    }

    private suspend fun getMedications(): ToolResult {
        val meds = repo.getActiveMedicationsOnce(patientId)
        return ToolResult("get_medications", true, meds)
    }

    private suspend fun getMedicationLogs(params: Map<String, Any?>): ToolResult {
        val date = params["date"] as? String ?: today()
        val logs = repo.getMedLogsForDateOnce(patientId, date)
        val meds = repo.getActiveMedicationsOnce(patientId)
        val enriched = logs.map { log ->
            val med = meds.find { it.id == log.medicationId }
            mapOf(
                "logId" to log.id,
                "medicationId" to log.medicationId,
                "medicationName" to (med?.name ?: "Unknown"),
                "dosage" to (med?.dosage ?: ""),
                "scheduledTime" to log.scheduledTime,
                "status" to log.status,
                "takenAt" to log.takenAt,
                "missedStreak" to log.missedStreak,
                "criticalMedication" to (med?.criticalMedication ?: false)
            )
        }
        return ToolResult("get_medication_logs", true, enriched)
    }

    private suspend fun getVitals(params: Map<String, Any?>): ToolResult {
        val type  = params["type"] as? String
        val limit = (params["limit"] as? Double)?.toInt() ?: 10
        val vitals = if (type != null) {
            repo.getRecentVitals(patientId, limit).filter { it.type == type }
        } else {
            repo.getRecentVitals(patientId, limit)
        }
        val weightTrend = if (type == null || type == "weight") {
            val weights = repo.getWeightSince(patientId, LocalDate.now().minusDays(7).format(dateFmt))
            if (weights.size >= 2) {
                val delta = (weights.last().value ?: 0f) - (weights.first().value ?: 0f)
                when {
                    delta > 2.0f  -> "INCREASING_ALERT"
                    delta > 0.5f  -> "INCREASING"
                    delta < -0.5f -> "DECREASING"
                    else          -> "STABLE"
                }
            } else "INSUFFICIENT_DATA"
        } else null

        return ToolResult("get_vitals", true, mapOf("vitals" to vitals, "weightTrend" to weightTrend))
    }

    private suspend fun getSymptomReports(params: Map<String, Any?>): ToolResult {
        val limit = (params["limit"] as? Double)?.toInt() ?: 10
        val reports = repo.getRecentSymptomReports(patientId, limit)
        return ToolResult("get_symptom_reports", true, reports)
    }

    private suspend fun getAppointments(): ToolResult {
        val appt = repo.getNextAppointment(patientId)
        return ToolResult("get_appointments", true, mapOf("nextAppointment" to appt))
    }

    private suspend fun getChecklist(params: Map<String, Any?>): ToolResult {
        val date = params["date"] as? String ?: today()
        val tasks = repo.getChecklistForDateOnce(patientId, date)
        val done = tasks.count { it.isDone }
        return ToolResult("get_checklist", true, mapOf(
            "tasks" to tasks,
            "completedCount" to done,
            "totalCount" to tasks.size,
            "percentComplete" to if (tasks.isEmpty()) 0 else done * 100 / tasks.size
        ))
    }

    private suspend fun getHospitalHistory(): ToolResult {
        val stays = mutableListOf<Any>()
        repo.getHospitalStays(patientId).collect { stays.addAll(it); return@collect }
        return ToolResult("get_hospital_history", true, stays)
    }

    private suspend fun getCareTeamReminders(): ToolResult {
        val reminders = mutableListOf<Any>()
        repo.getReminders(patientId).collect { reminders.addAll(it); return@collect }
        return ToolResult("get_care_team_reminders", true, reminders)
    }

    private suspend fun getCarePlan(): ToolResult {
        val stay = repo.getActiveStay(patientId)
            ?: return ToolResult("get_care_plan", false, null, "No active/recent hospital stay found")
        val parsed = stay.dischargeNoteParsed
        return ToolResult("get_care_plan", true, mapOf(
            "stayId" to stay.id,
            "primaryDiagnosis" to stay.primaryDiagnosis,
            "carePlan" to parsed,
            "dischargeNoteRaw" to stay.dischargeNoteRaw
        ))
    }

    private suspend fun getNextMedication(): ToolResult {
        val meds = repo.getActiveMedicationsOnce(patientId)
        val logs = repo.getMedLogsForDateOnce(patientId, today())
        val upcomingLogs = logs.filter { it.status == "UPCOMING" }
        val nextLog = upcomingLogs.firstOrNull()
        val nextMed = nextLog?.let { l -> meds.find { it.id == l.medicationId } }
        return ToolResult("get_next_medication", true, mapOf(
            "medication" to nextMed,
            "log" to nextLog,
            "scheduledTime" to (nextMed?.scheduledTime ?: "No upcoming medication")
        ))
    }

    // ── Write Tools ───────────────────────────────────────────────────────────
    private suspend fun markMedicationTaken(params: Map<String, Any?>): ToolResult {
        val logId = (params["logId"] as? Double)?.toLong()
            ?: return ToolResult("mark_medication_taken", false, null, "logId required")
        repo.markMedicationTaken(logId)
        val score = repo.calculateAdherenceScore(patientId)
        repo.updateAdherenceScore(patientId, score)
        return ToolResult("mark_medication_taken", true, mapOf("updatedAdherenceScore" to score))
    }

    private suspend fun recordVital(params: Map<String, Any?>): ToolResult {
        val type = params["type"] as? String
            ?: return ToolResult("record_vital", false, null, "type required")
        val vital = VitalEntity(
            patientId = patientId,
            type = type,
            value = (params["value"] as? Double)?.toFloat(),
            systolic = (params["systolic"] as? Double)?.toInt(),
            diastolic = (params["diastolic"] as? Double)?.toInt(),
            unit = params["unit"] as? String ?: "",
            flag = computeVitalFlag(type, params),
            recordedAt = now(),
            recordedBy = "patient",
            notes = params["notes"] as? String ?: ""
        )
        val id = repo.insertVital(vital)
        if (vital.flag == "warning" || vital.flag == "critical") {
            proposeEmrUpdate(mapOf(
                "type" to "RISK_FLAG",
                "changes" to mapOf("vitalType" to type, "flag" to vital.flag),
                "justification" to "Patient vital reading flagged: $type = ${vital.value ?: "${vital.systolic}/${vital.diastolic}"} ${vital.unit}",
                "urgency" to if (vital.flag == "critical") "IMMEDIATE" else "ROUTINE",
                "requiresReview" to (vital.flag == "critical")
            ))
        }
        return ToolResult("record_vital", true, mapOf("id" to id, "flag" to vital.flag))
    }

    private fun computeVitalFlag(type: String, params: Map<String, Any?>): String {
        return when (type) {
            "weight" -> {
                val v = (params["value"] as? Double)?.toFloat() ?: return "normal"
                when { v > 85f || v < 45f -> "warning"; else -> "normal" }
            }
            "bp" -> {
                val sys = (params["systolic"] as? Double)?.toInt() ?: return "normal"
                val dia = (params["diastolic"] as? Double)?.toInt() ?: return "normal"
                when {
                    sys >= 180 || dia >= 110 -> "critical"
                    sys >= 140 || dia >= 90  -> "warning"
                    else                      -> "normal"
                }
            }
            "blood_sugar" -> {
                val v = (params["value"] as? Double) ?: return "normal"
                when { v >= 11.0 || v < 3.9 -> "critical"; v > 7.8 || v < 4.4 -> "warning"; else -> "normal" }
            }
            "pulse" -> {
                val v = (params["value"] as? Double)?.toInt() ?: return "normal"
                when {
                    v >= 120 || v <= 50 -> "critical"
                    v > 100 || v < 60  -> "warning"
                    else                -> "normal"
                }
            }
            "spo2" -> {
                val v = (params["value"] as? Double)?.toFloat() ?: return "normal"
                when { v < 90f -> "critical"; v < 94f -> "warning"; else -> "normal" }
            }
            "temperature" -> {
                val v = (params["value"] as? Double)?.toFloat() ?: return "normal"
                when {
                    v >= 39.0f || v <= 35.0f -> "critical"
                    v > 37.5f || v < 36.0f  -> "warning"
                    else                    -> "normal"
                }
            }
            else -> "normal"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun recordSymptom(params: Map<String, Any?>): ToolResult {
        val symptoms = params["symptoms"] as? List<String> ?: listOf(params["symptoms"] as? String ?: "")
        val severity = (params["severity"] as? Double)?.toInt() ?: 1
        val note = params["note"] as? String ?: ""
        val source = params["source"] as? String ?: "agent"
        val riskLevel = params["riskLevel"] as? String ?: "STABLE"
        val reason = params["reason"] as? String ?: ""
        val action = params["action"] as? String ?: "Monitor"
        val escalate = params["escalate"] as? Boolean ?: false

        val report = SymptomReportEntity(
            patientId = patientId,
            symptoms = gson.toJson(symptoms),
            severity = severity,
            additionalNote = note,
            agentRiskLevel = riskLevel,
            agentReason = reason,
            agentRecommendedAction = action,
            escalatedImmediately = escalate,
            appointmentBooked = false,
            reportedAt = now(),
            source = source
        )
        val id = repo.insertSymptomReport(report)

        if (riskLevel != "STABLE") {
            proposeEmrUpdate(mapOf(
                "type" to "SYMPTOM_RECORD",
                "changes" to mapOf("symptoms" to symptoms, "severity" to severity, "riskLevel" to riskLevel),
                "justification" to "Agent-analysed symptom report: $reason",
                "urgency" to if (riskLevel == "CRITICAL") "IMMEDIATE" else "URGENT",
                "requiresReview" to (riskLevel == "CRITICAL")
            ))
        }
        return ToolResult("record_symptom", true, mapOf("id" to id, "riskLevel" to riskLevel))
    }

    private suspend fun completeChecklistTask(params: Map<String, Any?>): ToolResult {
        val taskId = params["taskId"] as? String
            ?: return ToolResult("complete_checklist_task", false, null, "taskId required")
        repo.completeTask(taskId)
        val pending = repo.countPendingTasks(patientId)
        return ToolResult("complete_checklist_task", true, mapOf("remainingTasks" to pending))
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun proposeEmrUpdate(params: Map<String, Any?>): ToolResult {
        val type = params["type"] as? String ?: "GENERAL"
        val changes = params["changes"]
        val justification = params["justification"] as? String ?: ""
        val urgency = params["urgency"] as? String ?: "ROUTINE"
        val requiresReview = params["requiresReview"] as? Boolean ?: true
        val agentId = params["agentId"] as? String ?: "orchestrator"

        val proposal = EmrProposalEntity(
            patientId = patientId,
            proposalType = type,
            proposedChanges = gson.toJson(changes),
            justification = justification,
            urgency = urgency,
            requiresClinicianReview = requiresReview,
            autoApprovable = !requiresReview,
            status = if (requiresReview) "PENDING" else "APPROVED",
            createdAt = now(),
            reviewedAt = if (!requiresReview) now() else null,
            reviewedBy = if (!requiresReview) "auto" else null,
            agentId = agentId
        )
        val id = repo.insertEmrProposal(proposal)
        return ToolResult("propose_emr_update", true, mapOf("proposalId" to id, "status" to proposal.status))
    }

    private suspend fun sendClinicianAlert(params: Map<String, Any?>): ToolResult {
        val message = params["message"] as? String ?: ""
        val urgency = params["urgency"] as? String ?: "URGENT"
        val alertType = params["alertType"] as? String ?: "GENERAL"
        val alertId = "ALERT_${System.currentTimeMillis()}"
        proposeEmrUpdate(mapOf(
            "type" to "RISK_FLAG",
            "changes" to mapOf("alertType" to alertType, "message" to message),
            "justification" to "Agent sent clinician alert: $message",
            "urgency" to urgency,
            "requiresReview" to true,
            "agentId" to "orchestrator"
        ))
        return ToolResult("send_clinician_alert", true, mapOf("alertId" to alertId, "urgency" to urgency))
    }

    private suspend fun requestAppointment(params: Map<String, Any?>): ToolResult {
        val reason = params["reason"] as? String ?: ""
        val preferredDate = params["preferredDate"] as? String ?: ""
        val notes = params["notes"] as? String ?: ""
        val urgency = params["urgency"] as? String ?: "ROUTINE"
        val apptId = "APPT_REQ_${System.currentTimeMillis()}"
        val appt = AppointmentEntity(
            id = apptId, patientId = patientId,
            doctorId = "D001", doctorName = "Dr. Amir Hassan",
            specialty = "Cardiologist",
            dateTime = "$preferredDate 00:00",
            location = "To be confirmed",
            appointmentType = if (urgency == "IMMEDIATE") "EMERGENCY" else "FOLLOW_UP",
            status = "PENDING",
            notes = "$reason. $notes",
            reminderSet = false,
            createdAt = now(),
            source = "agent"
        )
        repo.upsertAppointment(appt)
        sendClinicianAlert(mapOf(
            "message" to "Patient requesting appointment: $reason (Preferred: $preferredDate)",
            "urgency" to urgency,
            "alertType" to "APPOINTMENT"
        ))
        return ToolResult("request_appointment", true, mapOf("appointmentId" to apptId, "status" to "PENDING"))
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun logAgentDecision(params: Map<String, Any?>): ToolResult {
        val agentId = params["agentId"] as? String ?: "unknown"
        val action = params["action"] as? String ?: "UNKNOWN"
        val inputSummary = params["input"] as? String ?: ""
        val outputSummary = params["output"] as? String ?: ""
        val riskLevel = params["riskLevel"] as? String ?: "STABLE"
        val tools = params["tools"] as? List<String> ?: emptyList()

        val entry = AgentAuditTrailEntity(
            patientId = patientId,
            agentId = agentId,
            action = action,
            inputSummary = inputSummary.take(500),
            outputSummary = outputSummary.take(500),
            riskLevel = riskLevel,
            toolCallsMade = gson.toJson(tools),
            timestamp = now()
        )
        val id = repo.insertAuditTrail(entry)
        return ToolResult("log_agent_decision", true, mapOf("auditId" to id))
    }
}
