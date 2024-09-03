package me.pompel.elauncher;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.AlertDialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {
    private static final String NUMBER_OF_APPS = "number_of_apps_preference";
    private static final String DARK_MODE = "dark_mode_preference";

    private ArrayList<App> appList;
    private ArrayList<SpannableString> appNames;
    private EditText search;
    private SharedPreferences prefs;

    private static final String ELAUNCHER_TAG = "eLauncher";
    private static final String ELAUNCHER_PACKAGE = "me.pompel.elauncher";
    private static final String BIGME_LAUNCHER_AUTHORITY = "com.xrz.ebook.launcher.provider.LauncherProvider";
    private static final Uri BIGME_CONTENT_URI = Uri.parse("content://" + BIGME_LAUNCHER_AUTHORITY);
    private recyclerAdapter adapter;

    private static void queryLauncherProvider(@NonNull Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = null;

        try {
            Log.d(ELAUNCHER_TAG, "query started");
            cursor = contentResolver.query(BIGME_CONTENT_URI, null, null, null, null);
            Log.d(ELAUNCHER_TAG, "no failure yet");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    // Process each row in the cursor
                    // Example: Get data from the cursor
                    String columnName = cursor.getColumnName(0);
                    String columnValue = cursor.getString(0);
                    Log.d(ELAUNCHER_TAG, columnName + ": " + columnValue);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(ELAUNCHER_TAG, "Query failed", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void loadApps() {
        appList.clear();

        Set<String> activeProcessPackages = listActiveProcessPackages();

        PackageManager packageManager = getApplicationContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : packageManager.queryIntentActivities(intent, 0)) appList.add(new App(info.loadLabel(packageManager).toString(), info.activityInfo.packageName));
        appList.sort((app1, app2) -> app1.appName.toString().compareToIgnoreCase(app2.appName.toString()));
        for (App app : appList) {
            appNames.add(app.appName);

            if (activeProcessPackages.contains(app.packageId)) {
                app.appName.setSpan(new StyleSpan(Typeface.BOLD), 0, app.appName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    long keyboardActionTime = 0;

    private void keyboardAction(boolean hide) {
        // if this method has been called in the last 100 milliseconds, return
        long currentTime = System.currentTimeMillis();
        if (currentTime - 100 < keyboardActionTime) return;
        keyboardActionTime = currentTime;
        
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (hide) {
            search.clearFocus();
            inputManager.hideSoftInputFromWindow(search.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        } else {
            search.requestFocus();
            inputManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    private void changeLayout(boolean home, boolean animated) {
        if (!home) loadApps();
        keyboardAction(home);
        if (animated) {
            Transition transition = new Fade();
            transition.setDuration(300);
            transition.addTarget(R.id.HomeScreen);
            TransitionManager.beginDelayedTransition(findViewById(R.id.MainLayout), transition);
        }
        findViewById(R.id.HomeScreen).setVisibility(home ? View.VISIBLE : View.GONE);
        findViewById(R.id.AppDrawer).setVisibility(home ? View.GONE : View.VISIBLE);

        Set<String> activeProcessPackages = listActiveProcessPackages();

        if (home) {
            LinearLayout homescreen = findViewById(R.id.HomeScreen);

            int length = hasUsageStatsPermission() ?
                    homescreen.getChildCount() :
                    homescreen.getChildCount()-1;

            for (int i = 0; i < length; i++) {
                View view = homescreen.getChildAt(i);
                if (view instanceof TextView) {
                    TextView textView = (TextView) view;
                    String packageName = prefs.getString("p" + textView.getTag(), "");
                    if (activeProcessPackages.contains(packageName)) {
                        SpannableString spannableAppName = new SpannableString(textView.getText());
                        spannableAppName.setSpan(new StyleSpan(Typeface.BOLD), 0, spannableAppName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        textView.setText(spannableAppName);
                    }
                }
            }

            if (hasUsageStatsPermission()) {
                View view = homescreen.getChildAt(homescreen.getChildCount()-1);
                if (view instanceof TextView) {
                    TextView textView = (TextView) view;
                    textView.setText(getNameByPackageName(lastActiveProcessPackage()));
                }
            }
        } else {
            adapter.setProcessPackages(activeProcessPackages);
        }
    }

    private void openAppWithIntent(Intent intent, boolean change) {
        keyboardAction(true);
        search.setText("");
        safeStartActivity(intent);
        if (change) changeLayout(true, false);
    }

    private void safeStartActivity(Intent intent) {
        if (intent != null) startActivity(intent);
    }

    // check if you have USAGE_STATS permission
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override public void onBackPressed() { if (findViewById(R.id.AppDrawer).getVisibility() == View.VISIBLE) changeLayout(true, true); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isDarkMode = prefs.getBoolean(DARK_MODE, false);

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            setTheme(R.style.AppTheme_InvertedDark);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            setTheme(R.style.AppTheme);
        }

        setContentView(R.layout.activity_main);

        setContentView(R.layout.activity_main);

        // if it does not have USAGE_STATS and it's the first launch, open settings
        if (!hasUsageStatsPermission() && !prefs.getBoolean("firstLaunch", false)) {
            Intent usageAccessIntent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            usageAccessIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(usageAccessIntent);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("firstLaunch", true);
            editor.apply();
        }

        queryLauncherProvider(this);
        Window window = getWindow();
        //window.addFlags(FLAG_LAYOUT_NO_LIMITS);
        //window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(getColorFromAttr(androidx.appcompat.R.attr.background));
        window.setNavigationBarColor(getColorFromAttr(androidx.appcompat.R.attr.background));

        appList = new ArrayList<>();
        appNames = new ArrayList<>();
        loadApps();

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        adapter = new recyclerAdapter(appList, listActiveProcessPackages(), new recyclerAdapter.RecyclerViewClickListener() {
            @Override
            public void onClick(App app) {
                openAppWithIntent(getPackageManager().getLaunchIntentForPackage(app.packageId), true);
            }

            @Override
            public void onLongClick(App app) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + app.packageId));
                openAppWithIntent(intent, false);
            }
        });
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private boolean onTop = false;
            private boolean onBottom = false;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCREEN_STATE_ON) {
                    onTop = !recyclerView.canScrollVertically(-1);
                    onBottom = !recyclerView.canScrollVertically(1);
                    if (onTop) keyboardAction(true);
                } else if (newState == RecyclerView.SCREEN_STATE_OFF) {
                    if (!recyclerView.canScrollVertically(1)) {
                        if (onBottom) changeLayout(true, true);
                        else keyboardAction(true);
                    } else if (!recyclerView.canScrollVertically(-1)) {
                        if (onTop) changeLayout(true, true);
                        else keyboardAction(true);
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });

        search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                adapter.getFilter().filter(charSequence);
            }
        });

        LinearLayout homescreen = findViewById(R.id.HomeScreen);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        CharSequence[] alertApps = appNames.toArray(new CharSequence[0]);
        int i = 0;
        for (i = 0; i < prefs.getInt(NUMBER_OF_APPS, 8); i++) {
            TextView textView = new TextView(this);
            textView.setTextColor(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
            textView.setTypeface(Typeface.create(!hasUsageStatsPermission() ? "sans-serif" : "sans-serif-light", Typeface.NORMAL));
            textView.setPadding(0, 0, 0, 50);
            textView.setText(prefs.getString(Integer.toString(i), "App"));
            textView.setTag(i);
            textView.setLayoutParams(params);
            textView.setOnLongClickListener(v -> {
                loadApps();
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Select app");
                builder.setItems(alertApps, (dialog, which) -> {
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
                    builder1.setTitle("Set app name");
                    final EditText input = new EditText(MainActivity.this);
                    input.setText(appNames.get(which));
                    builder1.setView(input);
                    input.setTag(appList.get(which).packageId);
                    builder1.setPositiveButton("Add", (dialog1, which1) -> {
                        String name = input.getText().toString();
                        textView.setText(name);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(String.valueOf(textView.getTag()), name);
                        editor.putString("p" + textView.getTag(), String.valueOf(input.getTag()));
                        editor.apply();
                    });
                    builder1.create();
                    builder1.show();
                });
                builder.create();
                builder.show();
                return true;
            });
            textView.setOnClickListener(v -> openAppWithIntent(getPackageManager().getLaunchIntentForPackage(prefs.getString("p" + textView.getTag(), "")), true));
            homescreen.addView(textView);
        }

        if (hasUsageStatsPermission()) {
            TextView textView = new TextView(this);
            textView.setTextColor(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
            textView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            textView.setPadding(0, 0, 0, 50);
            String lastAppName = getNameByPackageName(lastActiveProcessPackage());
            textView.setText(lastAppName != null ? lastAppName : "Last App");
            textView.setTag(i);
            textView.setLayoutParams(params);
            textView.setOnClickListener(v -> {
                String pkg = lastActiveProcessPackage();
                openAppWithIntent(getPackageManager().getLaunchIntentForPackage(pkg), true);
            });
            homescreen.addView(textView);
        }

        new SwipeListener(homescreen);

    }

    private void homeUpdateUsage() {
        LinearLayout homescreen = findViewById(R.id.HomeScreen);
        Set<String> activeProcessPackages = listActiveProcessPackages();

        int length = hasUsageStatsPermission() ?
                homescreen.getChildCount() :
                homescreen.getChildCount()-1;

        for (int i = 0; i < length; i++) {
            View view = homescreen.getChildAt(i);
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                String packageName = prefs.getString("p" + textView.getTag(), "");
                if (activeProcessPackages.contains(packageName)) {
                    SpannableString spannableAppName = new SpannableString(textView.getText());
                    spannableAppName.setSpan(new StyleSpan(Typeface.BOLD), 0, spannableAppName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    textView.setText(spannableAppName);
                }
            }
        }

        if (hasUsageStatsPermission()) {
            View view = homescreen.getChildAt(homescreen.getChildCount()-1);
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                textView.setText(getNameByPackageName(lastActiveProcessPackage()));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        homeUpdateUsage();
    }

    private boolean canMakePhoneCall() {
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:1234567890"));
        return intent.resolveActivity(packageManager) != null;
    }

    private String getDefaultBrowserPackage() {
        Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://"));
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(browserIntent,PackageManager.MATCH_DEFAULT_ONLY);

        if (resolveInfo == null) return null;

        // This is the default browser's packageName
        return resolveInfo.activityInfo.packageName;
    }

    private Intent getDefaultBrowserIntent() {
        String pkg = getDefaultBrowserPackage();
        return pkg != null ? getPackageManager().getLaunchIntentForPackage(pkg) : null;
    }

    private List<ResolveInfo> getLaunchersResolveInfos() {
        List<ResolveInfo> launchers = new LinkedList<ResolveInfo>();
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addCategory(Intent.CATEGORY_DEFAULT);

        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        String currentPackageName = getPackageName();

        for (ResolveInfo resolveInfo : resolveInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (!packageName.equals(currentPackageName)) {
                launchers.add(resolveInfo);
            }
        }

        return launchers;
    }

    private String getDefaultPhoneAppPackage() {
        Intent phoneIntent = new Intent(Intent.ACTION_DIAL);
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(phoneIntent, PackageManager.MATCH_DEFAULT_ONLY);

        if (resolveInfo == null) return null;

        return resolveInfo.activityInfo.packageName;
    }

    public Intent getLastLauncherIntent() {
        ResolveInfo[] launcherResolveInfos = getLaunchersResolveInfos().toArray(new ResolveInfo[0]);
        ResolveInfo lastLauncher = launcherResolveInfos[launcherResolveInfos.length-1];

        if (lastLauncher != null) {
            String packageName = lastLauncher.activityInfo.packageName;

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setPackage(packageName);
            intent.setClassName(packageName, lastLauncher.activityInfo.name);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            return intent;
        } else {
            return null;
        }
    }

    private class SwipeListener implements View.OnTouchListener {
        private final GestureDetector gestureDetector;

        SwipeListener(View view) {
            GestureDetector.SimpleOnGestureListener simple = new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(@NonNull MotionEvent e) { return true; }
                @SuppressWarnings("JavaReflectionMemberAccess") @SuppressLint({"WrongConstant"}) @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    assert e1 != null;
                    float xDiff = e2.getX() - e1.getX();
                    float yDiff = e2.getY() - e1.getY();
                    if (Math.abs(xDiff) > Math.abs(yDiff) && Math.abs(xDiff) > 100 && Math.abs(velocityX) > 100) safeStartActivity((xDiff > 0)
                            ? new Intent(canMakePhoneCall() ? Intent.ACTION_DIAL : MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                            : getDefaultBrowserIntent());
                    else if (Math.abs(yDiff) > 100 && Math.abs(velocityY) > 100) {
                        if (yDiff > 0)
                            try { Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(getSystemService("statusbar")); }
                            catch (Exception e) { Log.d(App.class.toString(), SwipeListener.class+": onFling", e); }
                        else {
                            changeLayout(false, true);
                            keyboardAction(false);
                        }
                    }
                    return true;
                }

                @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
                    openAppWithIntent(getLastLauncherIntent(), true);
                    return true;
                }

                @Override public void onLongPress(@NonNull MotionEvent e) {
                    super.onLongPress(e);

                    // start SettingsActivity
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                }
            };
            gestureDetector = new GestureDetector(getApplicationContext(), simple);
            view.setOnTouchListener(this);
        }
        @Override public boolean onTouch (View view, MotionEvent motionEvent) { view.performClick(); return gestureDetector.onTouchEvent(motionEvent); }
    }

    private Set<String> listActiveProcessPackages() {
        Set<String> activePackages = new HashSet<>();
        UsageStatsManager mUsageStatsManager = (UsageStatsManager)getSystemService(Context.USAGE_STATS_SERVICE);
        long endTime = System.currentTimeMillis();
        long beginTime = endTime - 1000*60*60; // last 60 minutes

        // We get usage stats for the last day
        List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);

        // Sort the stats by the last time used
        if(stats != null)
        {
            SortedMap<Long,UsageStats> mySortedMap = new TreeMap<Long,UsageStats>();
            for (UsageStats usageStats : stats)
            {
                if (usageStats.getLastTimeUsed() < beginTime) continue;
                mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }
            if(!mySortedMap.isEmpty())
            {
                // topActivity =  mySortedMap.get(mySortedMap.lastKey()).getPackageName();
                // iterate over mySortedMap
                for (UsageStats usageStats : mySortedMap.values())
                {
                    activePackages.add(usageStats.getPackageName());
                }
            }
        }

        return activePackages;
    }

    private List<String> getHomescreenPackages() {
        int len = prefs.getInt(NUMBER_OF_APPS, 8);
        List<String> homescreenPackages = new LinkedList<>();

        for (int i = 0; i < len; i++) {
            String pkg = prefs.getString("p" + i, "");
            if (!pkg.isEmpty()) {
                homescreenPackages.add(pkg);
            }
        }

        return homescreenPackages;
    }

    private String getNameByPackageName(String pkg) {
        for (App app : appList) {
            if (app.packageId.equals(pkg))
                return app.appName.toString();
        }

        return null;
    }

    private String lastActiveProcessPackage() {
        Set<String> excludePackages = new HashSet<>();

        excludePackages.add(getDefaultBrowserPackage());
        getLaunchersResolveInfos().forEach(resolveInfo -> excludePackages.add(resolveInfo.activityInfo.packageName));
        excludePackages.add(getDefaultPhoneAppPackage());
        excludePackages.add(ELAUNCHER_PACKAGE);
        excludePackages.addAll(getHomescreenPackages());

        UsageStatsManager mUsageStatsManager = (UsageStatsManager)getSystemService(Context.USAGE_STATS_SERVICE);
        long endTime = System.currentTimeMillis();
        long beginTime = endTime - 1000*60*10; // last 10 minutes

        // We get usage stats for the last day
        List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);

        // Sort the stats by the last time used
        if(stats != null)
        {
            SortedMap<Long,UsageStats> mySortedMap = new TreeMap<Long,UsageStats>();
            for (UsageStats usageStats : stats)
            {
                if (usageStats.getLastTimeUsed() < beginTime) continue;
                if (excludePackages.contains(usageStats.getPackageName())) continue;
                if (getPackageManager().getLaunchIntentForPackage(usageStats.getPackageName()) == null) continue;
                mySortedMap.put(usageStats.getLastTimeUsed(),usageStats);
            }
            if(!mySortedMap.isEmpty())
            {
                UsageStats last = mySortedMap.get(mySortedMap.lastKey());
                return last != null ? last.getPackageName() : "";
            }
        }

        return "";
    }

    private int getColorFromAttr(int attr) {
        TypedValue typedValue = new TypedValue();
        int color;
        try (TypedArray a = obtainStyledAttributes(typedValue.data, new int[]{attr})) {
            color = a.getColor(0, 0);
        }
        return color;
    }
}
