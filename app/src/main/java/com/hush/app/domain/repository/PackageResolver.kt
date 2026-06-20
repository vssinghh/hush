package com.hush.app.domain.repository

interface PackageResolver {
    fun getInstalledApps(): List<AppInfo>
    fun resolvePackage(appName: String): String?
    fun isInstalled(packageName: String): Boolean
}

data class AppInfo(
    val displayName: String,
    val packageName: String
)
