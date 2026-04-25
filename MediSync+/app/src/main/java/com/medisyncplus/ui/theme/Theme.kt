package com.medisyncplus.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Brand Colors ──────────────────────────────────────────────────────────────
val Teal500    = Color(0xFF0D9488)
val Teal700    = Color(0xFF0F766E)
val TealLight  = Color(0xFFCCFBF1)
val Blue500    = Color(0xFF2563EB)
val BlueLight  = Color(0xFFDBEAFE)
val Amber500   = Color(0xFFD97706)
val AmberLight = Color(0xFFFEF3C7)
val Red500     = Color(0xFFDC2626)
val RedLight   = Color(0xFFFEE2E2)
val Green500   = Color(0xFF16A34A)
val GreenLight = Color(0xFFDCFCE7)
val Purple500  = Color(0xFF7C3AED)
val PurpleLight= Color(0xFFEDE9FE)
val SlateGrey  = Color(0xFF475569)
val SlateLight = Color(0xFFF8FAFC)
val BorderGrey = Color(0xFFE2E8F0)

val MediSyncColorScheme = lightColorScheme(
    primary          = Teal500,
    onPrimary        = Color.White,
    primaryContainer = TealLight,
    secondary        = Blue500,
    onSecondary      = Color.White,
    error            = Red500,
    background       = SlateLight,
    surface          = Color.White,
    onSurface        = Color(0xFF0F172A),
    onBackground     = Color(0xFF0F172A),
    outline          = BorderGrey
)

val MediSyncTypography = Typography(
    headlineLarge  = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall  = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleLarge     = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge      = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall      = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = SlateGrey),
    labelSmall     = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
)

@Composable
fun MediSyncPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MediSyncColorScheme,
        typography  = MediSyncTypography,
        content     = content
    )
}
