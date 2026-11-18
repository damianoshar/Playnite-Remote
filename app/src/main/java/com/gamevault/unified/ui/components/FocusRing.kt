package com.gamevault.unified.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visible focus ring for D-pad / controller / keyboard navigation.
 *
 * Touch screens never focus a list row, so the ring only shows while the
 * item actually holds focus (remote, hardware keyboard or the TalkBack
 * keyboard).
 */
@Composable
fun Modifier.focusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 3.dp
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(width, color, shape) else Modifier)
}
