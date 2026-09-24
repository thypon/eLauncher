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
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.action.ViewActions;
import me.pompel.elauncher.App;
import me.pompel.elauncher.MainActivity;
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
            try {
                java.lang.reflect.Field f = org.robolectric.shadows.ShadowPackageManager.class
                        .getDeclaredField("activityFilters");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.SortedMap<android.content.ComponentName, java.util.List<android.content.IntentFilter>> filters =
                        (java.util.SortedMap<android.content.ComponentName, java.util.List<android.content.IntentFilter>>) f.get(null);
                android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_MAIN);
                filter.addCategory(Intent.CATEGORY_LAUNCHER);
                filter.addCategory(Intent.CATEGORY_DEFAULT);
                filters.put(cn, new java.util.ArrayList<>(java.util.Collections.singletonList(filter)));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Seeds prefs so tests start clean: onboarding done + 2 homescreen slots (leaves
     * free space at the bottom of the home screen for swipe gestures on small Robolectric displays). */
    public void seedOnboardingDone() {
        android.preference.PreferenceManager.getDefaultSharedPreferences(appContext()).edit()
                .putBoolean("firstLaunch", true)
                .putInt("number_of_apps_preference", 2)
                .commit();
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