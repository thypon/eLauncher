package me.pompel.elauncher.unit;

import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import me.pompel.elauncher.App;
import me.pompel.elauncher.recyclerAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Classic unit tests for the drawer adapter's fuzzy filter internals. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class recyclerAdapterTest {
    private static class RecordingListener implements recyclerAdapter.RecyclerViewClickListener {
        final java.util.List<App> clicks = new ArrayList<>();
        final java.util.List<App> longClicks = new ArrayList<>();

        @Override public void onClick(App app) { clicks.add(app); }
        @Override public void onLongClick(App app) { longClicks.add(app); }
    }

    private ArrayList<App> apps;
    private RecordingListener listener;
    private recyclerAdapter adapter;

    @Before
    public void setUp() {
        apps = new ArrayList<>(Arrays.asList(app("Palm", "com.a"), app("Calm", "com.b"), app("Beta", "com.c")));
        listener = new RecordingListener();
        adapter = new recyclerAdapter(apps, null, listener);
    }

    private static App app(String name, String pkg) {
        return new App(name, pkg);
    }

    /** Runs the adapter filter and polls until the published count settles. */
    private void filter(String query, int expectedCount) throws Exception {
        adapter.getFilter().filter(query);
        long deadline = System.currentTimeMillis() + 5000;
        while (adapter.getItemCount() != expectedCount) {
            // publishResults is delivered on the main looper
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("filter did not settle to " + expectedCount
                        + " (is " + adapter.getItemCount() + ")");
            }
            Thread.sleep(20);
        }
        Thread.sleep(200); // let publishResults finish side effects (auto-launch clicks)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void emptyQueryKeepsAllApps() throws Exception {
        filter("", 3);
        assertTrue(listener.clicks.isEmpty());
    }

    @Test
    public void fuzzySubsequenceMatchesNonAdjacentChars() throws Exception {
        filter("am", 2); // matches "Palm" (a→m across 'l') and "Calm", not "Beta"
        assertTrue(listener.clicks.isEmpty());
    }

    @Test
    public void noMatchYieldsEmptyListWithoutClick() throws Exception {
        filter("zzz", 0);
        assertTrue(listener.clicks.isEmpty());
    }

    @Test
    public void exactMatchAutoLaunchesApp() throws Exception {
        // exact match must launch exactly once (the single-result rule must not
        // fire a second click for the same query)
        filter("Beta", 1);
        assertEquals(1, listener.clicks.size());
        assertEquals("com.c", listener.clicks.get(0).packageId);
    }

    @Test
    public void singleRemainingResultAutoLaunchesApp() throws Exception {
        filter("be", 1); // only "Beta" contains b→e
        assertEquals(1, listener.clicks.size());
        assertEquals("com.c", listener.clicks.get(0).packageId);
    }

    @Test
    public void matchedCharactersGetUnderlineSpans() throws Exception {
        filter("am", 2);
        App palm = apps.get(0);
        UnderlineSpan[] spans = palm.appName.getSpans(0, palm.appName.length(), UnderlineSpan.class);
        boolean second = false, fourth = false;
        for (UnderlineSpan span : spans) {
            int start = palm.appName.getSpanStart(span);
            second |= start == 1;
            fourth |= start == 3;
        }
        assertTrue("expected underlines at index 1 and 3 of 'Palm'", second && fourth);
    }

    @Test
    public void bindPreservesUnderlinesAndReappliesBoldOnly() {
        App beta = apps.get(1);
        beta.appName.setSpan(new UnderlineSpan(), 0, 1, 0);
        adapter.setProcessPackages(new HashSet<>(Arrays.asList("com.b")));

        LinearLayout parent = new LinearLayout(new android.view.ContextThemeWrapper(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                me.pompel.elauncher.R.style.AppTheme));
        recyclerAdapter.AppViewHolder holder = adapter.createViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 1);

        // underline spans survive the bind (shared appName state must not be wiped)
        assertEquals(1, beta.appName.getSpans(0, beta.appName.length(), UnderlineSpan.class).length);
        // bold for a recently-used app is (re)applied exactly once
        StyleSpan[] bold = beta.appName.getSpans(0, beta.appName.length(), StyleSpan.class);
        assertEquals(1, bold.length);
        assertEquals(0, beta.appName.getSpanStart(bold[0]));
        assertEquals(4, beta.appName.getSpanEnd(bold[0]));

        // a non-active app keeps its underline and gains no bold
        App palm = apps.get(0);
        palm.appName.setSpan(new UnderlineSpan(), 1, 2, 0);
        adapter.onBindViewHolder(adapter.createViewHolder(parent, 0), 0);
        assertEquals(1, palm.appName.getSpans(0, palm.appName.length(), UnderlineSpan.class).length);
        assertEquals(0, palm.appName.getSpans(0, palm.appName.length(), StyleSpan.class).length);
    }
}