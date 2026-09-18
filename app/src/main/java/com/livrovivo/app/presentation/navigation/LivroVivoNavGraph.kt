package com.livrovivo.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.ui.CompanionAvatar
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.presentation.creation.CreationScreen
import com.livrovivo.app.presentation.creation.CreationViewModel
import com.livrovivo.app.presentation.home.HomeScreen
import com.livrovivo.app.presentation.home.HomeViewModel
import com.livrovivo.app.presentation.onboarding.OnboardingMode
import com.livrovivo.app.presentation.onboarding.OnboardingScreen
import com.livrovivo.app.presentation.onboarding.OnboardingViewModel
import com.livrovivo.app.presentation.parent.ParentDashboardScreen
import com.livrovivo.app.presentation.parent.ParentDashboardViewModel
import com.livrovivo.app.presentation.paywall.PaywallScreen
import com.livrovivo.app.presentation.paywall.PaywallViewModel
import com.livrovivo.app.presentation.reader.ReaderScreen
import com.livrovivo.app.presentation.reader.ReaderViewModel
import com.livrovivo.app.presentation.settings.SettingsScreen
import com.livrovivo.app.presentation.settings.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Decide a tela inicial uma única vez, depois de ler o perfil salvo
 * (evita "piscar" o onboarding e não troca o grafo de navegação depois).
 */
class AppStartViewModel(
    getActiveChildUseCase: GetActiveChildUseCase,
    settingsManager: SettingsManager
) : ViewModel() {
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.applyPendingDefaults()
            val child = getActiveChildUseCase.getDirect()
            _startDestination.value = if (child != null) Screen.Home.route else Screen.Onboarding.route
        }
    }
}

@Composable
fun LivroVivoNavGraph(
    navController: NavHostController,
    startViewModel: AppStartViewModel = koinViewModel()
) {
    val startDestination by startViewModel.startDestination.collectAsState()
    val start = startDestination
    if (start == null) {
        SplashContent()
        return
    }

    NavHost(
        navController = navController,
        startDestination = start
    ) {
        composable(Screen.Onboarding.route) {
            val viewModel: OnboardingViewModel =
                koinViewModel(parameters = { parametersOf(OnboardingMode.FIRST_RUN) })
            OnboardingScreen(
                viewModel = viewModel,
                onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.EditProfile.route) {
            val viewModel: OnboardingViewModel =
                koinViewModel(parameters = { parametersOf(OnboardingMode.EDIT) })
            OnboardingScreen(
                viewModel = viewModel,
                onFinished = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.AddChild.route) {
            val viewModel: OnboardingViewModel =
                koinViewModel(parameters = { parametersOf(OnboardingMode.ADD_CHILD) })
            OnboardingScreen(
                viewModel = viewModel,
                onFinished = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = koinViewModel()
            HomeScreen(
                viewModel = viewModel,
                onNavigateToCreation = { navController.navigate(Screen.Creation.route) },
                onNavigateToReader = { storyId -> navController.navigate(Screen.Reader.createRoute(storyId)) },
                onNavigateToParentArea = { navController.navigate(Screen.ParentDashboard.route) },
                onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) }
            )
        }

        composable(Screen.ParentDashboard.route) {
            val viewModel: ParentDashboardViewModel = koinViewModel()
            ParentDashboardScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenPaywall = { navController.navigate(Screen.Paywall.route) },
                onEditProfile = { navController.navigate(Screen.EditProfile.route) },
                onAddChild = { navController.navigate(Screen.AddChild.route) }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = koinViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Creation.route) {
            val viewModel: CreationViewModel = koinViewModel()
            CreationScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onStoryGenerated = { storyId ->
                    navController.navigate(Screen.Reader.createRoute(storyId)) {
                        popUpTo(Screen.Creation.route) { inclusive = true }
                    }
                },
                onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(navArgument("storyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val storyId = backStackEntry.arguments?.getString("storyId").orEmpty()
            val viewModel: ReaderViewModel = koinViewModel(parameters = { parametersOf(storyId) })
            ReaderScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNewStory = {
                    navController.navigate(Screen.Creation.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.Paywall.route) {
            val viewModel: PaywallViewModel = koinViewModel()
            PaywallScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun SplashContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompanionAvatar(companion = MagicalCompanion.ALL.first(), size = 96.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Livro Vivo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
