package com.gamevault.unified.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

fun highlightedText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val lower = text.lowercase()
    val q = query.lowercase()
    return buildAnnotatedString {
        var start = 0
        var idx = lower.indexOf(q, start)
        while (idx >= 0) {
            append(text.substring(start, idx))
            withStyle(SpanStyle(background = Color(0xFFFFEB3B), color = Color.Black)) {
                append(text.substring(idx, idx + q.length))
            }
            start = idx + q.length
            idx = lower.indexOf(q, start)
        }
        append(text.substring(start))
    }
}