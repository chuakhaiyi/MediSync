package com.medisyncplus.data.database

import com.medisyncplus.data.models.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DatabaseSeeder {

    private val today = LocalDate.now()
    private val now = LocalDateTime.now()
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    const val PATIENT_ID = "P001"
    const val DOCTOR_ID = "D001"
    const val NURSE_ID = "N001"
    const val SPECIALIST_ID = "D002"

    fun patient() = PatientEntity(
        id = PATIENT_ID,
        name = "Margaret Chen",
        age = 72,
        mrn = "MRN-20240418-001",
        ward = "Cardiac Unit – Ward 4B",
        dischargeDate = today.minusDays(5).format(dateFmt),
        primaryCondition = "Heart Failure (CHF) – NYHA Class III",
        attendingDoctorId = DOCTOR_ID,
        riskLevel = "WARNING",
        bloodType = "B+",
        allergies = "[\"Penicillin\",\"NSAIDs\"]",
        contactNumber = "012-345-6789",
        emergencyContact = "012-987-6543",
        emergencyContactName = "David Chen (Son)",
        lastCheckedIn = now.format(tsFmt),
        adherenceScore = 72
    )

    fun careTeam() = listOf(
        CareTeamEntity(DOCTOR_ID, "Dr. Oscar", "DOCTOR", "Cardiologist", "03-1234-5678", "oscar@hospital.my", "H001"),
        CareTeamEntity(NURSE_ID, "Nurse Yong Jia Khen", "NURSE", "Cardiac Ward Nurse", "03-1234-5679", "priya.raj@hospital.my", "H001"),
        CareTeamEntity(SPECIALIST_ID, "Dr. Toh Bin Bin", "SPECIALIST", "Physiotherapist", "03-1234-5680", "sarah.lim@hospital.my", "H001")
    )

    fun medications() = listOf(
        MedicationEntity(
            id = "MED001", patientId = PATIENT_ID,
            name = "Furosemide", dosage = "40 mg",
            instruction = "Take with water. Avoid evening doses to prevent nocturia.",
            scheduledTime = "08:00", pillColorHex = "#2A7FBD",
            medicationClass = "Loop Diuretic", frequency = "once_daily",
            sideEffects = "Increased urination, dizziness, low potassium",
            prescribedBy = DOCTOR_ID, startDate = today.minusDays(5).format(dateFmt),
            endDate = null, isActive = true, requiresFood = false, criticalMedication = true
        ),
        MedicationEntity(
            id = "MED002", patientId = PATIENT_ID,
            name = "Lisinopril", dosage = "10 mg",
            instruction = "May cause dizziness on first dose. Sit slowly before standing.",
            scheduledTime = "12:00", pillColorHex = "#E8831A",
            medicationClass = "ACE Inhibitor", frequency = "once_daily",
            sideEffects = "Dry cough, dizziness, low blood pressure",
            prescribedBy = DOCTOR_ID, startDate = today.minusDays(5).format(dateFmt),
            endDate = null, isActive = true, requiresFood = true, criticalMedication = true
        ),
        MedicationEntity(
            id = "MED003", patientId = PATIENT_ID,
            name = "Carvedilol", dosage = "6.25 mg",
            instruction = "Take twice daily with food. Do not stop suddenly.",
            scheduledTime = "08:00", pillColorHex = "#3A9E6F",
            medicationClass = "Beta Blocker", frequency = "twice_daily",
            sideEffects = "Fatigue, slow heartbeat, dizziness",
            prescribedBy = DOCTOR_ID, startDate = today.minusDays(5).format(dateFmt),
            endDate = null, isActive = true, requiresFood = true, criticalMedication = true
        ),
        MedicationEntity(
            id = "MED004", patientId = PATIENT_ID,
            name = "Spironolactone", dosage = "25 mg",
            instruction = "Take in the morning. Avoid high-potassium foods.",
            scheduledTime = "08:00", pillColorHex = "#7B68EE",
            medicationClass = "Aldosterone Antagonist", frequency = "once_daily",
            sideEffects = "High potassium, breast tenderness, frequent urination",
            prescribedBy = DOCTOR_ID, startDate = today.minusDays(5).format(dateFmt),
            endDate = null, isActive = true, requiresFood = false, criticalMedication = true
        ),
        MedicationEntity(
            id = "MED005", patientId = PATIENT_ID,
            name = "Atorvastatin", dosage = "40 mg",
            instruction = "Take at bedtime. Avoid grapefruit juice.",
            scheduledTime = "21:00", pillColorHex = "#E74C8B",
            medicationClass = "Statin", frequency = "once_daily",
            sideEffects = "Muscle aches, liver enzyme changes",
            prescribedBy = DOCTOR_ID, startDate = today.minusDays(5).format(dateFmt),
            endDate = null, isActive = true, requiresFood = false, criticalMedication = false
        )
    )

    fun medicationLogs(): List<MedicationLogEntity> {
        val logs = mutableListOf<MedicationLogEntity>()
        val medIds = listOf("MED001","MED002","MED003","MED004","MED005")
        for (dayOffset in 4 downTo 0) {
            val date = today.minusDays(dayOffset.toLong()).format(dateFmt)
            medIds.forEachIndexed { idx, medId ->
                val isMissed = (dayOffset == 0 && idx == 3) || (dayOffset == 1 && idx == 3)
                val isTaken = !isMissed && dayOffset > 0
                logs.add(
                    MedicationLogEntity(
                        medicationId = medId, patientId = PATIENT_ID,
                        scheduledDate = date, scheduledTime = when(idx) { 0->"08:00"; 1->"12:00"; 2->"08:00"; 3->"08:00"; else->"21:00" },
                        status = when { isMissed -> "MISSED"; isTaken -> "TAKEN"; dayOffset == 0 && idx == 0 -> "TAKEN"; else -> "UPCOMING" },
                        takenAt = if (isTaken || (dayOffset == 0 && idx == 0)) "${date} ${if(idx==1) "12:05" else "08:12"}:00" else null,
                        missedStreak = if (isMissed) 2 else 0,
                        agentFlagged = isMissed && idx == 3
                    )
                )
            }
        }
        return logs
    }

    fun vitals(): List<VitalEntity> {
        val list = mutableListOf<VitalEntity>()
        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(dateFmt)
            list.add(VitalEntity(patientId = PATIENT_ID, type = "weight",
                value = (68.0 + (Math.random() * 1.5)).toFloat(), systolic = null, diastolic = null,
                unit = "kg", flag = "normal", recordedAt = "${dateStr} 08:15:00", recordedBy = "patient", syncedToHospital = i > 0))
            list.add(VitalEntity(patientId = PATIENT_ID, type = "bp",
                value = null, systolic = (135 + (Math.random() * 15).toInt()), diastolic = (82 + (Math.random() * 10).toInt()),
                unit = "mmHg", flag = if (i < 2) "warning" else "normal", recordedAt = "${dateStr} 08:20:00", recordedBy = "patient", syncedToHospital = i > 0))
            list.add(VitalEntity(patientId = PATIENT_ID, type = "blood_sugar",
                value = (5.5f + (Math.random() * 2.0).toFloat()), systolic = null, diastolic = null,
                unit = "mmol/L", flag = "normal", recordedAt = "${dateStr} 08:25:00", recordedBy = "patient", syncedToHospital = i > 0))
            list.add(VitalEntity(patientId = PATIENT_ID, type = "pulse",
                value = (68 + (Math.random() * 15).toInt()).toFloat(), systolic = null, diastolic = null,
                unit = "bpm", flag = "normal", recordedAt = "${dateStr} 08:20:00", recordedBy = "patient", syncedToHospital = i > 0))
            list.add(VitalEntity(patientId = PATIENT_ID, type = "spo2",
                value = (95f + (Math.random() * 4).toFloat()).coerceAtMost(100f), systolic = null, diastolic = null,
                unit = "%", flag = if (i < 1) "warning" else "normal", recordedAt = "${dateStr} 08:30:00", recordedBy = "patient", syncedToHospital = i > 0))
            list.add(VitalEntity(patientId = PATIENT_ID, type = "temperature",
                value = (36.2f + (Math.random() * 0.8).toFloat()), systolic = null, diastolic = null,
                unit = "°C", flag = "normal", recordedAt = "${dateStr} 08:30:00", recordedBy = "patient", syncedToHospital = i > 0))
        }
        return list
    }

    fun appointments() = listOf(
        AppointmentEntity(
            id = "APT001", patientId = PATIENT_ID, doctorId = DOCTOR_ID,
            doctorName = "Dr. Oscar", specialty = "Cardiologist",
            dateTime = today.plusDays(2).format(dateFmt) + " 10:30",
            location = "Cardiology Clinic · Level 3, Block B",
            appointmentType = "FOLLOW_UP", status = "CONFIRMED",
            notes = "Bring medication list and BP log. Echo may be ordered.",
            reminderSet = true, createdAt = now.format(tsFmt), source = "doctor"
        ),
        AppointmentEntity(
            id = "APT002", patientId = PATIENT_ID, doctorId = SPECIALIST_ID,
            doctorName = "Dr. Sarah Lim", specialty = "Physiotherapist",
            dateTime = today.plusDays(9).format(dateFmt) + " 14:00",
            location = "Rehabilitation Centre · Level 2",
            appointmentType = "SPECIALIST", status = "PENDING",
            notes = "Initial cardiac rehabilitation assessment.",
            reminderSet = false, createdAt = now.format(tsFmt), source = "doctor"
        ),
        AppointmentEntity(
            id = "APT003", patientId = PATIENT_ID, doctorId = DOCTOR_ID,
            doctorName = "Dr. Oscar", specialty = "Cardiologist",
            dateTime = today.plusDays(23).format(dateFmt) + " 09:00",
            location = "Cardiology Clinic · Level 3, Block B",
            appointmentType = "ROUTINE", status = "SCHEDULED",
            notes = "Monthly review. Echocardiogram scheduled same day.",
            reminderSet = false, createdAt = now.format(tsFmt), source = "doctor"
        ),
        AppointmentEntity(
            id = "APT000", patientId = PATIENT_ID, doctorId = DOCTOR_ID,
            doctorName = "Dr. Oscar", specialty = "Cardiologist",
            dateTime = today.minusDays(5).format(dateFmt) + " 11:00",
            location = "Cardiology Ward 4B",
            appointmentType = "DISCHARGE", status = "COMPLETED",
            notes = "Discharge review completed. Prescribed 5-medication regimen.",
            reminderSet = false, createdAt = now.minusDays(5).format(tsFmt), source = "doctor"
        )
    )

    fun checklistTasks(date: String): List<ChecklistTaskEntity> = listOf(
        ChecklistTaskEntity("CHK_${date}_1", PATIENT_ID, "Weigh yourself and record", "MORNING", "scale", true, "weight", date, templateId = "T_WEIGHT", scheduledTime = "08:00"),
        ChecklistTaskEntity("CHK_${date}_2", PATIENT_ID, "Morning medications (Furosemide, Carvedilol, Spironolactone)", "MORNING", "medication", false, null, date, templateId = "T_MED_AM", scheduledTime = "08:30"),
        ChecklistTaskEntity("CHK_${date}_3", PATIENT_ID, "10-minute gentle walk or light exercise", "MORNING", "walk", false, null, date, templateId = "T_WALK", scheduledTime = "10:00"),
        ChecklistTaskEntity("CHK_${date}_4", PATIENT_ID, "Lunch medication (Lisinopril)", "AFTERNOON", "medication", false, null, date, templateId = "T_MED_NOON", scheduledTime = "13:00"),
        ChecklistTaskEntity("CHK_${date}_5", PATIENT_ID, "Blood pressure check", "AFTERNOON", "bp", true, "bp", date, templateId = "T_BP", scheduledTime = "15:00"),
        ChecklistTaskEntity("CHK_${date}_6", PATIENT_ID, "Evening medication (Carvedilol 2nd dose, Atorvastatin)", "EVENING", "medication", false, null, date, templateId = "T_MED_PM", scheduledTime = "20:00"),
        ChecklistTaskEntity("CHK_${date}_7", PATIENT_ID, "Check for ankle swelling", "EVENING", "check", false, null, date, templateId = "T_SWELLING", scheduledTime = "21:00"),
        ChecklistTaskEntity("CHK_${date}_8", PATIENT_ID, "Report any new symptoms via app", "EVENING", "report", false, null, date, templateId = "T_SYMPTOMS", scheduledTime = "22:00")
    )

    fun hospitalStays() = listOf(
        HospitalStayEntity(
            id = "STAY001", patientId = PATIENT_ID,
            admissionDate = today.minusDays(13).format(dateFmt),
            dischargeDate = today.minusDays(5).format(dateFmt),
            ward = "Cardiac Unit – Ward 4B",
            primaryDiagnosis = "Acute Decompensated Heart Failure (ADHF)",
            secondaryDiagnoses = "[\"Hypertension\",\"Dyslipidaemia\",\"CKD Stage 2\"]",
            treatmentSummary = "IV diuresis with furosemide, medication optimisation, fluid restriction 1.5L/day, daily weight monitoring.",
            attendingDoctorId = DOCTOR_ID,
            dischargeNoteRaw = "Patient admitted with ADHF. Treated with IV Lasix. Stable on oral diuretics. Discharged with 5-drug regimen.",
            dischargeNoteParsed = """{"carePlan":{"fluidRestriction":"1.5L/day","saltRestriction":"<2g/day","dailyWeighIn":true,"weightGainAlert":"2kg in 2 days"},"followUp":"2 weeks","redFlags":["chest pain","sudden breathlessness","weight gain >2kg in 2 days","ankle swelling worsening"]}""",
            status = "DISCHARGED"
        ),
        HospitalStayEntity(
            id = "STAY002", patientId = PATIENT_ID,
            admissionDate = today.minusDays(170).format(dateFmt),
            dischargeDate = today.minusDays(163).format(dateFmt),
            ward = "Cardiac Unit – Ward 4B",
            primaryDiagnosis = "CHF Exacerbation",
            secondaryDiagnoses = "[\"Hypertension\"]",
            treatmentSummary = "Furosemide uptitration from 20mg to 40mg. Carvedilol initiated.",
            attendingDoctorId = DOCTOR_ID,
            dischargeNoteRaw = "CHF exacerbation managed. Furosemide dose increased.",
            dischargeNoteParsed = "{}",
            status = "DISCHARGED"
        )
    )

    fun careTeamReminders() = listOf(
        CareTeamReminderEntity(
            patientId = PATIENT_ID, fromCareTeamMemberId = DOCTOR_ID,
            fromName = "Dr. Oscar", role = "Cardiologist",
            message = "Mrs Chen, please monitor your weight daily. Call us immediately if you gain more than 2 kg in 2 days, experience sudden breathlessness, or chest tightness. Take all heart medications without fail.",
            priority = "URGENT", createdAt = today.minusDays(5).format(dateFmt) + " 15:00:00",
            isRead = false, requiresAction = false, actionType = null
        ),
        CareTeamReminderEntity(
            patientId = PATIENT_ID, fromCareTeamMemberId = NURSE_ID,
            fromName = "Nurse Priya Raj", role = "Ward Nurse",
            message = "Hi Margaret! Spironolactone has been flagged as missed for 2 days in a row. It is very important for your heart health. Please take it today and do not skip. Our team has been notified.",
            priority = "URGENT", createdAt = today.format(dateFmt) + " 09:30:00",
            isRead = false, requiresAction = true, actionType = "TAKE_MEDICATION"
        ),
        CareTeamReminderEntity(
            patientId = PATIENT_ID, fromCareTeamMemberId = DOCTOR_ID,
            fromName = "Dr. Oscar", role = "Cardiologist",
            message = "Your appointment on ${today.plusDays(2).format(dateFmt)} at 10:30 AM is confirmed. Please bring your home BP readings and medication list.",
            priority = "ROUTINE", createdAt = today.minusDays(1).format(dateFmt) + " 10:00:00",
            isRead = true, requiresAction = false, actionType = null
        )
    )

    fun symptomReports() = listOf(
        SymptomReportEntity(
            patientId = PATIENT_ID,
            symptoms = "[\"Mild shortness of breath\",\"Fatigue\"]",
            severity = 2, additionalNote = "Felt breathless after climbing stairs",
            agentRiskLevel = "WARNING", agentReason = "Dyspnoea in CHF patient post-discharge requires monitoring",
            agentRecommendedAction = "Clinician notified. Increase rest and monitor for 24h.",
            escalatedImmediately = false, appointmentBooked = false,
            reportedAt = today.minusDays(1).format(dateFmt) + " 19:42:00",
            source = "manual", syncedToHospital = true, clinicianReviewed = false
        ),
        SymptomReportEntity(
            patientId = PATIENT_ID,
            symptoms = "[\"Ankle swelling\"]",
            severity = 1, additionalNote = "",
            agentRiskLevel = "WARNING", agentReason = "Ankle oedema in CHF patient — possible fluid retention",
            agentRecommendedAction = "Monitor daily. Weigh tomorrow morning. Alert if worsens.",
            escalatedImmediately = false, appointmentBooked = false,
            reportedAt = today.format(dateFmt) + " 08:05:00",
            source = "manual", syncedToHospital = false, clinicianReviewed = false
        )
    )

    fun emrProposals() = listOf(
        EmrProposalEntity(
            patientId = PATIENT_ID,
            proposalType = "MEDICATION_UPDATE",
            proposedChanges = """{"field":"adherenceLog","medicationId":"MED004","missedDates":["${today.minusDays(1).format(dateFmt)}","${today.format(dateFmt)}"],"missedStreak":2}""",
            justification = "Spironolactone missed for 2 consecutive days. Critical cardiac medication. Clinician review required.",
            urgency = "URGENT", requiresClinicianReview = true, autoApprovable = false,
            status = "PENDING", createdAt = now.format(tsFmt),
            reviewedAt = null, reviewedBy = null, agentId = "adherence_agent"
        ),
        EmrProposalEntity(
            patientId = PATIENT_ID,
            proposalType = "SYMPTOM_RECORD",
            proposedChanges = """{"symptoms":["Ankle swelling"],"severity":1,"riskLevel":"WARNING"}""",
            justification = "Patient self-reported ankle swelling. Recorded to follow-up database.",
            urgency = "ROUTINE", requiresClinicianReview = false, autoApprovable = true,
            status = "APPROVED", createdAt = now.format(tsFmt),
            reviewedAt = now.format(tsFmt), reviewedBy = "auto", agentId = "symptom_agent"
        )
    )
}
