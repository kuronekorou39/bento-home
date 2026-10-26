package com.rou39.bentohome.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.data.IconShape
import com.rou39.bentohome.data.LauncherSettings
import com.rou39.bentohome.icon.IconCache
import com.rou39.bentohome.icon.IconRenderer
import com.rou39.bentohome.usage.GrayReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** アイコンの見た目の設定（plan.md §2 外観設定）。 */
data class IconStyle(
    val iconSize: Dp,
    val shape: IconShape,
    val labelVisible: Boolean,
    val labelSizeSp: Int,
)

fun LauncherSettings.iconStyle() = IconStyle(iconSizeDp.dp, iconShape, labelVisible, labelSize.sp)

/** 表示するバッジ。件数が 0 か、バッジを出さない設定なら null にする。 */
sealed interface BadgeDisplay {
    data object Dot : BadgeDisplay
    data class Count(val count: Int) : BadgeDisplay
}

@Composable
fun rememberAppIcon(entry: AppEntry?, size: Dp, shape: IconShape, cache: IconCache): ImageBitmap? {
    if (entry == null) return null
    val px = with(LocalDensity.current) { size.roundToPx() }
    val key = IconCache.key(entry.ref.component, entry.ref.profileSerial, px, shape)
    val bitmap by produceState(cache.get(key)?.asImageBitmap(), key, entry) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                IconRenderer.render(entry.info.getIcon(0), px, shape).also { cache.put(key, it) }.asImageBitmap()
            }
        }
    }
    return bitmap
}

/**
 * アイコンとラベル。占有範囲（footprint）の内側に収め、はみ出して隣と重ねない（docs/design.md §5）。
 * entry が null のときは、見つからないアプリとして表示する。labelSizeSp が null ならラベルを出さない。
 */
@Composable
fun AppIconView(
    entry: AppEntry?,
    cache: IconCache,
    iconSize: Dp,
    shape: IconShape,
    badge: BadgeDisplay?,
    labelSizeSp: Int?,
    modifier: Modifier = Modifier,
    gray: Boolean = false,
) {
    Column(
        modifier = modifier.semantics {
            if (gray) stateDescription = "しばらく使っていません"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(iconSize)) {
            val icon = rememberAppIcon(entry, iconSize, shape, cache)
            when {
                // グレー表示は彩度を落とすだけ。ラベルの読みやすさとタップ領域は変えない（plan.md §4）
                icon != null -> Image(
                    icon,
                    // ラベルを表示しているときは、名前を二重に読まないよう画像には説明を付けない
                    contentDescription = if (labelSizeSp == null) entry?.label else null,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = if (gray) GRAY_FILTER else null,
                    alpha = if (gray) GRAY_ALPHA else 1f,
                )
                entry == null -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.DarkGray),
                ) { Text("?", color = Color.White) }
            }
            when (badge) {
                null -> Unit
                BadgeDisplay.Dot -> Box(
                    Modifier
                        .semantics { contentDescription = "通知あり" }
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(BADGE_COLOR),
                )
                is BadgeDisplay.Count -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clearAndSetSemantics { contentDescription = "通知 ${badge.count} 件" }
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .clip(CircleShape)
                        .background(BADGE_COLOR)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(if (badge.count > 99) "99+" else badge.count.toString(), color = Color.White, fontSize = 10.sp)
                }
            }
        }
        if (labelSizeSp != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                entry?.label ?: "見つからないアプリ",
                style = TextStyle(
                    color = Color.White,
                    fontSize = labelSizeSp.sp,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val BADGE_COLOR = Color(0xFFD93025)
private val GRAY_FILTER = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private const val GRAY_ALPHA = 0.7f

/** 長押しメニューに出すグレー表示の理由。 */
fun GrayReason.describe(): String = when (this) {
    is GrayReason.NotLaunchedSince -> "このランチャーでは${days}日間起動なし"
    is GrayReason.NeverLaunched -> "観測を始めて${days}日、このランチャーでは未起動"
}
