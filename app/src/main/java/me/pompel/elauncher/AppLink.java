package me.pompel.elauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;

/** A user-added link target: gestures and home screen slots can point at an
 * arbitrary URI (a web address or an app URI such as market:) under a
 * user-chosen title. Launching goes through the system's ACTION_VIEW handler
 * (browser, store app, ...) instead of a package launch.
 *
 * Links are stored wherever a package name is stored, as a "@link:" sentinel
 * value: "@link:" + Uri.encode(title) + ":" + uri ("@" cannot appear in a
 * real package name or scheme, so it cannot collide with either). */
public final class AppLink {
    public static final String PREFIX = "@link:";

    /** Receives the confirmed title + address of {@link #showDialog}. */
    interface OnLinkListener { void onLink(String title, String uri); }

    private final String title;
    private final String uri;

    private AppLink(String title, String uri) {
        this.title = title;
        this.uri = uri;
    }

    public String title() { return title; }

    public String uri() { return uri; }

    public static boolean isLink(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String encode(String title, String uri) {
        return PREFIX + Uri.encode(title) + ":" + uri;
    }

    public static AppLink decode(String value) {
        String rest = value.substring(PREFIX.length());
        int separator = rest.indexOf(':');
        if (separator < 0) return new AppLink("", rest);
        return new AppLink(Uri.decode(rest.substring(0, separator)), rest.substring(separator + 1));
    }

    /** Bare hosts ("example.com") become web addresses; anything that already
     * carries a scheme ("https:", "market:", "mailto:") is kept verbatim. */
    public static String normalizeUri(String raw) {
        String uri = raw.trim();
        if (uri.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) return uri;
        return "https://" + uri;
    }

    /** Title + address input dialog shared by the gesture pickers and the
     * home screen slot picker. */
    public static void showDialog(Context context, OnLinkListener listener) {
        showDialog(context, "", "", listener);
    }

    public static void showDialog(Context context, String seedTitle, String seedUri, OnLinkListener listener) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        container.setPadding(pad, 0, pad, 0);

        EditText nameInput = new EditText(context);
        nameInput.setHint(R.string.link_hint_name);
        nameInput.setText(seedTitle);
        EditText addressInput = new EditText(context);
        addressInput.setHint(R.string.link_hint_uri);
        addressInput.setText(seedUri);

        container.addView(nameInput);
        container.addView(addressInput);

        new AlertDialog.Builder(context)
            .setTitle(R.string.link_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.link_dialog_add, (dialog, which) ->
                listener.onLink(nameInput.getText().toString(), normalizeUri(addressInput.getText().toString())))
            .setNegativeButton(R.string.gesture_picker_cancel, null)
            .show();
    }
}
