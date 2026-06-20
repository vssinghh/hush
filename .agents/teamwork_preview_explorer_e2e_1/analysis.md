# E2E Test Suite Design for Hush Android App

## 1. Executive Summary
Hush is a privacy-first Android application that filters system notifications based on natural language rules parsed via an on-device Gemini Nano model (Android AICore) and transcribed via `SpeechRecognizer`. Because these features depend heavily on system-level services and specific hardware capabilities (API 35+, AICore support, active microphones), standard end-to-end testing on common emulators or CI environments is highly prone to instability or failure.

To address this, this design outlines an **opaque-box E2E test suite** that runs under Android's instrumented test framework (`app/src/androidTest/`). By abstracting the Gemini Nano engine and SpeechRecognizer behind mockable domain interfaces, we can swap their implementations at test time using Hilt. This ensures that the entire application workflow—from onboarding and rule creation to real-time notification interception and DB logging—can be tested deterministically on any standard API 33+ emulator or physical device.

---

## 2. Instrumented Test Directory & Package Structure
All E2E and instrumented UI tests will reside under the standard `app/src/androidTest/java/com/hush/app/` source set. The files will be organized into packages as follows:

```
app/src/androidTest/java/com/hush/app/
├── runner/
│   └── HiltTestRunner.kt                  # Custom test runner to hook Hilt Test Application
│
├── di/
│   ├── TestAIModule.kt                    # Test Hilt Module for swapping AIEngine & SpeechRecognizer
│   └── TestDatabaseModule.kt              # Test Hilt Module for in-memory Room database setup
│
├── mock/
│   ├── FakeAIEngine.kt                    # Fake Gemini Nano parser implementation
│   └── FakeSpeechRecognizerWrapper.kt     # Fake on-device SpeechRecognizer wrapper
│
└── e2e/
    ├── AppFoundationE2ETest.kt            # Onboarding, bottom navigation, and settings theme tests
    ├── NotificationInterceptionE2ETest.kt # NLS listener rules execution and metadata extraction
    ├── ConversationalAIE2ETest.kt         # Text/voice chat commands, UI waveforms, and AI card confirmations
    ├── RuleManagementHistoryE2ETest.kt    # Rules CRUD, toggling, list empty states, history logs and searches
    ├── CrossFeatureE2ETest.kt             # Pairwise interaction tests between core features (Tier 3)
    └── RealWorldScenarioE2ETest.kt        # Realistic long-form user journey workflows (Tier 4)
```

---

## 3. Test Cases Catalog (51 Tests)

### Tier 1: Feature Coverage (20 Tests — 5 per Feature)

#### Feature 1: App Foundation (Onboarding, Navigation, Theme, Settings)
*   **Test Case ID**: `T1_F1_01`
    *   **Test Name**: `testOnboardingFlow_GrantAllPermissions_NavigatesToChat`
    *   **Objective**: Verify a first-time user can traverse onboarding, grant simulated permissions, and complete setup.
    *   **Setup**: SharedPreferences cleared (first launch).
    *   **Steps**:
        1. Start MainActivity. Verify Onboarding screen displays.
        2. Click "Next" button.
        3. Click "Grant Notification Access" button (Simulate grant).
        4. Click "Grant Microphone Permission" button (Simulate grant).
        5. Click "Exclude from Battery Optimization" button (Simulate ignore).
        6. Click "Get Started" button.
    *   **Expected Result**: Onboarding flag is updated in local preferences. App navigates to Chat screen; bottom navigation displays Chat as selected.
*   **Test Case ID**: `T1_F1_02`
    *   **Test Name**: `testBottomNav_SwitchTabs_RendersCorrectScreens`
    *   **Objective**: Verify that the bottom navigation bar successfully switches views.
    *   **Setup**: Onboarding completed.
    *   **Steps**:
        1. Click "Rules" icon in bottom nav. Verify Rules screen header.
        2. Click "History" icon in bottom nav. Verify History screen header.
        3. Click "Settings" icon in bottom nav. Verify Settings screen header.
        4. Click "Chat" icon in bottom nav. Verify Chat screen header.
    *   **Expected Result**: Each click switches the main layout container to the respective screen without lag or errors.
*   **Test Case ID**: `T1_F1_03`
    *   **Test Name**: `testSettingsScreen_DisplaysPermissionStatus`
    *   **Objective**: Verify that Settings screen indicators reflect active system permission states.
    *   **Setup**: Notification and Audio permissions are active in the test context.
    *   **Steps**:
        1. Click "Settings" icon in bottom nav.
        2. Observe statuses for Notification Interception and Voice Input.
    *   **Expected Result**: UI renders green "Active" badges for both.
*   **Test Case ID**: `T1_F1_04`
    *   **Test Name**: `testSettingsScreen_ToggleTheme_ThemeChangesAndPersists`
    *   **Objective**: Verify dark/light theme options can be set and persist.
    *   **Setup**: Standard app state.
    *   **Steps**:
        1. Open Settings screen.
        2. Click "Theme Preference". Select "Dark Theme".
        3. Re-create Activity (`ActivityScenario.recreate()`).
    *   **Expected Result**: After recreation, the theme remains set to Dark Theme, and Compose layout uses dark colors.
*   **Test Case ID**: `T1_F1_05`
    *   **Test Name**: `testAppLaunch_OnboardingAlreadyCompleted_LaunchesToChatDirectly`
    *   **Objective**: Verify subsequent launches bypass the onboarding screen.
    *   **Setup**: SharedPreferences onboarding flag pre-set to `true`.
    *   **Steps**:
        1. Launch MainActivity.
    *   **Expected Result**: The app launches directly onto the Chat screen; Onboarding layout is not shown.

#### Feature 2: Notification Interception (NotificationListenerService, Rule Evaluation, Actions, Logging)
*   **Test Case ID**: `T1_F2_01`
    *   **Test Name**: `testInterception_BlockRule_DismissesNotificationAndLogs`
    *   **Objective**: Verify a notification matching a block rule is cancelled and recorded as blocked.
    *   **Setup**: Active rule in DB: `App: com.whatsapp, Action: BLOCK, MatchField: ANY, MatchPattern: "Spam"`.
    *   **Steps**:
        1. Inject a notification with package `com.whatsapp`, title "Alert", text "Spam Offer".
    *   **Expected Result**: The notification is canceled. History DB contains a log with status `BLOCKED` and the correct rule ID.
*   **Test Case ID**: `T1_F2_02`
    *   **Test Name**: `testInterception_MuteRule_MutesNotificationAndLogs`
    *   **Objective**: Verify a notification matching a mute rule is delivered silently and recorded.
    *   **Setup**: Active rule in DB: `App: com.slack, Action: MUTE, MatchField: SENDER, MatchPattern: "Boss"`.
    *   **Steps**:
        1. Inject a notification with package `com.slack`, title "Boss", text "Report updates".
    *   **Expected Result**: Notification is allowed to post but muted (sound and vibration set to 0). History DB logs the action as `MUTED`.
*   **Test Case ID**: `T1_F2_03`
    *   **Test Name**: `testInterception_AllowRule_AllowsNotificationAndLogs`
    *   **Objective**: Verify a notification matching an allow rule is posted unchanged and recorded.
    *   **Setup**: Active rule in DB: `App: com.gmail, Action: ALLOW, MatchField: TITLE, MatchPattern: "Security"`.
    *   **Steps**:
        1. Inject a notification with package `com.gmail`, title "Security Alert", text "New login".
    *   **Expected Result**: Notification posts normally. History DB logs the action as `ALLOWED`.
*   **Test Case ID**: `T1_F2_04`
    *   **Test Name**: `testInterception_NoMatchingRules_AllowsNotificationWithoutLogs`
    *   **Objective**: Verify that notifications are passed through normally if no rules match.
    *   **Setup**: Empty rules database.
    *   **Steps**:
        1. Inject a notification from `com.instagram`.
    *   **Expected Result**: Notification is untouched. Depending on configuration, it either logs as a default `PASSED_THROUGH` or creates no history entry (here, we check that it is not canceled).
*   **Test Case ID**: `T1_F2_05`
    *   **Test Name**: `testInterception_ExtractsMetadataCorrectly`
    *   **Objective**: Verify NLS correctly extracts and saves notification headers.
    *   **Setup**: Generic allow-all rule to capture history.
    *   **Steps**:
        1. Inject notification: Package: `com.slack`, Display Name: "Slack", Title: "Development Team", Text: "Build fixed", Sender: "John Doe".
    *   **Expected Result**: History log row contains matched fields: app package `com.slack`, app display name "Slack", title "Development Team", content "Build fixed", and sender "John Doe".

#### Feature 3: Conversational AI (Chat Screen, AI Prompting, confirmation, Speech Recording)
*   **Test Case ID**: `T1_F3_01`
    *   **Test Name**: `testChat_TextCommand_RequestsAIAndShowsConfirmationCard`
    *   **Objective**: Verify typing and sending a text command calls the AI and returns a confirmation card.
    *   **Setup**: Mock AIEngine configured with a parse rule for "Mute WhatsApp".
    *   **Steps**:
        1. Open Chat screen.
        2. Type "Mute WhatsApp" in the input field. Click send.
    *   **Expected Result**: Chat bubble containing "Mute WhatsApp" appears. A system confirmation card containing the proposed rule details (Mute `com.whatsapp`) is displayed in the chat list.
*   **Test Case ID**: `T1_F3_02`
    *   **Test Name**: `testChat_VoiceCommand_StartsRecordingAndTranscribes`
    *   **Objective**: Verify pressing the voice button records and transcribes voice into text.
    *   **Setup**: Mock SpeechRecognizerWrapper.
    *   **Steps**:
        1. Click Mic button.
        2. Simulate voice input: "block slack notifications".
    *   **Expected Result**: Waveform UI appears. Transcribed text "block slack notifications" appears in the chat text input field, triggering the parser flow.
*   **Test Case ID**: `T1_F3_03`
    *   **Test Name**: `testChat_ConfirmRuleCard_SavesToDatabase`
    *   **Objective**: Verify confirming the parsed rule card saves the rule to the Room DB.
    *   **Setup**: AI confirmation card for "Block Slack" visible in Chat screen.
    *   **Steps**:
        1. Click "Confirm" on the card.
    *   **Expected Result**: A success message is posted in the chat log. Database contains a new active rule with package `com.slack`, action `BLOCK`.
*   **Test Case ID**: `T1_F3_04`
    *   **Test Name**: `testChat_CancelRuleCard_DiscardsRule`
    *   **Objective**: Verify canceling the parsed rule card discards the proposed rule.
    *   **Setup**: AI confirmation card for "Block Slack" visible in Chat screen.
    *   **Steps**:
        1. Click "Cancel" on the card.
    *   **Expected Result**: The confirmation card disappears. Database remains unchanged.
*   **Test Case ID**: `T1_F3_05`
    *   **Test Name**: `testChat_ConversationHistory_PersistsAcrossNavigation`
    *   **Objective**: Verify that messages in the Chat list persist when switching tabs.
    *   **Setup**: Active chat session with 3 messages.
    *   **Steps**:
        1. Click "Rules" tab.
        2. Click "Chat" tab to return.
    *   **Expected Result**: The scrollable chat list still contains the 3 previous messages.

#### Feature 4: Rule Management & History (Rules screen, History screen, Settings retention)
*   **Test Case ID**: `T1_F4_01`
    *   **Test Name**: `testRules_ListsRulesAndTogglesEnabledState`
    *   **Objective**: Verify that saved rules appear in the Rules screen and can be enabled/disabled.
    *   **Setup**: 1 active rule pre-populated in DB.
    *   **Steps**:
        1. Open Rules screen. Verify rule details match database contents.
        2. Click the toggle switch on the rule card to turn it off.
    *   **Expected Result**: The rule card visual state updates (dimmed). Database updates the rule's `enabled` field to `false`.
*   **Test Case ID**: `T1_F4_02`
    *   **Test Name**: `testRules_SwipeToDeleteRule_RemovesFromDB`
    *   **Objective**: Verify that swiping a rule card in the list deletes the rule.
    *   **Setup**: 1 rule pre-populated.
    *   **Steps**:
        1. Open Rules screen.
        2. Swipe left on the rule card.
    *   **Expected Result**: The rule card is removed from the UI. The rule is deleted from the Room database.
*   **Test Case ID**: `T1_F4_03`
    *   **Test Name**: `testRules_TapRule_OpensDetailDialog`
    *   **Objective**: Verify that clicking a rule card opens a detail popup with metadata.
    *   **Setup**: 1 rule pre-populated.
    *   **Steps**:
        1. Open Rules screen.
        2. Click the rule card.
    *   **Expected Result**: A detail dialog opens, showing the original text prompt, package mapping, and timestamps.
*   **Test Case ID**: `T1_F4_04`
    *   **Test Name**: `testHistory_ListsLogsAndFiltersByTabs`
    *   **Objective**: Verify history logs render and can be filtered by Action tab.
    *   **Setup**: DB populated with 1 `ALLOWED`, 1 `BLOCKED`, and 1 `MUTED` log.
    *   **Steps**:
        1. Open History screen. Verify 3 items are present.
        2. Click "Blocked" tab. Verify only the blocked item is visible.
        3. Click "All" tab. Verify all 3 items are visible.
    *   **Expected Result**: Filtering works correctly; the list updates dynamically based on the selected tab.
*   **Test Case ID**: `T1_F4_05`
    *   **Test Name**: `testHistory_TapItem_OpensDetailModal`
    *   **Objective**: Verify tapping a history log entry displays details including the matching rule.
    *   **Setup**: 1 history log pre-populated, referencing a specific rule ID.
    *   **Steps**:
        1. Open History screen.
        2. Click on the log item.
    *   **Expected Result**: A detail modal opens, showing full notification content and the text "Triggered by Rule: [Rule Name]".

---

### Tier 2: Boundary & Edge Cases (20 Tests — 5 per Feature)

#### Feature 1: App Foundation
*   **Test Case ID**: `T2_F1_01`
    *   **Test Name**: `testOnboarding_DenyNotificationAccess_ShowsRationaleAndDisablesNext`
    *   **Objective**: Verify that onboarding handles permission denial gracefully by preventing progress and showing help text.
    *   **Setup**: Fresh install.
    *   **Steps**:
        1. Launch Onboarding. Go to Notification Access step.
        2. Click "Grant". Simulate user returning without enabling the service (denied).
    *   **Expected Result**: A warning rationale appears on screen. The "Next" button remains disabled.
*   **Test Case ID**: `T2_F1_02`
    *   **Test Name**: `testThemeChange_MidSessionSystemThemeSwitch`
    *   **Objective**: Verify the app does not crash and updates colors immediately when system theme toggles.
    *   **Setup**: App open.
    *   **Steps**:
        1. Simulate system setting change from Light mode to Dark mode.
    *   **Expected Result**: The layout applies dark colors instantly without losing screen state or restarting views.
*   **Test Case ID**: `T2_F1_03`
    *   **Test Name**: `testAppLaunch_GeminiNanoUnsupported_DisplaysWarningBanner`
    *   **Objective**: Verify UI handles devices lacking AICore support.
    *   **Setup**: Configure Mock AIEngine to return `isAvailable = false`.
    *   **Steps**:
        1. Launch app onto Chat screen.
    *   **Expected Result**: A persistent banner is shown at the top: "On-device AI is not available on this device." Voice and text input buttons on the chat screen are disabled.
*   **Test Case ID**: `T2_F1_04`
    *   **Test Name**: `testActivityRecreation_SettingsStatePreserved`
    *   **Objective**: Verify settings state survives activity recreation (rotation/low memory).
    *   **Setup**: Open Settings screen. Set history retention to 90 days.
    *   **Steps**:
        1. Trigger Activity recreation.
    *   **Expected Result**: Settings view displays 90 days as the active preference.
*   **Test Case ID**: `T2_F1_05`
    *   **Test Name**: `testOnboarding_BatteryOptimizationRejected_AllowsProgressWithWarning`
    *   **Objective**: Verify battery optimization denial does not completely block onboarding.
    *   **Setup**: Go to Onboarding battery step.
    *   **Steps**:
        1. Simulate user denying the request.
    *   **Expected Result**: A warning popup appears explaining background death risks. Dismissing the popup enables the "Next" button.

#### Feature 2: Notification Interception
*   **Test Case ID**: `T2_F2_01`
    *   **Test Name**: `testInterception_NullOrEmptyMetadataFields_DoesNotCrash`
    *   **Objective**: Verify NLS evaluation handles empty notification properties safely.
    *   **Setup**: Rule active: Block all from `com.whatsapp`.
    *   **Steps**:
        1. Inject notification with package `com.whatsapp` but null title and null text.
    *   **Expected Result**: Notification is blocked safely. History records it with placeholders ("No Title", "No Content").
*   **Test Case ID**: `T2_F2_02`
    *   **Test Name**: `testInterception_ComplexRegexPatternMatching`
    *   **Objective**: Verify regex parsing evaluates complicated matching patterns correctly.
    *   **Setup**: Rule active: Mute where matchType = REGEX, matchPattern = `^.*\[URGENT\]\s(Security|Admin):\s.*$`
    *   **Steps**:
        1. Inject notification: Title = "[URGENT] Admin: password expired".
    *   **Expected Result**: Rule evaluates as a match; notification is muted.
*   **Test Case ID**: `T2_F2_03`
    *   **Test Name**: `testInterception_RapidConcurrentNotifications_ThreadSafety`
    *   **Objective**: Stress test NLS with concurrent incoming notification streams.
    *   **Setup**: Standard rules loaded.
    *   **Steps**:
        1. Concurrently post 30 notifications in a loop on separate threads.
    *   **Expected Result**: All notifications are processed and logged without Room DB locks or thread contention crashes.
*   **Test Case ID**: `T2_F2_04`
    *   **Test Name**: `testInterception_ExtremelyLongNotificationContent_HandlesTruncation`
    *   **Objective**: Verify rule matching works on massive text volumes and limits DB row sizes.
    *   **Setup**: Rule active: Block text containing "alert".
    *   **Steps**:
        1. Inject notification with 8,000 characters, containing "alert" at character 7,500.
    *   **Expected Result**: Notification is successfully blocked. History log contains a truncated text snippet (e.g. up to 1000 characters) containing "..." but preserves matching efficiency.
*   **Test Case ID**: `T2_F2_05`
    *   **Test Name**: `testInterception_RuleDisabled_BypassesInterception`
    *   **Objective**: Verify that disabled rules are ignored in the NLS loop.
    *   **Setup**: Rule in DB: Block all Gmail, but `enabled` is set to `false`.
    *   **Steps**:
        1. Inject a notification from `com.google.android.gm`.
    *   **Expected Result**: Notification posts normally. No rule action is taken.

#### Feature 3: Conversational AI
*   **Test Case ID**: `T2_F3_01`
    *   **Test Name**: `testChat_MalformedJsonFromAI_ShowsErrorMessage`
    *   **Objective**: Handle invalid or malformed outputs from Gemini Nano.
    *   **Setup**: Mock AIEngine set to return malformed JSON string.
    *   **Steps**:
        1. Type "Mute Slack" and Click Send.
    *   **Expected Result**: An error message bubble appears: "I couldn't process that command. Please try rephrasing."
*   **Test Case ID**: `T2_F3_02`
    *   **Test Name**: `testChat_UnresolvedAppName_DefaultsToNullPackage`
    *   **Objective**: Handle cases where user names an app not installed on the system.
    *   **Setup**: Mapped installed apps list does not contain "CustomApp". Mock AIEngine parses app as "CustomApp".
    *   **Steps**:
        1. Send command "Block CustomApp".
    *   **Expected Result**: Confirmation card appears. Mapped package shows as "Any App" (or null package) and warning text indicates "CustomApp is not installed; this will apply to any matching app".
*   **Test Case ID**: `T2_F3_03`
    *   **Test Name**: `testVoice_SpeechError_StopsRecordingAndShowsToast`
    *   **Objective**: Verify SpeechRecognizer errors are captured gracefully.
    *   **Setup**: Mock SpeechRecognizerWrapper set to fail with `ERROR_NO_MATCH`.
    *   **Steps**:
        1. Click Mic button. Start recording.
        2. Trigger error callback.
    *   **Expected Result**: Waveform UI disappears. Chat shows a toast: "Speech not recognized. Please try again."
*   **Test Case ID**: `T2_F3_04`
    *   **Test Name**: `testVoice_SilenceOnly_DoesNotSendQuery`
    *   **Objective**: Verify no AI query is made on silent recording inputs.
    *   **Setup**: Mock SpeechRecognizerWrapper.
    *   **Steps**:
        1. Click Mic. Click stop listening without voice input.
    *   **Expected Result**: Session closes. No new bubbles are added to the chat screen.
*   **Test Case ID**: `T2_F3_05`
    *   **Test Name**: `testChat_RapidQueries_ProcessesLatestOnly`
    *   **Objective**: Verify quick subsequent inputs do not trigger race conditions in UI.
    *   **Setup**: Mock AIEngine set to delay responses by 1.5 seconds.
    *   **Steps**:
        1. Send "Command A".
        2. Send "Command B" 200ms later.
    *   **Expected Result**: "Command B" cancellation cancels "Command A" task. UI displays "Command B" confirmation card upon completion.

#### Feature 4: Rule Management & History
*   **Test Case ID**: `T2_F4_01`
    *   **Test Name**: `testRulesScreen_EmptyState_DisplaysIllustration`
    *   **Objective**: Verify Rules screen handles empty list state cleanly.
    *   **Setup**: Rules database empty.
    *   **Steps**:
        1. Navigate to Rules screen.
    *   **Expected Result**: An empty state graphic and text "No active rules" are rendered.
*   **Test Case ID**: `T2_F4_02`
    *   **Test Name**: `testHistoryScreen_PagingAndLoadPerformanceStress`
    *   **Objective**: Verify history screen lists thousands of items without freezing.
    *   **Setup**: Pre-populate Room DB with 1,500 history entries.
    *   **Steps**:
        1. Open History screen.
        2. Scroll down rapidly to the bottom of the list.
    *   **Expected Result**: UI scrolls smoothly (FPS stays above 55) and loads elements incrementally.
*   **Test Case ID**: `T2_F4_03`
    *   **Test Name**: `testRules_RapidToggles_DoesNotDeadlockDB`
    *   **Objective**: Verify rapid user toggle inputs do not cause Room database deadlocks.
    *   **Setup**: 1 rule in DB.
    *   **Steps**:
        1. Navigate to Rules screen.
        2. Rapidly tap the enable/disable switch 8 times in 1 second.
    *   **Expected Result**: The final database state matches the final switch state; UI does not freeze.
*   **Test Case ID**: `T2_F4_04`
    *   **Test Name**: `testHistorySearch_SpecialCharacters_LiteralMatch`
    *   **Objective**: Verify history search bar query handles literal strings and regex safety.
    *   **Setup**: DB logs contain entries with characters like `*` or `[`.
    *   **Steps**:
        1. Open History. Type `*` in search.
    *   **Expected Result**: Items containing `*` are returned. No database syntax errors or crashes occur.
*   **Test Case ID**: `T2_F4_05`
    *   **Test Name**: `testSettings_ChangeRetention_TriggersImmediatePruning`
    *   **Objective**: Verify that reducing retention immediately prunes the database.
    *   **Setup**: Logs in DB dated 10 days ago.
    *   **Steps**:
        1. Change retention period in Settings from "30 Days" to "7 Days".
    *   **Expected Result**: Database deletes logs older than 7 days immediately. Verify the 10-day-old log is removed from DB.

---

### Tier 3: Cross-Feature Combinations (6 Tests — Pairwise Interactions)

*   **Test Case ID**: `T3_CF_01`
    *   **Test Name**: `testCombination_AICreation_To_ImmediateNotificationInterception`
    *   **Objective**: Verify that a rule generated via AI is immediately active in the interceptor service.
    *   **Setup**: Clean state. Mock AIEngine.
    *   **Steps**:
        1. Type "Block Instagram" in Chat screen. Click Send.
        2. Confirm the rule card.
        3. Post notification: package `com.instagram.android`.
    *   **Expected Result**: Notification is immediately intercepted and canceled.
*   **Test Case ID**: `T3_CF_02`
    *   **Test Name**: `testCombination_RulesUiToggle_To_NotificationInterception`
    *   **Objective**: Verify that disabling a rule in the Rules tab immediately modifies NLS interception behaviors.
    *   **Setup**: Active rule: Block Gmail.
    *   **Steps**:
        1. Post Gmail notification -> verify BLOCKED.
        2. Go to Rules Screen. Toggle the Gmail rule off.
        3. Post another Gmail notification -> verify ALLOWED.
    *   **Expected Result**: The service queries updated active rules from Room DB for every notification, reacting instantly to UI toggles.
*   **Test Case ID**: `T3_CF_03`
    *   **Test Name**: `testCombination_OnboardingCompletion_To_AIChatFlow`
    *   **Objective**: Verify that completing the onboarding flow leads immediately to a functioning Chat screen.
    *   **Setup**: Fresh launch.
    *   **Steps**:
        1. Complete onboarding, granting simulated Notification and Audio permissions.
        2. Immediately type "Mute Slack" in Chat and send.
    *   **Expected Result**: Onboarding screen is swapped out, Chat is enabled, and "Mute Slack" parses successfully.
*   **Test Case ID**: `T3_CF_04`
    *   **Test Name**: `testCombination_AICreation_To_RulesListAndEdit`
    *   **Objective**: Verify rules created via AI are editable inside the Rules tab.
    *   **Setup**: Clean DB. Mock AIEngine.
    *   **Steps**:
        1. Type "Mute WhatsApp" in Chat. Confirm the rule.
        2. Open Rules screen. Click on the new WhatsApp rule card.
        3. Edit rule: change action from MUTE to BLOCK. Save changes.
        4. Inspect Room DB.
    *   **Expected Result**: Rule action updates to BLOCK in DB.
*   **Test Case ID**: `T3_CF_05`
    *   **Test Name**: `testCombination_NotificationInterception_To_RealTimeHistoryLog`
    *   **Objective**: Verify intercepted notifications update the active History tab in real-time.
    *   **Setup**: Active rule: Block Slack. Open History Screen.
    *   **Steps**:
        1. With History screen open, inject a Slack notification.
    *   **Expected Result**: The Slack notification is blocked, and a new history log entry is inserted, immediately rendering on the open History screen without reloading.
*   **Test Case ID**: `T3_CF_06`
    *   **Test Name**: `testCombination_RuleDeletion_To_HistoryLogsGracefulDisplay`
    *   **Objective**: Verify history details render safely after the associated rule is deleted.
    *   **Setup**: Pre-populated rule mapping to a blocked log.
    *   **Steps**:
        1. Navigate to Rules screen. Swipe left to delete the rule.
        2. Open History screen. Tap on the log associated with that deleted rule.
    *   **Expected Result**: The log detail popup renders successfully, displaying the rule name placeholder: "Rule deleted" without crashing.

---

### Tier 4: Real-World Scenarios (5 Tests — Complex Workloads)

*   **Test Case ID**: `T4_RW_01`
    *   **Test Name**: `testScenario_FreshInstallOnboardingAndVoiceRuleCreation`
    *   **Objective**: Simulate a complete fresh-user journey: Install -> Onboard -> Voice command -> Confirm -> Intercept.
    *   **Setup**: Fresh install.
    *   **Steps**:
        1. Complete Onboarding. Grant all permissions.
        2. Open Chat. Click Mic. Simulate voice command: "Mute Gmail messages from Amazon".
        3. Verify AI parses to Mute: package `com.google.android.gm`, matchField `SENDER`, matchPattern `Amazon`.
        4. Confirm the rule.
        5. Inject Gmail notification from Amazon -> verify MUTED.
        6. Inject Gmail notification from Mom -> verify ALLOWED.
    *   **Expected Result**: The complete system is verified: permissions are set, database is updated, AI parses, and the listener blocks correctly.
*   **Test Case ID**: `T4_RW_02`
    *   **Test Name**: `testScenario_TimeWindowRule_ActiveAndInactiveEvaluations`
    *   **Objective**: Verify time-windowed rules behave correctly at different times.
    *   **Setup**: Mock TimeProvider. Rule active: "Block Instagram between 22:00 and 07:00".
    *   **Steps**:
        1. Set mock time to 23:00. Inject Instagram notification. Verify BLOCKED.
        2. Set mock time to 15:00. Inject Instagram notification. Verify ALLOWED.
    *   **Expected Result**: Rule engine correctly queries the TimeProvider, applying blocks only during specified windows.
*   **Test Case ID**: `T4_RW_03`
    *   **Test Name**: `testScenario_MultipleRules_PriorityEvaluation`
    *   **Objective**: Verify priority order (lower priority value = higher precedence).
    *   **Setup**: Pre-populate 2 rules:
        *   Rule A (Priority 0): ALLOW WhatsApp from "Dad".
        *   Rule B (Priority 1): BLOCK all WhatsApp notifications.
    *   **Steps**:
        1. Inject WhatsApp notification from "Dad". Verify ALLOWED.
        2. Inject WhatsApp notification from "Stranger". Verify BLOCKED.
    *   **Expected Result**: The rule engine iterates through rules in priority order. Rule A matches and allows "Dad", short-circuiting Rule B. Rule B catches "Stranger".
*   **Test Case ID**: `T4_RW_04`
    *   **Test Name**: `testScenario_InvertedRule_AllowOnlyMatchingExceptions`
    *   **Objective**: Verify inverted filter matching (block everything *except* what matches).
    *   **Setup**: Rule active: BLOCK Slack except when sender = "Manager" (`isInverted = true, action = BLOCK`).
    *   **Steps**:
        1. Inject Slack from "Manager". Verify ALLOWED.
        2. Inject Slack from "Co-worker". Verify BLOCKED.
    *   **Expected Result**: Inverted block rules successfully negate matching logic, acting as an allowlist for the matched pattern and a blocklist for the rest.
*   **Test Case ID**: `T4_RW_05`
    *   **Test Name**: `testScenario_SettingsRetention_DatabasePurgeJob`
    *   **Objective**: Verify historical data cleanup policies in a realistic session.
    *   **Setup**: History DB contains:
        *   Log X: Dated 14 days ago.
        *   Log Y: Dated 3 days ago.
    *   **Steps**:
        1. Open Settings. Change retention policy to "7 Days".
        2. Trigger database cleanup (e.g. running the Coroutine pruning worker).
        3. Navigate to History list.
    *   **Expected Result**: Log X is deleted. Log Y remains visible in the scrollable log.

---

## 4. On-Device AI & Speech Mock/Stub Strategy

To run E2E tests reliably on CI and standard emulators, we must prevent the app from attempting to bind to the system-level Android AICore (Gemini Nano) and SpeechRecognizer services. 

### Step 1: Define Interfaces in the Data Layer
We decouple these services by writing domain interfaces:

```kotlin
// com.hush.app.domain.repository.AIEngine
interface AIEngine {
    fun isAvailable(): Boolean
    suspend fun parseCommand(prompt: String): ParsedCommand
}

// com.hush.app.domain.repository.SpeechRecognizerWrapper
interface SpeechRecognizerWrapper {
    val state: Flow<SpeechState>
    fun startListening()
    fun stopListening()
}

sealed interface SpeechState {
    object Idle : SpeechState
    object Listening : SpeechState
    data class WaveformUpdate(val amplitude: Float) : SpeechState
    data class PartialResult(val text: String) : SpeechState
    data class FinalResult(val text: String) : SpeechState
    data class Error(val errorCode: Int) : SpeechState
}
```

### Step 2: Implement Fake Stubs in AndroidTest Source Set
We implement stubs that provide programmable mock responses:

```kotlin
// app/src/androidTest/java/com/hush/app/mock/FakeAIEngine.kt
class FakeAIEngine @Inject constructor() : AIEngine {
    private val responses = mutableMapOf<String, ParsedCommand>()
    private var available = true

    fun setResponse(prompt: String, command: ParsedCommand) {
        responses[prompt.trim().lowercase()] = command
    }

    fun setAvailable(available: Boolean) {
        this.available = available
    }

    override fun isAvailable(): Boolean = available

    override suspend fun parseCommand(prompt: String): ParsedCommand {
        if (!available) throw IllegalStateException("AICore unavailable")
        return responses[prompt.trim().lowercase()] ?: ParsedCommand(
            action = RuleAction.ALLOW,
            app = null,
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = prompt,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Fallback: $prompt"
        )
    }
}
```

```kotlin
// app/src/androidTest/java/com/hush/app/mock/FakeSpeechRecognizerWrapper.kt
class FakeSpeechRecognizerWrapper @Inject constructor() : SpeechRecognizerWrapper {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: Flow<SpeechState> = _state

    override fun startListening() {
        _state.value = SpeechState.Listening
    }

    override fun stopListening() {
        _state.value = SpeechState.Idle
    }

    fun simulateSpeech(text: String) {
        _state.value = SpeechState.WaveformUpdate(0.4f)
        _state.value = SpeechState.WaveformUpdate(0.8f)
        _state.value = SpeechState.PartialResult(text.substring(0, text.length / 2))
        _state.value = SpeechState.FinalResult(text)
    }

    fun simulateError(code: Int) {
        _state.value = SpeechState.Error(code)
    }
}
```

---

## 5. Detailed Test Infrastructure Plan

### 1. Hilt Testing Setup
We override standard Hilt dependencies in tests by implementing a custom Test Runner:

```kotlin
// app/src/androidTest/java/com/hush/app/runner/HiltTestRunner.kt
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

We configure this runner in `app/build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "com.hush.app.runner.HiltTestRunner"
    }
}
```

In `app/src/androidTest/java/com/hush/app/di/TestAIModule.kt`, we replace the production AI module with our stubs using Hilt's `@TestInstallIn`:
```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AIModule::class]
)
interface TestAIModule {
    @Binds
    @Singleton
    fun bindAIEngine(fake: FakeAIEngine): AIEngine

    @Binds
    @Singleton
    fun bindSpeechRecognizerWrapper(fake: FakeSpeechRecognizerWrapper): SpeechRecognizerWrapper
}
```

### 2. Room Database Setup
To ensure database isolation across tests, we configure an in-memory database in `app/src/androidTest/java/com/hush/app/di/TestDatabaseModule.kt`:

```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideInMemoryDB(@ApplicationContext context: Context): HushDatabase {
        return Room.inMemoryDatabaseBuilder(context, HushDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideRuleDao(db: HushDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideLogDao(db: HushDatabase): NotificationLogDao = db.notificationLogDao()
}
```

### 3. Compose UI Test Semantics & Tags
To reliably target Compose layout nodes, key screen elements will be decorated with `Modifier.testTag("tag_name")`.

| Screen | UI Element | Modifier Test Tag (`testTag`) |
| :--- | :--- | :--- |
| **Chat** | Text Input Field | `"chat_input_field"` |
| **Chat** | Send Message Button | `"chat_send_button"` |
| **Chat** | Voice Mic Button | `"chat_mic_button"` |
| **Chat** | AI Proposed Rule Card | `"ai_rule_card"` |
| **Chat** | Rule Confirm Button | `"ai_rule_confirm"` |
| **Chat** | Rule Cancel Button | `"ai_rule_cancel"` |
| **Rules** | Scrollable Rules List | `"rules_list"` |
| **Rules** | Individual Rule Card | `"rule_card_[id]"` |
| **Rules** | Rule Active Switch | `"rule_toggle_[id]"` |
| **History**| Scrollable Logs List | `"history_list"` |
| **History**| Search Bar Input | `"history_search_input"` |
| **History**| Tab Selector - All | `"history_tab_all"` |
| **History**| Tab Selector - Blocked| `"history_tab_blocked"` |

*Example Compose UI test template:*
```kotlin
@HiltAndroidTest
class ChatE2ETest {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var fakeAI: FakeAIEngine

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testTextRuleCreation() {
        val rule = ParsedCommand(
            action = RuleAction.BLOCK,
            app = "com.slack",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = "Urgent",
            isInverted = false,
            summary = "Block Slack unless urgent"
        )
        fakeAI.setResponse("block slack except urgent", rule)

        composeRule.onNodeWithTag("chat_input_field").performTextInput("block slack except urgent")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        
        composeRule.onNodeWithTag("ai_rule_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_rule_confirm").performClick()

        // Verify successful save message appears
        composeRule.onNodeWithText("Rule created successfully").assertIsDisplayed()
    }
}
```

### 4. Permission Handling in Tests
System-level dialogs (such as granting runtime audio recording or system-wide Notification Access) must be bypassable programmatically to avoid test blocking.

1.  **Audio Permission (Microphone)**:
    We use standard testing permission rules:
    ```kotlin
    @get:Rule(order = 2)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO
    )
    ```
2.  **Notification Access Permission (NLS)**:
    Since this is a system settings toggle rather than a standard runtime popup, we grant it using an ADB command through instrumentation:
    ```kotlin
    @Before
    fun grantNotificationListenerPermission() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val command = "cmd notification allow_listener com.hush.app/com.hush.app.service.HushNotificationListener"
        uiAutomation.executeShellCommand(command)
    }
    ```
3.  **Battery Optimization Exemption Dialog**:
    We use Espresso Intents to stub system settings intent responses:
    ```kotlin
    @Before
    fun setupIntents() {
        Intents.init()
        intending(hasAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDownIntents() {
        Intents.release()
    }
    ```

---

## 6. Conclusion
By decoupling the application logic from physical on-device requirements, we ensure that:
1.  Tests can execute deterministically on standard emulators or CI platforms (e.g. GitHub Actions).
2.  All 51 test cases across the four coverage tiers will execute and compile cleanly with minimal flakiness.
3.  Changes to DB schemas or rule execution priority are fully checked end-to-end.
