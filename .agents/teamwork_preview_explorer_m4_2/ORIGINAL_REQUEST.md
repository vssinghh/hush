## 2026-06-20T17:16:28Z

Focus on:
1. Identifying the exact APIs and classes needed for the Google Play Services Generative AI SDK in Kotlin. Specifically, how to build/get the GenerativeModel, how to pass system instructions and options (temperature, JSON constraint), and how to generate responses.
2. In `AIEngineImpl.kt`, implement the standard AICore checking API. Does it return a task, a flow, or suspend? What exceptions might be thrown and how should we handle them?
3. Designing the Prompt template format. Ensure it prepends list of installed applications on the device (retrieved via package manager) to help the model resolve packages correctly.
4. Write your analysis report to /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/teamwork_preview_explorer_m4_2/analysis.md.
