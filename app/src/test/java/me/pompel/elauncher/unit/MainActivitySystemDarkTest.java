package me.pompel.elauncher.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.pompel.elauncher.MainActivity;
import me.pompel.elauncher.R;

/**
 * Classic unit test: dark-mode preference unset must follow the system night
 * mode. Robolectric can only set the activity config at class level
 * (qualifiers="night"), so this cannot be expressed as a Gherkin scenario.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "night", shadows = ShadowNoRecreateActivity.class)
public class MainActivitySystemDarkTest {

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
                .putInt("number_of_apps_preference", 2)
                .commit();

        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> {
            LinearLayout home = activity.findViewById(R.id.HomeScreen);
            TextView slot = (TextView) home.getChildAt(0);
            // dark system -> AppTheme_InvertedDark -> colorPrimary = white
            assertEquals("dark theme should follow system night mode",
                    Color.WHITE, slot.getTextColors().getDefaultColor());
        });

        assertFalse("preference must stay unset (follow-system semantics)",
                android.preference.PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .contains("dark_mode_preference"));
    }
}
