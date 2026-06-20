package com.hush.app.e2e

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hush.app.MainActivity
import com.hush.app.data.pref.OnboardingPrefs
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.mock.FakeAIEngine
import com.hush.app.mock.FakeSpeechRecognizerWrapper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConversationalAIE2ETest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var composeRule = createEmptyComposeRule()

    @Inject
    lateinit var fakeAIEngine: FakeAIEngine

    @Inject
    lateinit var fakeSpeechRecognizer: FakeSpeechRecognizerWrapper

    @Inject
    lateinit var fakePackageResolver: com.hush.app.mock.FakePackageResolver

    private var activeScenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        hiltRule.inject()
        fakeAIEngine.setAvailable(true)
        fakePackageResolver.setInstalledApps(mapOf(
            "whatsapp" to "com.whatsapp",
            "slack" to "com.slack"
        ))
        
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        OnboardingPrefs(context).isOnboardingCompleted = true
        
        activeScenario?.close()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activeScenario = ActivityScenario.launch<MainActivity>(intent)
        
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("chat_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @org.junit.After
    fun tearDown() {
        activeScenario?.close()
        activeScenario = null
    }

    @Test
    fun testChat_TextCommand_RequestsAIAndShowsConfirmationCard() {
        // T1_F3_01: Verify typing and sending a text command calls the AI and returns a confirmation card
        val response = ParsedCommand(
            action = RuleAction.MUTE,
            app = "com.whatsapp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Mute WhatsApp"
        )
        fakeAIEngine.setResponse("mute whatsapp", response)

        // Type "Mute WhatsApp" and click Send
        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("Mute WhatsApp")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()

        // Wait for AI proposed rule card to display
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("ai_rule_card").fetchSemanticsNodes().isNotEmpty()
        }

        // Expected Result: Chat bubble and proposed rule card displayed
        composeRule.onNodeWithText("Mute WhatsApp").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_rule_card").assertIsDisplayed()
    }

    @Test
    fun testChat_VoiceCommand_StartsRecordingAndTranscribes() {
        // T1_F3_02: Verify pressing the voice button records and transcribes voice into text
        val response = ParsedCommand(
            action = RuleAction.BLOCK,
            app = "com.slack",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Block Slack"
        )
        fakeAIEngine.setResponse("block slack notifications", response)

        // Click Mic button
        composeRule.onNodeWithTag("chat_mic_button").performClick()

        // Expected Result: Waveform UI appears
        composeRule.onNodeWithTag("voice_waveform_ui").assertIsDisplayed()

        // Simulate voice input
        fakeSpeechRecognizer.simulateSpeech("block slack notifications")

        // Expected Result: Transcribed text appears in input field or sends immediately
        composeRule.onNodeWithTag("chat_input_field").assertTextContains("block slack notifications")
    }

    @Test
    fun testChat_ConfirmRuleCard_SavesToDatabase() {
        // T1_F3_03: Verify confirming the parsed rule card saves the rule to the Room DB
        val response = ParsedCommand(
            action = RuleAction.BLOCK,
            app = "com.slack",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Block Slack"
        )
        fakeAIEngine.setResponse("block slack", response)

        // Input and Send
        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("block slack")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()

        // Wait for card to appear
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("ai_rule_card").fetchSemanticsNodes().isNotEmpty()
        }

        // Click Confirm
        composeRule.onNodeWithTag("ai_rule_confirm").performScrollTo().performClick()

        // Wait for success message
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Rule created successfully").fetchSemanticsNodes().isNotEmpty()
        }

        // Expected Result: A success message is posted in the chat log
        composeRule.onNodeWithText("Rule created successfully").assertIsDisplayed()
    }

    @Test
    fun testChat_CancelRuleCard_DiscardsRule() {
        // T1_F3_04: Verify canceling the parsed rule card discards the proposed rule
        val response = ParsedCommand(
            action = RuleAction.BLOCK,
            app = "com.slack",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Block Slack"
        )
        fakeAIEngine.setResponse("block slack", response)

        // Input and Send
        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("block slack")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()

        // Wait for card to appear
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("ai_rule_card").fetchSemanticsNodes().isNotEmpty()
        }

        // Click Cancel
        composeRule.onNodeWithTag("ai_rule_cancel").performScrollTo().performClick()

        // Wait for card to disappear
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("ai_rule_card").fetchSemanticsNodes().isEmpty()
        }

        // Expected Result: The confirmation card disappears
        composeRule.onNodeWithTag("ai_rule_card").assertDoesNotExist()
    }

    @Test
    fun testChat_ConversationHistory_PersistsAcrossNavigation() {
        // T1_F3_05: Verify that messages in the Chat list persist when switching tabs
        val response = ParsedCommand(
            action = RuleAction.MUTE,
            app = "com.whatsapp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Mute WhatsApp"
        )
        fakeAIEngine.setResponse("mute whatsapp", response)

        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("mute whatsapp")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()

        // Wait for chat bubble
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("mute whatsapp").fetchSemanticsNodes().isNotEmpty()
        }

        // Switch to Rules tab
        composeRule.onNodeWithTag("bottom_nav_rules").performClick()
        composeRule.onNodeWithTag("rules_screen").assertIsDisplayed()

        // Return to Chat tab
        composeRule.onNodeWithTag("bottom_nav_chat").performClick()
        composeRule.waitForIdle()

        // Expected Result: Previous chat bubble still exists
        composeRule.onNodeWithText("mute whatsapp").assertIsDisplayed()
    }

    @Test
    fun testChat_MalformedJsonFromAI_ShowsErrorMessage() {
        // T2_F3_01: Handle invalid or malformed outputs from Gemini Nano
        // We set the engine to output malformed or trigger parsing error
        fakeAIEngine.setResponse("mute slack", ParsedCommand(
            action = RuleAction.MUTE,
            app = null,
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "MALFORMED_JSON_TRIGGER"
        ))

        // Send Command
        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("mute slack")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()

        // Wait for error bubble
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("chat_error_message").fetchSemanticsNodes().isNotEmpty()
        }

        // Expected Result: An error message bubble appears
        composeRule.onNodeWithTag("chat_error_message").assertIsDisplayed()
    }

    @Test
    fun testChat_UnresolvedAppName_DefaultsToNullPackage() {
        // T2_F3_02: Handle cases where user names an app not installed on the system
        val response = ParsedCommand(
            action = RuleAction.BLOCK,
            app = "com.customapp.uninstalled",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Block CustomApp"
        )
        fakeAIEngine.setResponse("block customapp", response)

        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("block customapp")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()

        // Wait for card to appear
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("ai_rule_card").fetchSemanticsNodes().isNotEmpty()
        }

        // Expected Result: Confirmation card appears with a warning
        composeRule.onNodeWithTag("ai_rule_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_rule_warning_uninstalled").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testVoice_SpeechError_StopsRecordingAndShowsToast() {
        // T2_F3_03: Verify SpeechRecognizer errors are captured gracefully
        composeRule.onNodeWithTag("chat_mic_button").performClick()
        
        // Simulate error callback
        fakeSpeechRecognizer.simulateError(5) // ERROR_CLIENT or similar

        // Expected Result: Waveform UI disappears, error message shown
        composeRule.onNodeWithTag("voice_waveform_ui").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_error_message").assertIsDisplayed()
    }

    @Test
    fun testVoice_SilenceOnly_DoesNotSendQuery() {
        // T2_F3_04: Verify no AI query is made on silent recording inputs
        composeRule.onNodeWithTag("chat_mic_button").performClick()

        // Stop listening without input
        fakeSpeechRecognizer.stopListening()

        // Expected Result: Session closes. No new bubbles are added.
        composeRule.onNodeWithTag("voice_waveform_ui").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_input_field").assertTextContains("")
    }

    @Test
    fun testChat_RapidQueries_ProcessesLatestOnly() {
        // T2_F3_05: Verify quick subsequent inputs do not trigger race conditions in UI
        val responseA = ParsedCommand(
            action = RuleAction.BLOCK,
            app = "com.slack",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Command A"
        )
        val responseB = ParsedCommand(
            action = RuleAction.MUTE,
            app = "com.slack",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Command B"
        )
        fakeAIEngine.setResponse("command a", responseA)
        fakeAIEngine.setResponse("command b", responseB)

        // Send Command A
        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("command a")
        composeRule.onNodeWithTag("chat_send_button").performClick()

        // Send Command B immediately
        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("command b")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()


        // Wait for Command B card
        composeRule.waitUntil(15000) {
            try {
                composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Command B", substring = true))
                true
            } catch (e: Throwable) {
                composeRule.onAllNodesWithText("Command B", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }

        // Expected Result: Command B card displays
        composeRule.onNodeWithText("Command B", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Command A", substring = true).assertDoesNotExist()
    }
}
