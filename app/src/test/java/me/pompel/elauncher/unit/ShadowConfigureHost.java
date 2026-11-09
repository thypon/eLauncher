package me.pompel.elauncher.unit;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAppWidgetHost;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extends the stock AppWidgetHost shadow so the board's add flow can be
 * observed the way a real launcher experiences it.
 *
 * The real AppWidgetHost.startAppWidgetConfigureActivityForResult asks the
 * system appwidget service for a PendingIntent (createAppWidgetConfigIntentSender)
 * and starts it; on Robolectric the binder service is null and the real method
 * silently returns, so nothing is observable. This shadow emulates what the
 * system service would launch: the configure activity for the widget id, with
 * EXTRA_APPWIDGET_ID in the intent — the same observable behavior the board's
 * onActivityResult sees on device. It also counts allocations so the suite can
 * pin the "no second allocation behind the bind dialog" contract.
 */
@Implements(android.appwidget.AppWidgetHost.class)
public class ShadowConfigureHost extends ShadowAppWidgetHost {

    public static final AtomicInteger allocations = new AtomicInteger();
    public static final AtomicInteger configureRequests = new AtomicInteger();

    public static void resetAllocations() {
        allocations.set(0);
        configureRequests.set(0);
    }

    public static int allocations() {
        return allocations.get();
    }

    public static int configureRequests() {
        return configureRequests.get();
    }

    @Implementation
    protected int allocateAppWidgetId() {
        allocations.incrementAndGet();
        return super.allocateAppWidgetId();
    }

    @Implementation
    protected void startAppWidgetConfigureActivityForResult(Activity activity,
            int appWidgetId, int intentFlags, int requestCode, Bundle options) {
        configureRequests.incrementAndGet();
        // the system service builds this intent (non-exported configure
        // activities are startable only through its sender); the emulation
        // keeps the same observable surface: action + widget id extra
        Intent configure = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        activity.startActivityForResult(configure, requestCode);
    }
}
