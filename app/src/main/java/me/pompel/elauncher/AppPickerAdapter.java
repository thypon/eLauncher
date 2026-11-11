package me.pompel.elauncher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.LruCache;

/** Icon + label list adapter shared by every app picker dialog (the home
 * screen slot assignment and the gesture targets in Preferences). Icons
 * load lazily — each visible row kicks off a background PackageManager
 * lookup, results are cached and posted back to the main thread. */
class AppPickerAdapter extends ArrayAdapter<AppPickerAdapter.AppRow> {
    /** Single background thread shared by all icon loads (binder calls must leave the UI thread). */
    private static final ExecutorService ICON_LOADER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /** Lazily filled icon cache, keyed by the package the icon comes from.
     * Static: package icons are process-wide constants, so every picker in
     * the app shares one cache and nothing is reloaded between dialogs. */
    private static final LruCache<String, Drawable> ICON_CACHE = new LruCache<>(64);

    AppPickerAdapter(Context context, List<AppRow> apps) {
        super(context, R.layout.gesture_picker_item, apps);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = LayoutInflater.from(getContext()).inflate(R.layout.gesture_picker_item, parent, false);
        }
        AppRow app = getItem(position);
        TextView name = row.findViewById(R.id.gesture_picker_name);
        ImageView icon = row.findViewById(R.id.gesture_picker_icon);

        name.setText(app.name);
        // tag marks the package this row currently wants; a late async result for a
        // recycled row showing another app must be dropped
        icon.setTag(app.iconPackage);
        Drawable cached = ICON_CACHE.get(app.iconPackage);
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
            ICON_CACHE.put(iconPackage, drawable);
            MAIN_HANDLER.post(() -> {
                if (iconPackage.equals(icon.getTag())) {
                    icon.setImageDrawable(drawable);
                }
            });
        });
    }

    /** One picker row: an app label, the package it launches, and the package
     * whose icon is displayed (differs from packageName only for eBoard). */
    static class AppRow {
        final String name;
        final String packageName;
        final String iconPackage;

        AppRow(String name, String pkg) {
            this(name, pkg, pkg);
        }

        AppRow(String name, String pkg, String iconPackage) {
            this.name = name;
            this.packageName = pkg;
            this.iconPackage = iconPackage;
        }

        /** The dialog test helper matches rows by label; toString() is what it sees. */
        @Override
        public String toString() {
            return name;
        }
    }
}
