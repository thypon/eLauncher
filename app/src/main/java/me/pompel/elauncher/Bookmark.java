package me.pompel.elauncher;

import android.content.Context;
import android.net.Uri;
import android.text.SpannableString;

public class Bookmark implements AndroidClickable {
    private SpannableString label;
    private Uri uri;

    public Bookmark(String title, String href) {
        label = new SpannableString(title);
    }

    @Override
    public SpannableString label() {
        return label;
    }

    @Override
    public void click(Context ctx) {

    }

    @Override
    public void longClick(Context ctx) {

    }
}
