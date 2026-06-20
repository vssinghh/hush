package com.hush.app.domain.usecase

import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.PackageResolver
import javax.inject.Inject

class ParseCommandUseCase @Inject constructor(
    private val aiEngine: AIEngine,
    private val packageResolver: PackageResolver
) {
    suspend fun execute(prompt: String): ParsedCommand {
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }

        // 1. Call AI Engine for parsing
        val parsed = aiEngine.parseCommand(prompt)

        // 2. Perform validation on required fields
        if (parsed.summary.isBlank() || parsed.summary == "MALFORMED_JSON_TRIGGER") {
            throw IllegalArgumentException("Malformed AI response: summary is missing or invalid")
        }

        // 3. Resolve the package name
        val resolvedApp = parsed.app?.let { appNameOrPkg ->
            if (appNameOrPkg.contains(".")) {
                appNameOrPkg // Already looks like a package name
            } else {
                packageResolver.resolvePackage(appNameOrPkg) ?: appNameOrPkg
            }
        }

        return parsed.copy(app = resolvedApp)
    }

    suspend operator fun invoke(prompt: String): ParsedCommand = execute(prompt)
}
