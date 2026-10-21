package com.converterplus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.converterplus.app.engine.FormatOption
import com.converterplus.app.ui.theme.AppDimens

/**
 * Ряд кнопок форматов результата.
 * Выбранный формат подсвечен акцентом и увеличен до 1.05.
 * Недоступный формат неактивен и при нажатии показывает причину.
 *
 * Чипы переносятся на следующую строку, а не сжимаются: в узкой колонке текст иначе
 * ломается по одной букве в столбик.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormatChipRow(
    options: List<FormatOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDisabledClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            FormatChip(
                option = option,
                selected = option.id == selectedId,
                onSelect = onSelect,
                onDisabledClick = onDisabledClick,
            )
        }
    }
}

/**
 * Ряд чипов-переключателей (качество, размер страницы, dpi, страницы PDF).
 * Перенос строк гарантирует, что ни один вариант не обрезан и не сжат,
 * а [isSelected] позволяет использовать ряд и для одиночного, и для множественного выбора.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SelectableChipRow(
    options: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceS),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = isSelected(value),
                onClick = { onSelect(value) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

/** Ряд с одиночным выбором — короткая запись для самого частого случая. */
@Composable
fun <T> SingleChoiceChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) = SelectableChipRow(
    options = options,
    isSelected = { it == selected },
    onSelect = onSelect,
    modifier = modifier,
)

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
            .padding(horizontal = AppDimens.SpaceL, vertical = AppDimens.SpaceM),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(id = option.titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(id = option.hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = if (option.enabled) 0.75f else 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Заголовок секции экрана: один и тот же отступ до содержимого на всех экранах. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(bottom = AppDimens.SpaceM),
    )
}

/** Пара «подпись — значение» для карточки результата. */
@Composable
fun StatRow(
    label: String,
    value: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    // fillMaxWidth обязателен: без него Row сжимается по содержимому и подпись
    // слипается со значением («Было2,2 МБ»).
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.StatGap),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(AppDimens.SpaceM))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor ?: if (accent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
    }
}
