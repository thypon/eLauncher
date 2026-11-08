package me.pompel.elauncher;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        LauncherTheme.applyTheme(this, LauncherTheme.isDarkMode(prefs, getResources()));
        LauncherTheme.applySystemBars(this);

        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        gestureDetector = new GestureDetector(this, new GestureListener());

        applyOrientationLock(this, prefs.getBoolean("lock_portrait_preference", true));
    }

    /** Applies the orientation lock: portrait unless the lock is off
     * (then the orientation follows the system). */
    static void applyOrientationLock(android.app.Activity activity, boolean lockPortrait) {
        activity.setRequestedOrientation(lockPortrait
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            assert e1 != null;
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffY) > Math.abs(diffX)) {
                if (diffY < 0 && Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    restartApplication();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent e) {
            restartApplication();
        }
    }

    @Override
    public void onBackPressed() {
        restartApplication();
    }

    private void restartApplication() {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity(); // Close all activities
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            
            // Initialize dark mode switch to show current system state if not explicitly set
            androidx.preference.SwitchPreferenceCompat darkModePreference = 
                findPreference(LauncherTheme.DARK_MODE_PREF);
            if (darkModePreference != null) {
                android.content.SharedPreferences prefs = 
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
                
                // Only set the visual state if the preference hasn't been explicitly set
                if (!prefs.contains(LauncherTheme.DARK_MODE_PREF)) {
                    boolean systemDarkMode = (getResources().getConfiguration().uiMode & 
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                            android.content.res.Configuration.UI_MODE_NIGHT_YES;
                    darkModePreference.setChecked(systemDarkMode);
                    // setChecked persists the seeded value; keep the follow-system
                    // semantics (seed is visual only) by dropping the auto-written
                    // key again — an unset preference must keep following the system.
                    prefs.edit().remove(LauncherTheme.DARK_MODE_PREF).apply();
                }

                // Live retheme: switching dark mode restyles the screen right away.
                // Replacing the fragment re-inflates the preference rows so every
                // theme attribute (text, switches, seekbar, dividers) re-resolves.
                darkModePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean dark = Boolean.TRUE.equals(newValue);
                    requireActivity().setTheme(dark ? R.style.AppTheme_InvertedDark : R.style.AppTheme);
                    LauncherTheme.applySystemBars(requireActivity());
                    requireActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.settings, new SettingsFragment())
                            .commit();
                    return true;
                });
            }

            // Re-apply the lock immediately when the switch is toggled
            androidx.preference.SwitchPreferenceCompat lockPortraitPreference =
                findPreference("lock_portrait_preference");
            if (lockPortraitPreference != null) {
                lockPortraitPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    applyOrientationLock(requireActivity(), Boolean.TRUE.equals(newValue));
                    return true;
                });
            }
        }
    }
}