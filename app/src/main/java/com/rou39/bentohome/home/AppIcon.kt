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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.icon.IconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberAppIcon(entry: AppEntry?, size: Dp, cache: IconCache): ImageBitmap? {
    if (entry == null) return null
    val px = with(LocalDensity.current) { size.roundToPx() }
    val key = IconCache.key(entry.ref.component, entry.ref.profileSerial, px)
    val bitmap by produceState(cache.get(key)?.asImageBitmap(), key, entry) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                entry.info.getIcon(0).toBitmap(px, px).also { cache.put(key, it) }.asImageBitmap()
            }
        }
    }
    return bitmap
}

private val labelStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f),
)

/**
 * アイコンとラベル。占有範囲（footprint）の内側に収め、はみ出して隣と重ねない（docs/design.md §5）。
 * entry が null のときは、見つからないアプリとして表示する。
 */
@Composable
fun AppIconView(
    entry: AppEntry?,
    cache: IconCache,
    iconSize: Dp,
    badge: Int?,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(iconSize)) {
            val icon = rememberAppIcon(entry, iconSize, cache)
            when {
                icon != null -> Image(icon, contentDescription = entry?.label, modifier = Modifier.fillMaxSize())
                entry == null -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.DarkGray),
                ) { Text("?", color = Color.White) }
            }
            if (badge != null && badge > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD93025))
                        .padding(horizontal = 4.dp),
                ) {
                    Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, fontSize = 10.sp)
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                entry?.label ?: "見つからないアプリ",
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
