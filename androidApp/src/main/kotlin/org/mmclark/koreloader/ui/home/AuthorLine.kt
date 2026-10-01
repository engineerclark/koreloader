package org.mmclark.koreloader.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import org.mmclark.koreloader.presentation.credits.AppAuthor

/** "koreloader by Michael Clark · michael.mmclark.org", with the website linked. */
@Composable
fun AuthorLine(modifier: Modifier = Modifier) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    Text(
        buildAnnotatedString {
            append("koreloader by ${AppAuthor.NAME} · ")
            withLink(LinkAnnotation.Url(AppAuthor.WEBSITE, linkStyles)) { append(AppAuthor.WEBSITE_LABEL) }
        },
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
