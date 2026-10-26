package com.rou39.bentohome.badge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface BadgeState {
    /** 通知へのアクセスが許可されていない。 */
    data object Disabled : BadgeState

    /** 許可はあるが未接続、または接続が切れた。古い数を確定値として残さない。 */
    data object Unknown : BadgeState

    data class Counts(val counts: Map<BadgeKey, Int>) : BadgeState
}

/** 通知サービスとホーム画面は同じプロセスで動くので、プロセス内の単一の状態で受け渡す。 */
object BadgeRepository {
    private val _state = MutableStateFlow<BadgeState>(BadgeState.Unknown)
    val state: StateFlow<BadgeState> = _state.asStateFlow()

    fun onAccessChecked(granted: Boolean) {
        if (!granted) {
            _state.value = BadgeState.Disabled
        } else if (_state.value == BadgeState.Disabled) {
            _state.value = BadgeState.Unknown
        }
    }

    fun publish(counts: Map<BadgeKey, Int>) {
        _state.value = BadgeState.Counts(counts)
    }

    fun markUnknown() {
        _state.value = BadgeState.Unknown
    }
}
