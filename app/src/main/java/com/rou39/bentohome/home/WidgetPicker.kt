package com.rou39.bentohome.home

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rou39.bentohome.widget.WidgetHostController

/** ウィジェットの選択。プレビュー画像は段階3で追加する。 */
@Composable
fun WidgetPicker(
    widgetHost: WidgetHostController,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onClose: () -> Unit,
) {
    val pm = LocalContext.current.packageManager
    val providers = remember {
        widgetHost.providers().map { info ->
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
            Triple(info, appLabel, info.loadLabel(pm))
        }.sortedWith(compareBy({ it.second.lowercase() }, { it.third.lowercase() }))
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2101010))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ウィジェットを選択（${providers.size}）", color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("閉じる") }
        }
        LazyColumn {
            items(providers, key = { it.first.provider.flattenToString() }) { (info, appLabel, label) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(info) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(label, color = Color.White)
                    Text(
                        "$appLabel ・ セル ${info.targetCellWidth}×${info.targetCellHeight}" +
                            (if (info.configure != null) " ・ 設定あり" else ""),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
