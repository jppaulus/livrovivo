package com.livrovivo.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.ai.AiException
import com.livrovivo.app.core.ai.ElevenLabsService
import com.livrovivo.app.core.ai.ElevenLabsVoice
import com.livrovivo.app.core.ai.GeminiService
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.audio.EngineKind
import com.livrovivo.app.core.audio.VoicePersona
import com.livrovivo.app.core.illustration.IllustrationService
import com.livrovivo.app.core.settings.AppSettings
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.settings.VoiceEngineChoice
import com.livrovivo.app.domain.model.IllustrationStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class TestState {
    data object Idle : TestState()
    data object Running : TestState()
    data class Success(val message: String) : TestState()
    data class Failure(val message: String) : TestState()
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
    val backendConfigured: Boolean = false,
    val geminiKeyInput: String = "",
    val elevenKeyInput: String = "",
    val textModelInput: String = "",
    val ttsModelInput: String = "",
    val textTest: TestState = TestState.Idle,
    val voiceTests: Map<VoicePersona, TestState> = emptyMap(),
    val imageTest: TestState = TestState.Idle,
    val sampleImagePath: String? = null,
    val elevenVoices: List<ElevenLabsVoice> = emptyList(),
    val voicesState: TestState = TestState.Idle,
    val savedMessage: String? = null
)

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val gemini: GeminiService,
    private val elevenLabs: ElevenLabsService,
    private val illustrationService: IllustrationService,
    private val audioPlayerController: AudioPlayerController,
    backendConfigured: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(backendConfigured = backendConfigured))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                _uiState.update { state ->
                    if (!state.loaded) {
                        state.copy(
                            settings = settings,
                            loaded = true,
                            geminiKeyInput = if (settings.geminiKeyFromDevConfig) "" else settings.geminiApiKey,
                            elevenKeyInput = if (settings.elevenLabsKeyFromDevConfig) "" else settings.elevenLabsApiKey,
                            textModelInput = settings.textModel,
                            ttsModelInput = settings.ttsModel
                        )
                    } else {
                        state.copy(settings = settings)
                    }
                }
            }
        }
    }

    fun onGeminiKeyChange(value: String) = _uiState.update { it.copy(geminiKeyInput = value.trim(), textTest = TestState.Idle) }

    fun onElevenKeyChange(value: String) = _uiState.update { it.copy(elevenKeyInput = value.trim(), voicesState = TestState.Idle) }

    fun onTextModelChange(value: String) = _uiState.update { it.copy(textModelInput = value.trim()) }

    fun onTtsModelChange(value: String) = _uiState.update { it.copy(ttsModelInput = value.trim()) }

    fun saveGeminiKey() = launchSaving("Chave do Gemini salva") {
        settingsManager.saveGeminiApiKey(_uiState.value.geminiKeyInput)
    }

    fun saveElevenKey() = launchSaving("Chave da ElevenLabs salva") {
        settingsManager.saveElevenLabsApiKey(_uiState.value.elevenKeyInput)
        _uiState.update { it.copy(elevenVoices = emptyList()) }
    }

    fun saveModels() = launchSaving("Modelos salvos") {
        settingsManager.setTextModel(_uiState.value.textModelInput)
        settingsManager.setTtsModel(_uiState.value.ttsModelInput)
    }

    fun setVoiceEngine(choice: VoiceEngineChoice) = viewModelScope.launch { settingsManager.setVoiceEngine(choice) }

    fun setDefaultPersona(persona: VoicePersona) = audioPlayerController.setPersona(persona)

    fun setElevenModel(model: String) = viewModelScope.launch { settingsManager.setElevenLabsModel(model) }

    fun setElevenVoice(persona: VoicePersona, voiceId: String?) = viewModelScope.launch {
        settingsManager.setElevenLabsVoice(persona.id, voiceId)
    }

    fun setIllustrationsEnabled(enabled: Boolean) = viewModelScope.launch { settingsManager.setIllustrationsEnabled(enabled) }

    fun setIllustrationStyle(style: IllustrationStyle) = viewModelScope.launch {
        settingsManager.setIllustrationStyle(style)
        _uiState.update { it.copy(imageTest = TestState.Idle, sampleImagePath = null) }
    }

    fun setImageModel(model: String) = viewModelScope.launch { settingsManager.setImageModel(model) }

    fun setAutoPlay(enabled: Boolean) = viewModelScope.launch { settingsManager.setAutoPlay(enabled) }

    fun setHighlight(enabled: Boolean) = viewModelScope.launch { settingsManager.setHighlightReading(enabled) }

    fun setSpeed(speed: Float) = audioPlayerController.setSpeed(speed)

    fun consumeSavedMessage() = _uiState.update { it.copy(savedMessage = null) }

    /** Testa a geração de texto (chave, modelo e cota). */
    fun testText() {
        viewModelScope.launch {
            persistPendingGeminiKey()
            _uiState.update { it.copy(textTest = TestState.Running) }
            val result = runCatchingAi {
                val json = gemini.generateJson(
                    systemPrompt = "Você é um assistente de testes. Responda somente JSON.",
                    userPrompt = "Responda exatamente com {\"ok\": true, \"mensagem\": \"Olá do Livro Vivo!\"}",
                    schema = null,
                    temperature = 0.0
                )
                "Conectado! A IA respondeu: ${json["mensagem"]?.toString()?.trim('"') ?: "ok"}"
            }
            _uiState.update { it.copy(textTest = result) }
        }
    }

    /** Toca uma amostra da persona com o motor escolhido (ou o melhor disponível no modo automático). */
    fun previewVoice(persona: VoicePersona) {
        viewModelScope.launch {
            persistPendingGeminiKey()
            persistPendingElevenKey()
            _uiState.update { it.copy(voiceTests = it.voiceTests + (persona to TestState.Running)) }
            val settings = settingsManager.current()
            val engine = when (settings.voiceEngine) {
                VoiceEngineChoice.ELEVENLABS -> EngineKind.ELEVENLABS
                VoiceEngineChoice.GEMINI -> EngineKind.GEMINI
                VoiceEngineChoice.DEVICE -> EngineKind.DEVICE
                VoiceEngineChoice.AUTO -> when {
                    settings.hasElevenLabsKey -> EngineKind.ELEVENLABS
                    settings.hasGeminiKey || _uiState.value.backendConfigured -> EngineKind.GEMINI
                    else -> EngineKind.DEVICE
                }
            }
            val state = audioPlayerController.previewVoice(persona, engine).fold(
                onSuccess = { kind ->
                    if (kind == EngineKind.DEVICE) {
                        val voice = audioPlayerController.lastDeviceVoiceName?.let { " ($it)" }.orEmpty()
                        TestState.Success("Tocando com a voz do aparelho$voice ▶ — é a voz robótica do Google. Para vozes naturais, adicione a chave do Gemini.")
                    } else {
                        TestState.Success("Tocando com ${kind.label} ▶")
                    }
                },
                onFailure = { TestState.Failure(describe(it)) }
            )
            _uiState.update { it.copy(voiceTests = it.voiceTests + (persona to state)) }
        }
    }

    fun loadElevenVoices() {
        viewModelScope.launch {
            persistPendingElevenKey()
            _uiState.update { it.copy(voicesState = TestState.Running) }
            try {
                val voices = elevenLabs.listVoices(forceRefresh = true)
                    .sortedWith(compareByDescending<ElevenLabsVoice> { it.speaksPortuguese }.thenBy { it.name })
                _uiState.update {
                    it.copy(
                        elevenVoices = voices,
                        voicesState = TestState.Success("${voices.size} vozes encontradas na sua conta")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(voicesState = TestState.Failure(describe(e))) }
            }
        }
    }

    fun testIllustration() {
        viewModelScope.launch {
            persistPendingGeminiKey()
            _uiState.update { it.copy(imageTest = TestState.Running, sampleImagePath = null) }
            try {
                val file = illustrationService.sample(settingsManager.current().illustrationStyle)
                _uiState.update { it.copy(imageTest = TestState.Success("Ilustração gerada!"), sampleImagePath = file.path) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(imageTest = TestState.Failure(describe(e))) }
            }
        }
    }

    private suspend fun persistPendingGeminiKey() {
        val input = _uiState.value.geminiKeyInput
        val current = settingsManager.current()
        if (input.isNotBlank() && input != current.geminiApiKey) settingsManager.saveGeminiApiKey(input)
    }

    private suspend fun persistPendingElevenKey() {
        val input = _uiState.value.elevenKeyInput
        val current = settingsManager.current()
        if (input.isNotBlank() && input != current.elevenLabsApiKey) settingsManager.saveElevenLabsApiKey(input)
    }

    private fun launchSaving(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            _uiState.update { it.copy(savedMessage = message) }
        }
    }

    private suspend fun runCatchingAi(block: suspend () -> String): TestState = try {
        TestState.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TestState.Failure(describe(e))
    }

    private fun describe(error: Throwable): String = when (error) {
        is AiException -> error.diagnosticMessage
        else -> error.message ?: "Erro desconhecido"
    }
}
