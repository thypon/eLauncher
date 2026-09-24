package me.pompel.elauncher.bdd.steps;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.action.ViewActions;
import me.pompel.elauncher.App;
import me.pompel.elauncher.MainActivity;
import me.pompel.elauncher.SettingsActivity;
import me.pompel.elauncher.R;
import me.pompel.elauncher.recyclerAdapter;
import org.robolectric.shadows.ShadowApplication;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.robolectric.Shadows.shadowOf;

/** Shared per-scenario state and helpers for BDD steps (picocontainer-managed). */
public class LauncherWorld {
    public ActivityScenario<MainActivity> scenario;
    public ActivityScenario<SettingsActivity> settingsScenario;

    /** All scenarios of a feature share one Robolectric sandbox, so the default
     * SharedPreferences file leaks between scenarios (e.g. a dark-mode toggle persisted
     * by an earlier scenario suppresses later seeding logic). AppCompatDelegate's default
     * night mode is static too and leaks the same way. Start every scenario with clean
     * preferences and a light system; Background steps re-seed what they need. */
    @io.cucumber.java.Before
    public void cleanScenarioState() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext())
                .edit().clear().commit();
    }

    public Context appContext() {
        return androidx.test.core.app.ApplicationProvider.getApplicationContext();
    }

    /** Registers fake launchable apps (label → com.fixtures.<lowercase>) in the package manager.
     * Mimics a manifest-style installation: package entry + activity component + MAIN/LAUNCHER
     * intent filter, so that queryIntentActivities, getLaunchIntentForPackage and
     * getApplicationInfo/Label all resolve (addResolveInfoForIntent alone does NOT support
     * getLaunchIntentForPackage — the shadow drops results when the query intent has setPackage
     * and the package is not registered). Clears prior com.fixtures.* entries first, because the
     * underlying shadow maps are static and may leak across scenarios. */
    public void installApps(String... labels) {
        android.content.pm.PackageManager pm = appContext().getPackageManager();
        // defensive cleanup: drop fixtures from previous scenarios (shadow maps are static)
        try {
            java.lang.reflect.Field f = org.robolectric.shadows.ShadowPackageManager.class
                    .getDeclaredField("activityFilters");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.SortedMap<android.content.ComponentName, java.util.List<android.content.IntentFilter>> filters =
                    (java.util.SortedMap<android.content.ComponentName, java.util.List<android.content.IntentFilter>>) f.get(null);
            filters.keySet().removeIf(cn -> cn != null && cn.getPackageName() != null
                    && cn.getPackageName().startsWith("com.fixtures."));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        for (String label : labels) {
            String pkg = "com.fixtures." + label.toLowerCase();
            shadowOf(pm).removePackage(pkg); // no-op if absent
            android.content.pm.PackageInfo pi = new android.content.pm.PackageInfo();
            pi.packageName = pkg;
            pi.versionCode = 1;
            pi.versionName = "1.0";
            pi.applicationInfo = new android.content.pm.ApplicationInfo();
            pi.applicationInfo.packageName = pkg;
            pi.applicationInfo.nonLocalizedLabel = label;
            pi.activities = new android.content.pm.ActivityInfo[1];
            pi.activities[0] = new android.content.pm.ActivityInfo();
            pi.activities[0].packageName = pkg;
            pi.activities[0].name = pkg + ".MainActivity";
            pi.activities[0].applicationInfo = pi.applicationInfo;
            pi.activities[0].nonLocalizedLabel = label;
            shadowOf(pm).addPackage(pi);
            android.content.ComponentName cn = new android.content.ComponentName(pkg, pkg + ".MainActivity");
            android.content.pm.ActivityInfo added = shadowOf(pm).addActivityIfNotPresent(cn);
            added.applicationInfo = pi.applicationInfo;
            added.nonLocalizedLabel = label;
            android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_MAIN);
            filter.addCategory(Intent.CATEGORY_LAUNCHER);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            addActivityFilter(cn, filter);
        }
    }

    public static String pkgFor(String label) {
        return "com.fixtures." + label.toLowerCase();
    }

    /** Appends (never replaces) an intent filter for a component in the shadow package
     * manager's static filter map, so extra capabilities (HOME, VIEW http, ...) can be
     * layered onto apps installed via installApps without breaking their launch filter. */
    private void addActivityFilter(android.content.ComponentName cn, android.content.IntentFilter filter) {
        try {
            java.lang.reflect.Field f = org.robolectric.shadows.ShadowPackageManager.class
                    .getDeclaredField("activityFilters");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.SortedMap<android.content.ComponentName, java.util.List<android.content.IntentFilter>> filters =
                    (java.util.SortedMap<android.content.ComponentName, java.util.List<android.content.IntentFilter>>) f.get(null);
            java.util.List<android.content.IntentFilter> existing = filters.get(cn);
            if (existing == null) {
                filters.put(cn, new java.util.ArrayList<>(java.util.Collections.singletonList(filter)));
            } else {
                existing.add(filter);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Registers a ResolveInfo for ACTION_CALL with a tel: URI so that
     * MainActivity.canMakePhoneCall() resolves (Robolectric has no phone app by default). */
    public void installPhoneApp(String label) {
        String pkg = pkgFor(label);
        android.content.pm.ResolveInfo ri = new android.content.pm.ResolveInfo();
        ri.activityInfo = new android.content.pm.ActivityInfo();
        ri.activityInfo.packageName = pkg;
        ri.activityInfo.name = pkg + ".MainActivity";
        ri.activityInfo.applicationInfo = new android.content.pm.ApplicationInfo();
        ri.activityInfo.applicationInfo.packageName = pkg;
        ri.nonLocalizedLabel = label;
        shadowOf(appContext().getPackageManager()).addResolveInfoForIntent(
                new Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:1234567890")), ri);
    }

    /** Installs a launchable app that additionally resolves ACTION_VIEW http: as the
     * system default browser (CATEGORY_DEFAULT is required by MATCH_DEFAULT_ONLY). */
    public void installBrowserApp(String label) {
        installApps(label);
        android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_VIEW);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addDataScheme("http");
        addActivityFilter(new android.content.ComponentName(pkgFor(label), pkgFor(label) + ".MainActivity"), filter);
    }

    /** Installs a second HOME launcher (needed for double-tap "last launcher" behavior;
     * MainActivity crashes with an empty launcher query, so scenarios must install one). */
    public void installOtherLauncher(String label) {
        installApps(label);
        android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        addActivityFilter(new android.content.ComponentName(pkgFor(label), pkgFor(label) + ".MainActivity"), filter);
    }

    public void setGesturePackage(String side, String pkg) {
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext()).edit()
                .putString(side + "_gesture_package", pkg).commit();
    }

    public String gesturePackage(String side) {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(appContext())
                .getString(side + "_gesture_package", null);
    }

    /** Explicit boolean pref write (committed synchronously). */
    public void setBooleanPref(String key, boolean value) {
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext()).edit()
                .putBoolean(key, value).commit();
    }

    /** Seeds prefs so tests start clean: onboarding done + 2 homescreen slots (leaves
     * free space at the bottom of the home screen for swipe gestures on small Robolectric displays).
     * Also denies USAGE_STATS: Robolectric's AppOpsManager defaults to MODE_ALLOWED, but the real
     * device starts without the grant. Scenarios needing usage data re-grant explicitly. */
    public void seedOnboardingDone() {
        resetPackageManagerResolvers();
        denyUsageStats();
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext()).edit()
                .putBoolean("firstLaunch", true)
                .putInt("number_of_apps_preference", 2)
                .commit();
    }

    /** Same as seedOnboardingDone() but WITHOUT marking onboarding done: MainActivity
     * must treat the next start as the first launch. */
    public void seedNoOnboarding() {
        resetPackageManagerResolvers();
        denyUsageStats();
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext()).edit()
                .putInt("number_of_apps_preference", 2)
                .commit();
    }

    /** ShadowPackageManager.resolveInfoForIntent is a STATIC map (leaks across scenarios):
     * a phone app registered by one scenario would make later scenarios resolve ACTION_CALL.
     * Clear it so every scenario starts from the same package-manager resolver state. */
    public void resetPackageManagerResolvers() {
        try {
            java.lang.reflect.Field f = org.robolectric.shadows.ShadowPackageManager.class
                    .getDeclaredField("resolveInfoForIntent");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Intent, java.util.List<android.content.pm.ResolveInfo>> resolvers =
                    (java.util.Map<Intent, java.util.List<android.content.pm.ResolveInfo>>) f.get(null);
            resolvers.clear();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Robolectric's ShadowAppOpsManager allows every op by default; make the grant explicit. */
    public void denyUsageStats() {
        setUsageStatsMode(android.app.AppOpsManager.MODE_ERRORED);
    }

    public void allowUsageStats() {
        setUsageStatsMode(android.app.AppOpsManager.MODE_ALLOWED);
    }

    private void setUsageStatsMode(int mode) {
        shadowOf((android.app.AppOpsManager) appContext().getSystemService(android.content.Context.APP_OPS_SERVICE))
                .setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), appContext().getPackageName(), mode);
    }

    public void startLauncher() {
        scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity.class);
        idle();
    }

    public MainActivity activity() {
        MainActivity[] ref = new MainActivity[1];
        scenario.onActivity(a -> ref[0] = a);
        return ref[0];
    }

    public recyclerAdapter drawerAdapter() {
        recyclerAdapter[] ref = new recyclerAdapter[1];
        scenario.onActivity(a -> {
            androidx.recyclerview.widget.RecyclerView rv = (androidx.recyclerview.widget.RecyclerView) a.findViewById(R.id.recycler_view);
            ref[0] = (recyclerAdapter) rv.getAdapter();
        });
        return ref[0];
    }

    /** Opens the app drawer via a deterministic fling dispatched through the activity. */
    public void openDrawer() {
        scenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            long base = android.os.SystemClock.uptimeMillis();
            int x = w / 2;
            int y0 = h - 20;
            android.view.MotionEvent down = android.view.MotionEvent.obtain(
                    base, base, android.view.MotionEvent.ACTION_DOWN, x, y0, 0);
            a.dispatchTouchEvent(down);
            down.recycle();
            int steps = 8;
            for (int i = 1; i <= steps; i++) {
                long t = base + i * 16L;
                android.view.MotionEvent move = android.view.MotionEvent.obtain(
                        base, t, android.view.MotionEvent.ACTION_MOVE, x, y0 - i * 60, 0);
                a.dispatchTouchEvent(move);
                move.recycle();
            }
            android.view.MotionEvent up = android.view.MotionEvent.obtain(
                    base, base + steps * 16L, android.view.MotionEvent.ACTION_UP, x, y0 - steps * 60, 0);
            a.dispatchTouchEvent(up);
            up.recycle();
        });
        await(this::drawerOpen);
    }

    public boolean drawerOpen() {
        boolean[] open = new boolean[1];
        scenario.onActivity(a -> open[0] = a.findViewById(R.id.AppDrawer).getVisibility() == View.VISIBLE
                && a.findViewById(R.id.HomeScreen).getVisibility() == View.GONE);
        return open[0];
    }

    // ---- synthetic gesture dispatch -------------------------------------------------------

    private void dispatch(android.view.MotionEvent event, android.app.Activity a) {
        a.dispatchTouchEvent(event);
        event.recycle();
    }

    /** Dispatches a touch stream through an activity: DOWN at (x0,y0), `steps` MOVE events
     * of (dx,dy) each `stepMs` milliseconds apart, then UP. Velocity ≈ dx/stepMs px per ms. */
    private void runTouchStream(android.app.Activity a, int x0, int y0, int dx, int dy, int steps, long stepMs) {
        long base = android.os.SystemClock.uptimeMillis();
        dispatch(android.view.MotionEvent.obtain(base, base,
                android.view.MotionEvent.ACTION_DOWN, x0, y0, 0), a);
        for (int i = 1; i <= steps; i++) {
            long t = base + i * stepMs;
            dispatch(android.view.MotionEvent.obtain(base, t,
                    android.view.MotionEvent.ACTION_MOVE, x0 + i * dx, y0 + i * dy, 0), a);
        }
        dispatch(android.view.MotionEvent.obtain(base, base + (steps + 1) * stepMs,
                android.view.MotionEvent.ACTION_UP, x0 + steps * dx, y0 + steps * dy, 0), a);
    }

    /** Horizontal/vertical fling on the home screen (below the slots). Fling velocity is
     * ~60px per 16ms — well past MainActivity's 100px/100 velocity thresholds. */
    public void fling(String direction) {
        scenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            int x0 = w / 2, y0 = h - 20, dx = 0, dy = 0;
            if ("left".equals(direction)) dx = -60;
            else if ("right".equals(direction)) dx = 60;
            else if ("up".equals(direction)) dy = -60;
            else if ("down".equals(direction)) { y0 = h / 2; dy = 60; }
            else throw new IllegalArgumentException("unknown direction: " + direction);
            runTouchStream(a, x0, y0, dx, dy, 8, 16L);
        });
        idle();
    }

    /** Quick tap within the 50dp edge zone (no movement, no long-press delay). */
    public void edgeTap(String side) {
        scenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            int x = "left".equals(side) ? 10 : w - 10;
            int y = h - 20;
            long base = android.os.SystemClock.uptimeMillis();
            dispatch(android.view.MotionEvent.obtain(base, base,
                    android.view.MotionEvent.ACTION_DOWN, x, y, 0), a);
            dispatch(android.view.MotionEvent.obtain(base, base + 50,
                    android.view.MotionEvent.ACTION_UP, x, y, 0), a);
        });
        idle();
    }

    /** Slow inward horizontal swipe from a screen edge (30px per 400ms ≈ 75px/s — under the
     * fling velocity threshold) so only the back-gesture state machine marks the touch. */
    public void edgeSwipe(String side) {
        scenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            int x = "left".equals(side) ? 10 : w - 10;
            int dx = "left".equals(side) ? 30 : -30;
            runTouchStream(a, x, h - 20, dx, 0, 8, 400L);
        });
        idle();
    }

    /** Two taps ~120ms apart; the second DOWN triggers GestureDetector.onDoubleTap. */
    public void doubleTapHome() {
        scenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            int x = w / 2, y = h - 20;
            long base = android.os.SystemClock.uptimeMillis();
            dispatch(android.view.MotionEvent.obtain(base, base,
                    android.view.MotionEvent.ACTION_DOWN, x, y, 0), a);
            dispatch(android.view.MotionEvent.obtain(base, base + 60,
                    android.view.MotionEvent.ACTION_UP, x, y, 0), a);
            dispatch(android.view.MotionEvent.obtain(base, base + 120,
                    android.view.MotionEvent.ACTION_DOWN, x, y, 0), a);
            dispatch(android.view.MotionEvent.obtain(base, base + 200,
                    android.view.MotionEvent.ACTION_UP, x, y, 0), a);
        });
        idle();
    }

    /** Long press (DOWN, let the 500ms GestureDetector timer fire on the looper, UP). */
    public void longPressHome() {
        scenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            long base = android.os.SystemClock.uptimeMillis();
            dispatch(android.view.MotionEvent.obtain(base, base,
                    android.view.MotionEvent.ACTION_DOWN, w / 2, h - 20, 0), a);
        });
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(700, TimeUnit.MILLISECONDS);
        scenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            long base = android.os.SystemClock.uptimeMillis();
            dispatch(android.view.MotionEvent.obtain(base, base + 700,
                    android.view.MotionEvent.ACTION_UP, w / 2, h - 20, 0), a);
        });
        idle();
    }

    /** Drives MainActivity.onBackPressed() directly (edge gesture flags are its input). */
    public void pressBack() {
        scenario.onActivity(a -> a.onBackPressed());
        idle();
    }

    // ---- settings screen helpers ---------------------------------------------------------

    public void startSettings() {
        settingsScenario = androidx.test.core.app.ActivityScenario.launch(SettingsActivity.class);
        idle();
    }

    public SettingsActivity settingsActivity() {
        SettingsActivity[] ref = new SettingsActivity[1];
        settingsScenario.onActivity(a -> ref[0] = a);
        return ref[0];
    }

    /** Vertical fling on the settings screen (SettingsActivity restarts the launcher on up-fling). */
    public void flingOnSettings(String direction) {
        settingsScenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            int x0 = w / 2, y0 = h - 20, dx = 0, dy = 0;
            if ("up".equals(direction)) dy = -60;
            else if ("down".equals(direction)) dy = 60;
            else throw new IllegalArgumentException("unknown direction: " + direction);
            runTouchStream(a, x0, y0, dx, dy, 8, 16L);
        });
        idle();
    }

    /** Long press on the settings screen (DOWN, let the 500ms timer fire, UP). */
    public void longPressOnSettings() {
        settingsScenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            long base = android.os.SystemClock.uptimeMillis();
            dispatch(android.view.MotionEvent.obtain(base, base,
                    android.view.MotionEvent.ACTION_DOWN, w / 2, h - 20, 0), a);
        });
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(700, TimeUnit.MILLISECONDS);
        settingsScenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            long base = android.os.SystemClock.uptimeMillis();
            dispatch(android.view.MotionEvent.obtain(base, base + 700,
                    android.view.MotionEvent.ACTION_UP, w / 2, h - 20, 0), a);
        });
        idle();
    }

    /** Drives SettingsActivity.onBackPressed() (restarts the launcher). */
    public void pressBackOnSettings() {
        settingsScenario.onActivity(a -> a.onBackPressed());
        idle();
    }

    public void clickGestureButton(String side) {
        onView(withId(side.equals("left") ? R.id.left_gesture_button : R.id.right_gesture_button)).perform(click());
        idle();
    }

    public String gestureButtonLabel(String side) {
        int id = side.equals("left") ? R.id.left_gesture_button : R.id.right_gesture_button;
        android.view.View view = findInSettings(
                v -> v instanceof android.widget.Button && v.getId() == id);
        if (view == null) throw new AssertionError("No " + side + " gesture button found");
        return ((android.widget.Button) view).getText().toString();
    }

    /** Searches the settings activity's view hierarchy (used for preference widgets that are
     * awkward to address with Espresso matchers under Robolectric). */
    public android.view.View findInSettings(java.util.function.Predicate<android.view.View> test) {
        android.view.View root = settingsActivity().getWindow().getDecorView();
        final android.view.View[] found = new android.view.View[1];
        collect(root, test, found);
        return found[0];
    }

    public boolean darkModeSwitchChecked() {
        android.view.View view = findInSettings(v -> v instanceof androidx.appcompat.widget.SwitchCompat);
        if (view == null) throw new AssertionError("No dark mode switch found");
        return ((androidx.appcompat.widget.SwitchCompat) view).isChecked();
    }

    public void toggleDarkModeSwitch() {
        android.view.View view = findInSettings(v -> v instanceof androidx.appcompat.widget.SwitchCompat);
        if (view == null) throw new AssertionError("No dark mode switch found");
        view.performClick();
        idle();
    }

    /** The number-of-apps SeekBarPreference renders an inline SeekBar (showSeekBarValue defaults
     * to true, so no dialog appears). Programmatic setProgress notifies the listener with
     * fromUser=false and androidx persists only on user input — invoke the bound listener
     * directly with fromUser=true to emulate a completed drag. */
    public void setNumberOfApps(int value) {
        android.view.View view = findInSettings(v -> v instanceof android.widget.SeekBar);
        if (view == null) throw new AssertionError("No SeekBar found in settings");
        android.widget.SeekBar seekBar = (android.widget.SeekBar) view;
        android.widget.SeekBar.OnSeekBarChangeListener listener;
        try {
            java.lang.reflect.Field f = android.widget.SeekBar.class
                    .getDeclaredField("mOnSeekBarChangeListener");
            f.setAccessible(true);
            listener = (android.widget.SeekBar.OnSeekBarChangeListener) f.get(seekBar);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        if (listener == null) throw new AssertionError("No OnSeekBarChangeListener bound");
        listener.onStartTrackingTouch(seekBar);
        seekBar.setProgress(value);
        listener.onProgressChanged(seekBar, value, true);
        listener.onStopTrackingTouch(seekBar);
        idle();
    }

    public boolean booleanPref(String key) {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(appContext()).getBoolean(key, false);
    }

    public int intPref(String key) {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(appContext()).getInt(key, 0);
    }

    public boolean homeVisible() {
        boolean[] visible = new boolean[1];
        scenario.onActivity(a -> visible[0] = a.findViewById(R.id.HomeScreen).getVisibility() == View.VISIBLE
                && a.findViewById(R.id.AppDrawer).getVisibility() == View.GONE);
        return visible[0];
    }

    /** Adapter data labels in drawer order. */
    public List<String> drawerLabels() {
        List<String> labels = new ArrayList<>();
        scenario.onActivity(a -> {
            androidx.recyclerview.widget.RecyclerView rv = a.findViewById(R.id.recycler_view);
            int n = rv.getAdapter() == null ? 0 : rv.getAdapter().getItemCount();
            for (int i = 0; i < n; i++) {
                androidx.recyclerview.widget.RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(i);
                if (h == null) { labels.add("<unlaid out>"); continue; }
                TextView tv = h.itemView.findViewById(R.id.app_name);
                labels.add(tv.getText().toString());
            }
        });
        return labels;
    }

    public int drawerItemCount() {
        int[] count = new int[1];
        scenario.onActivity(a -> count[0] = drawerAdapter().getItemCount());
        return count[0];
    }

    /** Simulates typing text into the search field (triggers TextWatcher → filter). */
    public void typeSearch(String text) {
        onView(withId(R.id.search)).perform(clearText(), typeText(text));
    }

    public void clearSearch() {
        onView(withId(R.id.search)).perform(clearText());
    }

    /** Position of a label in the drawer's adapter data; -1 if absent. */
    public int drawerPositionOf(String label) {
        int pos = -1;
        List<String> labels = drawerLabels();
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).equals(label)) { pos = i; break; }
        }
        return pos;
    }

    /** Taps a drawer row by adapter position. Avoids withText() matchers: under Robolectric the
     * RecyclerView can hold stale duplicate item views (same text), making withText ambiguous. */
    public void tapApp(String label) {
        int pos = drawerPositionOf(label);
        if (pos < 0) throw new AssertionError("No drawer row for label: " + label);
        onView(withId(R.id.recycler_view)).perform(
                androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition(pos, click()));
    }

    public void longPressApp(String label) {
        int pos = drawerPositionOf(label);
        if (pos < 0) throw new AssertionError("No drawer row for label: " + label);
        onView(withId(R.id.recycler_view)).perform(
                androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition(pos, longClick()));
    }

    /** Home screen slot helpers. Without USAGE_STATS permission the HomeScreen holds exactly
     * one TextView per configured slot (the "last app" row is only added with permission). */

    /** Seeds the number_of_apps_preference (must be called before startLauncher()). */
    public void configureSlots(int count) {
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext()).edit()
                .putInt("number_of_apps_preference", count)
                .commit();
    }

    /** Seeds slot assignment prefs ("i" -> label, "p<i>" -> package) before startLauncher(). */
    public void assignSlot(int slot, String label) {
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext()).edit()
                .putString(String.valueOf(slot), label)
                .putString("p" + slot, "com.fixtures." + label.toLowerCase())
                .commit();
    }

    public int slotCount() {
        int[] n = new int[1];
        scenario.onActivity(a -> n[0] = ((android.widget.LinearLayout) a.findViewById(R.id.HomeScreen)).getChildCount());
        return n[0];
    }

    public String slotText(int slot) {
        String[] text = new String[1];
        scenario.onActivity(a -> {
            android.widget.LinearLayout home = (android.widget.LinearLayout) a.findViewById(R.id.HomeScreen);
            text[0] = ((TextView) home.getChildAt(slot)).getText().toString();
        });
        return text[0];
    }

    /** Text color of homescreen slot 0: white on the dark theme, black on light. */
    public int slotTextColor() {
        int[] color = new int[1];
        scenario.onActivity(a -> {
            android.widget.LinearLayout home = (android.widget.LinearLayout) a.findViewById(R.id.HomeScreen);
            color[0] = ((TextView) home.getChildAt(0)).getTextColors().getDefaultColor();
        });
        return color[0];
    }


    public void clickSlot(int slot) {
        scenario.onActivity(a -> {
            android.widget.LinearLayout home = (android.widget.LinearLayout) a.findViewById(R.id.HomeScreen);
            home.getChildAt(slot).performClick();
        });
        idle();
    }

    public void longPressSlot(int slot) {
        scenario.onActivity(a -> {
            android.widget.LinearLayout home = (android.widget.LinearLayout) a.findViewById(R.id.HomeScreen);
            home.getChildAt(slot).performLongClick();
        });
        idle();
    }

    public String storedSlotLabel(int slot) {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(appContext())
                .getString(String.valueOf(slot), null);
    }

    public String storedSlotPackage(int slot) {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(appContext())
                .getString("p" + slot, null);
    }

    /** Clicks the item with the given text in the latest shown dialog's list. */
    public void pickAppInDialog(String label) {
        android.widget.ListView list = findInDialog(android.widget.ListView.class);
        int index = -1;
        for (int i = 0; i < list.getCount(); i++) {
            Object item = list.getItemAtPosition(i);
            if (item != null && label.contentEquals(item.toString())) { index = i; break; }
        }
        if (index < 0) throw new AssertionError("No item " + label + " in dialog list");
        list.performItemClick(list.getChildAt(index), index, list.getItemIdAtPosition(index));
        idle();
    }

    /** Sets the text of the EditText inside the latest dialog (the rename input). */
    public void setDialogInput(String text) {
        android.widget.EditText input = findInDialog(android.widget.EditText.class);
        if (input == null) throw new AssertionError("No EditText in dialog");
        input.setText(text);
        idle();
    }

    /** Clicks the button with the given text ("Add", "Cancel", ...) in the latest dialog. */
    public void confirmDialog(String buttonText) {
        android.widget.Button button = findInDialog(android.widget.Button.class, buttonText);
        if (button == null) throw new AssertionError("No button " + buttonText + " in dialog");
        button.performClick();
        idle();
    }

    private android.view.View findInDialog(java.util.function.Predicate<android.view.View> test) {
        android.app.Dialog latest = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        if (latest == null) throw new AssertionError("No dialog shown");
        final android.view.View[] found = new android.view.View[1];
        collect(latest.getWindow().getDecorView(), test, found);
        return found[0];
    }

    private <T extends android.view.View> T findInDialog(Class<T> type) {
        return (T) findInDialog(v -> type.isInstance(v));
    }

    private android.widget.Button findInDialog(Class<android.widget.Button> type, String text) {
        return (android.widget.Button) findInDialog(v -> type.isInstance(v) && text.equals(((TextView) v).getText().toString()));
    }

    private void collect(android.view.View view, java.util.function.Predicate<android.view.View> test, android.view.View[] out) {
        if (out[0] != null) return;
        if (test.test(view)) { out[0] = view; return; }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && out[0] == null; i++) {
                collect(group.getChildAt(i), test, out);
            }
        }
    }

    public Intent nextStartedActivity() {
        return ShadowApplication.getInstance().getNextStartedActivity();
    }

    /** Test-only read access to the adapter's currently filtered app list. */
    public App appAt(int position) {
        try {
            java.lang.reflect.Field f = recyclerAdapter.class.getDeclaredField("appListFiltered");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<App> filtered = (ArrayList<App>) f.get(drawerAdapter());
            return filtered.get(position);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Polls a condition until true or timeout. */
    public void await(java.util.concurrent.Callable<Boolean> condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (!condition.call()) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("Condition not met within timeout");
                }
                Thread.sleep(50);
                idle();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void idle() {
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}