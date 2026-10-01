package org.mmclark.koreloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.presentation.home.HomeViewModel
import org.mmclark.koreloader.presentation.packages.PackageDetailViewModel
import org.mmclark.koreloader.presentation.packages.PackagesViewModel
import org.mmclark.koreloader.ui.home.HomeScreen
import org.mmclark.koreloader.ui.packages.PackageDetailScreen
import org.mmclark.koreloader.ui.packages.PackagesScreen
import org.mmclark.koreloader.ui.theme.KoreloaderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // koreloader mark + wordmark (Theme.Koreloader.Splash)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KoreloaderApplication).container
        setContent {
            KoreloaderTheme {
                // Activity-scoped: the selected Kobo volume is shared by every screen.
                val home = viewModel { HomeViewModel(container.volumeFactory, container.detectKoboDevice) }
                val homeState by home.state.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = home,
                            onManagePackages = { navController.navigate("packages") },
                        )
                    }
                    composable("packages") {
                        val volume = homeState.volume ?: return@composable LaunchedEffect(Unit) { navController.popBackStack("home", false) }
                        val viewModel = viewModel { PackagesViewModel(volume, container.volumeFactory, container.getPackageOverview) }
                        LifecycleResumeEffect(viewModel) {
                            viewModel.refresh()
                            onPauseOrDispose {}
                        }
                        PackagesScreen(
                            viewModel = viewModel,
                            onOpenPackage = { id -> navController.navigate("package/${id.value}") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("package/{id}") { entry ->
                        val volume = homeState.volume ?: return@composable LaunchedEffect(Unit) { navController.popBackStack("home", false) }
                        val id = PackageId(entry.arguments?.getString("id").orEmpty())
                        val viewModel = viewModel {
                            PackageDetailViewModel(
                                id, volume, container.volumeFactory,
                                container.getPackageOverview, container.installPackage, container.uninstallPackage,
                            )
                        }
                        PackageDetailScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
