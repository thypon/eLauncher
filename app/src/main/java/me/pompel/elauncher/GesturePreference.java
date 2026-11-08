package me.pompel.elauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GesturePreference extends Preference {
    private static final String KEY_LEFT_GESTURE = "left_gesture_package";
    private static final String KEY_RIGHT_GESTURE = "right_gesture_package";

    /** Sentinel value stored when the user picks the built-in eBoard dashboard
     * as a gesture target ("@" cannot appear in a real package name). */
    public static final String EBOARD_PACKAGE = "@eboard";

    /** Single background thread shared by all icon loads (binder calls must leave the UI thread). */
    private static final ExecutorService ICON_LOADER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /** Lazily filled icon cache, keyed by the package the icon comes from. Lives with this
     * preference instance, so nothing outlives the settings screen (no static context leak). */
    private final LruCache<String, Drawable> iconCache = new LruCache<>(64);

    public GesturePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_gesture);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        Button leftButton = (Button) holder.itemView.findViewById(R.id.left_gesture_button);
        Button rightButton = (Button) holder.itemView.findViewById(R.id.right_gesture_button);

        updateButtonLabels(leftButton, rightButton);

        leftButton.setOnClickListener(v -> showAppPicker(true));
        rightButton.setOnClickListener(v -> showAppPicker(false));
    }

    private void updateButtonLabels(Button leftButton, Button rightButton) {
        SharedPreferences prefs = getSharedPreferences();
        String leftPkg = prefs.getString(KEY_LEFT_GESTURE, "");
        String rightPkg = prefs.getString(KEY_RIGHT_GESTURE, "");
        leftButton.setText(getAppName(leftPkg, R.string.gesture_left_default));
        rightButton.setText(getAppName(rightPkg, R.string.gesture_right_default));
    }

    private String getAppName(String packageName, int defaultRes) {
        if (packageName == null || packageName.isEmpty()) {
            return getContext().getString(defaultRes);
        }
        if (EBOARD_PACKAGE.equals(packageName)) {
            return getContext().getString(R.string.eboard_name);
        }
        try {
            PackageManager pm = getContext().getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return getContext().getString(defaultRes);
        }
    }

    private void showAppPicker(boolean isLeft) {
        List<AppInfo> apps = loadApps();
        // the built-in eBoard dashboard is offered first, above every external app
        AppInfo eboard = new AppInfo(getContext().getString(R.string.eboard_name), EBOARD_PACKAGE);
        // eBoard's entry shows this launcher's own icon
        eboard.iconPackage = getContext().getPackageName();
        apps.add(0, eboard);

        String title = isLeft ? getContext().getString(R.string.gesture_picker_left_title) : getContext().getString(R.string.gesture_picker_right_title);

        new AlertDialog.Builder(getContext())
            .setTitle(title)
            .setAdapter(new GestureAppAdapter(getContext(), apps), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getSharedPreferences().edit().putString(isLeft ? KEY_LEFT_GESTURE : KEY_RIGHT_GESTURE, apps.get(which).packageName).apply();
                    notifyChanged();
                }
            })
            .setNeutralButton(R.string.gesture_picker_reset, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    resetGesture(isLeft);
                }
            })
            .setNegativeButton(R.string.gesture_picker_cancel, null)
            .show();
    }

    private void resetGesture(boolean isLeft) {
        SharedPreferences prefs = getSharedPreferences();
        prefs.edit().putString(isLeft ? KEY_LEFT_GESTURE : KEY_RIGHT_GESTURE, "").apply();

        String msg = isLeft
            ? getContext().getString(R.string.gesture_reset_left)
            : getContext().getString(R.string.gesture_reset_right);
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();

        notifyChanged();
    }

    private List<AppInfo> loadApps() {
        List<AppInfo> apps = new ArrayList<>();
        PackageManager pm = getContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo info : resolveInfos) {
            // skip the launcher's own activities: the built-in eBoard target is
            // offered by the pinned first entry, self entries are just noise
            if (info.activityInfo.packageName.equals(getContext().getPackageName())) continue;
            String name = info.loadLabel(pm).toString();
            String pkg = info.activityInfo.packageName;
            apps.add(new AppInfo(name, pkg));
        }

        apps.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return apps;
    }

    private static class AppInfo {
        String name;
        String packageName;
        /** Package whose icon is displayed (differs from packageName only for eBoard). */
        String iconPackage;

        AppInfo(String name, String pkg) {
            this.name = name;
            this.packageName = pkg;
            this.iconPackage = pkg;
        }

        /** The dialog test helper matches rows by label; toString() is what it sees. */
        @Override
        public String toString() {
            return name;
        }
    }

    /** Adapter for the gesture picker list: one icon + label per app. Icons load lazily —
     * each row kicks off a background PackageManager lookup only when it becomes visible,
     * results are cached and posted back to the main thread. */
    private class GestureAppAdapter extends ArrayAdapter<AppInfo> {
        GestureAppAdapter(Context context, List<AppInfo> apps) {
            super(context, R.layout.gesture_picker_item, apps);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(getContext()).inflate(R.layout.gesture_picker_item, parent, false);
            }
            AppInfo app = getItem(position);
            TextView name = row.findViewById(R.id.gesture_picker_name);
            ImageView icon = row.findViewById(R.id.gesture_picker_icon);

            name.setText(app.name);
            // tag marks the package this row currently wants; a late async result for a
            // recycled row showing another app must be dropped
            icon.setTag(app.iconPackage);
            Drawable cached = iconCache.get(app.iconPackage);
            if (cached != null) {
                icon.setImageDrawable(cached);
            } else {
                icon.setImageDrawable(null);
                loadIconLazily(app.iconPackage, icon);
            }
            return row;
        }

        /** Resolves the icon off the UI thread, caches it, and binds it to the row only
         * if the row still expects this package when the result arrives. */
        private void loadIconLazily(String iconPackage, ImageView icon) {
            PackageManager pm = getContext().getPackageManager();
            ICON_LOADER.execute(() -> {
                Drawable drawable;
                try {
                    drawable = pm.getApplicationIcon(iconPackage);
                } catch (PackageManager.NameNotFoundException e) {
                    return; // package vanished while the picker was open: leave the row iconless
                }
                // real PackageManager always falls back to a default icon; a null here
                // only happens for icon-less packages (Robolectric fixtures) — skip
                if (drawable == null) return;
                iconCache.put(iconPackage, drawable);
                MAIN_HANDLER.post(() -> {
                    if (iconPackage.equals(icon.getTag())) {
                        icon.setImageDrawable(drawable);
                    }
                });
            });
        }
    }
}