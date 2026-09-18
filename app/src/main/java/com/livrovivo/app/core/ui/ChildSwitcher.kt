package com.livrovivo.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion

/**
 * Avatar de uma criança: o emoji do companheiro mágico dela sobre um círculo colorido.
 * Serve para distinguir irmãos sem precisar ler o nome.
 */
@Composable
fun ChildAvatar(
    child: ChildProfile,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    highlighted: Boolean = false
) {
    val companion = MagicalCompanion.findById(child.companionId)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .then(
                if (highlighted) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = companion.emoji, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Chip com a criança da vez. Só aparece quando há irmãos cadastrados — com uma criança
 * só, não há o que trocar e o chip viraria ruído na tela da criança.
 */
@Composable
fun ActiveChildChip(
    child: ChildProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChildAvatar(child = child, size = 30.dp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = child.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp)
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Folha para escolher de quem é a estante. Trocar de criança é coisa que a própria
 * criança pode fazer; cadastrar, editar e apagar ficam na Área dos Pais.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSwitcherSheet(
    profiles: List<ChildProfile>,
    activeChildId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onManage: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "De quem é a estante? 📚",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            profiles.forEach { profile ->
                ChildRow(
                    child = profile,
                    isActive = profile.id == activeChildId,
                    onClick = { onSelect(profile.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (onManage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                    Text("Adicionar ou editar crianças (Área dos Pais)")
                }
            }
        }
    }
}

@Composable
private fun ChildRow(
    child: ChildProfile,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChildAvatar(child = child, size = 44.dp, highlighted = isActive)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = child.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = AgeGroup.fromCode(child.ageGroup).label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Estante aberta",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
