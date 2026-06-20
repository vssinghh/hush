package com.hush.app.domain.usecase

import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.AppInfo
import com.hush.app.domain.repository.PackageResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalFakeAIEngine : AIEngine {
    var available = true
    var resultCommand: ParsedCommand? = null
    var parseError: Throwable? = null

    override fun isAvailable(): Boolean = available

    override suspend fun parseCommand(prompt: String): ParsedCommand {
        parseError?.let { throw it }
        return resultCommand ?: ParsedCommand(
            action = RuleAction.ALLOW,
            app = null,
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Fallback: $prompt"
        )
    }
}

class LocalFakePackageResolver : PackageResolver {
    val apps = mutableMapOf<String, String>()

    override fun getInstalledApps(): List<AppInfo> = apps.map { AppInfo(it.key, it.value) }

    override fun resolvePackage(appName: String): String? {
        val normalized = appName.trim().lowercase()
        return apps[normalized] ?: apps.entries.find { it.key.contains(normalized) }?.value
    }

    override fun isInstalled(packageName: String): Boolean = apps.containsValue(packageName)
}

class ParseCommandUseCaseTest {

    private val aiEngine = LocalFakeAIEngine()
    private val packageResolver = LocalFakePackageResolver()
    private val useCase = ParseCommandUseCase(aiEngine, packageResolver)

    @Test
    fun testExecute_emptyPrompt_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                useCase.execute("")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                useCase.execute("   ")
            }
        }
    }

    @Test
    fun testExecute_validPromptAndInstalledPackage_returnsSuccess() = runBlocking {
        packageResolver.apps["whatsapp"] = "com.whatsapp"
        val expected = ParsedCommand(
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
        aiEngine.resultCommand = expected

        val result = useCase.execute("Mute WhatsApp")
        assertEquals(expected, result)
    }

    @Test
    fun testExecute_friendlyNameApp_resolvesToPackage() = runBlocking {
        packageResolver.apps["whatsapp"] = "com.whatsapp"
        val returnedByAI = ParsedCommand(
            action = RuleAction.MUTE,
            app = "WhatsApp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Mute WhatsApp"
        )
        aiEngine.resultCommand = returnedByAI

        val result = useCase.execute("Mute WhatsApp")
        assertEquals("com.whatsapp", result.app)
    }

    @Test
    fun testExecute_nullApp_returnsNullApp() = runBlocking {
        val returnedByAI = ParsedCommand(
            action = RuleAction.MUTE,
            app = null,
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = "urgent",
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Mute notifications with urgent"
        )
        aiEngine.resultCommand = returnedByAI

        val result = useCase.execute("Mute notifications with urgent")
        assertEquals(null, result.app)
    }

    @Test
    fun testExecute_unresolvedApp_returnsOriginalAppName() = runBlocking {
        val returnedByAI = ParsedCommand(
            action = RuleAction.MUTE,
            app = "NonExistentApp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Mute NonExistentApp"
        )
        aiEngine.resultCommand = returnedByAI

        val result = useCase.execute("Mute NonExistentApp")
        assertEquals("NonExistentApp", result.app)
    }


    @Test
    fun testExecute_malformedAIResponse_throwsException() {
        // malformed if summary is blank or MALFORMED_JSON_TRIGGER
        val malformed1 = ParsedCommand(
            action = RuleAction.MUTE,
            app = null,
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = ""
        )
        aiEngine.resultCommand = malformed1
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                useCase.execute("Mute WhatsApp")
            }
        }

        val malformed2 = malformed1.copy(summary = "MALFORMED_JSON_TRIGGER")
        aiEngine.resultCommand = malformed2
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                useCase.execute("Mute WhatsApp")
            }
        }
    }
}
