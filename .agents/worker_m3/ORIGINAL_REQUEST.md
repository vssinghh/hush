## 2026-06-20T16:53:27Z
You are a Worker. Your task is to implement the following changes in the Hush Android app codebase to fix bugs, solve race conditions, correct test assertions, and improve test coverage for Milestone 3 (Rule Engine).

Please implement the following changes:

1. Correct the dialog assertion in E2E tests:
   In `app/src/androidTest/java/com/hush/app/e2e/RuleManagementHistoryE2ETest.kt`:
   Locate line 193:
   `composeRule.onNodeWithText("com.whatsapp").assertIsDisplayed()`
   Change it to:
   `composeRule.onNodeWithText("Package: com.whatsapp").assertIsDisplayed()`

2. Enable Room Database SQLite foreign keys:
   In `app/src/main/java/com/hush/app/di/DatabaseModule.kt`:
   In `provideDatabase()`, add a Room callback to enable foreign keys:
   ```kotlin
   import androidx.room.RoomDatabase
   import androidx.sqlite.db.SupportSQLiteDatabase
   ```
   Modify Room builder call to include `.addCallback`:
   ```kotlin
   return Room.databaseBuilder(
       context,
       HushDatabase::class.java,
       HushDatabase.DATABASE_NAME
   )
   .addCallback(object : RoomDatabase.Callback() {
       override fun onOpen(db: SupportSQLiteDatabase) {
           super.onOpen(db)
           db.execSQL("PRAGMA foreign_keys = ON;")
       }
   })
   .fallbackToDestructiveMigration()
   .build()
   ```

3. Enable correct Rule Priority ordering when creating rules in Chat:
   In `app/src/main/java/com/hush/app/ui/screens/chat/ChatScreen.kt`:
   Inside the click handler for confirming rules (around lines 279-301), replace the hardcoded `priority = 0` with a call to fetch the next priority from the repository:
   ```kotlin
   val priority = ruleRepository.getNextPriority()
   ```
   Pass this `priority` value to the created `Rule` object.

4. Enhance RulesScreen UI/UX:
   In `app/src/main/java/com/hush/app/ui/screens/rules/RulesScreen.kt`:
   - Import `androidx.compose.material.icons.filled.Delete`.
   - Update the display text in the rule card column (around line 104) to display the app's display name or package name:
     `Text(rule.appDisplayName ?: rule.appPackage ?: "All Apps", style = MaterialTheme.typography.bodySmall)`
   - Modify the `SwipeToDismissBox` configuration:
     - Set `enableDismissFromStartToEnd = false` and `enableDismissFromEndToStart = true`.
     - In the `backgroundContent` box, apply shape clipping to match the Card's shape, add horizontal padding, align content to `Alignment.CenterEnd`, and display the delete icon:
       ```kotlin
       Box(
           modifier = Modifier
               .fillMaxSize()
               .clip(CardDefaults.shape)
               .background(Color.Red.copy(alpha = 0.8f))
               .padding(horizontal = 16.dp),
           contentAlignment = Alignment.CenterEnd
       ) {
           Icon(
               imageVector = Icons.Default.Delete,
               contentDescription = "Delete Rule",
               tint = Color.White
           )
       }
       ```

5. Serialize rapid rule toggles in RulesViewModel:
   In `app/src/main/java/com/hush/app/ui/screens/rules/RulesViewModel.kt`:
   - Import `kotlinx.coroutines.sync.Mutex` and `kotlinx.coroutines.sync.withLock`.
   - Add a private field: `private val toggleMutex = Mutex()`.
   - In `toggleRuleEnabled(rule: Rule)`, use `toggleMutex.withLock` and load the latest state of the rule before updating:
     ```kotlin
     fun toggleRuleEnabled(rule: Rule) {
         viewModelScope.launch {
             toggleMutex.withLock {
                 val latestRule = ruleRepository.getRuleById(rule.id)
                 if (latestRule != null) {
                     ruleRepository.updateRule(latestRule.copy(enabled = !latestRule.enabled))
                 }
             }
         }
     }
     ```

6. Expand local unit tests in `app/src/test/java/com/hush/app/domain/usecase/EvaluateNotificationUseCaseTest.kt`:
   Add test cases verifying:
   - Inversion (`isInverted = true`) matching and non-matching.
   - App package matching (rules that match specific apps should not trigger for other packages).
   - Logging logic in the history repository (ensure historyRepository.insertLog is only called when a rule matches).
   - Regex matching.
   - Priority matching (multiple rules matching same notification, verifying lower priority runs first).

Your working directory is `/Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/worker_m3/`.
Please create your BRIEFING.md and progress.md in that directory.
After implementing these changes, run the build and test commands (both unit tests `./gradlew test` and instrumented tests `./gradlew connectedAndroidTest` or relevant test targets) to verify all tests compile and pass successfully.
