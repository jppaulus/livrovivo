package com.livrovivo.app.core.di

import com.livrovivo.app.BuildConfig
import com.livrovivo.app.core.ai.AiHttp
import com.livrovivo.app.core.ai.BackendConfig
import com.livrovivo.app.core.ai.ElevenLabsService
import com.livrovivo.app.core.ai.GeminiService
import com.livrovivo.app.core.ai.StoryWriter
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.audio.DeviceNarrationEngine
import com.livrovivo.app.core.audio.ElevenLabsNarrationEngine
import com.livrovivo.app.core.audio.GeminiNarrationEngine
import com.livrovivo.app.core.database.LivroVivoDatabase
import com.livrovivo.app.core.illustration.IllustrationService
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.data.repository.BillingRepositoryImpl
import com.livrovivo.app.data.repository.ChildProfileRepositoryImpl
import com.livrovivo.app.data.repository.StoryRepositoryImpl
import com.livrovivo.app.domain.repository.BillingRepository
import com.livrovivo.app.domain.repository.ChildProfileRepository
import com.livrovivo.app.domain.repository.StoryRepository
import com.livrovivo.app.domain.usecase.CheckStoryQuotaUseCase
import com.livrovivo.app.domain.usecase.ContinueStoryUseCase
import com.livrovivo.app.domain.usecase.DeleteAllStoriesUseCase
import com.livrovivo.app.domain.usecase.DeleteStoryUseCase
import com.livrovivo.app.domain.usecase.GenerateStoryUseCase
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.domain.usecase.GetParentInsightsUseCase
import com.livrovivo.app.domain.usecase.GetStoriesUseCase
import com.livrovivo.app.domain.usecase.GetStoryByIdUseCase
import com.livrovivo.app.domain.usecase.IllustrateChapterUseCase
import com.livrovivo.app.domain.usecase.RewindStoryUseCase
import com.livrovivo.app.domain.usecase.SaveChildProfileUseCase
import com.livrovivo.app.presentation.creation.CreationViewModel
import com.livrovivo.app.presentation.home.HomeViewModel
import com.livrovivo.app.presentation.navigation.AppStartViewModel
import com.livrovivo.app.presentation.onboarding.OnboardingViewModel
import com.livrovivo.app.presentation.parent.ParentDashboardViewModel
import com.livrovivo.app.presentation.paywall.PaywallViewModel
import com.livrovivo.app.presentation.reader.ReaderViewModel
import com.livrovivo.app.presentation.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database & DAOs
    single { LivroVivoDatabase.getInstance(androidContext()) }
    single { get<LivroVivoDatabase>().storyDao() }
    single { get<LivroVivoDatabase>().childProfileDao() }

    // Settings & DataStore
    single { SettingsManager(androidContext()) }

    // IA (texto, imagem e voz)
    single { AiHttp.createClient() }
    single { BackendConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) }
    single { GeminiService(androidContext(), get(), get(), get()) }
    single { ElevenLabsService(get(), get(), get()) }
    single { StoryWriter(get()) }
    single { IllustrationService(androidContext(), get(), get()) }

    // Narração
    single { GeminiNarrationEngine(get()) }
    single { ElevenLabsNarrationEngine(get()) }
    single { DeviceNarrationEngine(androidContext()) }
    single { AudioPlayerController(androidContext(), get(), get(), get(), get()) }

    // Repositories
    single<StoryRepository> { StoryRepositoryImpl(get(), get(), get(), get(), get()) }
    single<ChildProfileRepository> { ChildProfileRepositoryImpl(get()) }
    single<BillingRepository> { BillingRepositoryImpl(get(), get()) }

    // UseCases
    factory { GenerateStoryUseCase(get(), get(), get()) }
    factory { ContinueStoryUseCase(get()) }
    factory { RewindStoryUseCase(get()) }
    factory { IllustrateChapterUseCase(get()) }
    factory { DeleteStoryUseCase(get()) }
    factory { DeleteAllStoriesUseCase(get()) }
    factory { GetStoriesUseCase(get()) }
    factory { GetStoryByIdUseCase(get()) }
    factory { SaveChildProfileUseCase(get()) }
    factory { GetActiveChildUseCase(get()) }
    factory { CheckStoryQuotaUseCase(get(), get()) }
    factory { GetParentInsightsUseCase(get(), get()) }

    // ViewModels
    viewModel { AppStartViewModel(get(), get()) }
    viewModel { (isEditMode: Boolean) -> OnboardingViewModel(get(), get(), isEditMode) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get<BackendConfig>().isConfigured, get()) }
    viewModel { CreationViewModel(get(), get(), get(), get()) }
    viewModel { (storyId: String) ->
        ReaderViewModel(storyId, get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { PaywallViewModel(get()) }
    viewModel {
        ParentDashboardViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get<BackendConfig>().isConfigured, get())
    }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get<BackendConfig>().isConfigured) }
}
