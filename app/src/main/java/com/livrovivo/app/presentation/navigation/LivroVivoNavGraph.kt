package com.livrovivo.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.ui.CompanionAvatar
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.presentation.album.AlbumScreen
import com.livrovivo.app.presentation.album.AlbumViewModel
import com.livrovivo.app.presentation.bedtime.BedtimeScreen
import com.livrovivo.app.presentation.bedtime.BedtimeViewModel
import com.livrovivo.app.presentation.bedtime.SleepOverlay
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Decide a tela inicial uma única vez, depois de ler o perfil salvo
 * (evita "piscar" o onboarding e não troca o grafo de navegação depois).
 * Também sabe se o app está "dormindo" depois do boa-noite.
 */
class AppStartViewModel(
    getActiveChildUseCase: GetActiveChildUseCase,
    private val settingsManager: SettingsManager,
    private val audioPlayerController: AudioPlayerController
) : ViewModel() {
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    private val _sleepUntil = MutableStateFlow(0L)

    /** Até quando o app dorme (epoch ms); 0 = acordado. */
    val sleepUntil: StateFlow<Long> = _sleepUntil.asStateFlow()

    val activeChild: StateFlow<ChildProfile?> =
        getActiveChildUseCase().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            settingsManager.applyPendingDefaults()
            // Lido antes de liberar a tela inicial: quem abre o app de madrugada já cai no modo dormir.
            _sleepUntil.value = settingsManager.current().sleepUntil
            val child = getActiveChildUseCase.getDirect()
            _startDestination.value = if (child != null) Screen.Home.route else Screen.Onboarding.route
            settingsManager.settingsFlow.collect { _sleepUntil.value = it.sleepUntil }
        }
    }

    /** Um adulto acordou o app pelo portão parental. */
    fun wake() {
        audioPlayerController.stop()
        audioPlayerController.stopBedtimeMusic()
        viewModelScope.launch { settingsManager.setSleepUntil(0L) }
    }

    /** O app acordou sozinho de manhã: a caixinha já tinha parado, mas garante o silêncio. */
    fun onWokeUp() {
        audioPlayerController.stopBedtimeMusic()
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

    val sleepUntil by startViewModel.sleepUntil.collectAsState()
    val activeChild by startViewModel.activeChild.collectAsState()
    // O relógio anda enquanto o app dorme, para ele acordar sozinho às 6h mesmo aberto.
    val now by produceState(System.currentTimeMillis(), sleepUntil) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val sleeping = sleepUntil > now

    // Ao acordar (adulto ou manhã), volta para a estante em vez de reabrir a tela do ritual.
    var wasSleeping by remember { mutableStateOf(false) }
    LaunchedEffect(sleeping) {
        if (wasSleeping && !sleeping) {
            startViewModel.onWokeUp()
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
        wasSleeping = sleeping
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppNavHost(navController = navController, start = start)

        // Por cima de tudo, sem desmontar as telas: a navegação continua válida por baixo.
        if (sleeping) {
            SleepOverlay(child = activeChild, onWake = startViewModel::wake)
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    start: String
) {
    val goodnight: (String) -> Unit = { childId ->
        navController.navigate(Screen.Bedtime.createRoute(childId)) { launchSingleTop = true }
    }
    val openAlbum: (String) -> Unit = { childId ->
        navController.navigate(Screen.Album.createRoute(childId)) { launchSingleTop = true }
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
                onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) },
                onGoodnight = goodnight,
                onOpenAlbum = openAlbum
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
                onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) },
                onGoodnight = { childId ->
                    navController.navigate(Screen.Bedtime.createRoute(childId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
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
                },
                onGoodnight = goodnight,
                onOpenAlbum = openAlbum
            )
        }

        composable(
            route = Screen.Album.route,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            val viewModel: AlbumViewModel = koinViewModel(parameters = { parametersOf(childId) })
            AlbumScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Bedtime.route,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            val viewModel: BedtimeViewModel = koinViewModel(parameters = { parametersOf(childId) })
            BedtimeScreen(viewModel = viewModel)
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
