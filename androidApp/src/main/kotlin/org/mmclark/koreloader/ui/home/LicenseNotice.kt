package org.mmclark.koreloader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.mmclark.koreloader.presentation.credits.AppLicense

/**
 * koreloader's license notice, in the form the GPL's "How to Apply" recommends for
 * interactive programs, with a link and the full text (LICENSE, bundled as an asset).
 */
@Composable
fun LicenseNotice() {
    var showLicense by rememberSaveable { mutableStateOf(false) }
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("koreloader · ${AppLicense.COPYRIGHT}", style = MaterialTheme.typography.labelMedium)
        Text(
            buildAnnotatedString {
                append("This program comes with ABSOLUTELY NO WARRANTY. It is free software, and you are welcome to " +
                    "redistribute it under the terms of the ")
                withLink(LinkAnnotation.Url(AppLicense.URL, linkStyles)) { append(AppLicense.NAME) }
                append(" (${AppLicense.SPDX_ID}).")
                AppLicense.SOURCE_URL?.let { url ->
                    append(" Source code: ")
                    withLink(LinkAnnotation.Url(url, linkStyles)) { append(url) }
                    append(".")
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { showLicense = true }) { Text("View full license") }
    }
    if (showLicense) LicenseDialog(onDismiss = { showLicense = false })
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching { context.assets.open("LICENSE.txt").bufferedReader().use { reflow(it.readText()) } }
            .getOrElse { "The license text is missing from this build. See ${AppLicense.URL}" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppLicense.NAME) },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** The license file is hard-wrapped at ~70 columns; join each paragraph's lines so it wraps to the screen. */
private fun reflow(text: String): String =
    text.split(Regex("\\n\\s*\\n"))
        .joinToString("\n\n") { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
