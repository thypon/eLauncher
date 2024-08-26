package me.pompel.elauncher;

import android.text.SpannableString;

public class App {
    public SpannableString appName;
    public String packageId;

    public App(String appName, String packageId) {
        this.appName = new SpannableString(appName);
        this.packageId = packageId;
    }
}