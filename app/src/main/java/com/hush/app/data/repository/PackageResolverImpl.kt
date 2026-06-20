package com.hush.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.hush.app.domain.repository.AppInfo
import com.hush.app.domain.repository.PackageResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageResolverImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PackageResolver {
    private val packageManager: PackageManager = context.packageManager

    override fun getInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        return resolveInfos.map { resolveInfo ->
            val displayName = resolveInfo.loadLabel(packageManager).toString()
            val packageName = resolveInfo.activityInfo.packageName
            AppInfo(displayName, packageName)
        }
    }

    override fun resolvePackage(appName: String): String? {
        val normalized = appName.trim().lowercase()
        val apps = getInstalledApps()
        // Exact Match
        apps.find { it.displayName.lowercase() == normalized }?.let { return it.packageName }
        // Substring Match
        apps.find { it.displayName.lowercase().contains(normalized) }?.let { return it.packageName }
        return null
    }

    override fun isInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
