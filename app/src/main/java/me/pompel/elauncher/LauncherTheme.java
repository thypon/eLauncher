package me.pompel.elauncher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Single source of the launcher's dark/light theming, shared by every screen:
 * the dark_mode preference decides, defaulting to the system night mode while
 * the preference was never set.
 */
public final class LauncherTheme {

    public static final String DARK_MODE_PREF = "dark_mode_preference";

    private LauncherTheme() {
    }

    /** True when the launcher must render dark: the dark_mode preference when
     * set, the system night mode otherwise. */
    public static boolean isDarkMode(SharedPreferences prefs, Resources res) {
        boolean systemDark = (res.getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return prefs.getBoolean(DARK_MODE_PREF, systemDark);
    }

    /** Applies the launcher theme (light or dark) to an activity. Call before
     * setContentView, exactly like MainActivity does. */
    public static void applyTheme(Activity activity, boolean dark) {
        AppCompatDelegate.setDefaultNightMode(dark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        activity.setTheme(dark ? R.style.AppTheme_InvertedDark : R.style.AppTheme);
    }

    /** Tints status and navigation bars with the themed background color. */
    public static void applySystemBars(Activity activity) {
        int background = getColorFromAttr(activity, androidx.appcompat.R.attr.background);
        activity.getWindow().setStatusBarColor(background);
        activity.getWindow().setNavigationBarColor(background);
    }

    /** Resolves a color attribute from the context's themed style. */
    public static int getColorFromAttr(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        int color;
        try (TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[]{attr})) {
            color = a.getColor(0, 0);
        }
        return color;
    }
}
