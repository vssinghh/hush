package com.hush.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═════════════════════════════════════════════════════════════════════
//  "Nocturne" palette — the moment after the noise stops.
//  Light theme reads like paper at dawn; dark theme like a night sky.
// ═════════════════════════════════════════════════════════════════════

// ── Brand ──
val InkIndigo = Color(0xFF3E3663)      // deep dusk indigo — primary (light)
val MoonLilac = Color(0xFFC7BAF4)      // moonlit lilac — primary (dark)

// ── Dawn (light) surfaces ──
val DawnLinen = Color(0xFFF5F2EB)
val DawnCard = Color(0xFFFDFCF8)
val DawnInk = Color(0xFF232030)
val DawnInkMuted = Color(0xFF6F6A7B)
val DawnHairline = Color(0xFFE2DDD2)

val DawnContainerLowest = Color(0xFFFFFFFF)
val DawnContainerLow = Color(0xFFEFECE3)
val DawnContainer = Color(0xFFEAE6DC)
val DawnContainerHigh = Color(0xFFE3DFD3)
val DawnContainerHighest = Color(0xFFDCD7CA)

// ── Midnight (dark) surfaces ──
val MidnightSky = Color(0xFF121019)
val MidnightCard = Color(0xFF1C1926)
val MidnightInk = Color(0xFFECE8F4)
val MidnightInkMuted = Color(0xFFA39EB1)
val MidnightHairline = Color(0xFF353040)

val MidnightContainerLowest = Color(0xFF0C0A11)
val MidnightContainerLow = Color(0xFF181521)
val MidnightContainer = Color(0xFF1D1A28)
val MidnightContainerHigh = Color(0xFF272232)
val MidnightContainerHighest = Color(0xFF312B3F)

// ── Semantic action colors (dusty, never neon) ──
val EmberRed = Color(0xFFD9645C)       // Block
val DuskGold = Color(0xFFB98A2A)       // Mute
val SageGreen = Color(0xFF5E9B67)      // Allow / delivered / granted
val SlateBlue = Color(0xFF6480C4)      // informational (apps, downloads)
val PlumMist = Color(0xFF9384CD)       // match patterns / AI accents
val HarborTeal = Color(0xFF4F9490)     // time windows

// ── Primary roles ──
val PrimaryLight = InkIndigo
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE6E0F6)
val OnPrimaryContainerLight = Color(0xFF29224A)

val PrimaryDark = MoonLilac
val OnPrimaryDark = Color(0xFF2E2653)
val PrimaryContainerDark = Color(0xFF463D75)
val OnPrimaryContainerDark = Color(0xFFE6E0F6)

// ── Error ──
val ErrorLight = Color(0xFFB3261E)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)

val ErrorDark = Color(0xFFF2B8B5)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)
