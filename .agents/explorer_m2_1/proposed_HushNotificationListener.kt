package com.hush.app.service

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.usecase.EvaluateNotificationUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HushNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var evaluateNotificationUseCase: EvaluateNotificationUseCase

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        serviceScope.launch {
            val packageName = sbn.packageName
            val appName = try {
                // Since minSdk is 33 (Tiramisu), we can use the modern flags API
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

            // Metadata Sender Extraction:
            // 1. Try to extract sender from MessagingStyle using AndroidX NotificationCompat
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            val senderFromStyle = messagingStyle?.messages?.lastOrNull()?.person?.name?.toString()
                ?: messagingStyle?.messages?.lastOrNull()?.sender?.toString()

            // 2. Fallback to EXTRA_MESSAGING_PERSON (using modern typed API since minSdk is 33)
            val senderFromPerson = if (senderFromStyle == null) {
                val person = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)
                person?.name?.toString()
            } else {
                null
            }

            // 3. Fallback to EXTRA_TITLE if sender is still null (used by some 1-to-1 chat apps)
            val sender = senderFromStyle ?: senderFromPerson

            // Evaluate the intercepted notification using the injected Use Case
            val action = evaluateNotificationUseCase.execute(
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                sender = sender
            )

            // Dismiss the notification if the rule engine returns BLOCK
            if (action == RuleAction.BLOCK) {
                cancelNotification(sbn.key)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Skeleton trigger logic
    }
}
