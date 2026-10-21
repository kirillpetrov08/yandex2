package com.converterplus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.converterplus.app.R
import com.converterplus.app.ui.theme.AppDimens

/**
 * Круговой индикатор с процентами по центру и лёгким свечением цвета акцента.
 */
@Composable
fun CircleProgress(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Int = 116,
) {
    val animated by animateFloatAsState(
        targetValue = (percent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 220),
        label = "progress",
    )
    val accent = MaterialTheme.colorScheme.primary

    val description = stringResource(R.string.cd_progress)
    Box(
        modifier = modifier
            .size(size.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxSize()
                .shadow(8.dp, MaterialTheme.shapes.extraLarge, ambientColor = accent, spotColor = accent),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 8.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/** Пустое состояние: иллюстрация и пояснение. */
@Composable
fun EmptyState(
    iconRes: Int,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = stringResource(R.string.cd_empty_history),
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = AppDimens.SpaceL),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
