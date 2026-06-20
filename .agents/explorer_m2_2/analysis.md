# Analysis: HushNotificationListener Implementation Strategy

## Executive Summary
This document outlines the detailed implementation strategy for the `HushNotificationListener` service in the Hush Android application. The service, inheriting from `NotificationListenerService`, intercepts system notifications, extracts relevant metadata (package name, application label, title, text content, message sender, and timestamp), evaluates these against active database rules via `EvaluateNotificationUseCase`, and enforces blocks using `cancelNotification`.

---

## 1. AndroidManifest.xml Verification

The system requires specific declarations to bind and execute a `NotificationListenerService` safely.

### Current Declaration:
```xml
<service
    android:name=".service.HushNotificationListener"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

### Verification & Analysis:
- **System Permission**: Requiring `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` is mandatory. This ensures only the Android OS can bind to this service, protecting the user's notification stream from interceptors.
- **Exported Status**: `android:exported="true"` is required so that the OS binding process can initiate and attach the service.
- **Intent Filter Action**: Binding relies on the `android.service.notification.NotificationListenerService` action.
- **Package Visibility**: With `minSdk = 33` (Android 13) and `targetSdk = 35` (Android 15), package queries might be constrained by Android's package visibility changes. However, when acts as a `NotificationListenerService`, the application has implicit visibility to packages from which it intercepts notifications. Therefore, no additional `<queries>` element is required in the manifest.

---

## 2. Metadata Extraction Strategy

When `onNotificationPosted(sbn: StatusBarNotification?)` is triggered, metadata must be extracted safely to avoid null pointer exceptions, crashes, or unhandled states.

### Extraction Source Mapping:
| Metadata Field | Extraction Source | Fallback / Safe Handling |
| :--- | :--- | :--- |
| **Package Name** | `sbn.packageName` | Mandatory field (cannot be null). |
| **App Name** | `packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, ...))` | Fallback to `packageName` when `NameNotFoundException` is caught. |
| **Title** | `sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()` | Fallback to `null` (mapped to `"No Title"` in downstream database logs). |
| **Text** | `sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()` | Fallback to `Notification.EXTRA_BIG_TEXT` if `EXTRA_TEXT` is null or empty. Fallback to `null` (mapped to `"No Content"` in database logs). |
| **Sender** | 1. `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)`<br>2. `extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)` | Fallback to `null`. Non-chat notifications naturally have no sender. |
| **Timestamp** | `Instant.now()` or `sbn.postTime` | Logged to database using current `Instant.now()`. |

### Deep Dive: Chat Sender Extraction
Chat applications (like WhatsApp or Slack) represent sender info differently based on Android version and layout:
1. **`MessagingStyle` extraction (AndroidX `NotificationCompat`)**:
   By using `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)`, we can retrieve structured message history. The last message's sender person's name or fallback sender string is extracted:
   ```kotlin
   val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
   val sender = messagingStyle?.messages?.lastOrNull()?.person?.name?.toString()
       ?: messagingStyle?.messages?.lastOrNull()?.sender?.toString()
   ```
2. **`EXTRA_MESSAGING_PERSON` (API 33+)**:
   Since the project `minSdk` is 33, we can use the type-safe modern overload of `getParcelable`:
   ```kotlin
   val person = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)
   val senderFromPerson = person?.name?.toString()
   ```
3. **Implicit Title Fallback**:
   In older or non-standard 1-to-1 chat layouts, the sender is placed in `Notification.EXTRA_TITLE`. However, the Rule Engine evaluates `title` and `sender` separately, so keeping them distinct is preferred.

---

## 3. Rule Evaluation Integration & Threading Model

### Life Cycle and Threading Strategy
- **Threading Concern**: `NotificationListenerService` callbacks run on the main thread by default. DB operations (`getActiveRules`, `insertLog`) are blocking and must run on a background thread.
- **Coroutine Scope**: We define a custom lifecycle-bound `CoroutineScope` with `Dispatchers.Default` and a `SupervisorJob()`. This keeps execution off the main thread and ensures exceptions in one notification's evaluation do not cancel the listener's scope.
- **Service Termination**: The coroutine scope must be cancelled in `onDestroy()` to prevent memory leaks when the service is killed or unbound.

```kotlin
private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
}
```

### Control Flow:
1. When `onNotificationPosted` is called, check if `sbn` is null (if so, return).
2. Launch a coroutine in `serviceScope`.
3. Extract `packageName`, `appName`, `title`, `text`, and `sender`.
4. Call `evaluateNotificationUseCase.execute(packageName, appName, title, text, sender)` to evaluate the notification.
5. If the returned action is `RuleAction.BLOCK`, call `cancelNotification(sbn.key)`.
6. For other actions (`ALLOW`, `MUTE`), do not call `cancelNotification` so the notification posts normally.

---

## 4. Potential Edge Cases & Mitigations

1. **Uninstalled Apps**:
   If a notification from an app is processed while it is being uninstalled, `packageManager.getApplicationInfo` will throw a `NameNotFoundException`. Wrapping this in a try-catch and falling back to `packageName` prevents crashes.
2. **Empty or Missing Extras**:
   Notifications from system components or foreground service indicators may have empty/null `extras`. We must use safe-null operators (`?.`) and fallbacks (`?: Bundle.EMPTY`) to avoid null pointer exceptions.
3. **Hilt Dependency Injection**:
   Because `HushNotificationListener` is instantiated by the Android framework, we must annotate it with `@AndroidEntryPoint` to allow Hilt to perform field injection of `EvaluateNotificationUseCase`.
4. **App Name Retrieval with Modern Flags**:
   Since the app targets API 35 (with minSdk 33), package flags should be specified using the new type-safe API:
   ```kotlin
   PackageManager.ApplicationInfoFlags.of(0)
   ```
   This prevents deprecation warnings and ensures compatibility with Android 13+.

---

## 5. Proposed Implementation Code

Below is the proposed, complete implementation for `HushNotificationListener.kt`:

```kotlin
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

            // Extract sender name for messaging apps
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            val senderFromStyle = messagingStyle?.messages?.lastOrNull()?.person?.name?.toString()
                ?: messagingStyle?.messages?.lastOrNull()?.sender?.toString()

            val sender = if (senderFromStyle == null) {
                val person = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)
                person?.name?.toString()
            } else {
                senderFromStyle
            }

            // Evaluate intercepted notification
            val action = evaluateNotificationUseCase.execute(
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                sender = sender
            )

            // Block action -> Cancel notification
            if (action == RuleAction.BLOCK) {
                cancelNotification(sbn.key)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Ignored or logged if required in later milestones
    }
}
```
