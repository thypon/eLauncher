package me.pompel.elauncher.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowBuild;
import org.robolectric.shadows.ShadowContentResolver;



import me.pompel.elauncher.BigmeShims;
import me.pompel.elauncher.UnlockReceiver;

/** Unit tests for the Bigme HiBreak vendor shims (classic TDD; Build.MODEL is
 * only settable through ShadowBuild, so these cannot be Gherkin scenarios). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = BigmeShimsTest.ShadowCallRecordingContentResolver.class)
public class BigmeShimsTest {

    private static final String AUTHORITY = "com.xrz.LauncherProvider";
    private static final String ACTION_HIDE = "android.intent.action.HIDE_BAKCLOGO";
    private static final String ACTION_SHOW = "android.intent.action.SHOW_BACKLOGO";

    /** Records ContentResolver.call(String authority, ...) invocations. */
    @org.robolectric.annotation.Implements(android.content.ContentResolver.class)
    public static class ShadowCallRecordingContentResolver extends ShadowContentResolver {
        static final java.util.List<String> calls = new java.util.ArrayList<>();
        static boolean boom;

        @org.robolectric.annotation.Implementation
        protected Bundle call(String authority, String method, String arg, Bundle extras) {
            calls.add(authority + "|" + method + "|" + arg);
            if (boom) throw new IllegalStateException("provider exploded");
            return new Bundle();
        }
    }

    private Context context;

    @Before
    public void setUp() {
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        ShadowCallRecordingContentResolver.calls.clear();
        ShadowCallRecordingContentResolver.boom = false;
        ShadowBuild.setModel("GenericDevice");
    }

    @Test
    public void providerCallIsSkippedOnOtherDevices() {
        BigmeShims.queryLauncherProvider(context);
        BigmeShims.registerUnlockReceiver(context);
        assertEquals(0, ShadowCallRecordingContentResolver.calls.size());
        assertTrue("no receiver on other devices",
                ShadowCallRecordingContentResolver.calls.isEmpty() || shadowOf((android.app.Application) context).getRegisteredReceivers().stream().noneMatch(r -> r.getIntentFilter().hasAction(ACTION_HIDE)));
    }

    @Test
    public void providerIsCalledWithCustomKeyOnBigme() {
        ShadowBuild.setModel("HiBreak");
        BigmeShims.queryLauncherProvider(context);
        assertEquals(1, ShadowCallRecordingContentResolver.calls.size());
        assertEquals(AUTHORITY + "|custom_key|false",
                ShadowCallRecordingContentResolver.calls.get(0));
    }

    @Test
    public void providerFailureIsSwallowed() {
        ShadowBuild.setModel("HiBreak");
        ShadowCallRecordingContentResolver.boom = true;
        BigmeShims.queryLauncherProvider(context); // must not throw
        assertEquals(1, ShadowCallRecordingContentResolver.calls.size());
    }

    @Test
    public void unlockReceiverIsRegisteredWithVendorActionsOnBigme() {
        ShadowBuild.setModel("HiBreak");
        ShadowApplication shadowApp = shadowOf((android.app.Application) context);
        int baseline = shadowApp.getRegisteredReceivers().size(); // manifest-declared count
        BigmeShims.registerUnlockReceiver(context);
        com.google.common.collect.ImmutableList<org.robolectric.shadows.ShadowApplication.Wrapper> all =
                shadowApp.getRegisteredReceivers();
        assertEquals(baseline + 1, all.size());
        IntentFilter filter = all.get(all.size() - 1).getIntentFilter();
        assertTrue(filter.hasAction(ACTION_HIDE));
        assertTrue(filter.hasAction(ACTION_SHOW));
    }

    @Test
    public void unlockReceiverOnReceiveTriggersProviderCall() {
        ShadowBuild.setModel("HiBreak");
        new UnlockReceiver().onReceive(context, new Intent(ACTION_SHOW));
        assertEquals(1, ShadowCallRecordingContentResolver.calls.size());
    }
}
