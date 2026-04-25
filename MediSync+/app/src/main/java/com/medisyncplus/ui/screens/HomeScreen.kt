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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisyncplus.data.models.*
import com.medisyncplus.ui.components.*
import com.medisyncplus.ui.theme.*
import com.medisyncplus.viewmodel.*

@Composable
fun HomeScreen(
    homeState: HomeUiState,
    onNavigate: (String) -> Unit,
    onSOS: () -> Unit,
    onMarkMedTaken: (MedicationEntity) -> Unit
) {
    val patient = homeState.patient
    val progressPct = if (homeState.todayTasksTotal > 0)
        homeState.todayTasksDone.toFloat() / homeState.todayTasksTotal else 0f

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MediSync+", fontSize = 12.sp, color = Teal500, fontWeight = FontWeight.Bold)
                    Text("AI Post-Discharge Care", fontSize = 11.sp, color = SlateGrey)
                }
                Button(
                    onClick = onSOS,
                    colors = ButtonDefaults.buttonColors(containerColor = RedLight, contentColor = Red500),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("SOS", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

            // ── Patient Card ─────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGrey),
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Color(0xFFF0FDF4), Color(0xFFEFF6FF))))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Good morning,", fontSize = 13.sp, color = SlateGrey)
                                Text(
                                    patient?.name ?: "Loading...",
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
                                )
                                Text(
                                    patient?.primaryCondition ?: "",
                                    fontSize = 13.sp, color = SlateGrey, modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            RiskBadge(patient?.riskLevel ?: "STABLE")
                        }
                        HorizontalDivider(color = BorderGrey, modifier = Modifier.padding(vertical = 12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text("DOCTOR", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = SlateGrey, letterSpacing = 0.5.sp)
                                Text("Dr. Oscar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("DISCHARGED", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = SlateGrey, letterSpacing = 0.5.sp)
                                Text(patient?.dischargeDate ?: "--", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("MRN", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = SlateGrey, letterSpacing = 0.5.sp)
                                Text(patient?.mrn?.takeLast(6) ?: "--", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Missed medication alert ───────────────────────────────────────
            if ((homeState.agentStatus?.missedMedications?.isNotEmpty()) == true) {
                AlertBanner(
                    message = "⚠ Important dose(s) missed — please check your schedule. Care team has been notified.",
                    type = AlertType.WARNING,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // ── Next Medication ──────────────────────────────────────────────
            homeState.nextMedication?.let { med ->
                Card(
                    onClick = { onNavigate("medications") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderGrey),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(try { Color(android.graphics.Color.parseColor(med.pillColorHex)).copy(alpha = 0.15f) } catch (e: Exception) { TealLight }),
                            contentAlignment = Alignment.Center
                        ) { Text("💊", fontSize = 20.sp) }
                        Column(Modifier.weight(1f)) {
                            Text("Next Medication", fontSize = 11.sp, color = SlateGrey,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text(med.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${med.dosage} · ${med.scheduledTime}", fontSize = 12.sp, color = SlateGrey)
                        }
                        Button(
                            onClick = { onMarkMedTaken(med) },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("Take", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Today's Progress ─────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGrey),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Today's Checklist", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        TextButton(onClick = { onNavigate("checklist") }) {
                            Text("View All", color = Teal500, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progressPct },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (progressPct >= 1f) Green500 else Teal500,
                        trackColor = BorderGrey
                    )
                    Text(
                        "${homeState.todayTasksDone} of ${homeState.todayTasksTotal} tasks completed",
                        fontSize = 12.sp, color = SlateGrey, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Unread care team reminders badge ─────────────────────────────
            if (homeState.unreadReminders > 0) {
                AlertBanner(
                    message = "${homeState.unreadReminders} unread message(s) from your care team",
                    type = AlertType.INFO,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // ── Quick Actions Grid ───────────────────────────────────────────
            Text("Quick Actions", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 10.dp))
            val actions = listOf(
                Triple("💊", "Medications", "medications"),
                Triple("📊", "Log Vitals", "vitals"),
                Triple("📅", "Appointments", "appointments"),
                Triple("💬", "Ask MediSync AI", "chat"),
                Triple("📋", "Agent Audit Trail", "audit"),
                Triple("🏥", "Hospital History", "appointments")
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (icon, label, dest) ->
                            Card(
                                onClick = { onNavigate(dest) },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, BorderGrey),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(icon, fontSize = 24.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Tap to open", fontSize = 11.sp, color = SlateGrey)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
