package com.medisyncplus.ui.components

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisyncplus.ui.theme.*

// ── Risk Badge ────────────────────────────────────────────────────────────────
@Composable
fun RiskBadge(risk: String, modifier: Modifier = Modifier) {
    val (bg, fg, text) = when (risk.uppercase()) {
        "CRITICAL" -> Triple(RedLight, Red500, "⚠ Critical")
        "WARNING"  -> Triple(AmberLight, Amber500, "⚠ Warning")
        else       -> Triple(GreenLight, Green500, "● Stable")
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = CircleShape
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── Status Badge ──────────────────────────────────────────────────────────────
@Composable
fun StatusBadge(label: String, color: Color, bgColor: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = bgColor, shape = CircleShape) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

// ── Section Card ──────────────────────────────────────────────────────────────
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Info Row ──────────────────────────────────────────────────────────────────
@Composable
fun InfoRow(icon: String, label: String, value: String, valueColor: Color = Color(0xFF0F172A)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SlateGrey)
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = valueColor)
        }
    }
}

// ── Pill Dot ──────────────────────────────────────────────────────────────────
@Composable
fun PillDot(colorHex: String, size: Int = 14) {
    val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Teal500 }
    Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.3f)).border(1.dp, color, CircleShape))
}

// ── Alert Banner ──────────────────────────────────────────────────────────────
@Composable
fun AlertBanner(message: String, type: AlertType, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val (bg, border, icon) = when (type) {
        AlertType.DANGER  -> Triple(RedLight, Red500, "🚨")
        AlertType.WARNING -> Triple(AmberLight, Amber500, "⚠️")
        AlertType.SUCCESS -> Triple(GreenLight, Green500, "✅")
        AlertType.INFO    -> Triple(BlueLight, Blue500, "ℹ️")
    }
    Surface(modifier = modifier.fillMaxWidth(), color = bg, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, border)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(icon, fontSize = 16.sp)
            Text(message, Modifier.weight(1f), color = border, fontSize = 13.sp, lineHeight = 20.sp)
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(18.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = border, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

enum class AlertType { DANGER, WARNING, SUCCESS, INFO }

// ── Vital Card ────────────────────────────────────────────────────────────────
@Composable
fun VitalCard(
    icon: String, 
    label: String, 
    value: String, 
    unit: String, 
    flag: String, 
    time: String, 
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val flagColor = when (flag) { "critical" -> Red500; "warning" -> Amber500; else -> Teal500 }
    Card(onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateLight),
        border = BorderStroke(1.dp, if (flag != "normal") flagColor.copy(alpha = 0.4f) else BorderGrey),
        shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(), 
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = flagColor, textAlign = TextAlign.Center)
            Text(unit, fontSize = 11.sp, color = SlateGrey, textAlign = TextAlign.Center)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateGrey, textAlign = TextAlign.Center, lineHeight = 14.sp)
            if (time.isNotEmpty()) Text(time, fontSize = 10.sp, color = SlateGrey.copy(alpha = 0.7f))
            
            // Fixed height spacer or container for badges to avoid jumps
            Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (flag == "warning") StatusBadge("↑ Monitor", Amber500, AmberLight)
                if (flag == "critical") StatusBadge("⚠ Critical", Red500, RedLight)
            }
        }
    }
}

// ── Divider ───────────────────────────────────────────────────────────────────
@Composable
fun MediDivider() = HorizontalDivider(color = BorderGrey, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

// ── Agent Status Row ──────────────────────────────────────────────────────────
@Composable
fun AgentStatusRow(agentName: String, status: String, statusColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
        Text(agentName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(130.dp))
        Text(status, fontSize = 12.sp, color = SlateGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Loading Shimmer ───────────────────────────────────────────────────────────
@Composable
fun AgentThinkingCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TealLight),
        border = BorderStroke(1.dp, Teal500),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Teal500, strokeWidth = 2.dp)
            Text("🤖 MediSync AI is analysing...", color = Teal700, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Primary Button ────────────────────────────────────────────────────────────
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                  enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Teal500),
        shape = RoundedCornerShape(12.dp)) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

// ── Outline Button ────────────────────────────────────────────────────────────
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(50.dp),
        border = BorderStroke(1.5.dp, Teal500),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal500)) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

// ── Screen Header ─────────────────────────────────────────────────────────────
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Surface(color = Color.White, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            action?.invoke()
        }
    }
}

// ── Medication Status Color ───────────────────────────────────────────────────
fun medStatusColor(status: String) = when (status) {
    "TAKEN"    -> Green500
    "MISSED"   -> Red500
    "UPCOMING" -> Amber500
    else       -> SlateGrey
}

fun medStatusBg(status: String) = when (status) {
    "TAKEN"    -> GreenLight
    "MISSED"   -> RedLight
    "UPCOMING" -> AmberLight
    else       -> Color.White
}
