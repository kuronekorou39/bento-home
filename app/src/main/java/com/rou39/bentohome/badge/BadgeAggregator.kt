package com.rou39.bentohome.badge

/**
 * 集計に必要な最小限の通知メタデータ。タイトル・本文・送信者は持たない（plan.md §3）。
 *
 * @param groupKey アプリまたは OS がグループ化した通知だけ非 null。
 * @param isOngoing 常駐・フォアグラウンドサービスの通知。初版では数えない。
 */
data class NotificationFact(
    val key: String,
    val packageName: String,
    val userSerial: Long,
    val groupKey: String?,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val canShowBadge: Boolean,
)

data class BadgeKey(val packageName: String, val userSerial: Long)

object BadgeAggregator {
    /** バッジ数 = 現在の通知件数。まとめ通知は子通知があるときは数えない。 */
    fun aggregate(facts: Collection<NotificationFact>): Map<BadgeKey, Int> {
        val eligible = facts
            .associateBy { it.key }
            .values
            .filter { !it.isOngoing && it.canShowBadge }
        val groupsWithChildren = eligible
            .filter { !it.isGroupSummary && it.groupKey != null }
            .mapTo(HashSet()) { it.groupKey }
        return eligible
            .filterNot { it.isGroupSummary && it.groupKey in groupsWithChildren }
            .groupingBy { BadgeKey(it.packageName, it.userSerial) }
            .eachCount()
    }
}
