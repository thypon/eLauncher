package me.pompel.elauncher;

import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
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
import java.util.Collections;

public class MainActivity extends Activity {
    private ArrayList<App> appList;
    private ArrayList<SpannableString> appNames;
    private EditText search;
    private SharedPreferences prefs;

    private void loadApps() {
        appList.clear();
        PackageManager packageManager = getApplicationContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : packageManager.queryIntentActivities(intent, 0)) appList.add(new App(info.loadLabel(packageManager).toString(), info.activityInfo.packageName));
        Collections.sort(appList, (app1, app2) -> app1.appName.toString().compareToIgnoreCase(app2.appName.toString()));
        for (App app : appList) { appNames.add(app.appName); }
    }

    private void keyboardAction(boolean hide) {
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
    }

    private void openAppWithIntent(Intent intent, boolean change) {
        keyboardAction(true);
        search.setText("");
        if (intent != null) startActivity(intent);
        if (change) changeLayout(true, false);
    }

    @Override public void onBackPressed() { if (findViewById(R.id.AppDrawer).getVisibility() == View.VISIBLE) changeLayout(true, true); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Window window = getWindow();
        //window.addFlags(FLAG_LAYOUT_NO_LIMITS);
        //window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);

        appList = new ArrayList<>();
        appNames = new ArrayList<>();
        loadApps();

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerAdapter adapter = new recyclerAdapter(appList, new recyclerAdapter.RecyclerViewClickListener() {
            @Override public void onClick(App app) { openAppWithIntent(getPackageManager().getLaunchIntentForPackage(app.packageId), true); }
            @Override public void onLongClick(App app) {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + app.packageId));
                openAppWithIntent(intent, false);
            }
        });
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
           private boolean onTop = false;
            @Override public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCREEN_STATE_ON) {
                    onTop = !recyclerView.canScrollVertically(-1);
                    if (onTop) keyboardAction(true);
                } else if (newState == RecyclerView.SCREEN_STATE_OFF) {
                    if (!recyclerView.canScrollVertically(1)) keyboardAction(true);
                    else if (!recyclerView.canScrollVertically(-1)) {
                        if (onTop) changeLayout(true, true);
                        else keyboardAction(true);
                    }
                }
            }
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) { super.onScrolled(recyclerView, dx, dy); }
        });

        search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable editable) {}
            @Override public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { adapter.getFilter().filter(charSequence); }
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        LinearLayout homescreen = findViewById(R.id.HomeScreen);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        CharSequence[] alertApps = appNames.toArray(new CharSequence[0]);
        for (int i = 0; i < prefs.getInt("apps", 8); i++) {
            TextView textView = new TextView(this);
            textView.setTextColor(Color.BLACK);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
            textView.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));
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
        new SwipeListener(homescreen);

    }

    private boolean canMakePhoneCall() {
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:1234567890"));
        return intent.resolveActivity(packageManager) != null;
    }

    private Intent getDefaultBrowserIntent() {
        Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://"));  
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(browserIntent,PackageManager.MATCH_DEFAULT_ONLY);

        // This is the default browser's packageName
        String packageName = resolveInfo.activityInfo.packageName;

        return getPackageManager().getLaunchIntentForPackage(packageName);
    }

    private class SwipeListener implements View.OnTouchListener {
        private final GestureDetector gestureDetector;

        SwipeListener(View view) {
            GestureDetector.SimpleOnGestureListener simple = new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @SuppressWarnings("JavaReflectionMemberAccess") @SuppressLint({"WrongConstant"}) @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    float xDiff = e2.getX() - e1.getX();
                    float yDiff = e2.getY() - e1.getY();
                    if (Math.abs(xDiff) > Math.abs(yDiff) && Math.abs(xDiff) > 100 && Math.abs(velocityX) > 100) startActivity((xDiff > 0)
                            ? new Intent(canMakePhoneCall() ? Intent.ACTION_DIAL : MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                            : getDefaultBrowserIntent());
                    else if (Math.abs(yDiff) > 100 && Math.abs(velocityY) > 100) {
                        if (yDiff > 0)
                            try { Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(getSystemService("statusbar")); }
                            catch (Exception e) { e.printStackTrace(); }
                        else {
                            changeLayout(false, true);
                            keyboardAction(true);
                        }
                    }
                    return true;
                }

                @Override public boolean onDoubleTap(MotionEvent e) {
                    changeLayout(false, true);
                    keyboardAction(false);
                    return true;
                }

                @Override public void onLongPress(MotionEvent e) {
                    super.onLongPress(e);
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Number of apps (please manually restart app afterwards)");
                    final EditText input = new EditText(MainActivity.this);
                    input.setRawInputType(InputType.TYPE_CLASS_NUMBER);
                    builder.setView(input);
                    builder.setPositiveButton("Apply", (dialog1, which1) -> {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putInt("apps", Integer.parseInt(input.getText().toString()));
                        editor.apply();
                    });
                    builder.create();
                    builder.show();
                }
            };
            gestureDetector = new GestureDetector(getApplicationContext(), simple);
            view.setOnTouchListener(this);
        }
        @Override public boolean onTouch (View view, MotionEvent motionEvent) { view.performClick(); return gestureDetector.onTouchEvent(motionEvent); }
    }
}
