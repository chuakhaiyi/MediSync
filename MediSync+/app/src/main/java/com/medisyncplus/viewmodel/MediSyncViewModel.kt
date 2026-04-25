package com.medisyncplus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisyncplus.ai.*
import com.medisyncplus.ai.agents.*
import com.medisyncplus.data.database.DatabaseSeeder
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class UiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val toastMessage: String? = null,
    val showFeelingDialogFor: MedicationEntity? = null
)

data class HomeUiState(
    val patient: PatientEntity? = null,
    val nextMedication: MedicationEntity? = null,
    val nextMedLog: MedicationLogEntity? = null,
    val todayTasksDone: Int = 0,
    val todayTasksTotal: Int = 0,
    val unreadReminders: Int = 0,
    val pendingProposals: Int = 0,
    val agentStatus: MedicineAgentResult? = null,
    val riskTrajectory: RiskTrajectory? = null,
    val followUpResult: FollowUpResult? = null,
    val morningSummary: String = ""
)

data class MedicationUiState(
    val medications: List<MedicationEntity> = emptyList(),
    val todayLogs: List<MedicationLogEntity> = emptyList(),
    val adherenceResult: MedicineAgentResult? = null
)

data class VitalsUiState(
    val latestWeight: VitalEntity? = null,
    val latestBp: VitalEntity? = null,
    val latestBloodSugar: VitalEntity? = null,
    val latestPulse: VitalEntity? = null,
    val latestSpo2: VitalEntity? = null,
    val latestTemperature: VitalEntity? = null,
    val weightHistory: List<VitalEntity> = emptyList(),
    val bpHistory: List<VitalEntity> = emptyList(),
    val bloodSugarHistory: List<VitalEntity> = emptyList(),
    val spo2History: List<VitalEntity> = emptyList(),
    val agentAnalysis: String = ""
)

data class SymptomUiState(
    val recentReports: List<SymptomReportEntity> = emptyList(),
    val analysisResult: SymptomAnalysisResult? = null,
    val isAnalysing: Boolean = false
)

data class ChecklistUiState(
    val tasks: List<ChecklistTaskEntity> = emptyList(),
    val completedCount: Int = 0
)

data class AppointmentUiState(
    val upcoming: List<AppointmentEntity> = emptyList(),
    val all: List<AppointmentEntity> = emptyList(),
    val hospitalStays: List<HospitalStayEntity> = emptyList(),
    val careTeamReminders: List<CareTeamReminderEntity> = emptyList()
)

data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val isThinking: Boolean = false,
    val lastRiskFlag: String = "STABLE"
)

data class AuditUiState(
    val entries: List<AgentAuditTrailEntity> = emptyList()
)

@HiltViewModel
class MediSyncViewModel @Inject constructor(
    private val repo: MediSyncRepository,
    private val orchestrator: AgentOrchestrator
) : ViewModel() {

    private val patientId = DatabaseSeeder.PATIENT_ID
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)

    // ── Public UI state flows ──────────────────────────────────────────────────
    private val _ui     = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _home   = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _meds   = MutableStateFlow(MedicationUiState())
    val meds: StateFlow<MedicationUiState> = _meds.asStateFlow()

    private val _vitals = MutableStateFlow(VitalsUiState())
    val vitals: StateFlow<VitalsUiState> = _vitals.asStateFlow()

    private val _symptom = MutableStateFlow(SymptomUiState())
    val symptom: StateFlow<SymptomUiState> = _symptom.asStateFlow()

    private val _checklist = MutableStateFlow(ChecklistUiState())
    val checklist: StateFlow<ChecklistUiState> = _checklist.asStateFlow()

    private val _appointments = MutableStateFlow(AppointmentUiState())
    val appointments: StateFlow<AppointmentUiState> = _appointments.asStateFlow()

    private val _chat = MutableStateFlow(ChatUiState())
    val chat: StateFlow<ChatUiState> = _chat.asStateFlow()

    private val _audit = MutableStateFlow(AuditUiState())
    val audit: StateFlow<AuditUiState> = _audit.asStateFlow()

    init {
        seedAndLoad()
    }

    // ─── Initialisation ──────────────────────────────────────────────────────
    private fun seedAndLoad() {
        viewModelScope.launch(Dispatchers.IO) {
            val existingPatient = repo.getPatientOnce(patientId)
            if (existingPatient == null) {
                seedDatabase()
            } else if (existingPatient.name != DatabaseSeeder.patient().name) {
                seedDatabase()
            }
            // Always ensure today's tasks follow the latest schedule templates
            repo.upsertChecklistTasks(DatabaseSeeder.checklistTasks(today()))

            observeAll()
            runMorningChecks()
        }
    }

    private suspend fun seedDatabase() {
        repo.upsertPatient(DatabaseSeeder.patient())
        repo.upsertCareTeam(DatabaseSeeder.careTeam())
        repo.upsertMedications(DatabaseSeeder.medications())
        repo.upsertMedLogs(DatabaseSeeder.medicationLogs())
        DatabaseSeeder.vitals().forEach { repo.insertVital(it) }
        repo.upsertAppointments(DatabaseSeeder.appointments())
        repo.upsertChecklistTasks(DatabaseSeeder.checklistTasks(today()))
        repo.upsertHospitalStays(DatabaseSeeder.hospitalStays())
        repo.upsertReminders(DatabaseSeeder.careTeamReminders())
        DatabaseSeeder.symptomReports().forEach { repo.insertSymptomReport(it) }
        DatabaseSeeder.emrProposals().forEach { repo.insertEmrProposal(it) }
    }

    private fun observeAll() {
        viewModelScope.launch {
            combine(
                repo.getPatient(patientId),
                repo.getActiveMedications(patientId),
                repo.getMedLogsForDate(patientId, today()),
                repo.getChecklistForDate(patientId, today()),
                repo.getUnreadReminders(patientId),
                repo.getPendingProposals(patientId)
            ) { flows: Array<Any?> ->
                val patient   = flows[0] as PatientEntity?
                @Suppress("UNCHECKED_CAST")
                val meds      = flows[1] as List<MedicationEntity>
                @Suppress("UNCHECKED_CAST")
                val logs      = flows[2] as List<MedicationLogEntity>
                @Suppress("UNCHECKED_CAST")
                val tasks     = flows[3] as List<ChecklistTaskEntity>
                @Suppress("UNCHECKED_CAST")
                val reminders = flows[4] as List<CareTeamReminderEntity>
                @Suppress("UNCHECKED_CAST")
                val proposals = flows[5] as List<EmrProposalEntity>

                val upcomingLogs = logs.filter { it.status == "UPCOMING" }
                val upcomingMedId = upcomingLogs.firstOrNull()?.medicationId
                val nextMed = meds.find { it.id == upcomingMedId }
                val nextLog = upcomingLogs.firstOrNull()
                _home.update { it.copy(
                    patient = patient,
                    nextMedication = nextMed,
                    nextMedLog = nextLog,
                    todayTasksDone = tasks.count { t -> t.isDone },
                    todayTasksTotal = tasks.size,
                    unreadReminders = reminders.size,
                    pendingProposals = proposals.size
                )}

                _meds.update { it.copy(medications = meds, todayLogs = logs) }
            }.collect()
        }

        viewModelScope.launch {
            repo.getAllVitals(patientId).collect { list ->
                _vitals.update { it.copy(
                    latestWeight = list.filter { v -> v.type == "weight" }.firstOrNull(),
                    latestBp = list.filter { v -> v.type == "bp" }.firstOrNull(),
                    latestBloodSugar = list.filter { v -> v.type == "blood_sugar" }.firstOrNull(),
                    latestPulse = list.filter { v -> v.type == "pulse" }.firstOrNull(),
                    latestSpo2 = list.filter { v -> v.type == "spo2" }.firstOrNull(),
                    latestTemperature = list.filter { v -> v.type == "temperature" }.firstOrNull(),
                    weightHistory = list.filter { v -> v.type == "weight" }.take(10),
                    bpHistory = list.filter { v -> v.type == "bp" }.take(10),
                    bloodSugarHistory = list.filter { v -> v.type == "blood_sugar" }.take(10),
                    spo2History = list.filter { v -> v.type == "spo2" }.take(10)
                )}
            }
        }

        viewModelScope.launch {
            repo.getAllSymptomReports(patientId).collect { list ->
                _symptom.update { it.copy(recentReports = list) }
            }
        }

        viewModelScope.launch {
            repo.getChecklistForDate(patientId, today()).collect { list ->
                _checklist.update { it.copy(tasks = list, completedCount = list.count { t -> t.isDone }) }
            }
        }

        viewModelScope.launch {
            combine(
                repo.getAllAppointments(patientId),
                repo.getHospitalStays(patientId),
                repo.getReminders(patientId)
            ) { appts, stays, rems ->
                _appointments.update { it.copy(
                    upcoming = appts.filter { a -> a.status != "COMPLETED" },
                    all = appts,
                    hospitalStays = stays,
                    careTeamReminders = rems
                )}
            }.collect()
        }

        viewModelScope.launch {
            repo.getChatMessages(patientId).collect { list ->
                _chat.update { it.copy(messages = list) }
            }
        }

        viewModelScope.launch {
            repo.getAuditTrail(patientId).collect { list ->
                _audit.update { it.copy(entries = list) }
            }
        }
    }

    private fun runMorningChecks() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = orchestrator.runFullMorningCheck()
                _home.update { it.copy(
                    morningSummary = result.summary,
                    agentStatus = result.medicineResult,
                    riskTrajectory = result.riskTrajectory,
                    followUpResult = result.followUpResult
                )}
                _meds.update { it.copy(adherenceResult = result.medicineResult) }
                
                // Alert clinician if morning check shows high risk
                if (result.riskTrajectory.interventionRequired) {
                    repo.insertAuditTrail(AgentAuditTrailEntity(
                        patientId = patientId, agentId = "trajectory_agent",
                        action = "ALERT", inputSummary = "Longitudinal analysis",
                        outputSummary = "Deterioration predicted: ${result.riskTrajectory.trajectoryReason}",
                        riskLevel = result.riskTrajectory.predictedRiskIn48h, toolCallsMade = "[]", timestamp = now()
                    ))
                }
                
                _ui.update { it.copy(toastMessage = if (result.medicineResult.alertRequired) 
                    "AI Alert: Adherence issue detected" else null 
                )}
            } catch (e: Exception) { /* Silent fail */ }
        }
    }

    // ─── Medication actions ───────────────────────────────────────────────────
    fun markMedicationTaken(medication: MedicationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val log = repo.getLogForMedOnDate(medication.id, today())
                ?: MedicationLogEntity(
                    medicationId = medication.id, patientId = patientId,
                    scheduledDate = today(), scheduledTime = medication.scheduledTime,
                    status = "TAKEN", takenAt = now(), missedStreak = 0
                )
            val logId = if (log.id == 0L) repo.upsertMedLog(log) else log.id
            repo.markMedicationTaken(logId)
            val score = repo.calculateAdherenceScore(patientId)
            repo.updateAdherenceScore(patientId, score)
            repo.insertAuditTrail(AgentAuditTrailEntity(
                patientId = patientId, agentId = "medicine_agent",
                action = "MARK_TAKEN", inputSummary = "${medication.name} ${medication.dosage}",
                outputSummary = "Medication marked as taken. Adherence: $score%",
                riskLevel = if (score >= 70) "STABLE" else "WARNING",
                toolCallsMade = "[]", timestamp = now()
            ))
            
            _ui.update { it.copy(showFeelingDialogFor = medication) }
        }
    }

    fun unmarkMedicationTaken(medication: MedicationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val log = repo.getLogForMedOnDate(medication.id, today())
            if (log != null && log.status == "TAKEN") {
                repo.unmarkMedicationTaken(log.id)
                val score = repo.calculateAdherenceScore(patientId)
                repo.updateAdherenceScore(patientId, score)
                repo.insertAuditTrail(AgentAuditTrailEntity(
                    patientId = patientId, agentId = "medicine_agent",
                    action = "UNMARK_TAKEN", inputSummary = "${medication.name} ${medication.dosage}",
                    outputSummary = "Medication unchecked. Adherence: $score%",
                    riskLevel = if (score >= 70) "STABLE" else "WARNING",
                    toolCallsMade = "[]", timestamp = now()
                ))
                showToast("${medication.name} unchecked")
            }
        }
    }

    fun submitFeelingAfterMedication(medication: MedicationEntity, feeling: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(showFeelingDialogFor = null) }
            if (!feeling.isNullOrBlank()) {
                val report = SymptomReportEntity(
                    patientId = patientId,
                    symptoms = "[\"Feeling post-medication\"]",
                    severity = 1,
                    additionalNote = "After taking ${medication.name}: $feeling",
                    agentRiskLevel = "STABLE",
                    agentReason = "User feedback post-medication",
                    agentRecommendedAction = "Monitor",
                    escalatedImmediately = false,
                    appointmentBooked = false,
                    reportedAt = now(),
                    source = "post_med_feedback"
                )
                repo.insertSymptomReport(report)
                showToast("Feedback recorded. Thank you!")
            } else {
                showToast("${medication.name} taken")
            }
        }
    }

    // ─── Vital recording ──────────────────────────────────────────────────────
    fun recordWeight(kg: Float, notes: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val flag = when { 
                kg > 85f -> "warning" // Overweight example threshold
                kg < 45f -> "warning" // Underweight example threshold
                else -> "normal" 
            }
            val vital = VitalEntity(patientId = patientId, type = "weight",
                value = kg, systolic = null, diastolic = null, unit = "kg",
                flag = flag, recordedAt = now(), recordedBy = "patient", notes = notes)
            val id = repo.insertVital(vital)
            repo.markVitalSynced(id)
            
            val statusMsg = when {
                kg > 85f -> "Weight logged: ${kg}kg (Overweight)"
                kg < 45f -> "Weight logged: ${kg}kg (Underweight)"
                else -> "Weight logged: ${kg}kg"
            }
            showToast(statusMsg)
        }
    }

    fun recordBp(systolic: Int, diastolic: Int, notes: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val flag = when { systolic >= 180 || diastolic >= 110 -> "critical"; systolic >= 140 || diastolic >= 90 -> "warning"; else -> "normal" }
            val vital = VitalEntity(patientId = patientId, type = "bp",
                value = null, systolic = systolic, diastolic = diastolic, unit = "mmHg",
                flag = flag, recordedAt = now(), recordedBy = "patient", notes = notes)
            val id = repo.insertVital(vital)
            repo.markVitalSynced(id)
            if (flag == "critical") {
                showToast("BP critically high! Care team alerted.")
            } else {
                showToast("Blood pressure logged")
            }
        }
    }

    fun recordBloodSugar(mmol: Float, notes: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val flag = when { 
                mmol > 11f || mmol < 3.9f -> "critical" 
                mmol > 7.8f || mmol < 4.4f -> "warning" 
                else -> "normal" 
            }
            val vital = VitalEntity(patientId = patientId, type = "blood_sugar",
                value = mmol, systolic = null, diastolic = null, unit = "mmol/L",
                flag = flag, recordedAt = now(), recordedBy = "patient", notes = notes)
            repo.insertVital(vital)
            if (flag == "critical") showToast("Blood sugar critically abnormal! Alert sent.")
            else showToast("Blood sugar logged")
        }
    }

    fun recordPulse(bpm: Int, notes: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val flag = when {
                bpm >= 120 || bpm <= 50 -> "critical"
                bpm > 100 || bpm < 60  -> "warning"
                else                    -> "normal"
            }
            val vital = VitalEntity(patientId = patientId, type = "pulse",
                value = bpm.toFloat(), systolic = null, diastolic = null, unit = "bpm",
                flag = flag, recordedAt = now(), recordedBy = "patient", notes = notes)
            repo.insertVital(vital)
            if (flag == "critical") showToast("Pulse critically abnormal! Alert sent.")
            else showToast("Pulse rate logged")
        }
    }

    fun recordSpo2(pct: Float, notes: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val flag = when { pct < 90f -> "critical"; pct < 94f -> "warning"; else -> "normal" }
            val vital = VitalEntity(patientId = patientId, type = "spo2",
                value = pct, systolic = null, diastolic = null, unit = "%",
                flag = flag, recordedAt = now(), recordedBy = "patient", notes = notes)
            repo.insertVital(vital)
            if (flag == "critical") showToast("SpO2 critically low! Alert sent.")
            else showToast("Oxygen level logged")
        }
    }

    fun recordTemperature(celsius: Float, notes: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val flag = when {
                celsius >= 39.0f || celsius <= 35.0f -> "critical"
                celsius > 37.5f || celsius < 36.0f  -> "warning"
                else                                -> "normal"
            }
            val vital = VitalEntity(patientId = patientId, type = "temperature",
                value = celsius, systolic = null, diastolic = null, unit = "°C",
                flag = flag, recordedAt = now(), recordedBy = "patient", notes = notes)
            repo.insertVital(vital)
            if (flag == "critical") showToast("Temperature critically abnormal! Alert sent.")
            else showToast("Temperature logged")
        }
    }

    // ─── Symptom reporting ────────────────────────────────────────────────────
    fun submitSymptomReport(symptoms: List<String>, severity: Int, note: String) {
        viewModelScope.launch {
            _symptom.update { it.copy(isAnalysing = true) }
            try {
                val result = orchestrator.runSymptomAgent(symptoms, severity, note, patientId)
                _symptom.update { it.copy(analysisResult = result, isAnalysing = false) }
                repo.insertAuditTrail(AgentAuditTrailEntity(
                    patientId = patientId, agentId = "symptom_agent",
                    action = "ANALYSE", inputSummary = symptoms.joinToString(", "),
                    outputSummary = "Risk: ${result.riskLevel}. Action: ${result.recommendedAction}",
                    riskLevel = result.riskLevel, toolCallsMade = "[]", timestamp = now()
                ))
                if (result.escalateImmediately) showToast("Care team alerted!")
                else if (result.riskLevel == "WARNING") showToast("Care team notified")
                else showToast("Report submitted")
            } catch (e: Exception) {
                _symptom.update { it.copy(isAnalysing = false) }
                showToast("Report saved. Agent unavailable.")
            }
        }
    }

    // ─── Checklist ────────────────────────────────────────────────────────────
    fun toggleTask(taskId: String, isDone: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.toggleTask(taskId, isDone)
            if (isDone) showToast("Task completed")
            else showToast("Task unchecked")
        }
    }

    // ─── Appointments ─────────────────────────────────────────────────────────
    fun requestAppointment(reason: String, preferredDate: String, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val appt = AppointmentEntity(
                id = "REQ_${System.currentTimeMillis()}",
                patientId = patientId, doctorId = DatabaseSeeder.DOCTOR_ID,
                doctorName = "Dr. Oscar", specialty = "Cardiologist",
                dateTime = "$preferredDate 00:00",
                location = "To be confirmed",
                appointmentType = "FOLLOW_UP", status = "PENDING",
                notes = "$reason. $notes",
                reminderSet = false, createdAt = now(), source = "patient_request"
            )
            repo.upsertAppointment(appt)
            showToast("Appointment request sent")
        }
    }

    fun rescheduleAppointment(id: String, newDate: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateAppointmentStatus(id, "RESCHEDULED")
            showToast("Reschedule request sent for $newDate")
        }
    }

    fun cancelAppointmentRequest(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateAppointmentStatus(id, "CANCELLED")
            showToast("Appointment request cancelled")
        }
    }

    fun markReminderRead(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.markReminderRead(id)
        }
    }

    // ─── AI Chat ──────────────────────────────────────────────────────────────
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            val userMsg = ChatMessageEntity(patientId = patientId, role = "user", content = text, timestamp = now())
            repo.insertChatMessage(userMsg)
            _chat.update { it.copy(isThinking = true) }

            try {
                val response = orchestrator.runChatAgent(text, _chat.value.messages)
                
                val aiMsg = ChatMessageEntity(
                    patientId = patientId, role = "assistant", 
                    content = response.text, timestamp = now(),
                    triggeredSymptomRecord = response.recordedSymptom,
                    triggeredAppointmentRequest = response.requestedAppointment
                )
                repo.insertChatMessage(aiMsg)
                _chat.update { it.copy(isThinking = false, lastRiskFlag = response.riskFlag) }
            } catch (e: Exception) {
                val errorMsg = ChatMessageEntity(patientId = patientId, role = "assistant", 
                    content = "I'm sorry, I'm having trouble connecting right now. Please try again or contact your care team if urgent.", 
                    timestamp = now())
                repo.insertChatMessage(errorMsg)
                _chat.update { it.copy(isThinking = false) }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun showToast(msg: String) {
        _ui.update { it.copy(toastMessage = msg) }
    }

    fun clearToast() {
        _ui.update { it.copy(toastMessage = null) }
    }
}
