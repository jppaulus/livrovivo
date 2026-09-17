package com.livrovivo.app.core.parentalgate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.random.Random

data class MathChallenge(
    val num1: Int,
    val num2: Int,
    val expectedAnswer: Int
) {
    /** Operação por extenso (ex.: "sete vezes oito"), difícil para crianças pequenas, como pede a política Famílias. */
    val questionText: String
        get() = "Quanto é ${numberInWords(num1)} vezes ${numberInWords(num2)}?"

    companion object {
        private val WORDS = listOf("zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove")

        fun numberInWords(value: Int): String = WORDS.getOrElse(value) { value.toString() }

        fun generate(): MathChallenge {
            val a = Random.nextInt(3, 10)
            val b = Random.nextInt(3, 10)
            return MathChallenge(a, b, a * b)
        }
    }
}

@Composable
fun ParentalGateDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var challenge by remember { mutableStateOf(MathChallenge.generate()) }
    var userInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔒 Área dos Pais",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Para acessar opções de assinatura ou configurações, por favor resolva a multiplicação abaixo:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = challenge.questionText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = userInput,
                    onValueChange = {
                        userInput = it
                        isError = false
                    },
                    label = { Text("Digite o resultado") },
                    isError = isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Resultado incorreto. Tente esta nova conta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Voltar")
                    }

                    Button(
                        onClick = {
                            val parsed = userInput.trim().toIntOrNull()
                            if (parsed == challenge.expectedAnswer) {
                                onSuccess()
                            } else {
                                // Nova conta a cada erro, para não dar para acertar no chute.
                                isError = true
                                userInput = ""
                                challenge = MathChallenge.generate()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirmar")
                    }
                }
            }
        }
    }
}
