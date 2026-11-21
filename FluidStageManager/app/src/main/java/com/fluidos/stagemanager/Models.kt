package com.fluidos.stagemanager

import android.content.Context
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val lastUsedAt: Long = 0L
)

data class Workspace(
    val id: String,
    val name: String,
    val packages: List<String>
)

object WorkspaceStore {
    private const val PREFS = "fluid_stage_manager"
    private const val WORKSPACES = "workspaces"
    private const val FAVORITES = "favorites"
    private const val ACTIVE_WORKSPACE = "active_workspace"
    private const val AUTO_START = "auto_start"
    private const val DOCK_X = "dock_x_dp"
    private const val DOCK_Y = "dock_y_dp"
    private const val PANEL_X = "panel_x_dp"
    private const val PANEL_Y = "panel_y_dp"
    private const val FREEFORM_LAUNCH = "freeform_launch"

    fun loadWorkspaces(context: Context): List<Workspace> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(WORKSPACES, null)
        if (raw.isNullOrBlank()) return listOf(Workspace("main", "Mi espacio", emptyList()))

        return try {
            val array = JSONArray(raw)
            val result = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() }
                        ?: UUID.randomUUID().toString()
                    val name = item.optString("name", "Espacio").trim().take(28)
                        .ifBlank { "Espacio ${i + 1}" }
                    val apps = item.optJSONArray("apps") ?: JSONArray()
                    val packages = buildList {
                        for (j in 0 until apps.length()) {
                            val pkg = apps.optString(j).trim()
                            if (pkg.isNotBlank()) add(pkg)
                        }
                    }.distinct()
                    add(Workspace(id, name, packages))
                }
            }.distinctBy { it.id }.take(12)
            result.ifEmpty { listOf(Workspace("main", "Mi espacio", emptyList())) }
        } catch (_: Exception) {
            listOf(Workspace("main", "Mi espacio", emptyList()))
        }
    }

    fun saveWorkspaces(context: Context, workspaces: List<Workspace>) {
        val safe = workspaces.distinctBy { it.id }.take(12)
        val array = JSONArray()
        safe.forEach { workspace ->
            val apps = JSONArray()
            workspace.packages.filter { it.isNotBlank() }.distinct().forEach { pkg -> apps.put(pkg) }
            array.put(
                JSONObject()
                    .put("id", workspace.id)
                    .put("name", workspace.name.trim().take(28).ifBlank { "Espacio" })
                    .put("apps", apps)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(WORKSPACES, array.toString())
            .apply()
    }

    fun loadFavorites(context: Context): Set<String> = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun saveFavorites(context: Context, favorites: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(FAVORITES, favorites.filter { it.isNotBlank() }.toSet())
            .apply()
    }

    fun activeWorkspaceId(context: Context): String = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(ACTIVE_WORKSPACE, "main") ?: "main"

    fun setActiveWorkspaceId(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACTIVE_WORKSPACE, id).apply()
    }

    fun isAutoStartEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(AUTO_START, false)

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(AUTO_START, enabled).apply()
    }


    fun isFreeformLaunchEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(FREEFORM_LAUNCH, false)

    fun setFreeformLaunchEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(FREEFORM_LAUNCH, enabled).apply()
    }

    fun dockPosition(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(DOCK_X, 8) to prefs.getInt(DOCK_Y, 110)
    }

    fun saveDockPosition(context: Context, xDp: Int, yDp: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(DOCK_X, xDp.coerceAtLeast(0))
            .putInt(DOCK_Y, yDp.coerceAtLeast(0))
            .apply()
    }

    fun panelPosition(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(PANEL_X, 12) to prefs.getInt(PANEL_Y, 24)
    }

    fun savePanelPosition(context: Context, xDp: Int, yDp: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(PANEL_X, xDp.coerceAtLeast(0))
            .putInt(PANEL_Y, yDp.coerceAtLeast(0))
            .apply()
    }
}
