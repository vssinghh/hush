## 2026-06-20T04:17:48Z

You are the E2E Test Suite Designer. Your task is to design the opaque-box E2E test suite for the Hush Android app.
Specifically:
1. Read ORIGINAL_REQUEST.md, PROJECT.md, and TEST_INFRA.md in the project root, as well as implementation_plan.md at /Users/vipinsingh/.gemini/antigravity/brain/254de90a-80da-4745-a4fc-ba492deac66b/implementation_plan.md.
2. Outline the structure of the instrumented Android tests that should live under app/src/androidTest/java/com/hush/app/.
3. Detail all the required test cases for:
   - Tier 1: Feature Coverage (5 per feature = 20 tests)
   - Tier 2: Boundary & Edge Cases (5 per feature = 20 tests)
   - Tier 3: Cross-Feature Combinations (pairwise interactions = 6 tests)
   - Tier 4: Real-World Scenarios (5 tests)
4. Design the mock/stub strategy for the on-device Gemini Nano AI and SpeechRecognizer so that the tests can run reliably on an emulator or standard device without depending on actual hardware AI components.
5. Create a detailed test infrastructure plan including how Hilt testing, Room DB verification, and the Compose UI tests will compile and run.
6. Write your findings to a file named `analysis.md` in your working directory /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/teamwork_preview_explorer_e2e_1/.
7. Send a message to the caller (main agent / E2E Testing Orchestrator) when done with the path to the analysis file.
