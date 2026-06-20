# Analysis: HushNotificationListener Implementation Strategy

## Executive Summary
This report details the implementation strategy for the `HushNotificationListener` service to intercept notifications, extract metadata, evaluate them against user-defined rules, and dismiss notifications that match blocking rules.

---

## 1. AndroidManifest.xml Review and Verification
The `HushNotificationListener` must be declared correctly in the `AndroidManifest.xml` to grant it permissions to intercept notifications.

### Current Declaration in `app/src/main/AndroidManifest.xml`:
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

### Verification & Recommendations:
- **Service Name**: `.service.HushNotificationListener` is correct, fully resolving to `com.hush.app.service.HushNotificationListener`.
- **System Permission**: `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` is specified. This is critical to ensure that only the Android System can bind to the service, maintaining privacy and security.
- **Exported Status**: `android:exported="true"` is correct and required so that the system binding process is permitted.
- **Intent Filter**: The filter action `android.service.notification.NotificationListenerService` is correct and necessary for the service discovery mechanism.
- **Package Visibility (API 33+)**: Since the app targets SDK 35 and has `minSdk = 33`, it has implicit visibility to packages from which it intercepts notifications. However, using `PackageManager` to retrieve application info (like labels) could trigger name-not-found exceptions if the package is being uninstalled. A standard try-catch block is recommended. No additional `<queries>` declaration is needed in the manifest for standard listener operation.

---

## 2. Metadata Extraction Strategy
The `HushNotificationListener` receives a `StatusBarNotification` (SBN) when a notification is posted. It must extract the following properties from `sbn` and its internal `Notification` object.

| Target Field | Source API / Retrieval Method | Fallback Behavior |
| :--- | :--- | :--- |
| **Package Name** | `sbn.packageName` | None (Always available) |
| **App Name (Display)** | `packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, ...))` | `packageName` on `NameNotFoundException` |
| **Title** | `sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()` | `null` |
| **Text** | `sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()` | Fallback to `Notification.EXTRA_BIG_TEXT` |
| **Sender** | 1. `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)` -> get last message sender/person.<br>2. `extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)` | `null` (Note: for non-chat notifications, a null sender is standard and expected by the rule engine) |
| **Timestamp** | Implicitly used for rule execution (e.g. `LocalTime.now()`) and stored in Room logs via `Instant.now()`. | `Instant.now()` |

### Detailed Sender Extraction:
For chat applications (such as WhatsApp, Slack, Telegram), extracting the correct individual sender is vital. We propose:
1. **Primary Method**: Use `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)`. This extracts the `MessagingStyle` if present, allowing access to the last message's `Person` or `sender` string.
2. **Secondary Method**: Use `extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)`. Since `minSdk` is 33, we can safely use the type-safe modern `getParcelable` overload without checking build versions.
3. **Tertiary Method**: Let it resolve to `null` if no explicit sender can be extracted. In standard notifications, `EXTRA_TITLE` contains the sender name for 1-to-1 chats, but we evaluate `title` and `sender` separately in the use case.

---

## 3. Rule Evaluation Integration & Threading Model
Since rule evaluation and history logging involve database operations (retrieving active rules, querying, and logging matched events), the service must run these operations asynchronously.

### Threading Model:
- **Service Scope**: Declare a custom `CoroutineScope` with `Dispatchers.Default` and a `SupervisorJob()`. This ensures that even if one evaluation fails, the scope is not cancelled.
- **Service Lifecycle**: Clean up and cancel the scope in the service's `onDestroy()` method to prevent memory leaks:
  ```kotlin
  private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  override fun onDestroy() {
      super.onDestroy()
      serviceScope.cancel()
  }
  ```
- **Execution**: Launch a coroutine in `onNotificationPosted` to offload database query and evaluation operations from the main binder thread.

### Logic Flow:
1. Extract metadata safely inside the launched coroutine.
2. Call `evaluateNotificationUseCase.execute(packageName, appName, title, text, sender)`.
3. If the returned `RuleAction` is `RuleAction.BLOCK`, call `cancelNotification(sbn.key)` to dismiss the notification.
4. If the returned action is `RuleAction.ALLOW` or `RuleAction.MUTE`, do nothing (let the notification post naturally).

---

## 4. Verification and Test Infrastructure
We investigated `app/src/androidTest/java/com/hush/app/e2e/NotificationInterceptionE2ETest.kt`.

### Key Findings from Test:
- The E2E tests verify the rule engine using `simulateNotificationPost`, which invokes `evaluateNotificationUseCase.execute(...)` directly.
- The tests assert:
  - `BLOCK` action returns `true` for cancellation, logs matching rule ID, and logs `BLOCK` status.
  - `MUTE`/`ALLOW` actions return `false` for cancellation, and write corresponding logs.
  - Non-matching notifications do not write any database logs.
  - Rapid concurrent streams (30 concurrent posts) run concurrently without crashes (verifying thread-safety of the Room DB and use case).
  - Truncation limit on long text is handled safely.
- **Action Item**: Implementing `HushNotificationListener` following this design will perfectly align with the expectations set up in the E2E tests.

---

## 5. Reference Implementation File
A fully structured proposed code file has been created at:
`.agents/explorer_m2_1/proposed_HushNotificationListener.kt`
This file can be directly used as a replacement by the implementer.
