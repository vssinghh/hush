package com.hush.app.mock

import com.hush.app.domain.repository.AppInfo
import com.hush.app.domain.repository.PackageResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePackageResolver @Inject constructor() : PackageResolver {
    private val installedApps = mutableMapOf<String, String>()

    fun setInstalledApps(apps: Map<String, String>) {
        installedApps.clear()
        installedApps.putAll(apps)
    }

    override fun getInstalledApps(): List<AppInfo> {
        return installedApps.map { AppInfo(it.key, it.value) }
    }

    override fun resolvePackage(appName: String): String? {
        val normalized = appName.trim().lowercase()
        return installedApps[normalized] ?: installedApps.entries.find { 
            it.key.contains(normalized) 
        }?.value
    }

    override fun isInstalled(packageName: String): Boolean {
        return installedApps.values.contains(packageName)
    }
}
