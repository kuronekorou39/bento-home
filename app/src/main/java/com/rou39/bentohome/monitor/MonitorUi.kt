package com.rou39.bentohome.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** モニターの各タブで揃える外側の余白。 */
internal val TAB_PADDING = 16.dp

/** タブの中の見出し。読み上げで見出しとして飛べるようにする。 */
@Composable
internal fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/** 補足の説明。本文より一段控えめにする。 */
@Composable
internal fun Small(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** 見出し付きのまとまり。説明の文が続く部分を、背景の面で区切って読みやすくする。 */
@Composable
internal fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let { Heading(it) }
            content()
        }
    }
}

/** 一覧の上に置く注記をひとまとめにする。行が増えても一覧と混ざらないように面を敷く。 */
@Composable
internal fun NoteBox(notes: List<String>, modifier: Modifier = Modifier) {
    if (notes.isEmpty()) return
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            notes.forEach { Small(it) }
        }
    }
}
