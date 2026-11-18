package com.vaultview.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** A text field that only starts typing when pressed, so the D-pad can pass over it safely. */
@Composable
fun TapField(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var focusedAt by remember { mutableStateOf(0L) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    if (!editing) {
        Column(
            modifier.fillMaxWidth()
                .onFocusChanged { if (it.isFocused) focusedAt = android.os.SystemClock.uptimeMillis() }
                .focusRing(shape, 1.01f).clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable { if (android.os.SystemClock.uptimeMillis() - focusedAt > 450) { hadFocus = false; editing = true } }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(
                when { value.isEmpty() -> "Press to type"; secret -> "•".repeat(value.length.coerceAtMost(24)); else -> value },
                color = if (value.isEmpty()) Muted else MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, shape = shape,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); editing = false }),
            modifier = modifier.fillMaxWidth().focusRequester(field).onFocusChanged {
                if (it.isFocused) hadFocus = true else if (hadFocus) editing = false
            },
        )
        LaunchedEffect(Unit) { runCatching { field.requestFocus(); keyboard?.show() } }
    }
}
