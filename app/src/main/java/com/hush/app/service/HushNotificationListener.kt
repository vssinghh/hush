package com.hush.app.service

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.usecase.EvaluateNotificationUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class HushNotificationListener : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationListenerEntryPoint {
        fun evaluateNotificationUseCase(): EvaluateNotificationUseCase
    }

    lateinit var evaluateNotificationUseCase: EvaluateNotificationUseCase

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun ensureInjected(): Boolean {
        if (::evaluateNotificationUseCase.isInitialized) return true
        return try {
            val entryPoint = EntryPoints.get(applicationContext, NotificationListenerEntryPoint::class.java)
            evaluateNotificationUseCase = entryPoint.evaluateNotificationUseCase()
            true
        } catch (e: Exception) {
            Log.e("HushNotificationListener", "Failed to inject dependencies: ${e.message}")
            false
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureInjected()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!ensureInjected()) return

        serviceScope.launch {
            try {
                val packageName = sbn.packageName
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0)
                    )
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                }

                val notification = sbn.notification ?: return@launch
                val extras = notification.extras ?: Bundle.EMPTY

                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

                // Sender extraction sequence:
                // 1. Extract from MessagingStyle if present
                val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                val senderFromStyle = messagingStyle?.messages?.lastOrNull()?.person?.name?.toString()
                    ?: messagingStyle?.messages?.lastOrNull()?.sender?.toString()

                // 2. Fallback to EXTRA_MESSAGING_PERSON
                val senderFromPerson = if (senderFromStyle == null) {
                    val person = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)
                    person?.name?.toString()
                } else {
                    null
                }

                val sender = senderFromStyle ?: senderFromPerson

                // Calculate notification's LocalTime based on sbn.postTime
                val notificationTime = Instant.ofEpochMilli(sbn.postTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()

                // Invoke rule evaluation usecase
                val action = evaluateNotificationUseCase.execute(
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    sender = sender,
                    currentTime = notificationTime
                )

                // Dismiss notification if matched rule action is BLOCK
                if (action == RuleAction.BLOCK) {
                    cancelNotification(sbn.key)
                }
            } catch (e: Exception) {
                Log.e("HushNotificationListener", "Error evaluating notification: ${e.message}", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional tracking of removed notifications
    }
}
