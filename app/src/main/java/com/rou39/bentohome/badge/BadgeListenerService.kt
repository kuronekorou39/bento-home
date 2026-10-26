package com.rou39.bentohome.badge

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class BadgeListenerService : NotificationListenerService() {
    private val facts = HashMap<String, NotificationFact>()
    private val userManager by lazy { getSystemService(UserManager::class.java) }

    override fun onListenerConnected() {
        Log.d(TAG, "listener connected")
        rebuild()
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "listener disconnected")
        facts.clear()
        BadgeRepository.markUnknown()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        facts[sbn.key] = toFact(sbn, rankingMap)
        publish("posted ${sbn.packageName}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        facts.remove(sbn.key)
        publish("removed ${sbn.packageName}")
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        val ranking = Ranking()
        for (key in facts.keys.toList()) {
            if (rankingMap.getRanking(key, ranking)) {
                facts[key] = facts.getValue(key).copy(canShowBadge = ranking.canShowBadge())
            }
        }
        publish("ranking update")
    }

    /** 接続時はアクティブな通知から全再構築する。 */
    private fun rebuild() {
        facts.clear()
        val ranking = currentRanking
        activeNotifications?.forEach { facts[it.key] = toFact(it, ranking) }
        publish("rebuild")
    }

    private fun toFact(sbn: StatusBarNotification, rankingMap: RankingMap?): NotificationFact {
        val ranking = Ranking()
        val hasRanking = rankingMap?.getRanking(sbn.key, ranking) == true
        val flags = sbn.notification.flags
        return NotificationFact(
            key = sbn.key,
            packageName = sbn.packageName,
            userSerial = userManager.getSerialNumberForUser(sbn.user),
            groupKey = if (sbn.isGroup) sbn.groupKey else null,
            isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            canShowBadge = !hasRanking || ranking.canShowBadge(),
        )
    }

    private fun publish(reason: String) {
        val counts = BadgeAggregator.aggregate(facts.values)
        BadgeRepository.publish(counts)
        // パッケージ名と件数だけを記録する。通知の内容は出さない。
        Log.d(
            TAG,
            "$reason -> active=${facts.size} counts=" +
                counts.entries.joinToString { "${it.key.packageName}=${it.value}" },
        )
    }

    companion object {
        private const val TAG = "BadgeListener"

        fun component(context: Context) = ComponentName(context, BadgeListenerService::class.java)

        fun isAccessGranted(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(component(context))
    }
}
