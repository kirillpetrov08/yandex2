package com.converterplus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.converterplus.app.engine.FormatOption
import com.converterplus.app.ui.theme.AppDimens

/**
 * Ряд кнопок форматов результата.
 * Выбранный формат подсвечен акцентом и увеличен до 1.05.
 * Недоступный формат неактивен и при нажатии показывает причину.
 */
@Composable
fun FormatChipRow(
    options: List<FormatOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDisabledClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(options, key = { it.id }) { option ->
            FormatChip(
                option = option,
                selected = option.id == selectedId,
                onSelect = onSelect,
                onDisabledClick = onDisabledClick,
            )
        }
    }
}

@Composable
private fun FormatChip(
    option: FormatOption,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onDisabledClick: (Int) -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(),
        label = "chipScale",
    )
    val container = when {
        !option.enabled -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        !option.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .clickable {
                if (option.enabled) {
                    onSelect(option.id)
                } else {
                    option.disabledReasonRes?.let(onDisabledClick)
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(id = option.titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = content,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(id = option.hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = if (option.enabled) 0.75f else 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Заголовок секции экрана. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** Пара «подпись — значение» для карточки результата. */
@Composable
fun StatRow(label: String, value: String, accent: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
