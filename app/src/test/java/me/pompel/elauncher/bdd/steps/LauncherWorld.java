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
import android.widget.LinearLayout;
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
import org.robolectric.RuntimeEnvironment;

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
        // the eBoard layout persists in its own prefs file and leaks across scenarios
        appContext().getSharedPreferences(me.pompel.elauncher.widget.WidgetPrefs.PREFS_FILE, Context.MODE_PRIVATE)
                .edit().clear().commit();
        // usage events accumulate in static shadow state and leak across scenarios
        org.robolectric.shadows.ShadowUsageStatsManager.reset();
        // the shadow widget manager leaks installed providers and bound
        // widgets across scenarios of the same feature sandbox: clear them
        // so every scenario registers its fixture provider from scratch
        resetWidgetManagerShadow();
        // the widget host shadow counts allocations/configure launches statically
        me.pompel.elauncher.unit.ShadowConfigureHost.resetAllocations();
    }

    /** Clears the shadow AppWidgetManager's provider/widget bookkeeping. */
    private void resetWidgetManagerShadow() {
        try {
            android.appwidget.AppWidgetManager mgr =
                    android.appwidget.AppWidgetManager.getInstance(appContext());
            org.robolectric.shadows.ShadowAppWidgetManager shadow =
                    org.robolectric.Shadows.shadowOf(mgr);
            for (String name : new String[]{"installedProviders",
                    "installedProvidersForProfile", "widgetInfos", "widgetPreviews"}) {
                try {
                    java.lang.reflect.Field f =
                            org.robolectric.shadows.ShadowAppWidgetManager.class
                                    .getDeclaredField(name);
                    f.setAccessible(true);
                    Object val = f.get(shadow);
                    if (val instanceof java.util.Map) ((java.util.Map<?, ?>) val).clear();
                    else if (val instanceof java.util.Collection)
                        ((java.util.Collection<?>) val).clear();
                    else if (val instanceof com.google.common.collect.Multimap)
                        ((com.google.common.collect.Multimap<?, ?>) val).clear();
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (Exception e) {
            // no widget manager in this sandbox: nothing to clean
        }
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
            // real launcher apps always ship an icon; give the fixture the framework
            // default so icon-related flows (e.g. the gesture picker) behave like reality
            pi.applicationInfo.icon = android.R.drawable.sym_def_app_icon;
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
            // the shadow PackageManager only returns icons registered through
            // setApplicationIcon; mirror a real app's launcher icon here
            shadowOf(pm).setApplicationIcon(pkg, new android.graphics.drawable.ColorDrawable(android.graphics.Color.LTGRAY));
        }
        // the launcher's own icon backs the eBoard entry in the gesture picker
        shadowOf(pm).setApplicationIcon(appContext().getPackageName(), new android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY));
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

    /** Records a usage-stats entry, as if the given fixture app had last been used
     * the given number of minutes ago (mirrors UsageStatsManager.queryUsageStats
     * INTERVAL_DAILY semantics the app relies on). */
    public void addUsageStats(String label, long minutesAgo) {
        long lastTimeUsed = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutesAgo);
        android.app.usage.UsageStats stats = org.robolectric.shadows.ShadowUsageStatsManager.UsageStatsBuilder.newBuilder()
                .setPackageName(pkgFor(label))
                .setFirstTimeStamp(lastTimeUsed - TimeUnit.MINUTES.toMillis(1))
                .setLastTimeStamp(lastTimeUsed)
                .setLastTimeUsed(lastTimeUsed)
                .setTotalTimeInForeground(60_000)
                .build();
        android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                appContext().getSystemService(android.content.Context.USAGE_STATS_SERVICE);
        shadowOf(usm).addUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, stats);
    }

    /** The dedicated last-app row is appended after the homescreen slots. */
    private TextView lastAppRow() {
        LinearLayout home = activity().findViewById(R.id.HomeScreen);
        View view = home.getChildAt(home.getChildCount() - 1);
        if (!(view instanceof TextView)) throw new AssertionError("Last app row missing");
        return (TextView) view;
    }

    public int homeRowCount() {
        LinearLayout home = activity().findViewById(R.id.HomeScreen);
        return home.getChildCount();
    }

    public void tapLastAppRow() {
        lastAppRow().performClick();
        idle();
    }

    public String lastAppText() {
        return lastAppRow().getText().toString();
    }

    public boolean slotTextIsBold(int slot) {
        LinearLayout home = activity().findViewById(R.id.HomeScreen);
        TextView slotView = (TextView) home.getChildAt(slot);
        if (!(slotView.getText() instanceof android.text.Spanned)) return false;
        android.text.style.StyleSpan[] spans = ((android.text.Spanned) slotView.getText())
                .getSpans(0, slotView.length(), android.text.style.StyleSpan.class);
        for (android.text.style.StyleSpan span : spans) {
            if ((span.getStyle() & android.graphics.Typeface.BOLD) != 0) return true;
        }
        return false;
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

    /** Reference to the dashboard captured before a dashboard fling, so the
     * exit animation can be asserted even after the activity finishes. */
    public me.pompel.elauncher.DashboardActivity dashboardFlingTarget;

    /** Horizontal fling on the eBoard dashboard screen. Mirrors fling() on
     * the home screen: ~60px per 16ms, center start. Captures the activity
     * reference first so the pending transition can be asserted later. */
    public void flingOnDashboard(String direction) {
        dashboardFlingTarget = dashboard();
        me.pompel.elauncher.DashboardActivity a = dashboardFlingTarget;
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        int x0 = w / 2, y0 = h - 20;
        int dx = 0, dy = 0;
        if ("left".equals(direction)) dx = -60;
        else if ("right".equals(direction)) dx = 60;
        else if ("up".equals(direction)) dy = -60;
        else throw new IllegalArgumentException("unknown direction: " + direction);
        runTouchStream(a, x0, y0, dx, 0, 8, 16L);
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

    /** Fast horizontal fling starting inside the 50dp edge zone. Inward = toward the
     * screen centre (the direction that can either mark a back gesture or fling);
     * outward = toward/past the screen edge. Velocity ≈60px per 16ms — past the
     * fling thresholds — so GestureDetector.onFling fires like a real quick swipe. */
    public void edgeFling(String side, boolean inward) {
        scenario.onActivity(a -> {
            int w = a.getResources().getDisplayMetrics().widthPixels;
            int h = a.getResources().getDisplayMetrics().heightPixels;
            boolean leftEdge = "left".equals(side);
            int x = leftEdge ? 10 : w - 10;
            int dx;
            if (inward) dx = leftEdge ? 60 : -60;
            else dx = leftEdge ? -60 : 60;
            runTouchStream(a, x, h - 20, dx, 0, 8, 16L);
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

    /** Requested orientation of the running MainActivity. */
    public int mainRequestedOrientation() {
        int[] orientation = new int[1];
        scenario.onActivity(a -> orientation[0] = a.getRequestedOrientation());
        return orientation[0];
    }

    /** Requested orientation of the running SettingsActivity. */
    public int settingsRequestedOrientation() {
        int[] orientation = new int[1];
        settingsScenario.onActivity(a -> orientation[0] = a.getRequestedOrientation());
        return orientation[0];
    }

    /** Clicks the Lock Portrait preference in the settings fragment: toggles, persists
     * and fires its change listener — the same path as tapping the switch row. */
    public void toggleLockPortraitPreference() {
        settingsScenario.onActivity(a -> {
            androidx.preference.SwitchPreferenceCompat pref = lockPortraitPreference(a);
            pref.performClick();
        });
        idle();
    }

    /** Checked state of the Lock Portrait preference. */
    public boolean lockPortraitPreferenceChecked() {
        boolean[] checked = new boolean[1];
        settingsScenario.onActivity(a -> checked[0] = lockPortraitPreference(a).isChecked());
        return checked[0];
    }

    private androidx.preference.SwitchPreferenceCompat lockPortraitPreference(SettingsActivity a) {
        androidx.preference.PreferenceFragmentCompat fragment =
                (androidx.preference.PreferenceFragmentCompat)
                        a.getSupportFragmentManager().findFragmentById(R.id.settings);
        androidx.preference.SwitchPreferenceCompat pref =
                fragment == null ? null : fragment.findPreference("lock_portrait_preference");
        if (pref == null) throw new AssertionError("lock_portrait_preference not found");
        return pref;
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
        // performClick, not Espresso click(): Espresso injects real motion events and the
        // tap's UP lands inside the just-opened picker dialog, phantom-clicking a row
        int id = side.equals("left") ? R.id.left_gesture_button : R.id.right_gesture_button;
        android.view.View view = findInSettings(
                v -> v instanceof android.widget.Button && v.getId() == id);
        if (view == null) throw new AssertionError("No " + side + " gesture button found");
        view.performClick();
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

    /** Resolved colorPrimary of the settings activity's theme: pure black on the
     * light theme, pure white on AppTheme_InvertedDark (e-ink palette swap). */
    public int settingsPrimaryColor() {
        int[] color = new int[1];
        settingsScenario.onActivity(a -> {
            try (android.content.res.TypedArray ta =
                         a.obtainStyledAttributes(new int[]{androidx.appcompat.R.attr.colorPrimary})) {
                color[0] = ta.getColor(0, 0);
            }
        });
        return color[0];
    }

    /** Text color of the first preference row title in the settings screen:
     * pure colorPrimary in the custom row layouts (black on light, white on dark). */
    public int settingsTitleTextColor() {
        android.view.View view = findInSettings(
                v -> v instanceof android.widget.TextView && v.getId() == android.R.id.title);
        if (!(view instanceof android.widget.TextView)) throw new AssertionError("No preference title found");
        return ((android.widget.TextView) view).getTextColors().getDefaultColor();
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

    /** True once the latest dialog's list shows rows and every visible row carries a
     * non-null icon drawable (icons are loaded lazily, so this may be false briefly). */
    public boolean pickerRowsShowIcons() {
        android.widget.ListView list = findInDialog(android.widget.ListView.class);
        if (list.getChildCount() == 0) {
            // Robolectric does not always run a layout pass for dialog lists: force one
            // so the adapter's rows (and their lazy icon loads) materialize
            int w = list.getWidth() > 0 ? list.getWidth() : 800;
            list.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
            list.layout(0, 0, w, 2000);
            if (list.getChildCount() == 0) throw new AssertionError("picker list has no laid out rows");
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            android.widget.ImageView icon = list.getChildAt(i).findViewById(R.id.gesture_picker_icon);
            if (icon == null) throw new AssertionError("row " + i + " has no icon view");
            if (icon.getDrawable() == null) return false; // still loading lazily
        }
        return true;
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
        return org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
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

    /** idles the looper long enough for delayed posts (HOLD_MS etc.). */
    public void idle(long ms) {
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper())
                .idleFor(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    // ---- eBoard dashboard helpers ---------------------------------------------------------

    public ActivityScenario<me.pompel.elauncher.DashboardActivity> dashboardScenario;
    /** ComponentName of the fake widget provider installed by installWidgetProvider(). */
    public android.content.ComponentName widgetProvider;
    /** The fixture provider's AppWidgetProviderInfo (configure flow tweaks). */
    private android.appwidget.AppWidgetProviderInfo lastProviderInfo;
    /** The pending configure request's intent (configure-result delivery). */
    private android.content.Intent lastConfigureIntent;
    /** Touch actions seen by the home-gesture spy (tap-mode scenarios). */
    public final List<Integer> homeGestureEvents = new ArrayList<>();
    /** Set by spyCurrentWidgetTouches when the widget view sees a touch. */
    public final boolean[] widgetTouchSpied = new boolean[1];

    public void startDashboard() {
        android.app.Dialog stale = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        if (stale != null && stale.isShowing()) stale.dismiss();
        if (dashboardScenario != null) {
            dashboardScenario.close();
            idle(); // let the old decor detach before touching views again
        }
        dashboardScenario = androidx.test.core.app.ActivityScenario.launch(me.pompel.elauncher.DashboardActivity.class);
        idle();
    }

    /** Launches the dashboard live with the exact intent the launcher produced
     * (extras such as the entry direction are preserved). */
    public void launchDashboardWith(android.content.Intent intent) {
        android.app.Dialog stale = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        if (stale != null && stale.isShowing()) stale.dismiss();
        if (dashboardScenario != null) {
            dashboardScenario.close();
            idle(); // let the old decor detach before touching views again
        }
        dashboardScenario = androidx.test.core.app.ActivityScenario.launch(intent);
        idle();
    }

    /** Back press on the eBoard dashboard screen. */
    public void pressBackOnDashboard() {
        dashboardScenario.onActivity(a -> a.onBackPressed());
        idle();
    }

    public me.pompel.elauncher.DashboardActivity dashboard() {
        me.pompel.elauncher.DashboardActivity[] ref = new me.pompel.elauncher.DashboardActivity[1];
        dashboardScenario.onActivity(a -> ref[0] = a);
        return ref[0];
    }

    /** Closes and relaunches the eBoard, as if the user left and reopened it. */
    public void restartDashboard() {
        dashboardScenario.close();
        startDashboard();
    }

    public android.appwidget.AppWidgetManager widgetManager() {
        return android.appwidget.AppWidgetManager.getInstance(appContext());
    }

    /** Registers a fake app widget provider with the shadow AppWidgetManager;
     * the label (popover title) and description (popover kind line) can be
     * set for the widget-adder scenarios. */
    public void installWidgetProvider(String label, String description) {
        widgetProvider = new android.content.ComponentName(appContext().getPackageName(),
                "me.fixtures.WidgetProvider");
        android.appwidget.AppWidgetProviderInfo info = new android.appwidget.AppWidgetProviderInfo();
        info.provider = widgetProvider;
        info.label = label;
        installProviderInfo(info, label);
    }

    /** Same fixture, with a configure activity so the widget must be set up
     * before it lands on the board (Olauncher's configure flow). */
    public void installConfigurableWidgetProvider(String label, String description) {
        widgetProvider = new android.content.ComponentName(appContext().getPackageName(),
                "me.fixtures.WidgetProvider");
        android.appwidget.AppWidgetProviderInfo info = new android.appwidget.AppWidgetProviderInfo();
        info.provider = widgetProvider;
        info.label = label;
        info.configure = new android.content.ComponentName(appContext().getPackageName(),
                me.pompel.elauncher.SettingsActivity.class.getName());
        installProviderInfo(info, label);
    }

    private void installProviderInfo(android.appwidget.AppWidgetProviderInfo info, String label) {
        lastProviderInfo = info;
        // a realistic clock-widget minimum: the board's default row height
        info.minHeight = Math.round(200 * appContext().getResources().getDisplayMetrics().density);
        // providerInfo backs loadLabel(): a bare ActivityInfo with a
        // non-localized label keeps the popover title deterministic
        android.content.pm.ActivityInfo ai = new android.content.pm.ActivityInfo();
        ai.packageName = appContext().getPackageName();
        ai.nonLocalizedLabel = label.isEmpty() ? "Widget" : label;
        try {
            java.lang.reflect.Field f = android.appwidget.AppWidgetProviderInfo.class.getField("providerInfo");
            f.set(info, ai);
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field f = android.appwidget.AppWidgetProviderInfo.class
                    .getField("descriptionRes");
            f.set(info, appContext().getResources().getIdentifier(
                    "eboard_kind_sample", "string", appContext().getPackageName()));
        } catch (Exception ignored) {
            // no descriptionRes field: popover falls back to the app name
        }
        shadowOf(widgetManager()).addInstalledProvider(info);
    }

    public void allowWidgetBinding(boolean allowed) {
        shadowOf(widgetManager()).setAllowedToBindAppWidgets(allowed);
    }

    /** The configure activity the fixture provider demands must have started:
     * either the host-emulated ACTION_APPWIDGET_CONFIGURE launch (with the
     * widget id extra) or a direct start of the configure component. */
    public void assertConfigureStarted() {
        org.robolectric.shadows.ShadowActivity.IntentForResult req = peekRequest();
        if (req == null) throw new AssertionError("no activity started");
        if (android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                .equals(req.intent.getAction())
                && req.intent.hasExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID)) {
            return;
        }
        android.content.ComponentName c = req.intent.getComponent();
        if (c == null || !c.getClassName().contains("SettingsActivity")) {
            throw new AssertionError("expected the configure activity, got "
                    + req.intent.getComponent());
        }
    }

    /** The user finished the configure screen: RESULT_OK for the pending id. */
    public void resolveConfigureOutcome() {
        org.robolectric.shadows.ShadowActivity.IntentForResult req = takeRequest();
        if (req == null) throw new AssertionError("no configure activity to resolve");
        shadowOf(dashboard()).receiveResult(req.intent, android.app.Activity.RESULT_OK,
                new Intent());
        idle();
    }

    public boolean addWidgetButtonVisible() {
        boolean[] visible = new boolean[1];
        dashboardScenario.onActivity(a ->
                visible[0] = a.findViewById(me.pompel.elauncher.R.id.add_widget)
                        .getVisibility() == View.VISIBLE);
        return visible[0];
    }

    public void tapAddWidgetButton() {
        dashboardScenario.onActivity(a ->
                a.findViewById(me.pompel.elauncher.R.id.add_widget).performClick());
        idle();
    }

    public void longPressBoardEmpty() {
        dashboardScenario.onActivity(a ->
                a.onEmptySpaceLongPressed(0, 0)); // empty board: coordinates unused
        idle();
    }

    /** Real-touch hold on the empty space below the widget rows: DOWN on the
     * board (the hold timer lives in the board touch listener), wait past
     * HOLD_MS, then release — the adder popover must open. */
    public void holdBoardEmptyBelowWidgets() {
        dashboardScenario.onActivity(a -> {
            if (a.rowCount() > 0) {
                a.applyRowHeight(0, me.pompel.elauncher.DashboardActivity.MIN_HEIGHT_DP);
            }
            android.view.View board = a.findViewById(me.pompel.elauncher.R.id.board);
            android.view.ViewGroup stack =
                    (android.view.ViewGroup) a.findViewById(me.pompel.elauncher.R.id.board_stack);
            int y = (stack != null && stack.getChildCount() > 0)
                    ? Math.min(stack.getChildAt(stack.getChildCount() - 1).getBottom() + 120,
                        board.getHeight() - 20)
                    : Math.max(1, board.getHeight() - 20);
            long now = android.os.SystemClock.uptimeMillis();
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_DOWN, 5, y, 0));
        });
        idle(800);
        dashboardScenario.onActivity(a -> {
            android.view.View board = a.findViewById(me.pompel.elauncher.R.id.board);
            long now = android.os.SystemClock.uptimeMillis();
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_UP, 5, 10, 0));
        });
        idle();
    }

    /** Widget rows must be built on the plain (non-AppCompat-inflater)
     * wrapper context: RemoteViews reflection rejects AppCompat classes. */
    public boolean widgetRowBuiltOnPlainContext() {
        boolean[] ok = new boolean[1];
        dashboardScenario.onActivity(a -> {
            android.content.Context ctx =
                    ((android.view.ViewGroup) a.boardRowAt(0)).getChildAt(0).getContext();
            ok[0] = ctx != a && ctx instanceof android.content.ContextWrapper;
        });
        return ok[0];
    }

    /** Spies direct touches on the current widget host view of row 0. */
    public void spyCurrentWidgetTouches() {
        widgetTouchSpied[0] = false;
        dashboardScenario.onActivity(a -> {
            View widget = a.widgetHostViewAt(0);
            widget.setOnTouchListener((v, ev) -> {
                widgetTouchSpied[0] = true;
                return false;
            });
        });
        idle();
    }

    public boolean widgetTouchSpied() {
        return widgetTouchSpied[0];
    }

    public boolean adderOpen() {
        return dashboard().adderOpen();
    }

    public void assertAdderCoversHalfBottom() {
        android.app.Dialog latest =
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        if (latest == null || !latest.isShowing()) {
            throw new AssertionError("widget adder popover is not open");
        }
        android.view.WindowManager.LayoutParams lp = latest.getWindow().getAttributes();
        int half = appContext().getResources().getDisplayMetrics().heightPixels / 2;
        if (lp.height != half) {
            throw new AssertionError("adder height " + lp.height + " != half screen " + half);
        }
        if ((lp.gravity & android.view.Gravity.BOTTOM) == 0) {
            throw new AssertionError("adder is not bottom-anchored");
        }
    }

    public boolean adderListsKind(String label, String description) {
        android.app.Dialog latest =
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        LinearLayout list = latest.findViewById(me.pompel.elauncher.R.id.adder_list);
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            TextView title = row.findViewById(me.pompel.elauncher.R.id.kind_title);
            TextView sub = row.findViewById(me.pompel.elauncher.R.id.kind_sub);
            if (title != null && sub != null
                    && label.contentEquals(title.getText().toString())
                    && sub.getText().toString().contains(description)) return true;
        }
        return false;
    }

    public boolean adderRowsShowPreview() {
        android.app.Dialog latest =
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        LinearLayout list = latest.findViewById(me.pompel.elauncher.R.id.adder_list);
        if (list.getChildCount() == 0) return false;
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            View preview = row.findViewById(me.pompel.elauncher.R.id.kind_preview);
            if (preview == null) return false;
        }
        return true;
    }

    public boolean adderRowsShowBigPreview() {
        android.app.Dialog latest =
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        LinearLayout list = latest.findViewById(me.pompel.elauncher.R.id.adder_list);
        if (list.getChildCount() == 0) return false;
        float density = appContext().getResources().getDisplayMetrics().density;
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            View preview = row.findViewById(me.pompel.elauncher.R.id.kind_preview);
            TextView sub = row.findViewById(me.pompel.elauncher.R.id.kind_sub);
            TextView title = row.findViewById(me.pompel.elauncher.R.id.kind_title);
            if (preview == null || sub == null || title == null) return false;
            // big preview slot and a description smaller than the title
            if (preview.getWidth() < 100 * density) return false;
            if (sub.getTextSize() >= title.getTextSize()) return false;
        }
        return true;
    }

    public void setDarkModePreference(boolean on) {
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext())
                .edit().putBoolean("dark_mode_preference", on).commit();
    }

    public void setAnimationsEnabled(boolean on) {
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext())
                .edit().putBoolean(me.pompel.elauncher.MainActivity.ANIMATIONS_PREF, on).commit();
    }

    /** The settings preference fragment, for preference-model lookups that
     * survive RecyclerView row virtualization. */
    private androidx.preference.PreferenceFragmentCompat settingsFragment() {
        androidx.fragment.app.Fragment f = settingsActivity().getSupportFragmentManager()
                .findFragmentById(me.pompel.elauncher.R.id.settings);
        if (!(f instanceof androidx.preference.PreferenceFragmentCompat)) {
            throw new AssertionError("settings preference fragment not found");
        }
        return (androidx.preference.PreferenceFragmentCompat) f;
    }

    public boolean animationsSwitchChecked() {
        androidx.preference.TwoStatePreference pref = animationsSwitchPreference();
        return pref.isChecked();
    }

    public void toggleAnimationsSwitch() {
        androidx.preference.TwoStatePreference pref = animationsSwitchPreference();
        // same persist+notify path the switch's click takes
        pref.setChecked(!pref.isChecked());
        idle();
    }

    private androidx.preference.TwoStatePreference animationsSwitchPreference() {
        androidx.preference.Preference pref = settingsFragment()
                .findPreference(me.pompel.elauncher.MainActivity.ANIMATIONS_PREF);
        if (!(pref instanceof androidx.preference.TwoStatePreference)) {
            throw new AssertionError("No animations switch preference");
        }
        return (androidx.preference.TwoStatePreference) pref;
    }

    /** windowAnimations of the board's widget-adder popover window (0 = none). */
    public int adderWindowAnimations() {
        int[] anims = new int[1];
        dashboardScenario.onActivity(a -> {
            android.view.Window w = ((me.pompel.elauncher.DashboardActivity) a).adderWindow();
            anims[0] = w == null ? 0 : w.getAttributes().windowAnimations;
        });
        return anims[0];
    }

    public boolean boardBackgroundIsThemeBackground() {
        boolean[] match = new boolean[1];
        dashboardScenario.onActivity(a -> {
            android.view.View root = a.findViewById(me.pompel.elauncher.R.id.board_root);
            android.util.TypedValue tv = new android.util.TypedValue();
            a.getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true);
            int expected = tv.data;
            android.graphics.drawable.Drawable bg = root.getBackground();
            if (bg instanceof android.graphics.drawable.ColorDrawable) {
                match[0] = ((android.graphics.drawable.ColorDrawable) bg).getColor() == expected;
            } else {
                // layered/rounded backgrounds: sample the center pixel color
                android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                        2, 2, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
                bg.setBounds(0, 0, 2, 2);
                bg.draw(canvas);
                match[0] = bmp.getPixel(1, 1) == expected;
            }
        });
        return match[0];
    }

    public void pressBoardEmpty() {
        dashboardScenario.onActivity(a -> {
            // with a small test display the widget may cover the board: shrink
            // the first row to its minimum so empty space exists below it
            if (a.rowCount() > 0) a.applyRowHeight(0,
                    me.pompel.elauncher.DashboardActivity.MIN_HEIGHT_DP);
            // dispatch directly on the board: the ScrollView is the touch
            // target on empty board space, where the hold/exit logic lives
            android.view.View board = a.findViewById(me.pompel.elauncher.R.id.board);
            long now = android.os.SystemClock.uptimeMillis();
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_DOWN, 5, 400, 0));
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_UP, 5, 400, 0));
        });
        idle();
    }

    private org.robolectric.shadows.ShadowActivity.IntentForResult lastBindRequest;

    public void assertSystemBindConfirmationStarted() {
        lastBindRequest = shadowOf(dashboard()).getNextStartedActivityForResult();
        if (lastBindRequest == null) throw new AssertionError("no activity started");
        if (!android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND.equals(lastBindRequest.intent.getAction())) {
            throw new AssertionError("expected the system bind confirmation, got "
                    + lastBindRequest.intent.getAction());
        }
    }

    public void confirmSystemBinding() {
        if (lastBindRequest == null) throw new AssertionError("no bind request to confirm");
        int id = lastBindRequest.intent.getIntExtra(
                android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        shadowOf(widgetManager()).bindAppWidgetId(id, widgetProvider);
        Intent result = new Intent().putExtra(
                android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        shadowOf(dashboard()).receiveResult(lastBindRequest.intent,
                android.app.Activity.RESULT_OK, result);
        idle();
    }

    public boolean boardFitsSystemWindows() {
        boolean[] fits = new boolean[1];
        dashboardScenario.onActivity(a ->
                fits[0] = a.findViewById(me.pompel.elauncher.R.id.board_root).getFitsSystemWindows());
        return fits[0];
    }

    public boolean removeHandleVisibleAndDark(int position) {
        View handle = rowOrEditBarChild(position, R.id.remove_handle);
        if (handle == null || handle.getVisibility() != View.VISIBLE) return false;
        // the badge must float: dark background + elevation shadow above the
        // widget (ink on ink with no lift = invisible)
        return handle.getBackground() != null && handle.getElevation() > 0;
    }

    private void collectDialogTexts(View view, java.util.Set<String> out) {
        if (view instanceof TextView) out.add(((TextView) view).getText().toString());
        if (view instanceof android.view.ViewGroup) {
            for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++) {
                collectDialogTexts(((android.view.ViewGroup) view).getChildAt(i), out);
            }
        }
    }

    public void clickFirstAdderKind() {
        android.app.Dialog latest =
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        LinearLayout list = latest.findViewById(me.pompel.elauncher.R.id.adder_list);
        if (list == null || list.getChildCount() == 0) {
            throw new AssertionError("widget adder popover has no kinds");
        }
        list.getChildAt(0).performClick();
        idle();
    }

    public void clickAdderKind(String label) {
        android.app.Dialog latest =
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        LinearLayout list = latest.findViewById(me.pompel.elauncher.R.id.adder_list);
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            TextView title = row.findViewById(me.pompel.elauncher.R.id.kind_title);
            if (title != null && label.contentEquals(title.getText().toString())) {
                row.performClick();
                idle();
                return;
            }
        }
        throw new AssertionError("no widget kind labeled " + label + " in the adder");
    }

    /** Completes whatever binding path the adder chose: a direct bind is
     * already done; a system handoff (bind confirmation or picker) is
     * fulfilled by binding the reserved id to the fixture provider, like the
     * real system dialog would. */
    public void resolveAdderOutcome() {
        idle();
        org.robolectric.shadows.ShadowActivity.IntentForResult req =
                shadowOf(dashboard()).getNextStartedActivityForResult();
        if (req == null) return; // direct bind happened, nothing to resolve
        String action = req.intent.getAction();
        int id = req.intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (android.appwidget.AppWidgetManager.ACTION_APPWIDGET_PICK.equals(action)
                || android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND.equals(action)) {
            shadowOf(widgetManager()).bindAppWidgetId(id, widgetProvider);
            Intent result = new Intent().putExtra(
                    android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            shadowOf(dashboard()).receiveResult(req.intent, android.app.Activity.RESULT_OK, result);
        } else {
            // not a bind handoff (e.g. the widget's configure activity):
            // keep it for the configure steps instead of consuming it
            lastUnresolvedRequest = req;
        }
        idle();
    }

    private org.robolectric.shadows.ShadowActivity.IntentForResult lastUnresolvedRequest;

    /** The pending for-result request a previous resolve left untouched:
     * peek keeps it (assert), take clears it (resolve). */
    private org.robolectric.shadows.ShadowActivity.IntentForResult peekRequest() {
        if (lastUnresolvedRequest != null) return lastUnresolvedRequest;
        return shadowOf(dashboard()).getNextStartedActivityForResult();
    }

    private org.robolectric.shadows.ShadowActivity.IntentForResult takeRequest() {
        org.robolectric.shadows.ShadowActivity.IntentForResult req = peekRequest();
        lastUnresolvedRequest = null;
        return req;
    }

    public void assertSystemPickerStarted() {
        org.robolectric.shadows.ShadowActivity.IntentForResult req = shadowOf(dashboard()).getNextStartedActivityForResult();
        if (req == null) throw new AssertionError("no activity started");
        if (!android.appwidget.AppWidgetManager.ACTION_APPWIDGET_PICK.equals(req.intent.getAction())) {
            throw new AssertionError("expected the system widget picker, got " + req.intent.getAction());
        }
    }

    /** A row child, or — for edit-bar controls like the remove handle — a
     * child of the bar shown above the edited row. */
    public View rowOrEditBarChild(int position, int childTag) {
        View row = dashboard().boardRowAt(position);
        View child = row.findViewWithTag(childTag);
        if (child == null && row.getParent() instanceof android.view.ViewGroup) {
            child = ((android.view.ViewGroup) row.getParent()).findViewWithTag(childTag);
        }
        return child;
    }

    public boolean rowChildVisible(int position, int childTag) {
        View handle = rowOrEditBarChild(position, childTag);
        return handle != null && handle.getVisibility() == View.VISIBLE;
    }

    public void rowChildPerformClick(int position, int childTag) {
        View handle = rowOrEditBarChild(position, childTag);
        if (handle == null) throw new AssertionError("no child tagged " + childTag);
        handle.performClick();
        idle();
    }

    public void dismissAdder() {
        dashboard().closeAdder();
        idle();
    }

    public void seedTwoWidgets() {
        shadowOf(widgetManager()).bindAppWidgetId(11, widgetProvider);
        shadowOf(widgetManager()).bindAppWidgetId(12, widgetProvider);
        seedBoardIds(11, 12);
        restartDashboard();
    }

    public String storedBoardIdsAsString() {
        StringBuilder sb = new StringBuilder();
        for (int id : storedBoardIds()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    public int storedHeight(int position) {
        return widgetPrefs().heightAt(position);
    }

    public int renderedRowHeightDp(int position) {
        int[] h = new int[1];
        dashboardScenario.onActivity(a -> {
            android.view.View row = a.boardRowAt(position);
            float density = a.getResources().getDisplayMetrics().density;
            h[0] = row == null ? -1 : Math.round(row.getHeight() / density);
        });
        return h[0];
    }

    /** Pixel gap between the first two stack children (the divider spacing). */
    public int stackGapPx() {
        int[] gap = new int[1];
        dashboardScenario.onActivity(a -> {
            android.view.ViewGroup stack =
                    (android.view.ViewGroup) a.findViewById(me.pompel.elauncher.R.id.board_stack);
            gap[0] = stack.getChildCount() >= 2
                    ? stack.getChildAt(1).getTop() - stack.getChildAt(0).getBottom()
                    : -1;
        });
        return gap[0];
    }

    /** Seeds the board store directly (ids only, no binding) — for stale-widget
     * scenarios. Deliberately writes the legacy BoardStore key so the
     * WidgetPrefs migration is exercised on first read. */
    public void seedBoardIds(int... ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ids[i]);
        }
        appContext().getSharedPreferences(me.pompel.elauncher.widget.WidgetPrefs.PREFS_FILE, Context.MODE_PRIVATE)
                .edit().putString("widget_ids", sb.toString()).commit();
    }

    private me.pompel.elauncher.widget.WidgetPrefs widgetPrefs() {
        return new me.pompel.elauncher.widget.WidgetPrefs(
                appContext().getSharedPreferences(
                        me.pompel.elauncher.widget.WidgetPrefs.PREFS_FILE, Context.MODE_PRIVATE));
    }

    public List<Integer> storedBoardIds() {
        return widgetPrefs().ids();
    }

    public int rowCount() {
        return dashboard().rowCount();
    }

    public boolean emptyHintVisible() {
        boolean[] visible = new boolean[1];
        dashboardScenario.onActivity(a ->
                visible[0] = a.findViewById(R.id.empty_hint).getVisibility() == View.VISIBLE);
        return visible[0];
    }

    public void longPressBoardRow(int row) {
        holdBoardRow(row);
    }

    /** Real-touch path: dispatches DOWN + hold + UP directly on the widget
     * view, whose Olauncher-style dispatcher detects the long press itself
     * and enters the widget's edit mode. */
    public void holdBoardRow(int row) {
        dashboardScenario.onActivity(a -> {
            View widget = a.widgetHostViewAt(row);
            long now = android.os.SystemClock.uptimeMillis();
            widget.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_DOWN, 10, 10, 0));
        });
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper())
                .idleFor(700 + 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        dashboardScenario.onActivity(a -> {
            View widget = a.widgetHostViewAt(row);
            long now = android.os.SystemClock.uptimeMillis();
            widget.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_UP, 10, 10, 0));
        });
        idle();
    }

    /** Keeps a widget held after the long press fired and drags it down past
     * the next row (Olauncher's drag-to-reorder), then releases. */
    public void dragBoardRowDown(int row) {
        dashboardScenario.onActivity(a -> {
            View widget = a.widgetHostViewAt(row);
            long now = android.os.SystemClock.uptimeMillis();
            widget.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_DOWN, 10, 10, 0));
        });
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper())
                .idleFor(700 + 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        dashboardScenario.onActivity(a -> {
            View widget = a.widgetHostViewAt(row);
            float height = widget.getHeight() * 2f;
            long now = android.os.SystemClock.uptimeMillis();
            widget.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_MOVE, 10, 10 + height, 0));
            widget.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_UP, 10, 10 + height, 0));
        });
        idle();
    }

    /** Replaces the board's home-gesture wiring on a row's widget with a
     * counting spy (tap-mode scenarios). */
    public void spyHomeGestures(int row) {
        homeGestureEvents.clear();
        dashboardScenario.onActivity(a -> {
            me.pompel.elauncher.widget.WidgetHostView widget = a.widgetHostViewAt(row);
            widget.setHomeGestures(ev -> homeGestureEvents.add(ev.getActionMasked()));
            widget.setOnHomeGestureCancel(() -> { });
        });
        idle();
    }

    public int homeGestureCount() {
        return homeGestureEvents.size();
    }

    public String storedTaps(int position) {
        List<Integer> ids = storedBoardIds();
        me.pompel.elauncher.widget.WidgetTaps taps = widgetPrefs().widgets().isEmpty()
                ? me.pompel.elauncher.widget.WidgetTaps.ON
                : widgetPrefs().widgets().get(Math.min(position, widgetPrefs().widgets().size() - 1)).taps();
        switch (taps) {
            case DOUBLE_TAP: return "double";
            case OFF: return "off";
            default: return "on";
        }
    }

    public void setWidgetTaps(int position, String mode) {
        me.pompel.elauncher.widget.WidgetTaps taps = "off".equals(mode)
                ? me.pompel.elauncher.widget.WidgetTaps.OFF
                : "double".equals(mode)
                        ? me.pompel.elauncher.widget.WidgetTaps.DOUBLE_TAP
                        : me.pompel.elauncher.widget.WidgetTaps.ON;
        dashboard().setWidgetTaps(position, taps);
        idle();
    }

    /** True when a MAIN/LAUNCHER entry with the given label is queryable, i.e. the
     * launcher lists it (Robolectric merges the real manifest). */
    /** A full tap straight on the widget's own view: whatever tap mode the
     * widget is in decides who sees it. */
    public void tapWidget() {
        dashboardScenario.onActivity(a -> {
            View widget = a.widgetHostViewAt(0);
            long now = android.os.SystemClock.uptimeMillis();
            widget.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_DOWN, 10, 10, 0));
            widget.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_UP, 10, 10, 0));
        });
        idle();
    }

    public boolean hasLauncherEntry(String label) {
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        android.content.pm.PackageManager pm = appContext().getPackageManager();
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (label.contentEquals(ri.loadLabel(pm))) return true;
        }
        return false;
    }

    /** The eBoard activity must not be exported: reachable only through
     * launcher gestures and explicit in-app intents (master's "make eBoard
     * internal to the launcher" behavior). */
    public boolean dashboardExported() {
        android.content.ComponentName cn = new android.content.ComponentName(
                appContext(), me.pompel.elauncher.DashboardActivity.class);
        try {
            return appContext().getPackageManager().getActivityInfo(cn, 0).exported;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /** Widget ids the board's host allocated in this scenario. */
    public int hostAllocations() {
        return me.pompel.elauncher.unit.ShadowConfigureHost.allocations();
    }

    /** Launches the app's own home activity, as the system HOME key does. */
    public void callHomeLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(appContext().getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext().startActivity(home);
        idle();
    }

    /** The home entry must declare singleTask: a home invocation then lands on
     * the existing main view instance and clears whatever sits above it (the
     * board, settings), instead of surfacing the board again. */
    public boolean mainActivityIsSingleTask() {
        android.content.pm.ActivityInfo info;
        try {
            info = appContext().getPackageManager().getActivityInfo(
                    new android.content.ComponentName(appContext(),
                            me.pompel.elauncher.MainActivity.class), 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            throw new AssertionError("main activity not found", e);
        }
        return info.launchMode == android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK;
    }

    /** Root view background of the settings screen. */
    public int settingsRootBackgroundColor() {
        int[] color = new int[1];
        settingsScenario.onActivity(a -> {
            android.view.ViewGroup content =
                    (android.view.ViewGroup) a.findViewById(android.R.id.content);
            android.graphics.drawable.Drawable bg = content.getChildAt(0).getBackground();
            color[0] = bg instanceof android.graphics.drawable.ColorDrawable
                    ? ((android.graphics.drawable.ColorDrawable) bg).getColor() : 0;
        });
        return color[0];
    }

    /** Resolved android:colorBackground of the settings activity's theme. */
    public int settingsThemeBackgroundColor() {
        int[] color = new int[1];
        settingsScenario.onActivity(a -> {
            try (android.content.res.TypedArray ta =
                         a.obtainStyledAttributes(new int[]{android.R.attr.colorBackground})) {
                color[0] = ta.getColor(0, 0);
            }
        });
        return color[0];
    }

    /** Widget configure launches that went through AppWidgetHost. */
    public int hostConfigureRequests() {
        return me.pompel.elauncher.unit.ShadowConfigureHost.configureRequests();
    }

    /** Makes the fixture provider's kind require a configure activity. */
    public void requireWidgetConfiguration() {
        if (lastProviderInfo == null) throw new AssertionError("no fixture provider installed");
        lastProviderInfo.configure = new android.content.ComponentName(
                widgetProvider.getPackageName(), "me.fixtures.Configure");
        // the framework resolves the configure component before starting it:
        // register a stub activity so the request is observable
        org.robolectric.Shadows.shadowOf(appContext().getPackageManager())
                .addActivityIfNotPresent(lastProviderInfo.configure);
    }

    /** Asserts the board started the widget configure flow and remembers
     * the pending request for deliverConfigureResult(). */
    public void configureRequested() {
        final org.robolectric.shadows.ShadowActivity.IntentForResult[] rec =
                new org.robolectric.shadows.ShadowActivity.IntentForResult[1];
        dashboardScenario.onActivity(a ->
                rec[0] = org.robolectric.Shadows.shadowOf(a)
                        .getNextStartedActivityForResult());
        if (rec[0] == null) {
            throw new AssertionError("no activity result request was started");
        }
        if (!android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                .equals(rec[0].intent.getAction())) {
            throw new AssertionError("expected the widget configure intent, got "
                    + rec[0].intent.getAction());
        }
        if (!rec[0].intent.hasExtra(
                android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID)) {
            throw new AssertionError("configure intent must carry the app widget id");
        }
        lastConfigureIntent = rec[0].intent;
    }

    /** Delivers the configure activity's result for the pending request. */
    public void deliverConfigureResult(boolean ok) {
        dashboardScenario.onActivity(a -> {
            org.robolectric.shadows.ShadowActivity shadow =
                    org.robolectric.Shadows.shadowOf(a);
            android.content.Intent pending = lastConfigureIntent;
            if (pending == null) {
                // scenarios that do not assert the request first: the pending
                // configure request is still in the activity's queue
                org.robolectric.shadows.ShadowActivity.IntentForResult ir =
                        shadow.getNextStartedActivityForResult();
                if (ir == null || !android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                        .equals(ir.intent.getAction())) {
                    throw new AssertionError("the widget configure flow was not started");
                }
                pending = ir.intent;
            }
            int id = pending.getIntExtra(
                    android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            android.content.Intent data = null;
            if (ok) {
                data = new android.content.Intent();
                data.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            }
            shadow.receiveResult(pending,
                    ok ? android.app.Activity.RESULT_OK
                            : android.app.Activity.RESULT_CANCELED,
                    data);
        });
        idle();
    }

    /** The widget host view of the row at position must carry no system
     * white rounded container background (transparent board aesthetic). */
    public boolean widgetTransparentBackground(int position) {
        boolean[] transparent = new boolean[1];
        dashboardScenario.onActivity(a -> {
            View row = a.boardRowAt(position);
            View widget = ((android.view.ViewGroup) row).getChildAt(0);
            android.graphics.drawable.Drawable bg = widget.getBackground();
            transparent[0] = bg == null
                    || (bg instanceof android.graphics.drawable.ColorDrawable
                        && ((android.graphics.drawable.ColorDrawable) bg).getColor() == 0);
        });
        return transparent[0];
    }
}