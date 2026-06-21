package com.hush.app.data.repository

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.AIStatus
import com.hush.app.domain.repository.PackageResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageResolver: PackageResolver
) : AIEngine {

    @Volatile
    private var isAvailableCached: Boolean = false

    private val _status = MutableStateFlow(AIStatus.CHECKING)
    override val status: StateFlow<AIStatus> = _status.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    override val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val model: GenerativeModel by lazy {
        Generation.getClient()
    }

    init {
        CoroutineScope(Dispatchers.Default).launch {
            checkAvailability()
        }
    }

    private fun isDebugBuild(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val buildType = method.invoke(null, "ro.build.type", "user") as String
            buildType == "userdebug" || buildType == "eng"
        } catch (_: Exception) {
            false
        }
    }

    private fun isBootloaderUnlocked(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val bootState = method.invoke(null, "ro.boot.verifiedbootstate", "green") as String
            bootState != "green"
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun checkAvailability() {
        _status.value = AIStatus.CHECKING
        _errorMessage.value = null

        // Check bootloader — ML Kit GenAI won't work on unlocked bootloaders
        // Skip this check on userdebug/eng builds where AICore works despite unlocked bootloader
        if (isBootloaderUnlocked() && !isDebugBuild()) {
            isAvailableCached = false
            _errorMessage.value = "On-device AI requires a locked bootloader. Your device's bootloader is unlocked, which prevents Gemini Nano from running. Please re-lock your bootloader to use this feature."
            _status.value = AIStatus.NOT_SUPPORTED
            return
        }

        try {
            val statusCode = model.checkStatus()
            when (statusCode) {
                FeatureStatus.AVAILABLE -> {
                    isAvailableCached = true
                    _status.value = AIStatus.READY
                }
                FeatureStatus.DOWNLOADABLE -> {
                    isAvailableCached = false
                    _status.value = AIStatus.NOT_DOWNLOADED
                }
                FeatureStatus.DOWNLOADING -> {
                    isAvailableCached = false
                    _status.value = AIStatus.DOWNLOADING
                }
                FeatureStatus.UNAVAILABLE -> {
                    isAvailableCached = false
                    _status.value = AIStatus.NOT_SUPPORTED
                }
                else -> {
                    isAvailableCached = false
                    _status.value = AIStatus.NOT_DOWNLOADED
                }
            }
        } catch (e: Exception) {
            logError("AIEngineImpl", "Model availability check failed", e)
            isAvailableCached = false
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("606") || errorMsg.contains("FEATURE_NOT_FOUND")) {
                _errorMessage.value = "On-device AI is not yet available. Please ensure Android AICore is up to date and try restarting your device."
                _status.value = AIStatus.NOT_SUPPORTED
            } else {
                val hasAICore = try {
                    context.packageManager.getPackageInfo("com.google.android.aicore", 0)
                    true
                } catch (_: Exception) {
                    false
                }
                _errorMessage.value = if (hasAICore) "AI setup failed. Try updating AICore from the Play Store." else null
                _status.value = if (hasAICore) AIStatus.ERROR else AIStatus.NOT_SUPPORTED
            }
        }
    }

    override fun isAvailable(): Boolean = isAvailableCached

    override suspend fun recheckAvailability() {
        checkAvailability()
    }

    override suspend fun downloadModel() {
        _status.value = AIStatus.DOWNLOADING
        _downloadProgress.value = 0
        try {
            model.download().collect { downloadStatus ->
                when (downloadStatus) {
                    is DownloadStatus.DownloadStarted -> {
                        _downloadProgress.value = 1
                    }
                    is DownloadStatus.DownloadProgress -> {
                        // totalBytesDownloaded is cumulative; we don't know total size
                        // Use a value between 1-99 to show progress is happening
                        val downloaded = downloadStatus.totalBytesDownloaded
                        _downloadProgress.value = if (downloaded > 0) 50 else 1 // indeterminate-ish
                    }
                    is DownloadStatus.DownloadCompleted -> {
                        _downloadProgress.value = 100
                        isAvailableCached = true
                        _status.value = AIStatus.READY
                    }
                    is DownloadStatus.DownloadFailed -> {
                        logError("AIEngineImpl", "Download failed", Exception("Download failed"))
                        _downloadProgress.value = 0
                        _status.value = AIStatus.ERROR
                    }
                }
            }
        } catch (e: Exception) {
            logError("AIEngineImpl", "Model download/preparation failed", e)
            _downloadProgress.value = 0
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("606") || errorMsg.contains("FEATURE_NOT_FOUND")) {
                if (isBootloaderUnlocked()) {
                    _errorMessage.value = "On-device AI requires a locked bootloader. Your device's bootloader is unlocked, which prevents Gemini Nano from running."
                } else {
                    _errorMessage.value = "On-device AI is not yet available on this device. Please update AICore and restart your device."
                }
                _status.value = AIStatus.NOT_SUPPORTED
            } else {
                _errorMessage.value = "Download failed: ${e.message}"
                _status.value = AIStatus.ERROR
            }
        }
    }

    override suspend fun parseCommand(prompt: String): ParsedCommand {
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be blank")
        }

        if (!isAvailableCached) {
            // One more check before failing
            try {
                val statusCode = model.checkStatus()
                if (statusCode == FeatureStatus.AVAILABLE) {
                    isAvailableCached = true
                    _status.value = AIStatus.READY
                }
            } catch (_: Exception) { }

            if (!isAvailableCached) {
                throw IllegalStateException("AI model unavailable — please set up on-device AI first")
            }
        }

        val appListString = getInstalledAppsString()

        val fullPrompt = """
            ${PromptTemplates.SYSTEM_INSTRUCTIONS}
            
            INSTALLED APPLICATIONS ON DEVICE:
            $appListString
            
            USER COMMAND:
            "$prompt"
        """.trimIndent()

        val responseText = try {
            val response = model.generateContent(fullPrompt)
            response.candidates.firstOrNull()?.text
                ?: throw IllegalStateException("Model returned empty response")
        } catch (e: Exception) {
            logError("AIEngineImpl", "AI engine failure during content generation", e)
            throw IllegalStateException("AI engine failure: ${e.message}", e)
        }

        try {
            val cleanedJsonString = cleanJsonText(responseText)
            val json = JSONObject(cleanedJsonString)

            val action = when (json.optString("action", "allow").lowercase()) {
                "allow" -> RuleAction.ALLOW
                "block" -> RuleAction.BLOCK
                "mute" -> RuleAction.MUTE
                else -> RuleAction.ALLOW
            }

            val matchField = when (json.optString("matchField", "any").lowercase()) {
                "title" -> MatchField.TITLE
                "text" -> MatchField.TEXT
                "sender" -> MatchField.SENDER
                "any" -> MatchField.ANY
                else -> MatchField.ANY
            }

            val matchType = when (json.optString("matchType", "contains").lowercase()) {
                "contains" -> MatchType.CONTAINS
                "regex" -> MatchType.REGEX
                "exact" -> MatchType.EXACT
                else -> MatchType.CONTAINS
            }

            val app = if (json.isNull("app")) null else json.getString("app")
            val matchPattern = if (json.isNull("matchPattern")) null else json.getString("matchPattern")
            val isInverted = json.optBoolean("isInverted", false)

            val timeStartStr = if (json.isNull("timeStart")) null else json.getString("timeStart")
            val timeEndStr = if (json.isNull("timeEnd")) null else json.getString("timeEnd")

            val timeStart = timeStartStr?.let { parseTimeRobust(it) }
            val timeEnd = timeEndStr?.let { parseTimeRobust(it) }
            val summary = json.optString("summary", "")

            return ParsedCommand(
                action = action,
                app = app,
                matchField = matchField,
                matchType = matchType,
                matchPattern = matchPattern,
                isInverted = isInverted,
                timeStart = timeStart,
                timeEnd = timeEnd,
                summary = summary
            )
        } catch (e: IllegalArgumentException) {
            logError("AIEngineImpl", "Failed to parse command due to invalid argument", e)
            throw e
        } catch (e: Exception) {
            logError("AIEngineImpl", "Malformed JSON from AI model", e)
            throw IllegalArgumentException("Malformed JSON from AI model: ${e.message}", e)
        }
    }

    private fun parseTimeRobust(timeStr: String): LocalTime? {
        val formats = listOf("HH:mm", "H:mm", "HH:mm:ss", "h:mm a", "hh:mm a")
        for (pattern in formats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                return LocalTime.parse(timeStr.trim(), formatter)
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    private fun cleanJsonText(responseText: String): String {
        val startIndex = responseText.indexOf('{')
        val endIndex = responseText.lastIndexOf('}')
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            throw IllegalArgumentException("Model response did not contain a valid JSON object")
        }
        return responseText.substring(startIndex, endIndex + 1)
    }

    private fun getInstalledAppsString(): String {
        return packageResolver.getInstalledApps().joinToString("\n") { app ->
            "- App Name: \"${app.displayName}\", Package Name: \"${app.packageName}\""
        }
    }

    private fun logError(tag: String, msg: String, tr: Throwable) {
        try {
            Log.e(tag, msg, tr)
        } catch (e: RuntimeException) {
            println("ERROR: [$tag] $msg - ${tr.message}")
        }
    }
}
