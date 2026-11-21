package com.fluidos.stagemanager

import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Process

object AppCatalog {
    fun hasUsageAccess(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }

    fun loadAll(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        return resolved
            .distinctBy { it.activityInfo?.packageName }
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                try {
                    AppEntry(
                        packageName = packageName,
                        label = info.loadLabel(pm)?.toString()?.trim()?.ifBlank { packageName }
                            ?: packageName,
                        icon = info.loadIcon(pm)
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    fun loadRecent(context: Context, allApps: List<AppEntry>, limit: Int = 30): List<AppEntry> {
        if (!hasUsageAccess(context)) return emptyList()
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 7L * 24L * 60L * 60L * 1000L
            val events = manager.queryEvents(start, end) ?: return emptyList()
            val lastForeground = LinkedHashMap<String, Long>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val resumed = if (Build.VERSION.SDK_INT >= 29) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (resumed && event.packageName != context.packageName) {
                    lastForeground[event.packageName] = event.timeStamp
                }
            }
            val byPackage = allApps.associateBy { it.packageName }
            lastForeground.entries
                .sortedByDescending { it.value }
                .mapNotNull { (pkg, time) -> byPackage[pkg]?.copy(lastUsedAt = time) }
                .take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasFreeformSupport(context: Context): Boolean = try {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
    } catch (_: Exception) {
        false
    }

    fun launch(context: Context, packageName: String, tryFreeform: Boolean = false): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            // Always try public launch bounds when the user opts in. On a device without
            // freeform/PiP support Android ignores the bounds and opens the app normally.
            if (tryFreeform && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val metrics = context.resources.displayMetrics
                val width = metrics.widthPixels
                val height = metrics.heightPixels
                val left = (width * 0.16f).toInt()
                val top = (height * 0.10f).toInt()
                val right = (width * 0.86f).toInt().coerceAtMost(width - 1)
                val bottom = (height * 0.88f).toInt().coerceAtMost(height - 1)
                val bounds = Rect(left, top, right.coerceAtLeast(left + 1), bottom.coerceAtLeast(top + 1))
                val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
                try {
                    context.startActivity(intent, options.toBundle())
                } catch (_: Exception) {
                    // Freeform launch can be rejected by an OEM or by a non-resizable target app.
                    context.startActivity(intent)
                }
            } else {
                context.startActivity(intent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
