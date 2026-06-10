package me.pompel.elauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import java.util.ArrayList;
import java.util.List;

public class GesturePreference extends Preference {
    private static final String KEY_LEFT_GESTURE = "left_gesture_package";
    private static final String KEY_RIGHT_GESTURE = "right_gesture_package";

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
        leftButton.setText(getAppName(leftPkg));
        rightButton.setText(getAppName(rightPkg));
    }

    private String getAppName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return getContext().getString(R.string.gesture_left_default);
        }
        try {
            PackageManager pm = getContext().getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return getContext().getString(R.string.gesture_left_default);
        }
    }

    private void showAppPicker(boolean isLeft) {
        List<AppInfo> apps = loadApps();

        String[] names = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i).name;
        }

        String title = isLeft ? getContext().getString(R.string.gesture_picker_left_title) : getContext().getString(R.string.gesture_picker_right_title);

        new AlertDialog.Builder(getContext())
            .setTitle(title)
            .setItems(names, new DialogInterface.OnClickListener() {
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
        AppInfo(String name, String pkg) {
            this.name = name;
            this.packageName = pkg;
        }
    }
}