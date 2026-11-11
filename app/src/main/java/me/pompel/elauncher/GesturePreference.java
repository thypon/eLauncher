package me.pompel.elauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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

    /** Sentinel value stored when the user picks the built-in eBoard dashboard
     * as a gesture target ("@" cannot appear in a real package name). */
    public static final String EBOARD_PACKAGE = "@eboard";

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
            android.content.pm.PackageManager pm = getContext().getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return getContext().getString(defaultRes);
        }
    }

    private void showAppPicker(boolean isLeft) {
        List<AppPickerAdapter.AppRow> apps = loadApps();
        // the built-in eBoard dashboard is offered first, above every external app
        // eBoard's entry shows this launcher's own icon
        apps.add(0, new AppPickerAdapter.AppRow(
                getContext().getString(R.string.eboard_name), EBOARD_PACKAGE,
                getContext().getPackageName()));

        String title = isLeft ? getContext().getString(R.string.gesture_picker_left_title) : getContext().getString(R.string.gesture_picker_right_title);

        new AlertDialog.Builder(getContext())
            .setTitle(title)
            .setAdapter(new AppPickerAdapter(getContext(), apps), new DialogInterface.OnClickListener() {
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

    private List<AppPickerAdapter.AppRow> loadApps() {
        List<AppPickerAdapter.AppRow> apps = new ArrayList<>();
        android.content.pm.PackageManager pm = getContext().getPackageManager();
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MAIN, null);
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);

        for (android.content.pm.ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
            // skip the launcher's own activities: the built-in eBoard target is
            // offered by the pinned first entry, self entries are just noise
            if (info.activityInfo.packageName.equals(getContext().getPackageName())) continue;
            apps.add(new AppPickerAdapter.AppRow(info.loadLabel(pm).toString(), info.activityInfo.packageName));
        }

        apps.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return apps;
    }
}
