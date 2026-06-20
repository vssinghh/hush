package com.hush.app.ui.screens.chat

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.Rule
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.permission.PermissionManager
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.AppInfo
import com.hush.app.domain.repository.PackageResolver
import com.hush.app.domain.repository.RuleRepository
import com.hush.app.domain.repository.SpeechRecognizerWrapper
import com.hush.app.domain.repository.SpeechState
import com.hush.app.domain.usecase.ParseCommandUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

class LocalFakeAIEngine : AIEngine {
    var available = true
    override fun isAvailable(): Boolean = available
    override suspend fun parseCommand(prompt: String): ParsedCommand {
        if (prompt == "malformed") {
            return ParsedCommand(
                action = RuleAction.MUTE,
                app = null,
                matchField = MatchField.ANY,
                matchType = MatchType.CONTAINS,
                matchPattern = null,
                isInverted = false,
                timeStart = null,
                timeEnd = null,
                summary = "MALFORMED_JSON_TRIGGER"
            )
        }
        return ParsedCommand(
            action = RuleAction.MUTE,
            app = "com.whatsapp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = LocalTime.of(9, 0),
            timeEnd = LocalTime.of(17, 0),
            summary = "Mute WhatsApp"
        )
    }
}

class LocalFakeSpeechRecognizerWrapper : SpeechRecognizerWrapper {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: Flow<SpeechState> = _state

    var startListeningCalled = false
    var stopListeningCalled = false

    override fun startListening() {
        startListeningCalled = true
        _state.value = SpeechState.Listening
    }

    override fun stopListening() {
        stopListeningCalled = true
        _state.value = SpeechState.Idle
    }

    fun emitState(newState: SpeechState) {
        _state.value = newState
    }
}

class LocalFakeRuleRepository : RuleRepository {
    val insertedRules = mutableListOf<Rule>()
    var nextPriority = 42

    override fun getAllRules(): Flow<List<Rule>> = throw UnsupportedOperationException()
    override suspend fun getActiveRules(): List<Rule> = throw UnsupportedOperationException()
    override suspend fun getRuleById(id: Long): Rule? = throw UnsupportedOperationException()

    override suspend fun insertRule(rule: Rule): Long {
        insertedRules.add(rule)
        return insertedRules.size.toLong()
    }

    override suspend fun updateRule(rule: Rule) = throw UnsupportedOperationException()
    override suspend fun deleteRule(rule: Rule) = throw UnsupportedOperationException()
    override suspend fun deleteRuleById(id: Long) = throw UnsupportedOperationException()

    override suspend fun getNextPriority(): Int = nextPriority
}

class LocalFakePackageResolver : PackageResolver {
    override fun getInstalledApps(): List<AppInfo> = emptyList()
    override fun resolvePackage(appName: String): String? = "com.whatsapp"
    override fun isInstalled(packageName: String): Boolean = true
}

class LocalFakePermissionManager : PermissionManager {
    var hasPermission = true
    var requestPermissionCalled = false

    override fun hasNotificationAccess(): Boolean = true
    override fun hasMicrophonePermission(): Boolean = hasPermission
    override fun isBatteryExempt(): Boolean = true
    override fun isNotificationAccessDenied(): Boolean = false
    override fun requestNotificationAccess(context: Context) {}
    override fun requestMicrophonePermission(launcher: ManagedActivityResultLauncher<String, Boolean>) {
        requestPermissionCalled = true
    }
    override fun requestBatteryExemption(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {}
    override fun setNotificationAccessDenied(denied: Boolean) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var aiEngine: LocalFakeAIEngine
    private lateinit var speechRecognizerWrapper: LocalFakeSpeechRecognizerWrapper
    private lateinit var ruleRepository: LocalFakeRuleRepository
    private lateinit var packageResolver: LocalFakePackageResolver
    private lateinit var permissionManager: LocalFakePermissionManager
    private lateinit var parseCommandUseCase: ParseCommandUseCase
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        aiEngine = LocalFakeAIEngine()
        speechRecognizerWrapper = LocalFakeSpeechRecognizerWrapper()
        ruleRepository = LocalFakeRuleRepository()
        packageResolver = LocalFakePackageResolver()
        permissionManager = LocalFakePermissionManager()
        parseCommandUseCase = ParseCommandUseCase(aiEngine, packageResolver)

        viewModel = ChatViewModel(
            aiEngine = aiEngine,
            speechRecognizerWrapper = speechRecognizerWrapper,
            ruleRepository = ruleRepository,
            parseCommandUseCase = parseCommandUseCase,
            packageResolver = packageResolver,
            permissionManager = permissionManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() {
        assertFalse(viewModel.isListening.value)
        assertEquals("", viewModel.textState.value)
        assertNull(viewModel.proposedRule.value)
        assertNull(viewModel.errorMessage.value)
        assertEquals(15, viewModel.amplitudes.size)
        assertTrue(viewModel.amplitudes.all { it == 0.1f })
    }

    @Test
    fun testSpeechStateListening_updatesIsListening() = runTest {
        speechRecognizerWrapper.emitState(SpeechState.Listening)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.isListening.value)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun testSpeechStateWaveformUpdate_normalizesAndLimitsAmplitudes() = runTest {
        // Listening first
        speechRecognizerWrapper.emitState(SpeechState.Listening)
        testScheduler.advanceUntilIdle()

        // Send WaveformUpdate. RMSdB range is [-2f, 10f] normalized to [0.1f, 1.0f]
        // -2f -> (-2 + 2)/12 = 0f -> coerceIn -> 0.1f
        speechRecognizerWrapper.emitState(SpeechState.WaveformUpdate(-2f))
        testScheduler.advanceUntilIdle()
        assertEquals(0.1f, viewModel.amplitudes.last())

        // 10f -> (10 + 2)/12 = 1.0f -> 1.0f
        speechRecognizerWrapper.emitState(SpeechState.WaveformUpdate(10f))
        testScheduler.advanceUntilIdle()
        assertEquals(1.0f, viewModel.amplitudes.last())

        // 4f -> (4 + 2)/12 = 0.5f
        speechRecognizerWrapper.emitState(SpeechState.WaveformUpdate(4f))
        testScheduler.advanceUntilIdle()
        assertEquals(0.5f, viewModel.amplitudes.last())

        // Ensure we still have exactly 15 amplitudes
        assertEquals(15, viewModel.amplitudes.size)
    }

    @Test
    fun testSpeechStatePartialResult_updatesTextState() = runTest {
        speechRecognizerWrapper.emitState(SpeechState.PartialResult("mute what"))
        testScheduler.advanceUntilIdle()

        assertEquals("mute what", viewModel.textState.value)
    }

    @Test
    fun testSpeechStateFinalResult_updatesTextStateAndStopsListening() = runTest {
        speechRecognizerWrapper.emitState(SpeechState.FinalResult("mute whatsapp"))
        testScheduler.advanceUntilIdle()

        assertEquals("mute whatsapp", viewModel.textState.value)
        assertFalse(viewModel.isListening.value)
    }

    @Test
    fun testSpeechStateError_setsErrorMessageAndStopsListening() = runTest {
        speechRecognizerWrapper.emitState(SpeechState.Error(5))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isListening.value)
        assertEquals("Speech recognition error: 5", viewModel.errorMessage.value)
    }

    @Test
    fun testSpeechStateIdle_stopsListening() = runTest {
        speechRecognizerWrapper.emitState(SpeechState.Listening)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.isListening.value)

        speechRecognizerWrapper.emitState(SpeechState.Idle)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.isListening.value)
    }

    @Test
    fun testToggleListening_startsAndStopsListening() {
        assertFalse(viewModel.isListening.value)

        // Toggle to start
        viewModel.toggleListening()
        assertTrue(speechRecognizerWrapper.startListeningCalled)

        // Fake isListening state change
        viewModel.isListening.value = true

        // Toggle to stop
        viewModel.toggleListening()
        assertTrue(speechRecognizerWrapper.stopListeningCalled)
    }

    @Test
    fun testHandleSend_addsMessageAndTriggersAI() = runTest {
        val initialSize = viewModel.mockMessages.size

        viewModel.handleSend("Mute WhatsApp")
        testScheduler.advanceUntilIdle()

        // Prompt added to mockMessages
        assertEquals(initialSize + 1, viewModel.mockMessages.size)
        assertEquals("Mute WhatsApp", viewModel.mockMessages.last())
        // Input text cleared
        assertEquals("", viewModel.textState.value)

        // Proposed rule returned
        val proposed = viewModel.proposedRule.value
        assertTrue(proposed != null)
        assertEquals("Mute WhatsApp", proposed!!.summary)
        assertEquals("com.whatsapp", proposed.app)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun testHandleSend_blankPrompt_ignored() = runTest {
        val initialSize = viewModel.mockMessages.size

        viewModel.handleSend("   ")
        testScheduler.advanceUntilIdle()

        assertEquals(initialSize, viewModel.mockMessages.size)
        assertNull(viewModel.proposedRule.value)
    }

    @Test
    fun testHandleSend_malformedAIResponse_setsErrorMessage() = runTest {
        viewModel.handleSend("malformed")
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.proposedRule.value)
        assertEquals("AI Engine error: Malformed AI response: summary is missing or invalid", viewModel.errorMessage.value)
    }

    @Test
    fun testConfirmProposedRule_savesToDatabaseAndClearsProposal() = runTest {
        // Set up a proposed rule
        viewModel.handleSend("Mute WhatsApp")
        testScheduler.advanceUntilIdle()
        val initialMessagesSize = viewModel.mockMessages.size

        // Confirm it
        viewModel.confirmProposedRule()
        testScheduler.advanceUntilIdle()

        // Rule inserted in database
        assertEquals(1, ruleRepository.insertedRules.size)
        val saved = ruleRepository.insertedRules.first()
        assertEquals("Mute WhatsApp", saved.name)
        assertEquals("com.whatsapp", saved.appPackage)
        assertEquals("Whatsapp", saved.appDisplayName)
        assertEquals(42, saved.priority)
        assertTrue(saved.enabled)

        // Success bubble added to chat log
        assertEquals(initialMessagesSize + 1, viewModel.mockMessages.size)
        assertEquals("Rule created successfully", viewModel.mockMessages.last())

        // Proposed rule cleared
        assertNull(viewModel.proposedRule.value)
    }

    @Test
    fun testCancelProposedRule_clearsProposal() = runTest {
        viewModel.handleSend("Mute WhatsApp")
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.proposedRule.value != null)

        viewModel.cancelProposedRule()
        assertNull(viewModel.proposedRule.value)
    }
}
