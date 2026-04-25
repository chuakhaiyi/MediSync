package com.medisyncplus.data.repository

import com.medisyncplus.data.database.*
import com.medisyncplus.data.models.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediSyncRepository @Inject constructor(
    private val db: MediSyncDatabase
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)

    // ── Patient ──────────────────────────────────────────────────────────────
    fun getPatient(id: String) = db.patientDao().getPatient(id)
    suspend fun getPatientOnce(id: String) = db.patientDao().getPatientOnce(id)
    suspend fun upsertPatient(p: PatientEntity) = db.patientDao().upsert(p)
    suspend fun updateRiskLevel(id: String, risk: String) = db.patientDao().updateRiskLevel(id, risk)
    suspend fun updateAdherenceScore(id: String, score: Int) = db.patientDao().updateAdherenceScore(id, score)

    // ── Medications ──────────────────────────────────────────────────────────
    fun getActiveMedications(patientId: String) = db.medicationDao().getActiveMedications(patientId)
    suspend fun getActiveMedicationsOnce(patientId: String) = db.medicationDao().getActiveMedicationsOnce(patientId)
    suspend fun getMedicationById(id: String) = db.medicationDao().getById(id)
    suspend fun upsertMedication(med: MedicationEntity) = db.medicationDao().upsert(med)
    suspend fun upsertMedications(meds: List<MedicationEntity>) = db.medicationDao().upsertAll(meds)

    // ── Medication Logs ──────────────────────────────────────────────────────
    fun getMedLogsForDate(patientId: String, date: String = today()) =
        db.medicationLogDao().getLogsForDate(patientId, date)

    suspend fun getMedLogsForDateOnce(patientId: String, date: String = today()) =
        db.medicationLogDao().getLogsForDateOnce(patientId, date)

    fun getRecentMedLogs(patientId: String, limit: Int = 30) =
        db.medicationLogDao().getRecentLogs(patientId, limit)

    suspend fun upsertMedLog(log: MedicationLogEntity): Long =
        db.medicationLogDao().upsert(log)

    suspend fun upsertMedLogs(logs: List<MedicationLogEntity>) =
        db.medicationLogDao().upsertAll(logs)

    suspend fun markMedicationTaken(logId: Long) =
        db.medicationLogDao().updateStatus(logId, "TAKEN", now())

    suspend fun markMedicationMissed(logId: Long) =
        db.medicationLogDao().updateStatus(logId, "MISSED", null)

    suspend fun unmarkMedicationTaken(logId: Long) =
        db.medicationLogDao().updateStatus(logId, "UPCOMING", null)

    suspend fun getLogForMedOnDate(medId: String, date: String): MedicationLogEntity? =
        db.medicationLogDao().getLogForMedOnDate(medId, date)

    suspend fun countMissedSince(patientId: String, since: String) =
        db.medicationLogDao().countMissedSince(patientId, since)

    suspend fun countTakenSince(patientId: String, since: String) =
        db.medicationLogDao().countTakenSince(patientId, since)

    suspend fun calculateAdherenceScore(patientId: String): Int {
        val since = LocalDate.now().minusDays(7).format(dateFmt)
        val taken = countTakenSince(patientId, since)
        val missed = countMissedSince(patientId, since)
        val total = taken + missed
        return if (total == 0) 100 else (taken * 100 / total)
    }

    // ── Vitals ───────────────────────────────────────────────────────────────
    fun getAllVitals(patientId: String) = db.vitalDao().getAllVitals(patientId)
    fun getVitalsOfType(patientId: String, type: String, limit: Int = 14) =
        db.vitalDao().getVitalsOfType(patientId, type, limit)
    suspend fun getLatestVital(patientId: String, type: String) =
        db.vitalDao().getLatestVital(patientId, type)
    suspend fun getRecentVitals(patientId: String, limit: Int = 10) =
        db.vitalDao().getRecentOnce(patientId, limit)
    suspend fun insertVital(v: VitalEntity): Long = db.vitalDao().insert(v)
    suspend fun markVitalSynced(id: Long) = db.vitalDao().markSynced(id)
    suspend fun getWeightSince(patientId: String, since: String) =
        db.vitalDao().getWeightSince(patientId, since)
    suspend fun getBpSince(patientId: String, since: String) =
        db.vitalDao().getBpSince(patientId, since)

    // ── Symptoms ─────────────────────────────────────────────────────────────
    fun getAllSymptomReports(patientId: String) = db.symptomReportDao().getAllReports(patientId)
    fun getHighRiskReports(patientId: String) = db.symptomReportDao().getHighRiskReports(patientId)
    suspend fun getRecentSymptomReports(patientId: String, limit: Int = 10) =
        db.symptomReportDao().getRecentOnce(patientId, limit)
    suspend fun insertSymptomReport(r: SymptomReportEntity): Long =
        db.symptomReportDao().insert(r)
    suspend fun markSymptomSynced(id: Long) = db.symptomReportDao().markSynced(id)

    // ── Appointments ─────────────────────────────────────────────────────────
    fun getUpcomingAppointments(patientId: String) = db.appointmentDao().getUpcomingAppointments(patientId)
    fun getAllAppointments(patientId: String) = db.appointmentDao().getAllAppointments(patientId)
    suspend fun getNextAppointment(patientId: String) = db.appointmentDao().getNextAppointment(patientId)
    suspend fun upsertAppointment(a: AppointmentEntity) = db.appointmentDao().upsert(a)
    suspend fun upsertAppointments(list: List<AppointmentEntity>) = db.appointmentDao().upsertAll(list)
    suspend fun updateAppointmentStatus(id: String, status: String) =
        db.appointmentDao().updateStatus(id, status)

    // ── Checklist ─────────────────────────────────────────────────────────────
    fun getChecklistForDate(patientId: String, date: String = today()) =
        db.checklistDao().getTasksForDate(patientId, date)
    suspend fun getChecklistForDateOnce(patientId: String, date: String = today()) =
        db.checklistDao().getTasksForDateOnce(patientId, date)
    suspend fun upsertChecklistTask(t: ChecklistTaskEntity) = db.checklistDao().upsert(t)
    suspend fun upsertChecklistTasks(tasks: List<ChecklistTaskEntity>) = db.checklistDao().upsertAll(tasks)
    suspend fun toggleTask(id: String, isDone: Boolean) = 
        db.checklistDao().updateStatus(id, isDone, if (isDone) now() else null)
    suspend fun completeTask(id: String) = toggleTask(id, true)
    suspend fun countPendingTasks(patientId: String, date: String = today()) =
        db.checklistDao().countPendingTasks(patientId, date)

    // ── Hospital Stays ────────────────────────────────────────────────────────
    fun getHospitalStays(patientId: String) = db.hospitalStayDao().getStays(patientId)
    suspend fun upsertHospitalStays(list: List<HospitalStayEntity>) = db.hospitalStayDao().upsertAll(list)

    // ── Care Team Reminders ───────────────────────────────────────────────────
    fun getReminders(patientId: String) = db.careTeamReminderDao().getReminders(patientId)
    fun getUnreadReminders(patientId: String) = db.careTeamReminderDao().getUnreadReminders(patientId)
    suspend fun upsertReminders(list: List<CareTeamReminderEntity>) = db.careTeamReminderDao().upsertAll(list)
    suspend fun markReminderRead(id: Long) = db.careTeamReminderDao().markRead(id)
    suspend fun markAllRemindersRead(patientId: String) = db.careTeamReminderDao().markAllRead(patientId)

    // ── EMR Proposals ─────────────────────────────────────────────────────────
    fun getPendingProposals(patientId: String) = db.emrProposalDao().getPending(patientId)
    suspend fun insertEmrProposal(p: EmrProposalEntity): Long = db.emrProposalDao().insert(p)
    suspend fun approveProposal(id: Long, by: String) =
        db.emrProposalDao().updateStatus(id, "APPROVED", now(), by)

    // ── Chat Messages ─────────────────────────────────────────────────────────
    fun getChatMessages(patientId: String) = db.chatMessageDao().getMessages(patientId)
    suspend fun getRecentChatMessages(patientId: String, limit: Int = 20) =
        db.chatMessageDao().getRecentMessages(patientId, limit)
    suspend fun insertChatMessage(msg: ChatMessageEntity): Long = db.chatMessageDao().insert(msg)

    // ── Care Team ─────────────────────────────────────────────────────────────
    suspend fun getCareTeamMember(id: String) = db.careTeamDao().getById(id)
    suspend fun upsertCareTeam(list: List<CareTeamEntity>) = db.careTeamDao().upsertAll(list)

    // ── Hospital Stay (active) ────────────────────────────────────────────────
    suspend fun getActiveStay(patientId: String) = db.hospitalStayDao().getActiveStay(patientId)

    // ── Agent Audit Trail ─────────────────────────────────────────────────────
    fun getAuditTrail(patientId: String) = db.agentAuditTrailDao().getAll(patientId)
    suspend fun getRecentAuditTrail(patientId: String, limit: Int = 50) =
        db.agentAuditTrailDao().getRecent(patientId, limit)
    suspend fun insertAuditTrail(entry: AgentAuditTrailEntity): Long =
        db.agentAuditTrailDao().insert(entry)
    suspend fun getAuditByAgent(patientId: String, agentId: String, limit: Int = 20) =
        db.agentAuditTrailDao().getByAgent(patientId, agentId, limit)
}
