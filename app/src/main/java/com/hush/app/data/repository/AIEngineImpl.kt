package com.hush.app.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.generativeai.GenerationConfig
import com.google.android.gms.generativeai.GenerativeModel
import com.google.android.gms.generativeai.GenerativeModelClient
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.PackageResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    init {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val modelClient = GenerativeModelClient.getClient(context)
                isAvailableCached = modelClient.isAvailable().await()
            } catch (e: Exception) {
                logError("AIEngineImpl", "Failed to check AICore availability", e)
                isAvailableCached = false
            }
        }
    }

    override fun isAvailable(): Boolean = isAvailableCached

    override suspend fun parseCommand(prompt: String): ParsedCommand {
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be blank")
        }

        if (!isAvailableCached) {
            val dynamicAvailable = try {
                GenerativeModelClient.getClient(context).isAvailable()?.await() ?: false
            } catch (e: Exception) {
                false
            }
            isAvailableCached = dynamicAvailable
            if (!dynamicAvailable) {
                throw IllegalStateException("AICore unavailable")
            }
        }

        val appListString = getInstalledAppsString()

        val userPrompt = """
            INSTALLED APPLICATIONS ON DEVICE:
            $appListString
            
            USER COMMAND:
            "$prompt"
        """.trimIndent()

        val config = GenerationConfig.builder()
            .setTemperature(0.0f)
            .setResponseMimeType("application/json")
            .build()

        val model = GenerativeModel.Builder(context)
            .setModelName("gemini-nano")
            .setGenerationConfig(config)
            .setSystemInstruction(PromptTemplates.SYSTEM_INSTRUCTIONS)
            .build()

        val responseText = try {
            val response = model.generateContent(userPrompt).await()
            response.text ?: throw IllegalStateException("Model returned empty text")
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
