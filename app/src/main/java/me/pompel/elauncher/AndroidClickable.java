package me.pompel.elauncher;

import android.content.Context;
import android.text.SpannableString;

public interface AndroidClickable {
    // label used to order inside an Adapter
    SpannableString label();

    // click the AndroidClickable using Android APIs
    void click(Context ctx);

    // longClick the AndroidClickable using Android APIs
    void longClick(Context ctx);
}
