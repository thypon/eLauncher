package me.pompel.elauncher.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import me.pompel.elauncher.widget.WidgetEntry;
import me.pompel.elauncher.widget.WidgetPrefs;
import me.pompel.elauncher.widget.WidgetTaps;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WidgetPrefsTest {

    private WidgetPrefs prefs() {
        SharedPreferences prefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences(WidgetPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        return new WidgetPrefs(prefs);
    }

    private SharedPreferences rawPrefs() {
        return ApplicationProvider.getApplicationContext()
                .getSharedPreferences(WidgetPrefs.PREFS_FILE, Context.MODE_PRIVATE);
    }

    @Test
    public void emptyByDefault() {
        assertEquals(Collections.emptyList(), prefs().widgets());
    }

    @Test
    public void parsesOlauncherFormat() {
        rawPrefs().edit().putString(WidgetPrefs.KEY_WIDGETS, "42:200:1;7:150:2").commit();
        List<WidgetEntry> widgets = prefs().widgets();
        assertEquals(2, widgets.size());
        assertEquals(42, widgets.get(0).id());
        assertEquals(200, widgets.get(0).heightDp());
        assertEquals(WidgetTaps.ON, widgets.get(0).taps());
        assertEquals(7, widgets.get(1).id());
        assertEquals(150, widgets.get(1).heightDp());
        assertEquals(WidgetTaps.DOUBLE_TAP, widgets.get(1).taps());
    }

    @Test
    public void parsesOlauncherFiveFieldFormat() {
        // Olauncher writes "id:height:width:top:taps"; its parser tolerates
        // extra fields, and so must ours tolerate this richer shape
        rawPrefs().edit().putString(WidgetPrefs.KEY_WIDGETS, "42:200:0:-1:2").commit();
        List<WidgetEntry> widgets = prefs().widgets();
        assertEquals(1, widgets.size());
        assertEquals(42, widgets.get(0).id());
        assertEquals(200, widgets.get(0).heightDp());
        assertEquals(WidgetTaps.DOUBLE_TAP, widgets.get(0).taps());
    }

    @Test
    public void migratesLegacyBoardStore() {
        rawPrefs().edit().putString(WidgetPrefs.LEGACY_KEY_IDS, "42,7").commit();
        rawPrefs().edit().putString(WidgetPrefs.LEGACY_KEY_SIZES, "42=200,7=150").commit();
        List<WidgetEntry> widgets = prefs().widgets();
        assertEquals(2, widgets.size());
        assertEquals(200, widgets.get(0).heightDp());
        assertEquals(150, widgets.get(1).heightDp());
        assertEquals(WidgetTaps.ON, widgets.get(1).taps());
        // the legacy keys are consumed by the migration
        assertTrue(!rawPrefs().contains(WidgetPrefs.LEGACY_KEY_IDS));
        assertTrue(rawPrefs().contains(WidgetPrefs.KEY_WIDGETS));
    }

    @Test
    public void migrationUsesDefaultHeightWhenUnset() {
        rawPrefs().edit().putString(WidgetPrefs.LEGACY_KEY_IDS, "42").commit();
        assertEquals(WidgetPrefs.DEFAULT_HEIGHT_DP, prefs().widgets().get(0).heightDp());
    }

    @Test
    public void corruptEntriesAreSkipped() {
        rawPrefs().edit().putString(WidgetPrefs.KEY_WIDGETS, "abc:10:1;;x:-5:2;7:150:2;9:abc:1").commit();
        List<WidgetEntry> widgets = prefs().widgets();
        assertEquals(1, widgets.size());
        assertEquals(7, widgets.get(0).id());
        assertEquals(150, widgets.get(0).heightDp());
    }

    @Test
    public void negativeIdsAreSkipped() {
        rawPrefs().edit().putString(WidgetPrefs.KEY_WIDGETS, "-1:100:1;7:150:2").commit();
        assertEquals(1, prefs().widgets().size());
    }

    @Test
    public void addKeepsInsertionOrder() {
        WidgetPrefs prefs = prefs();
        prefs.add(7, 150);
        prefs.add(2, 300);
        assertEquals(Arrays.asList(7, 2), prefs.ids());
        assertEquals(150, prefs.heightAt(0));
        assertEquals(WidgetTaps.ON, prefs.widgets().get(0).taps());
    }

    @Test
    public void addDuplicateIdIsNoOp() {
        WidgetPrefs prefs = prefs();
        prefs.add(7, 150);
        prefs.add(7, 300);
        assertEquals(Arrays.asList(7), prefs.ids());
        assertEquals(150, prefs.heightAt(0));
    }

    @Test
    public void removeMiddleKeepsOrder() {
        WidgetPrefs prefs = prefs();
        prefs.add(1, 150);
        prefs.add(2, 150);
        prefs.add(3, 150);
        prefs.remove(2);
        assertEquals(Arrays.asList(1, 3), prefs.ids());
    }

    @Test
    public void removeAbsentIdKeepsOrder() {
        WidgetPrefs prefs = prefs();
        prefs.add(5, 150);
        prefs.add(9, 150);
        prefs.remove(4);
        assertEquals(Arrays.asList(5, 9), prefs.ids());
    }

    @Test
    public void moveReordersAndPersists() {
        WidgetPrefs prefs = prefs();
        prefs.add(1, 150);
        prefs.add(2, 150);
        prefs.move(1, 0);
        assertEquals(Arrays.asList(2, 1), prefs.ids());
        assertEquals(Arrays.asList(2, 1), new WidgetPrefs(rawPrefs()).ids());
    }

    @Test
    public void moveOutOfRangeIsNoOp() {
        WidgetPrefs prefs = prefs();
        prefs.add(1, 150);
        prefs.move(-1, 0);
        prefs.move(0, 5);
        prefs.move(0, 0);
        assertEquals(Arrays.asList(1), prefs.ids());
    }

    @Test
    public void setHeightClampsAndPersists() {
        WidgetPrefs prefs = prefs();
        prefs.add(1, 200);
        prefs.setHeight(1, 50);
        assertEquals(WidgetPrefs.MIN_HEIGHT_DP, prefs.heightAt(0));
        prefs.setHeight(1, 100000);
        assertEquals(WidgetPrefs.MAX_HEIGHT_DP, prefs.heightAt(0));
        assertEquals(WidgetPrefs.MAX_HEIGHT_DP, new WidgetPrefs(rawPrefs()).heightAt(0));
    }

    @Test
    public void setHeightUnknownIdIsNoOp() {
        WidgetPrefs prefs = prefs();
        prefs.setHeight(99, 300);
        assertEquals(Collections.emptyList(), prefs.widgets());
    }

    @Test
    public void setTapsPersists() {
        WidgetPrefs prefs = prefs();
        prefs.add(1, 200);
        prefs.setTaps(1, WidgetTaps.OFF);
        assertEquals(WidgetTaps.OFF, prefs.widgets().get(0).taps());
        assertEquals(WidgetTaps.OFF, new WidgetPrefs(rawPrefs()).widgets().get(0).taps());
    }

    @Test
    public void saveRoundTrips() {
        WidgetPrefs prefs = prefs();
        prefs.add(42, 200);
        prefs.add(7, 150);
        prefs.move(1, 0);
        prefs.setHeight(42, 300);
        prefs.setTaps(7, WidgetTaps.DOUBLE_TAP);
        prefs.remove(42);
        prefs.add(9, 480);

        List<WidgetEntry> widgets = new WidgetPrefs(rawPrefs()).widgets();
        assertEquals(2, widgets.size());
        assertEquals(7, widgets.get(0).id());
        assertEquals(150, widgets.get(0).heightDp());
        assertEquals(WidgetTaps.DOUBLE_TAP, widgets.get(0).taps());
        assertEquals(9, widgets.get(1).id());
        assertEquals(480, widgets.get(1).heightDp());
        assertEquals(WidgetTaps.ON, widgets.get(1).taps());
    }

    /** Port of BoardStorePropertyTest: random operation sequences must
     * round-trip through the persisted string exactly. */
    @Test
    public void propertyRandomSequencesRoundTrip() {
        Random random = new Random(42);
        for (int iteration = 0; iteration < 50; iteration++) {
            WidgetPrefs prefs = prefs();
            List<WidgetEntry> model = new java.util.ArrayList<>();
            int nextId = 1;
            for (int step = 0; step < 40; step++) {
                int op = random.nextInt(5);
                if (op == 0 && nextId <= 12) {
                    int id = nextId++;
                    int height = 140 + random.nextInt(461);
                    prefs.add(id, height);
                    model.add(new WidgetEntry(id, height, WidgetTaps.ON));
                } else if (!model.isEmpty()) {
                    int index = random.nextInt(model.size());
                    int id = model.get(index).id();
                    if (op == 1) {
                        prefs.remove(id);
                        model.remove(index);
                    } else if (op == 2) {
                        int height = 140 + random.nextInt(461);
                        prefs.setHeight(id, height);
                        model.set(index, model.get(index).withHeight(height));
                    } else if (op == 3) {
                        WidgetTaps taps = WidgetTaps.values()[random.nextInt(3)];
                        prefs.setTaps(id, taps);
                        model.set(index, model.get(index).withTaps(taps));
                    } else {
                        int to = random.nextInt(model.size());
                        prefs.move(index, to);
                        model.add(to, model.remove(index));
                    }
                }
                assertEquals("iteration " + iteration + " step " + step,
                        model, new WidgetPrefs(rawPrefs()).widgets());
            }
            rawPrefs().edit().clear().commit();
        }
    }
}
