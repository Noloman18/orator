package com.noloxtreme.tts.reader.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private val placeholderPalette = listOf(
    Color(0xFF285E61),
    Color(0xFF6B4F7A),
    Color(0xFF8A5A44),
    Color(0xFF526D3F),
    Color(0xFF365B7D),
    Color(0xFF7A4A58)
)

/** A stable, content-derived cover used when an EPUB cover is intentionally not extracted. */
@Composable
fun BookPlaceholder(
    title: String,
    sha256: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .semantics { contentDescription = title }
            .clip(RoundedCornerShape(MaterialTheme.shapes.medium.topStart)).background(
            placeholderPalette[paletteIndex(sha256)]
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = titleInitials(title),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun paletteIndex(sha256: String): Int {
    val prefix = sha256.take(2)
    return prefix.toIntOrNull(16)?.mod(placeholderPalette.size) ?: 0
}

private fun titleInitials(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val initials = when {
        words.size >= 2 -> words.take(2).mapNotNull { it.firstOrNull { c -> c.isLetterOrDigit() } }.joinToString("")
        words.size == 1 -> words.first().filter { it.isLetterOrDigit() }.take(2)
        else -> "O"
    }
    return initials.ifBlank { "O" }.uppercase().take(2)
}
