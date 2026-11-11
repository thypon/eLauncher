// Ported from Olauncher (https://github.com/tanujnotes/Olauncher), GPL-3.0.
// Modified for eLauncher: eInk (no crossfade), Java, single-column board.
package me.pompel.elauncher.widget;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;

/** The eBoard's AppWidgetHost. Keeps eBoard's HOST_ID so previously placed
 * widgets bind to the new view classes unchanged. */
public class WidgetHost extends AppWidgetHost {
    /** Distinguishes eBoard-hosted widgets from other hosts' widget ids. */
    public static final int HOST_ID = 0x0E13;

    public WidgetHost(Context context) {
        super(context, HOST_ID);
    }

    @Override
    protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                                             AppWidgetProviderInfo info) {
        return new WidgetHostView(context);
    }
}
