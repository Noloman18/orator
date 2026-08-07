package com.noloxtreme.tts.reader.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared visual tokens. Feature modules consume these instead of inventing local values. */
object OratorDesignTokens {
    // Roman fresco palette: cinnabar, aged stone, olive, and bronze.
    val primary = Color(0xFF8B3A32)
    val primaryContainer = Color(0xFFF1D4CB)
    val secondary = Color(0xFF686047)
    val secondaryContainer = Color(0xFFEDE4C8)
    val tertiary = Color(0xFF8B5E28)
    val warmHighlight = Color(0xFFD3A646)
    val parchment = Color(0xFFFFF8F0)
    val ink = Color(0xFF241A16)
    val nightPrimary = Color(0xFFFFB4A8)
    val nightPrimaryContainer = Color(0xFF74302B)
    val nightSecondary = Color(0xFFD5C7A7)
    val nightSecondaryContainer = Color(0xFF4F4932)
    val nightTertiary = Color(0xFFE8BB77)
    val nightTertiaryContainer = Color(0xFF604313)
    val nightBackground = Color(0xFF1B1512)
    val nightSurfaceVariant = Color(0xFF50453D)
    val nightOnSurfaceVariant = Color(0xFFD8C2B6)
    val readerHorizontalPadding = 22.dp
    val readerParagraphGap = 18.dp
    val defaultReaderFontSize = 20.sp
}
