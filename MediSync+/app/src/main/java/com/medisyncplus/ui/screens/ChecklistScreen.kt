package com.medisyncplus.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisyncplus.data.models.ChecklistTaskEntity
import com.medisyncplus.ui.components.*
import com.medisyncplus.ui.theme.*
import com.medisyncplus.viewmodel.ChecklistUiState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

// ── Vital input dialog state ──────────────────────────────────────────────────
data class VitalInputRequest(val taskId: String, val vitalType: String, val taskDesc: String)

@Composable
fun ChecklistScreen(
    state: ChecklistUiState,
    onToggleTask: (String, Boolean) -> Unit,
    onRecordWeight: (Float) -> Unit,
    onRecordBp: (Int, Int) -> Unit,
    onRecordBloodSugar: (Float) -> Unit,
    onRecordPulse: (Int) -> Unit = {},
    onRecordSpo2: (Float) -> Unit = {},
    onRecordTemperature: (Float) -> Unit = {},
    onCriticalAlert: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val tasks = state.tasks
    val done  = state.completedCount
    val total = tasks.size
    val pct   = if (total > 0) done.toFloat() / total else 0f

    var vitalRequest by remember { mutableStateOf<VitalInputRequest?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {

        ScreenHeader(title = "Today's Checklist")

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

            // ── Progress bar ──────────────────────────────────────────────────
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = if (pct >= 1f) Green500 else Teal500,
                trackColor = BorderGrey
            )
            Text("$done of $total tasks completed today",
                fontSize = 13.sp, color = SlateGrey, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))

            if (pct >= 1f) {
                AlertBanner("🎉 All tasks completed today! Great job, Alex.",
                    AlertType.SUCCESS, modifier = Modifier.padding(bottom = 12.dp))
            }

            // ── Tasks by time of day ──────────────────────────────────────────
            listOf("MORNING" to "🌅 Morning", "AFTERNOON" to "☀️ Afternoon", "EVENING" to "🌙 Evening").forEach { (period, label) ->
                val periodTasks = tasks.filter { it.timeOfDay == period }
                if (periodTasks.isEmpty()) return@forEach

                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SlateGrey,
                    letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 8.dp))

                Card(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderGrey),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(4.dp)) {
                        periodTasks.forEachIndexed { idx, task ->
                            ChecklistTaskRow(
                                task = task,
                                onToggle = {
                                    if (task.isDone) {
                                        onToggleTask(task.id, false)
                                    } else {
                                        // Time restriction check
                                        val now = LocalTime.now()
                                        val scheduledTime = try { 
                                            LocalTime.parse(task.scheduledTime, DateTimeFormatter.ofPattern("HH:mm")) 
                                        } catch (e: Exception) { LocalTime.MIN }

                                        if (now.isBefore(scheduledTime)) {
                                            Toast.makeText(context, "It's too early for this task. Scheduled for ${task.scheduledTime}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            if (task.requiresVitalInput && task.vitalType != null) {
                                                vitalRequest = VitalInputRequest(task.id, task.vitalType, task.description)
                                            } else {
                                                onToggleTask(task.id, true)
                                            }
                                        }
                                    }
                                }
                            )
                            if (idx < periodTasks.lastIndex) {
                                HorizontalDivider(color = BorderGrey, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }

            // ── Smart reminders info ──────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BlueLight),
                border = BorderStroke(1.dp, Blue500.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🔔", fontSize = 18.sp)
                    Column {
                        Text("Smart Reminders Active", fontWeight = FontWeight.SemiBold, color = Blue500)
                        Text("MediSync AI will send notifications for uncompleted tasks. Missed vitals automatically generate clinician alerts.",
                            fontSize = 12.sp, color = Blue500.copy(alpha = 0.8f), lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }

    // ── Vital Input Dialog ────────────────────────────────────────────────────
    vitalRequest?.let { req ->
        VitalInputDialog(
            vitalType = req.vitalType,
            taskDesc = req.taskDesc,
            onSave = { w, sys, dia, sugar, pulse, spo2, temp ->
                when (req.vitalType) {
                    "weight" -> w?.let { 
                        onRecordWeight(it)
                    }
                    "bp"     -> if (sys != null && dia != null) {
                        onRecordBp(sys, dia)
                        if (sys >= 160 || dia >= 100) onCriticalAlert("High Blood Pressure", "BP reading $sys/$dia is dangerously high. Care team notified.")
                    }
                    "blood_sugar" -> sugar?.let { 
                        onRecordBloodSugar(it)
                        if (it >= 11f) onCriticalAlert("Hyperglycemia", "Blood sugar is critically high (%.1f mmol/L).".format(it))
                        else if (it < 3.9f) onCriticalAlert("Hypoglycemia", "Blood sugar is dangerously low (%.1f mmol/L).".format(it))
                    }
                    "pulse" -> pulse?.let {
                        onRecordPulse(it)
                        if (it >= 120 || it <= 50) onCriticalAlert("Pulse Alert", "Pulse rate $it bpm is outside normal range.")
                    }
                    "spo2" -> spo2?.let {
                        onRecordSpo2(it)
                        if (it < 90f) onCriticalAlert("Low Oxygen", "SpO2 reading %.0f%% is critically low.".format(it))
                    }
                    "temperature" -> temp?.let {
                        onRecordTemperature(it)
                        if (it >= 39.0f || it <= 35.0f) onCriticalAlert("Temperature Alert", "Temperature %.1f°C is abnormal.".format(it))
                    }
                }
                onToggleTask(req.taskId, true)
                vitalRequest = null
            },
            onDismiss = {
                // Not completed, just cancel dialog
                vitalRequest = null
            }
        )
    }
}

@Composable
fun ChecklistTaskRow(task: ChecklistTaskEntity, onToggle: () -> Unit) {
    val now = LocalTime.now()
    val scheduled = try {
        LocalTime.parse(task.scheduledTime, DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) { LocalTime.MIN }

    val isOverdue = !task.isDone && now.isAfter(scheduled)
    val isSoon = !task.isDone && !now.isAfter(scheduled) && now.isAfter(scheduled.minusHours(1))

    val bgColor = when {
        task.isDone -> GreenLight
        isOverdue -> RedLight.copy(alpha = 0.15f)
        isSoon -> AmberLight.copy(alpha = 0.4f)
        else -> Color.White
    }
    
    val borderColor = when {
        task.isDone -> Green500
        isOverdue -> Red500.copy(alpha = 0.3f)
        isSoon -> Amber500.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Checkbox circle
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (task.isDone) Teal500 else Color.White)
                .border(2.dp, if (task.isDone) Teal500 else if (isOverdue) Red500.copy(alpha = 0.4f) else if (isSoon) Amber500.copy(alpha = 0.5f) else BorderGrey, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (task.isDone) {
                Icon(Icons.Default.Check, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        // Task icon
        Text(
            when (task.iconName) {
                "scale"      -> "⚖️"; "medication" -> "💊"; "walk"       -> "🚶"
                "bp"         -> "🩺"; "check"      -> "👀"; "report"     -> "📋"
                else         -> "✅"
            },
            fontSize = 20.sp
        )

        Column(Modifier.weight(1f)) {
            Text(
                task.description,
                fontSize = 14.sp,
                fontWeight = if (task.isDone) FontWeight.Normal else FontWeight.Medium,
                color = if (task.isDone) SlateGrey else if (isOverdue) Red500 else Color(0xFF0F172A),
                textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None
            )
            if (!task.isDone) {
                Text(
                    if (isOverdue) "Overdue (Scheduled: ${task.scheduledTime})" 
                    else "Scheduled: ${task.scheduledTime}",
                    fontSize = 11.sp, 
                    color = if (isOverdue) Red500 else SlateGrey
                )
            }
            if (task.requiresVitalInput && !task.isDone) {
                Text("Tap to log reading", fontSize = 11.sp, color = Teal500, modifier = Modifier.padding(top = 2.dp))
            }
            if (task.isDone && task.completedAt != null) {
                Text("Done at ${task.completedAt.takeLast(8).take(5)}", fontSize = 11.sp, color = SlateGrey)
            }
        }

        if (task.isDone) {
            Text("✅", fontSize = 18.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalInputDialog(
    vitalType: String,
    taskDesc: String,
    onSave: (weight: Float?, systolic: Int?, diastolic: Int?, sugar: Float?, pulse: Int?, spo2: Float?, temp: Float?) -> Unit,
    onDismiss: () -> Unit
) {
    var weightVal by remember { mutableStateOf("") }
    var sysVal    by remember { mutableStateOf("") }
    var diaVal    by remember { mutableStateOf("") }
    var sugarVal  by remember { mutableStateOf("") }
    var pulseVal  by remember { mutableStateOf("") }
    var spo2Val   by remember { mutableStateOf("") }
    var tempVal   by remember { mutableStateOf("") }
    
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (vitalType) {
                "weight"      -> "⚖️ Record Weight"
                "bp"          -> "🩺 Record Blood Pressure"
                "blood_sugar" -> "🩸 Record Blood Sugar"
                else          -> "Record Reading"
            }, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Recording for: $taskDesc", fontSize = 13.sp, color = SlateGrey)
                when (vitalType) {
                    "weight" -> OutlinedTextField(
                        value = weightVal, onValueChange = { weightVal = it; errorText = null },
                        label = { Text("Weight (kg)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorText != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                    "bp" -> {
                        OutlinedTextField(
                            value = sysVal, onValueChange = { sysVal = it; errorText = null },
                            label = { Text("Systolic (mmHg)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorText != null,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = diaVal, onValueChange = { diaVal = it; errorText = null },
                            label = { Text("Diastolic (mmHg)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorText != null,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                    "blood_sugar" -> OutlinedTextField(
                        value = sugarVal, onValueChange = { sugarVal = it; errorText = null },
                        label = { Text("Blood Sugar (mmol/L)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorText != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                    "pulse" -> OutlinedTextField(
                        value = pulseVal, onValueChange = { pulseVal = it; errorText = null },
                        label = { Text("Pulse Rate (bpm)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorText != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    "spo2" -> OutlinedTextField(
                        value = spo2Val, onValueChange = { spo2Val = it; errorText = null },
                        label = { Text("SpO2 (%)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorText != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                    "temperature" -> OutlinedTextField(
                        value = tempVal, onValueChange = { tempVal = it; errorText = null },
                        label = { Text("Temperature (°C)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorText != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                }
                
                errorText?.let {
                    Text(it, color = Red500, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                
                Text("Reading will be synced to hospital EMR automatically.",
                    fontSize = 11.sp, color = Teal500)
            }
        },
        confirmButton = {
            Button(onClick = {
                val weight = weightVal.toFloatOrNull()
                val sys = sysVal.toIntOrNull()
                val dia = diaVal.toIntOrNull()
                val sugar = sugarVal.toFloatOrNull()
                val pulse = pulseVal.toIntOrNull()
                val spo2 = spo2Val.toFloatOrNull()
                val temp = tempVal.toFloatOrNull()

                var hasError = false
                when (vitalType) {
                    "weight" -> {
                        if (weight == null) { errorText = "Please enter weight"; hasError = true }
                        else if (weight < 30f || weight > 300f) { errorText = "Please enter reasonable weight (30-300kg)"; hasError = true }
                    }
                    "bp" -> {
                        if (sys == null || dia == null) { errorText = "Please enter BP"; hasError = true }
                        else if (sys < 50 || sys > 250 || dia < 30 || dia > 150) { errorText = "Please enter reasonable BP"; hasError = true }
                    }
                    "blood_sugar" -> {
                        if (sugar == null) { errorText = "Please enter sugar level"; hasError = true }
                        else if (sugar < 1f || sugar > 50f) { errorText = "Please enter reasonable sugar (1-50)"; hasError = true }
                    }
                    "pulse" -> {
                        if (pulse == null) { errorText = "Please enter pulse"; hasError = true }
                        else if (pulse < 30 || pulse > 250) { errorText = "Please enter reasonable pulse (30-250)"; hasError = true }
                    }
                    "spo2" -> {
                        if (spo2 == null) { errorText = "Please enter SpO2"; hasError = true }
                        else if (spo2 < 50f || spo2 > 100f) { errorText = "Please enter reasonable SpO2 (50-100%)"; hasError = true }
                    }
                    "temperature" -> {
                        if (temp == null) { errorText = "Please enter temperature"; hasError = true }
                        else if (temp < 30f || temp > 45f) { errorText = "Please enter reasonable temperature (30-45°C)"; hasError = true }
                    }
                }

                if (!hasError) {
                    onSave(weight, sys, dia, sugar, pulse, spo2, temp)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Teal500)) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
