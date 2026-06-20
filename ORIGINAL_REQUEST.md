# Original User Request

## Initial Request — 2026-06-19T21:15:25-07:00

Build **Hush**, an open-source, privacy-first Android app that lets users control device notifications through natural language voice and text commands. Users speak or type commands like *"Mute WhatsApp except from Alice"* and on-device AI (Gemini Nano) parses them into structured filtering rules. The app intercepts notifications in real-time and applies these rules to allow, block, or mute them — all entirely on-device with zero network access.

Working directory: /Users/vipinsingh/Documents/Antigravity/open source/hush
Integrity mode: development

## Reference Material

A detailed architecture and feature plan is available at:
`/Users/vipinsingh/.gemini/antigravity/brain/254de90a-80da-4745-a4fc-ba492deac66b/implementation_plan.md`

Read this file thoroughly before starting. It contains the agreed-upon tech stack, architecture diagrams, data models, project structure, AI prompt design, and milestone breakdown. Follow it closely.

## Requirements

### R1. Android App Foundation
Build a Kotlin + Jetpack Compose Android app with package name `com.hush.app`. The app must:
- Target SDK 35, minimum SDK 33
- Use Material 3 / Material You theming with a premium, polished design
- Have bottom navigation with four tabs: Chat, Rules, History, Settings
- Include a first-launch onboarding flow that guides users through granting Notification Access, Microphone permission, and battery optimization exclusion
- Use Hilt for dependency injection and Room for local persistence
- Follow MVVM + Clean Architecture (UI → Domain → Data layers)

### R2. Notification Interception & Rule Engine
Implement a `NotificationListenerService` that:
- Intercepts all incoming notifications and extracts metadata (app name, package, title, text, sender if available, timestamp)
- Evaluates each notification against user-defined rules stored in a Room database
- Supports four filtering dimensions: by app, by sender/contact, by keyword (contains/regex), and by time window
- Executes one of three actions based on the first matching rule: Allow, Block (dismiss via `cancelNotification`), or Mute (silent delivery)
- Logs every intercepted notification (with the action taken and which rule matched) to a notification history table with configurable retention

### R3. Conversational AI Rule Creation
Implement a chat-style interface where users create notification rules through natural language:
- Text input field and a voice input button that uses Android's `SpeechRecognizer` for on-device speech-to-text
- On-device AI via Gemini Nano (Android AICore) parses the user's command into a structured rule (JSON with fields: action, app, matchField, matchType, matchPattern, isInverted, timeStart, timeEnd, summary)
- The app shows the parsed rule to the user and asks for confirmation before saving
- The chat maintains conversation history so users can see past commands and AI responses
- Include an app name → package name resolver that maps common names (e.g., "WhatsApp" → `com.whatsapp`) using the device's installed apps list

### R4. Rule Management & Notification History
- Rules screen: card-based list of all rules with toggle switches, swipe-to-delete, tap to view details (including the original natural language command that created it)
- History screen: scrollable log of intercepted notifications with tabs (All / Allowed / Blocked / Muted), search bar, and per-entry details showing which rule acted on it
- Settings screen: notification history retention period, about section, permission status indicators

### R5. Git Repository & Release
- Initialize a Git repository in the working directory
- Include a comprehensive README.md with: project description, features list, screenshots placeholders, build instructions, architecture overview, and MIT license
- Include a LICENSE file (MIT)
- Create an initial commit with the complete working codebase
- Set up a remote repository and push (use `gh repo create` if the `gh` CLI is available, otherwise just initialize locally)

## Acceptance Criteria

### Build & Structure
- [ ] `./gradlew assembleDebug` completes successfully with zero errors
- [ ] Project follows Clean Architecture with clear separation: `ui/`, `domain/`, `data/`, `service/`, `di/` packages
- [ ] All Hilt dependency injection is properly configured and compiles

### Notification Engine
- [ ] `NotificationListenerService` is declared in AndroidManifest.xml with correct intent filter
- [ ] Unit tests exist for rule evaluation logic covering: exact match, contains match, regex match, inverted rules, time-window rules, and priority ordering
- [ ] `./gradlew test` passes with all rule engine unit tests green

### Conversational AI
- [ ] Chat screen renders with text input, send button, and microphone button
- [ ] Gemini Nano integration code is present with proper AICore session management and structured prompts
- [ ] A fallback/error path exists for devices where AICore is unavailable

### UI Completeness
- [ ] All four navigation tabs (Chat, Rules, History, Settings) render functional screens
- [ ] Onboarding screen exists and checks for required permissions
- [ ] Material You / Material 3 theming is applied consistently across all screens
- [ ] The app looks polished and premium — not a basic/skeleton UI

### Data Persistence
- [ ] Room database schema includes tables for rules and notification history
- [ ] DAOs support full CRUD for rules and insert + query for notification logs

### Repository
- [ ] Git repository is initialized with at least one commit containing the full codebase
- [ ] README.md exists with project description, features, build instructions, and architecture overview
- [ ] MIT LICENSE file is present
