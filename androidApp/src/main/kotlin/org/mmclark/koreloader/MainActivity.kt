package org.mmclark.koreloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.presentation.install.InstallViewModel
import org.mmclark.koreloader.presentation.storagecheck.StorageCheckViewModel
import org.mmclark.koreloader.ui.install.InstallScreen
import org.mmclark.koreloader.ui.storagecheck.StorageCheckScreen
import org.mmclark.koreloader.ui.theme.KoreloaderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KoreloaderApplication).container
        setContent {
            KoreloaderTheme {
                var showStorageTest by rememberSaveable { mutableStateOf(false) }
                if (showStorageTest) {
                    val viewModel = viewModel {
                        StorageCheckViewModel(container.volumeFactory, container.detectKoboDevice, container.runStorageCheck)
                    }
                    StorageCheckScreen(viewModel, onBack = { showStorageTest = false })
                } else {
                    val viewModel = viewModel {
                        InstallViewModel(
                            PackageId.KOREADER,
                            container.volumeFactory,
                            container.fetchPackageRelease,
                            container.detectKoboDevice,
                            container.installPackage,
                        )
                    }
                    InstallScreen(viewModel, onOpenStorageTest = { showStorageTest = true })
                }
            }
        }
    }
}
