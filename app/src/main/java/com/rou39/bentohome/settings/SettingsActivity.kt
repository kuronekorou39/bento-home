package com.rou39.bentohome.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rou39.bentohome.badge.BadgeListenerService
import com.rou39.bentohome.container
import com.rou39.bentohome.data.LauncherSettings
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment

/** 設定画面。通知バッジ・ホーム画面・既定ホームの切り替え導線を持つ。 */
class SettingsActivity : ComponentActivity() {
    private var notificationAccess by mutableStateOf(false)
    private val settingsRepo by lazy { container.settingsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = LauncherSettings())
            val scope = rememberCoroutineScope()
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                    ) {
                        Text("通知バッジ", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (notificationAccess) "通知へのアクセス: 許可済み" else "通知へのアクセス: 未許可",
                        )
                        Text(
                            "通知へのアクセスは通知の内容を読める強い権限です。このアプリは件数の集計だけに使い、" +
                                "タイトル・本文・送信者を保存・記録しません。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = ::openNotificationAccess, modifier = Modifier.fillMaxWidth()) {
                            Text("通知へのアクセス設定を開く")
                        }
                        Text("ホーム画面", style = MaterialTheme.typography.titleMedium)
                        SwitchRow("配置をロック（移動・削除・追加をしない）", settings.layoutLocked) {
                            scope.launch { settingsRepo.setLayoutLocked(it) }
                        }
                        SwitchRow("ドックを表示", settings.dockVisible) {
                            scope.launch { settingsRepo.setDockVisible(it) }
                        }
                        Text("ホームアプリ", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("既定のホームアプリを変更（元のホームへ戻す）")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationAccess = BadgeListenerService.isAccessGranted(this)
    }

    private fun openNotificationAccess() {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                BadgeListenerService.component(this).flattenToString(),
            )
        runCatching { startActivity(detail) }
            .onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
