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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisyncplus.data.models.*
import com.medisyncplus.ui.components.*
import com.medisyncplus.ui.theme.*
import com.medisyncplus.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(
    state: MedicationUiState,
    onMarkTaken: (MedicationEntity) -> Unit,
    onUnmarkTaken: (MedicationEntity) -> Unit
) {
    var showHistorySheet by remember { mutableStateOf(false) }
    var expandedMedId by remember { mutableStateOf<String?>(null) }

    val meds = state.medications
    val logs = state.todayLogs
    val takenCount   = logs.count { it.status == "TAKEN" }
    val missedCount  = logs.count { it.status == "MISSED" }
    val upcomingCount= logs.count { it.status == "UPCOMING" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {

        ScreenHeader(title = "Medications", subtitle = "Today's schedule") {
            TextButton(onClick = { showHistorySheet = true }) { Text("History", color = Teal500) }
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

            // ── Summary chips ────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = GreenLight, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                    Text("✓ $takenCount Taken", color = Green500, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, modifier = Modifier.padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Surface(color = AmberLight, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                    Text("⏰ $upcomingCount Due", color = Amber500, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, modifier = Modifier.padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Surface(color = RedLight, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                    Text("✗ $missedCount Missed", color = Red500, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, modifier = Modifier.padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Adherence score ──────────────────────────────────────────────
            state.adherenceResult?.let { adh ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = BorderStroke(1.dp, Teal500.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { adh.adherenceScore / 100f },
                                modifier = Modifier.size(56.dp),
                                color = when { adh.adherenceScore >= 90 -> Green500; adh.adherenceScore >= 70 -> Amber500; else -> Red500 },
                                trackColor = BorderGrey, strokeWidth = 5.dp
                            )
                            Text("${adh.adherenceScore}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Adherence: ${adh.adherenceLabel}", fontWeight = FontWeight.SemiBold)
                            if (adh.missedMedications.isNotEmpty()) {
                                Text("Missed: ${adh.missedMedications.joinToString { it.name }}",
                                    fontSize = 12.sp, color = Red500)
                            }
                            Text(adh.patientMessage, fontSize = 12.sp, color = SlateGrey,
                                lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

                // ── Medication cards ─────────────────────────────────────────────
            meds.forEach { med ->
                val log = logs.find { it.medicationId == med.id }
                val status = log?.status ?: "UPCOMING"
                val isExpanded = expandedMedId == med.id

                val now = java.time.LocalTime.now()
                val scheduledTime = try {
                    java.time.LocalTime.parse(med.scheduledTime, java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                } catch (e: Exception) { java.time.LocalTime.MAX }

                val isSoon = status == "UPCOMING" && !now.isAfter(scheduledTime) && now.isAfter(scheduledTime.minusHours(1))

                val cardBg = when {
                    status == "TAKEN" -> GreenLight
                    status == "MISSED" -> RedLight.copy(alpha = 0.15f)
                    isSoon -> AmberLight.copy(alpha = 0.4f)
                    else -> Color.White
                }
                
                val statusColor = when {
                    status == "TAKEN" -> Green500
                    status == "MISSED" -> Red500
                    isSoon -> Amber500
                    else -> BorderGrey
                }

                Card(
                    onClick = { expandedMedId = if (isExpanded) null else med.id },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Pill icon
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(try { Color(android.graphics.Color.parseColor(med.pillColorHex)).copy(alpha = 0.2f) } catch (e: Exception) { TealLight }),
                                contentAlignment = Alignment.Center
                            ) { Text("💊", fontSize = 22.sp) }

                            Column(Modifier.weight(1f)) {
                                Text(med.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${med.dosage} · ${med.medicationClass}", fontSize = 12.sp, color = SlateGrey)
                            }
                            Surface(
                                color = when {
                                    status == "TAKEN" -> GreenLight
                                    status == "MISSED" -> RedLight
                                    isSoon -> AmberLight
                                    else -> AmberLight.copy(alpha = 0.5f)
                                },
                                shape = CircleShape
                            ) {
                                Text(
                                    when {
                                        status == "TAKEN" -> "✓ Taken"
                                        status == "MISSED" -> "✗ Missed"
                                        isSoon -> "Due Soon"
                                        else -> "Upcoming"
                                    },
                                    color = if (status == "TAKEN") Green500 else if (status == "MISSED") Red500 else if (isSoon) Amber500 else SlateGrey,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Time row
                        Row(Modifier.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null,
                                tint = Blue500, modifier = Modifier.size(14.dp))
                            Text(med.scheduledTime, fontSize = 13.sp, color = Blue500, fontWeight = FontWeight.SemiBold)
                            Text("· ${med.frequency.replace('_',' ')}", fontSize = 13.sp, color = SlateGrey)
                        }

                        // Missed streak warning
                        if (status == "MISSED" && (log?.missedStreak ?: 0) >= 2) {
                            Surface(
                                color = RedLight, shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) {
                                Text(
                                    "⚠ Missed ${log?.missedStreak} doses in a row — care team notified",
                                    color = Red500, fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Expanded details
                        if (isExpanded) {
                            HorizontalDivider(color = BorderGrey, modifier = Modifier.padding(vertical = 10.dp))
                            Text("Instructions", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = SlateGrey, letterSpacing = 0.5.sp)
                            Text(med.instruction, fontSize = 13.sp, lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                            Text("Side Effects", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = SlateGrey, letterSpacing = 0.5.sp)
                            Text(med.sideEffects, fontSize = 13.sp, color = SlateGrey,
                                lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                            if (med.criticalMedication) {
                                Surface(color = RedLight, shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    Text("❗ Critical cardiac medication — do not skip",
                                        color = Red500, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(10.dp))
                                }
                            }
                        }

                        // Action button
                        Spacer(Modifier.height(10.dp))
                        if (status != "TAKEN") {
                            Button(
                                onClick = { onMarkTaken(med) },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (status == "MISSED") Red500 else Teal500
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (status == "MISSED") "Mark as Taken Now" else "Mark as Taken",
                                    fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onUnmarkTaken(med) },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                border = BorderStroke(1.dp, SlateGrey),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Uncheck (Mark as Not Taken)", color = SlateGrey, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // ── Timeline ─────────────────────────────────────────────────────
            SectionCard(title = "Today's Timeline") {
                meds.forEachIndexed { idx, med ->
                    val log = logs.find { it.medicationId == med.id }
                    val status = log?.status ?: "UPCOMING"
                    
                    val nowT = java.time.LocalTime.now()
                    val schedT = try {
                        java.time.LocalTime.parse(med.scheduledTime, java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    } catch (e: Exception) { java.time.LocalTime.MAX }
                    val isSoonT = status == "UPCOMING" && !nowT.isAfter(schedT) && nowT.isAfter(schedT.minusHours(1))
                    
                    val dotColor = when {
                        status == "TAKEN" -> Green500
                        status == "MISSED" -> Red500
                        isSoonT -> Amber500
                        else -> BorderGrey
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(dotColor)
                            )
                            if (idx < meds.lastIndex) {
                                Box(Modifier.width(2.dp).height(32.dp).background(BorderGrey))
                            }
                        }
                        Column(Modifier.padding(bottom = if (idx < meds.lastIndex) 8.dp else 0.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(med.scheduledTime, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(if (isSoonT) "DUE SOON" else status, fontSize = 11.sp, color = dotColor, fontWeight = FontWeight.Bold)
                            }
                            Text("${med.name} ${med.dosage}", fontSize = 12.sp, color = SlateGrey)
                        }
                    }
                }
            }
        }
    }

    // ── History Bottom Sheet ─────────────────────────────────────────────────
    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                Text("Medication History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(14.dp))
                listOf("Yesterday", "2 Days Ago", "3 Days Ago").forEach { dayLabel ->
                    Text(dayLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateGrey,
                        letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 8.dp, top = 4.dp))
                    meds.forEach { med ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillDot(med.pillColorHex)
                            Text(med.name, Modifier.weight(1f), fontSize = 13.sp)
                            val fakeTaken = (med.id.hashCode() + dayLabel.hashCode()) % 5 != 0
                            StatusBadge(
                                if (fakeTaken) "Taken" else "Missed",
                                if (fakeTaken) Green500 else Red500,
                                if (fakeTaken) GreenLight else RedLight
                            )
                        }
                        HorizontalDivider(color = BorderGrey)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
