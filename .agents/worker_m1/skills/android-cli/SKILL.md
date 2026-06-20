---
name: android-cli
description: Orchestrates Android development tasks including project creation, deployment, SDK management, and environment diagnostics using the `android` command-line tool.
---
# Android CLI Specialist

This skill provides instructions for using the `android` CLI tool. The tool includes various commands for creating projects, running applications, interacting with devices, and managing the CLI environment.

## Installation

If the android tool is not in the path install it. To install run the following command:
Linux: curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
Mac: curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh | bash
Windows: curl.exe -fsSL https://dl.google.com/android/cli/latest/windows_x86_64/install.cmd -o "%TEMP%\i.cmd" && "%TEMP%\i.cmd"

## SDK management
To manage the installation of Android SDKs and tools, use the `sdk` command. For example:

- `android sdk install <package>[@<version>]...`: Install specific packages. Multiple packages can be specified, separated by spaces. `<version>` defaults to latest. For example: `android sdk install platforms/android-30@2 platforms/android-34`
- `android sdk update [<pkg-name>]`: Update a specific package or all packages to the latest version.
- `android sdk remove <pkg-name>`: Remove a package from the local SDK.
- `android sdk list --all`: List installed and available SDK packages.
