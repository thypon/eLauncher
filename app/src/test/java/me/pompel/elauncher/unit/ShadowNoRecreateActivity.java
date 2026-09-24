package me.pompel.elauncher.unit;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowActivity;

/**
 * Breaks the Robolectric recreate loop (robolectric/robolectric#4810): when
 * AppCompatDelegate applies a local night-mode change it calls Activity.recreate(),
 * and Robolectric re-runs onCreate -> setDefaultNightMode -> recreate -> ... forever.
 * MainActivity applies its theme via setTheme() itself, so a no-op recreate is safe
 * for these scenarios.
 */
@Implements(android.app.Activity.class)
public class ShadowNoRecreateActivity extends ShadowActivity {

    @Implementation
    @Override protected void recreate() {
        // no-op: prevents infinite recreate loop under Robolectric (issue #4810)
    }
}
