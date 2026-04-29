package com.goldsmith.billing.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.stringPreferencesKey
import com.goldsmith.billing.R

/**
 * Manages the app icon selection between:
 * 1. DEFAULT  — Abu Star Diamonds gold logo PNG (shipped with app)
 * 2. DIAMOND  — Generic diamond vector icon
 * 3. CUSTOM   — User-uploaded image from gallery
 *
 * On Android, switching launcher icons is done via Activity aliases.
 * This class handles the alias switching and persisting user preference.
 */
object AppIconManager {

    const val ICON_DEFAULT = "default"   // Abu Star Diamonds logo
    const val ICON_DIAMOND = "diamond"   // Generic diamond icon
    const val ICON_CUSTOM  = "custom"    // User uploaded

    val PREF_KEY = stringPreferencesKey("selected_app_icon")

    /**
     * Returns the drawable resource ID for the currently selected icon
     * to display inside the app (e.g. in top bar, splash, settings preview)
     */
    fun getIconDrawableRes(iconType: String): Int = when (iconType) {
        ICON_DIAMOND -> R.drawable.ic_splash_logo
        else -> R.drawable.abu_star_logo   // default = Abu Star logo
    }

    /**
     * Switch the launcher icon via Activity aliases.
     * Requires three <activity-alias> entries in AndroidManifest.xml:
     *  - .MainActivity.Default  (Abu Star logo)
     *  - .MainActivity.Diamond  (diamond vector)
     *  - .MainActivity.Custom   (same as default, user changes profile photo)
     */
    fun switchIcon(context: Context, iconType: String) {
        val pkg = context.packageName
        val aliases = mapOf(
            ICON_DEFAULT to "$pkg.MainActivity.Default",
            ICON_DIAMOND to "$pkg.MainActivity.Diamond",
            ICON_CUSTOM  to "$pkg.MainActivity.Custom"
        )

        aliases.forEach { (type, alias) ->
            val state = if (type == iconType)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            try {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(pkg, alias),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {
                // Alias may not exist in manifest - gracefully skip
            }
        }
    }
}
