package org.mmclark.koreloader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.mmclark.koreloader.presentation.credits.Credits

/** Attribution for the upstream projects koreloader is built on, with links to them. */
@Composable
fun CreditsCard(modifier: Modifier = Modifier) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Built on the work of", style = MaterialTheme.typography.titleSmall)
            Credits.projects.forEach { credit ->
                Text(
                    buildAnnotatedString {
                        withLink(LinkAnnotation.Url(credit.url, linkStyles)) {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(credit.project) }
                        }
                        append(" by ${credit.author}: ${credit.usedFor}.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                buildAnnotatedString {
                    append("Reference data: ")
                    Credits.references.forEachIndexed { index, reference ->
                        if (index > 0) append(", ")
                        withLink(LinkAnnotation.Url(reference.url, linkStyles)) { append(reference.label) }
                    }
                    append(".")
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "koreloader is not affiliated with these projects or with Rakuten Kobo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
