package me.pompel.elauncher;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableString;

import java.util.Objects;

public class App implements AndroidClickable {
    private SpannableString appName;
    private String packageId;

    public App(String appName, String packageId) {
        this.appName = new SpannableString(appName);
        this.packageId = packageId;
    }

    public SpannableString label() {
        return appName;
    }

    public void click(Context ctx) {
        Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(packageId);
        if (intent != null) ctx.startActivity(intent);
    }

    public void longClick(Context ctx) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageId));
        ctx.startActivity(intent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        App app = (App) o;
        return Objects.equals(packageId, app.packageId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(packageId);
    }
}