import os
import re

# Base paths
repo_root = "/Users/vipinsingh/Documents/Antigravity/open source/hush"
readme_path = os.path.join(repo_root, "README.md")
app_package_root = os.path.join(repo_root, "app/src/main/java/com/hush/app")

# Read README.md
with open(readme_path, "r") as f:
    readme_content = f.read()

# Extract the directory tree structure block
match = re.search(r"```\s*\ncom\.hush\.app/\n(.*?)\n```", readme_content, re.DOTALL)
if not match:
    print("[ERROR] Could not find the com.hush.app/ package structure block in README.md")
    exit(1)

tree_block = match.group(1)

# Let's parse all file and directory paths listed in the tree
lines = tree_block.splitlines()

current_path_parts = []
files_to_check = []
dirs_to_check = []

files_documented_in_readme = [
    # di
    "di/AIModule.kt",
    "di/DatabaseModule.kt",
    "di/PermissionModule.kt",
    "di/PreferencesModule.kt",
    "di/RepositoryModule.kt",
    # domain
    "domain/model/Rule.kt",
    "domain/model/NotificationEvent.kt",
    "domain/model/ParsedCommand.kt",
    "domain/permission/PermissionManager.kt",
    "domain/repository/AIEngine.kt",
    "domain/repository/HistoryRepository.kt",
    "domain/repository/PackageResolver.kt",
    "domain/repository/RuleRepository.kt",
    "domain/repository/SpeechRecognizerWrapper.kt",
    "domain/repository/SpeechState.kt",
    "domain/usecase/EvaluateNotificationUseCase.kt",
    "domain/usecase/ParseCommandUseCase.kt",
    # data
    "data/db/HushDatabase.kt",
    "data/db/RoomConverters.kt",
    "data/db/dao/NotificationLogDao.kt",
    "data/db/dao/RuleDao.kt",
    "data/db/entity/NotificationLogEntity.kt",
    "data/db/entity/RuleEntity.kt",
    "data/pref/OnboardingPrefs.kt",
    "data/repository/AIEngineImpl.kt",
    "data/repository/HistoryRepositoryImpl.kt",
    "data/repository/PackageResolverImpl.kt",
    "data/repository/PermissionManagerImpl.kt",
    "data/repository/PromptTemplates.kt",
    "data/repository/RuleRepositoryImpl.kt",
    "data/repository/SpeechRecognizerWrapperImpl.kt",
    # service
    "service/HushNotificationListener.kt",
    # ui
    "ui/navigation/HushNavigation.kt",
    "ui/navigation/ScreenRoute.kt",
    "ui/screens/MainScreen.kt",
    "ui/screens/chat/ChatScreen.kt",
    "ui/screens/chat/ChatViewModel.kt",
    "ui/screens/history/HistoryScreen.kt",
    "ui/screens/history/HistoryViewModel.kt",
    "ui/screens/onboarding/OnboardingScreen.kt",
    "ui/screens/onboarding/OnboardingViewModel.kt",
    "ui/screens/rules/RulesScreen.kt",
    "ui/screens/rules/RulesViewModel.kt",
    "ui/screens/settings/SettingsScreen.kt",
    "ui/screens/settings/SettingsViewModel.kt",
    "ui/theme/Color.kt",
    "ui/theme/Theme.kt",
    "ui/theme/Type.kt"
]

print(f"Verifying {len(files_documented_in_readme)} documented paths under {app_package_root}...")

missing_files = []
for file_rel in files_documented_in_readme:
    full_path = os.path.join(app_package_root, file_rel)
    exists = os.path.isfile(full_path)
    status = "OK" if exists else "MISSING"
    print(f"[{status}] {file_rel}")
    if not exists:
        missing_files.append(file_rel)

print("\n--- Summary ---")
if missing_files:
    print(f"FAIL: {len(missing_files)} missing files:")
    for f in missing_files:
        print(f"  - {f}")
else:
    print("PASS: All documented files exist in the codebase!")
