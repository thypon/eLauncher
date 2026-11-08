package me.pompel.elauncher.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.pompel.elauncher.SettingsActivity;

/**
 * Classic unit test: the settings screen must follow the system night mode
 * while the dark-mode preference was never set — Robolectric can only set the
 * activity config at class level (qualifiers="night"), so this cannot be
 * expressed as a Gherkin scenario.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "night", shadows = ShadowNoRecreateActivity.class)
public class SettingsActivitySystemDarkTest {

    @Before
    public void neutralizeDelegateLeak() {
        // AppCompatDelegate.sDefaultNightMode is static and leaks between test classes
        // in the same JVM; follow-system lets @Config(qualifiers="night") drive the theme.
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Test
    public void darkModePrefUnset_followsSystemNightMode_withoutWritingPreference() {
        Context context = ApplicationProvider.getApplicationContext();
        android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .clear()
                .putBoolean("firstLaunch", true)
                .commit();

        ActivityScenario<SettingsActivity> scenario =
                ActivityScenario.launch(SettingsActivity.class);
        scenario.onActivity(activity -> {
            int primary;
            try (android.content.res.TypedArray ta = activity.obtainStyledAttributes(
                    new int[]{androidx.appcompat.R.attr.colorPrimary})) {
                primary = ta.getColor(0, 0);
            }
            // dark system -> AppTheme_InvertedDark -> colorPrimary = white
            assertEquals("dark theme should follow system night mode",
                    0xFFFFFFFF, primary);
        });

        assertFalse("preference must stay unset (follow-system semantics)",
                android.preference.PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .contains("dark_mode_preference"));
    }
}
