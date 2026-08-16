package com.something.keystrokes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun KeyButton(
    text: String,
    pressed: Boolean
) {

    Box(
        modifier = Modifier
            .size(60.dp)
            .background(
                if (pressed)
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                else
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = text
        )
    }
}