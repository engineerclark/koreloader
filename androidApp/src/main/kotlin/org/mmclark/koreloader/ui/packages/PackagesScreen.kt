package org.mmclark.koreloader.ui.packages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mmclark.koreloader.application.packages.OverviewResult
import org.mmclark.koreloader.application.packages.PackageOverview
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.presentation.packages.PackagesViewModel

/** Grid of package tiles, each with its install/update status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagesScreen(viewModel: PackagesViewModel, onOpenPackage: (PackageId) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kobo packages") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Refresh") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (val overview = state.overview) {
                null -> Unit
                is OverviewResult.DeviceProblem, is OverviewResult.VolumeUnavailable -> Text(
                    "Can't read the Kobo. Is it still connected? Go back and tap Re-check.",
                    modifier = Modifier.padding(16.dp),
                )
                is OverviewResult.Ready -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (overview.koboUpdatePending) {
                        item(span = { GridItemSpan(maxLineSpan) }) { PendingUpdateNote(Modifier.padding(top = 12.dp)) }
                    }
                    items(overview.packages, key = { it.definition.id.value }) { pkg ->
                        PackageTile(pkg, onClick = { onOpenPackage(pkg.definition.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageTile(pkg: PackageOverview, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(logoFor(pkg.definition.id)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            Text(pkg.definition.name, style = MaterialTheme.typography.titleMedium)
            StatusChip(pkg.status.label())
        }
    }
}

@Composable
fun PendingUpdateNote(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            "A Kobo update is waiting (.kobo/KoboRoot.tgz). It runs when you eject and unplug the Kobo, which then restarts.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

