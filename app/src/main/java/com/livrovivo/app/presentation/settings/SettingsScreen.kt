package com.livrovivo.app.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.BuildConfig
import com.livrovivo.app.core.audio.VoicePersona
import com.livrovivo.app.core.settings.AiModelDefaults
import com.livrovivo.app.core.settings.VoiceEngineChoice
import com.livrovivo.app.core.theme.FairyEmerald
import com.livrovivo.app.core.ui.LocalImage
import com.livrovivo.app.core.ui.SectionHeader
import com.livrovivo.app.domain.model.IllustrationStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeSavedMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Narração e ilustrações", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------------------------------------------------------------- Narrador (pais)
            SettingsCard {
                SectionHeader(
                    title = "🎙️ Narrador",
                    subtitle = "Ouça uma amostra de cada narrador e escolha o preferido da sua família."
                )
                VoicePersona.entries.forEach { persona ->
                    PersonaRow(
                        persona = persona,
                        isDefault = settings.defaultPersonaId == persona.id,
                        test = uiState.voiceTests[persona],
                        onPreview = { viewModel.previewVoice(persona) },
                        onSetDefault = { viewModel.setDefaultPersona(persona) }
                    )
                }
                HorizontalDivider()
                Text("Velocidade da narração", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.85f to "🐢 Calminha", 1.0f to "🙂 Normal", 1.15f to "🐇 Animada").forEach { (speed, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(settings.narrationSpeed - speed) < 0.01f,
                            onClick = { viewModel.setSpeed(speed) },
                            label = { Text(label) }
                        )
                    }
                }
                SwitchRow("Preparar os próximos caminhos durante a leitura", settings.prepareNextChoices) { viewModel.setPrepareChoices(it) }
                Text("A próxima página fica pronta enquanto a criança ainda está lendo.", style = MaterialTheme.typography.bodySmall)
                SwitchRow("Narrar automaticamente ao abrir a página", settings.autoPlayNarration) { viewModel.setAutoPlay(it) }
                SwitchRow("Destacar a frase que está sendo lida", settings.highlightReading) { viewModel.setHighlight(it) }
            }

            // ---------------------------------------------------------------- Ilustrações (pais)
            SettingsCard {
                SectionHeader(
                    title = "🎨 Ilustrações",
                    subtitle = "Uma ilustração por página, mantendo os personagens iguais do começo ao fim."
                )
                SwitchRow("Ilustrar cada página", settings.illustrationsEnabled) { viewModel.setIllustrationsEnabled(it) }
                Text("Estilo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IllustrationStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.illustrationStyle == style,
                            onClick = { viewModel.setIllustrationStyle(style) },
                            label = { Text("${style.emoji} ${style.title}") }
                        )
                    }
                }
            }

            SettingsCard {
                SectionHeader(
                    title = "🔒 Privacidade",
                    subtitle = "Para criar as histórias usamos apenas o primeiro nome, a faixa de idade, os interesses e a aparência escolhida."
                )
            }

            // Chaves, modelos e testes: só na versão de desenvolvimento. Na loja, os pais nunca configuram IA.
            if (BuildConfig.DEBUG) {
                DeveloperOptions(uiState = uiState, viewModel = viewModel)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Opções de quem desenvolve o app (só existem na versão de teste): chaves, motor de voz, ElevenLabs,
 * modelos e testes. Com a chave no local.properties, nada disso precisa ser preenchido.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperOptions(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🛠️ Desenvolvedor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Só aparece na versão de teste; some na versão da loja.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { open = !open }) { Text(if (open) "Fechar" else "Abrir") }
        }
        if (!open) return@SettingsCard

        // ------------------------------------------------------------ IA de texto
        HorizontalDivider()
        Text("🧠 Google Gemini (chave de API)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (uiState.backendConfigured) {
            StatusLine(ok = true, text = "Servidor Livro Vivo configurado: a chave abaixo é opcional.")
        }
        if (settings.geminiKeyFromDevConfig) {
            StatusLine(ok = true, text = "Usando a chave do local.properties.")
        }
        SecretField(
            value = uiState.geminiKeyInput,
            onValueChange = viewModel::onGeminiKeyChange,
            label = "Chave de API do Gemini",
            placeholder = "AIza..."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.saveGeminiKey() }) { Text("Salvar") }
            OutlinedButton(onClick = viewModel::testText) { Text("Testar conexão") }
        }
        TestResult(uiState.textTest)
        TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }) {
            Text("Obter uma chave no Google AI Studio →")
        }

        // ------------------------------------------------------------ Motor de voz
        HorizontalDivider()
        Text("🔈 Motor de voz", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VoiceEngineChoice.entries.forEach { choice ->
                FilterChip(
                    selected = settings.voiceEngine == choice,
                    onClick = { viewModel.setVoiceEngine(choice) },
                    label = { Text(choice.title) }
                )
            }
        }
        Text(
            text = settings.voiceEngine.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        OutlinedButton(onClick = {
            val intents = listOf(
                Intent("com.android.settings.TTS_SETTINGS"),
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            )
            for (intent in intents) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    break
                } catch (_: ActivityNotFoundException) {
                }
            }
        }) { Text("Configurações de voz do Android") }

        // ------------------------------------------------------------ ElevenLabs
        HorizontalDivider()
        Text("✨ ElevenLabs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "A política de uso da ElevenLabs proíbe o serviço para menores de 13 anos: serve só para testes.",
            style = MaterialTheme.typography.bodySmall
        )
        if (settings.elevenLabsKeyFromDevConfig) {
            StatusLine(ok = true, text = "Usando a chave do local.properties.")
        }
        SecretField(
            value = uiState.elevenKeyInput,
            onValueChange = viewModel::onElevenKeyChange,
            label = "Chave de API da ElevenLabs",
            placeholder = "sk_..."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.saveElevenKey() }) { Text("Salvar") }
            OutlinedButton(onClick = viewModel::loadElevenVoices) { Text("Carregar vozes") }
        }
        TestResult(uiState.voicesState)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AiModelDefaults.ELEVENLABS_MODELS.forEach { (model, label) ->
                FilterChip(
                    selected = settings.elevenLabsModel == model,
                    onClick = { viewModel.setElevenModel(model) },
                    label = { Text(label) }
                )
            }
        }
        if (uiState.elevenVoices.isNotEmpty()) {
            VoicePersona.entries.forEach { persona ->
                VoicePicker(
                    persona = persona,
                    voices = uiState.elevenVoices,
                    selectedId = settings.elevenLabsVoiceIds[persona.id],
                    onSelect = { viewModel.setElevenVoice(persona, it) }
                )
            }
        }

        // ------------------------------------------------------------ Ilustrações
        HorizontalDivider()
        Text("🎨 Modelo de ilustração", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AiModelDefaults.IMAGE_MODELS.forEach { (model, label) ->
                FilterChip(
                    selected = settings.imageModel == model,
                    onClick = { viewModel.setImageModel(model) },
                    label = { Text(label) }
                )
            }
        }
        OutlinedButton(onClick = viewModel::testIllustration) { Text("Gerar ilustração de teste") }
        TestResult(uiState.imageTest)
        uiState.sampleImagePath?.let { path ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                LocalImage(path = path, modifier = Modifier.fillMaxSize())
            }
        }

        // ------------------------------------------------------------ Modelos
        HorizontalDivider()
        Text("⚙️ Modelos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = uiState.textModelInput,
            onValueChange = viewModel::onTextModelChange,
            label = { Text("Modelo de texto") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.ttsModelInput,
            onValueChange = viewModel::onTtsModelChange,
            label = { Text("Modelo de voz (Gemini TTS)") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.saveModels() }) { Text("Salvar modelos") }
            TextButton(onClick = {
                viewModel.onTextModelChange(AiModelDefaults.TEXT)
                viewModel.onTtsModelChange(AiModelDefaults.TTS)
            }) { Text("Restaurar padrão") }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar chave" else "Mostrar chave"
                )
            }
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (ok) FairyEmerald else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TestResult(state: TestState) {
    when (state) {
        TestState.Idle -> Unit
        TestState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Testando...", style = MaterialTheme.typography.bodySmall)
        }
        is TestState.Success -> StatusLine(ok = true, text = state.message)
        is TestState.Failure -> StatusLine(ok = false, text = state.message)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PersonaRow(
    persona: VoicePersona,
    isDefault: Boolean,
    test: TestState?,
    onPreview: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isDefault) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(persona.emoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(persona.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(persona.description, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onPreview, enabled = test != TestState.Running) {
                    if (test == TestState.Running) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Ouvir amostra de ${persona.title}")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDefault) {
                    Text("Narrador padrão ⭐", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = onSetDefault) { Text("Usar como padrão") }
                }
            }
            if (test is TestState.Success || test is TestState.Failure) TestResult(test)
        }
    }
}

@Composable
private fun VoicePicker(
    persona: VoicePersona,
    voices: List<com.livrovivo.app.core.ai.ElevenLabsVoice>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = voices.find { it.id == selectedId }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("${persona.emoji} ${persona.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selected?.name ?: "Automática", maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Automática (escolher pelo perfil)") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(voice.name + if (voice.speaksPortuguese) " · PT" else "", fontWeight = FontWeight.SemiBold)
                                if (voice.summary.isNotBlank()) Text(voice.summary, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        onClick = {
                            onSelect(voice.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
