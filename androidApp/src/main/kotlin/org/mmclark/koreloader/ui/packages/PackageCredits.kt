package org.mmclark.koreloader.ui.packages

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
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.presentation.credits.Credits

/** Who made what this package installs, with links to the upstream projects. */
@Composable
fun PackageCredits(id: PackageId, modifier: Modifier = Modifier) {
    val credits = Credits.forPackage(id)
    if (credits.isEmpty()) return
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Credits", style = MaterialTheme.typography.titleSmall)
            credits.forEach { credit ->
                Text(
                    buildAnnotatedString {
                        withLink(LinkAnnotation.Url(credit.url, linkStyles)) {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(credit.project) }
                        }
                        append(" by ${credit.author} (${credit.license}): ${credit.usedFor}.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "koreloader downloads these from their official releases; it is not affiliated with these projects.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
