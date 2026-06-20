# Implementation Strategy Analysis: HushNotificationListener Service

## 1. Executive Summary
This document outlines the design and implementation strategy for the `HushNotificationListener` service in the **Hush** application. The service acts as the entry point for notification interception on Android. It extracts detailed metadata from incoming notifications, delegates the evaluation to `EvaluateNotificationUseCase` asynchronously, and executes cancellation actions if a rule match results in `RuleAction.BLOCK`.

---

## 2. Requirements & Context
Based on `PROJECT.md` and `SCOPE.md` (specifically `sub_orch_m2/SCOPE.md`), we identify the following requirements:
- **Min SDK / Target SDK**: Min SDK is 33, Target SDK is 35. Since minSdk is 33, we can leverage modern Android APIs (such as `PackageManager.ApplicationInfoFlags` and the typed version of `Bundle.getParcelable`) without backward-compatibility version checks.
- **Service Integration**: Extends `NotificationListenerService` and interacts with the Hilt-managed `EvaluateNotificationUseCase`.
- **Metadata Fields**: Extract:
  - App Package Name
  - App Display Name (resolving labels using `PackageManager`)
  - Notification Title
  - Notification Text (with fallback mechanisms for long text)
  - Sender (crucial for messaging and chat applications)
  - Timestamp (extracted from post time for precise rule matching)
- **Rule Action Execution**: Dismiss notifications on `BLOCK`, pass through on `MUTE` or `ALLOW`. (MUTE is treated as silent delivery in Android terms; since the system delivers it and we do not dismiss it, no additional handling is needed in this service for MUTE/ALLOW).
- **Asynchronous Execution**: Rule evaluation and database logging are suspending operations. The service binder thread must not be blocked; a custom coroutine scope must manage background executions.

---

## 3. AndroidManifest.xml Analysis and Verification
The `HushNotificationListener` must be declared correctly in the `AndroidManifest.xml` to allow the Android System to bind to it and grant notification access permissions.

### Verified Current Manifest Entry
In `app/src/main/AndroidManifest.xml`, lines 23-31:
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

### Analysis of the Attributes:
1. **`android:name`**: `.service.HushNotificationListener` correctly points to the Hilt-annotated class `com.hush.app.service.HushNotificationListener`.
2. **`android:permission`**: `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` ensures that only the system's notification manager service can bind to our service. This is a security requirement enforced by the Android OS.
3. **`android:exported`**: `true` is required because the binding is done by an external system process.
4. **`intent-filter`**: The action `android.service.notification.NotificationListenerService` is required so that the system registers this service as an eligible notification listener.
5. **Package Visibility (API 33+)**: In Android 13 (API 33) and above, package visibility rules are strict. However, a registered `NotificationListenerService` has implicit visibility to packages from which it intercepts notifications. When using `PackageManager` to look up the display name (`getApplicationLabel`), it is still possible to throw a `NameNotFoundException` if a package is uninstalled in the split second between the notification arrival and display name extraction. A standard try-catch block is recommended to gracefully fall back to the package name.

---

## 4. Metadata Extraction Strategy
The `onNotificationPosted(sbn: StatusBarNotification?)` callback receives a `StatusBarNotification` (SBN) wrapper. The extraction strategy for the required fields is detailed below:

| Target Field | Retrieval Method | Fallback Behavior |
| :--- | :--- | :--- |
| **Package Name** | `sbn.packageName` | None. This is always available. |
| **App Name (Display)** | `packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)))` | Fallback to `packageName` if `PackageManager.NameNotFoundException` is thrown. |
| **Title** | `sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()` | `null`. (Handled inside usecase as `"No Title"`). |
| **Text** | `sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()` | Fallback to `Notification.EXTRA_BIG_TEXT`. (Handled inside usecase as `"No Content"`). |
| **Sender** | 1. `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)` -> last message sender.<br>2. `extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)` | `null`. |
| **Timestamp** | `Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault()).toLocalTime()` | `LocalTime.now()` if for some reason the timestamp conversion fails. |

### Detail on Sender Extraction:
For messaging apps (WhatsApp, Slack, Telegram), extracting the actual sender is critical:
- **`NotificationCompat.MessagingStyle`**: This helper class can extract the messaging style if present in the notification. By checking `messagingStyle.messages`, we can retrieve the sender of the last message using `message.person.name` or `message.sender`.
- **`Notification.EXTRA_MESSAGING_PERSON`**: In API 28+, the system stores the messaging person in `extras`. Since `minSdk` is 33, we can directly call `extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)` to fetch the sender name.
- **Title as Fallback**: While many single-conversation chats place the sender's name in `EXTRA_TITLE`, we must keep them distinct because the rule engine allows matching specifically on title vs. sender. We should not fall back to `title` for the `sender` parameter unless they are explicitly meant to match the same field.

### Detail on Timestamp Extraction:
The `EvaluateNotificationUseCase.execute(...)` method accepts a `currentTime: LocalTime`.
Instead of calling `LocalTime.now()`, extracting `sbn.postTime` and converting it to the system's local time ensures that even if there is an interception lag (e.g., system throttling or background wake-up latency), rules that evaluate time-range constraints (e.g., quiet hour blocks) are evaluated against the *exact moment the notification was created*, rather than when the app processed it.

---

## 5. Rule Evaluation Integration & Threading Strategy
Since the `NotificationListenerService` lifecycle calls are executed on the main UI/binder thread of the application process, database operations inside `EvaluateNotificationUseCase` must be dispatched to background threads.

### Custom Coroutine Scope
We must declare a custom `CoroutineScope` with a `SupervisorJob` to manage the lifecycle of background computations within the service:
```kotlin
private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
```

- **`Dispatchers.Default`** is appropriate since rule matching consists of CPU-intensive pattern and regex matching.
- **`SupervisorJob()`** ensures that if any single notification evaluation task fails or throws an unhandled exception, it does not cancel the entire scope (which would break future interceptions).
- **Lifecycle Cleanup**: The scope must be cancelled in `onDestroy()` to prevent memory leaks when the service is stopped:
  ```kotlin
  override fun onDestroy() {
      super.onDestroy()
      serviceScope.cancel()
  }
  ```

### Handling Execution Flow
Within `onNotificationPosted`:
1. Check if the incoming `StatusBarNotification` is null.
2. Launch a coroutine in `serviceScope`.
3. Wrap the extraction and use case execution inside a `try-catch` block to prevent service crashes.
4. Call `evaluateNotificationUseCase.execute(...)` passing the extracted parameters.
5. If the returned action is `RuleAction.BLOCK`, invoke `cancelNotification(sbn.key)`.

---

## 6. Proposed Service Code Structure
A replacement file is proposed at `.agents/explorer_m2_3/proposed_HushNotificationListener.kt`.

Below is the complete proposed code structure:
```kotlin
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
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

                val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                val senderFromStyle = messagingStyle?.messages?.lastOrNull()?.person?.name?.toString()
                    ?: messagingStyle?.messages?.lastOrNull()?.sender?.toString()

                val senderFromPerson = if (senderFromStyle == null) {
                    val person = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)
                    person?.name?.toString()
                } else {
                    null
                }

                val sender = senderFromStyle ?: senderFromPerson

                val notificationTime = Instant.ofEpochMilli(sbn.postTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()

                val action = evaluateNotificationUseCase.execute(
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    sender = sender,
                    currentTime = notificationTime
                )

                if (action == RuleAction.BLOCK) {
                    cancelNotification(sbn.key)
                }
            } catch (e: Exception) {
                Log.e("HushNotificationListener", "Error evaluating notification: ${e.message}", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Reserved for future requirements (e.g. logging when a notification was dismissed by the user)
    }
}
```

---

## 7. Verification Method
To verify that this implementation meets all requirements and integrates correctly:
1. **Instrumented Tests**:
   The E2E test class `NotificationInterceptionE2ETest.kt` verifies:
   - Dismissing/canceling notifications matching `BLOCK` rules.
   - Letting notifications pass through for `ALLOW`/`MUTE` rules.
   - Database logging of intercepted/matched notifications.
   - Thread safety with rapid concurrent notifications.
   - Truncation of extremely long text content.
   
   To run these tests on a connected device/emulator:
   ```bash
   ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hush.app.e2e.NotificationInterceptionE2ETest
   ```
2. **Local Unit Tests**:
   Verify time windows and basic rule parsing evaluations:
   ```bash
   ./gradlew testDebugUnitTest --tests com.hush.app.domain.usecase.EvaluateNotificationUseCaseTest
   ```
