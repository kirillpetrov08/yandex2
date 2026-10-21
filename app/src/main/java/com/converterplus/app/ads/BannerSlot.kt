package com.converterplus.app.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest

/**
 * Высота контейнера баннера. Резервируется заранее, чтобы появление креатива
 * не сдвигало интерфейс (требование РСЯ: никакого layout jump).
 */
const val BANNER_CONTAINER_HEIGHT_DP = 50

/**
 * Баннер РСЯ внутри Compose.
 *
 * - место под баннер фиксированной высоты и резервируется сразу;
 * - поверх креатива нет своих элементов: ни рамок, ни кнопок, ни затемнения;
 * - отступ от интерактивных элементов (минимум 12dp) обеспечивает вызывающий экран.
 *
 * @param visible false — слот не занимает места (онбординг, настройки, процесс конвертации,
 *                шит результата, активный режим «без рекламы»).
 */
@Composable
fun BannerSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val context = LocalContext.current
    val containerColor = MaterialTheme.colorScheme.surface

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BANNER_CONTAINER_HEIGHT_DP.dp)
            .background(containerColor),
    ) {
        val widthDp = (constraints.maxWidth / context.resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(280)
        val adView = remember(widthDp) { createBannerView(context, widthDp) }
        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun createBannerView(context: Context, widthDp: Int): BannerAdView {
    val units = Ads.units
    return BannerAdView(context).apply {
        setAdSize(BannerAdSize.sticky(context, widthDp))
        loadAd(AdRequest.Builder(units.bannerUnitId).build())
    }
}
