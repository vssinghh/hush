# Scope: Milestone 4 (AI Integration)

## Architecture
Milestone 4 integrates on-device natural language command parsing using Gemini Nano via Google Play Services Generative AI (AICore).
The architecture spans these layers:
- **Domain**: `ParseCommandUseCase` which orchestrates AI command parsing, package resolution, and validation.
- **Data**: `AIEngineImpl` in `com.hush.app.data.repository` wrapping AICore APIs, and package resolution logic to map user-spoken app names to package names.
- **DI**: `AIModule` configured to inject the real/fake `AIEngine` bindings.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | AICore & SDK Setup | Add Generative AI SDK to Gradle dependencies, implement `isAvailable()` checking, handle fallback | none | PLANNED |
| 2 | Prompt & Model Configuration | Define prompt system instructions, dynamic app mappings (e.g. "whatsapp" -> `com.whatsapp`), model temperature/JSON constraints | M1 | PLANNED |
| 3 | ParseCommandUseCase & Validation | Implement `ParseCommandUseCase` processing user prompt, parsing model JSON output into `ParsedCommand` structure, validating required fields | M2 | PLANNED |
| 4 | Package Resolver | Build dynamic installed apps mapping resolver to match spoken/typed names with real package names | M3 | PLANNED |
| 5 | Test & Audit Verification | Verify app builds, execute AI-related unit tests, pass E2E tests, and secure Forensic Auditor CLEAN verdict | M4 | PLANNED |

## Interface Contracts

### AI Command Prompt Schema
The model prompt should instruct Gemini Nano to produce a strict JSON output matching the following contract:
```json
{
  "action": "block" | "allow" | "mute",
  "app": "package.name" | null,
  "matchField": "title" | "text" | "sender" | "any",
  "matchType": "contains" | "regex" | "exact",
  "matchPattern": "string" | null,
  "isInverted": boolean,
  "timeStart": "HH:mm" | null,
  "timeEnd": "HH:mm" | null,
  "summary": "human-readable description"
}
```

### Prompt Input Mapping
The prompt template must dynamically prepend the list of installed applications on the user's device (retrieved via package manager) to help the model accurately resolve package names.

### Error Handling & Malformed Responses
- If the model output is not valid JSON or lacks mandatory fields (e.g., `action` or `summary`), a structured parsing error should be thrown, causing the UI to display a user-facing error bubble.
- If the package name in the model output refers to an uninstalled application package name, a warning flag must be set so the UI displays `ai_rule_warning_uninstalled`.
