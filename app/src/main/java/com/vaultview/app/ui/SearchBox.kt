package com.vaultview.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A search box that only starts typing once you press it (A or a tap),
 * so moving the D-pad across it never pops the keyboard up by accident.
 */
@Composable
fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(28.dp)

    AnimatedContent(
        targetState = editing,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        label = "search",
        modifier = modifier,
    ) { isEditing ->
        if (!isEditing) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .focusRing(shape, 1.02f)
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .clickable { hadFocus = false; editing = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = Muted)
                Spacer(Modifier.width(12.dp))
                Text(
                    query.ifEmpty { placeholder },
                    color = if (query.isEmpty()) Muted else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, "Clear") }
                }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(placeholder) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Filled.Close, "Clear") } },
                singleLine = true,
                shape = shape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(); keyboard?.hide(); editing = false }),
                modifier = Modifier.fillMaxWidth().focusRequester(field).onFocusChanged {
                    if (it.isFocused) hadFocus = true else if (hadFocus) editing = false
                },
            )
            LaunchedEffect(Unit) { runCatching { field.requestFocus(); keyboard?.show() } }
        }
    }
}
