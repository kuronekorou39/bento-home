package com.rou39.bentohome.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 長押しメニューの背景。少し透かして、下のホームが分かるようにする。 */
private val MENU_COLOR = Color(0xD92A2A32)
private val MENU_SYMBOL_COLOR = Color.White.copy(alpha = 0.75f)

/**
 * メニュー項目の頭に付ける記号。絵文字で描かれないよう、文字として描く記号だけを使う
 * （必要なものは異体字セレクタ U+FE0E で文字の形を指定する）。
 */
object MenuSymbol {
    const val ADD = "＋"
    const val SELECT = "✓"
    const val REMOVE = "✕"
    const val INFO = "ⓘ"
    const val UNINSTALL = "⊖"
    const val APPEARANCE = "◐"
    const val OPEN = "↗"
    const val RENAME = "✎"
    const val TAKE_OUT = "⇱"
    const val CONFIGURE = "⚙︎"
    const val MEASURE = "◷"
    const val RESIZE = "⤡"
    const val WIDGET = "▦"
    const val PAGES = "▤"
    const val PARTITION = "◫"
    const val UNDO = "↶"
    const val LOCK = "⊟"
    const val UNLOCK = "⊞"
    const val ALL_APPS = "⠿"
    const val MONITOR = "∿"
    const val SETTINGS = CONFIGURE
    const val HIDE = "⊘"
}

@Composable
fun BentoMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        containerColor = MENU_COLOR,
        content = content,
    )
}

/** 記号付きのメニュー項目。symbol が null なら記号の幅だけ空けて、文字の頭を揃える。 */
@Composable
fun BentoMenuItem(label: String, symbol: String?, onClick: () -> Unit, enabled: Boolean = true) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            // 記号は飾りなので読み上げない
            Box(Modifier.size(20.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                if (symbol != null) Text(symbol, color = MENU_SYMBOL_COLOR, fontSize = 16.sp)
            }
        },
    )
}

/** 操作できない説明の行（状態の表示）。小さめの文字で、記号は付けない。 */
@Composable
fun BentoMenuNote(text: String) {
    DropdownMenuItem(text = { Text(text, fontSize = 12.sp) }, onClick = {}, enabled = false)
}
