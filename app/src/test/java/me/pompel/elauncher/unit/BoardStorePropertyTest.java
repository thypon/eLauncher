package me.pompel.elauncher.unit;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import me.pompel.elauncher.BoardStore;

import static org.junit.Assert.assertEquals;

/**
 * Property-based and symbolic-boundary checks over BoardStore: the store's
 * parse/persist logic is pure, so its input space is enumerated directly
 * (corrupt raw strings, boundary ids) and exercised with seeded random
 * operation sequences against an in-memory model.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BoardStorePropertyTest {

    private BoardStore store() {
        SharedPreferences prefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences(BoardStore.PREFS_FILE, Context.MODE_PRIVATE);
        return new BoardStore(prefs);
    }

    private SharedPreferences rawPrefs() {
        return ApplicationProvider.getApplicationContext()
                .getSharedPreferences(BoardStore.PREFS_FILE, Context.MODE_PRIVATE);
    }

    /** ids() equals the model after every random add/remove, including absent
     * and repeated ids. */
    @Test
    public void randomSequenceMatchesModel() {
        BoardStore store = store();
        List<Integer> model = new ArrayList<>();
        Random random = new Random(42);
        for (int step = 0; step < 400; step++) {
            int id = random.nextInt(6) - 2; // -2..3: repeats and absent removals
            if (random.nextBoolean()) {
                store.add(id);
                model.add(id);
            } else {
                store.remove(id);
                model.remove(Integer.valueOf(id));
            }
            assertEquals("step " + step, model, store.ids());
        }
    }

    /** replaceAll round-trips every random permutation exactly. */
    @Test
    public void permutationRoundtrip() {
        BoardStore store = store();
        Random random = new Random(7);
        for (int round = 0; round < 100; round++) {
            int size = random.nextInt(9);
            List<Integer> order = new ArrayList<>();
            while (order.size() < size) {
                int id = random.nextInt(1000);
                if (!order.contains(id)) order.add(id);
            }
            store.replaceAll(order);
            assertEquals("round " + round, order, store.ids());
        }
    }

    /** Symbolic boundary enumeration of the raw ids string: garbage skipped,
     * spacing tolerated, int boundaries parsed, nothing crashes. */
    @Test
    public void corruptAndBoundaryIds() {
        Map<String, List<Integer>> cases = new HashMap<>();
        cases.put("", Collections.emptyList());
        cases.put(" ", Collections.emptyList());
        cases.put(",", Collections.emptyList());
        cases.put(",,,", Collections.emptyList());
        cases.put("x,7,,9,", Arrays.asList(7, 9));
        cases.put("-1", Collections.singletonList(-1));
        cases.put("2147483647", Collections.singletonList(Integer.MAX_VALUE));
        cases.put("-2147483648", Collections.singletonList(Integer.MIN_VALUE));
        cases.put("17, 18", Arrays.asList(17, 18));
        cases.put("007", Collections.singletonList(7));
        cases.put("1e3", Collections.emptyList());
        cases.put("17,,18,", Arrays.asList(17, 18));
        for (Map.Entry<String, List<Integer>> e : cases.entrySet()) {
            rawPrefs().edit().putString(BoardStore.KEY_IDS, e.getKey()).commit();
            assertEquals("raw: [" + e.getKey() + "]", e.getValue(), store().ids());
        }
    }

    /** Stored heights round-trip; absent ids fall back to the default. */
    @Test
    public void heightRoundtripAndDefault() {
        BoardStore store = store();
        store.add(5);
        store.add(9);
        store.setSize(5, 140);
        store.setSize(9, 600);
        assertEquals(140, store.size(5));
        assertEquals(600, store.size(9));
        store.add(11);
        assertEquals(BoardStore.DEFAULT_SIZE_DP, store.size(11));
    }

    /** Height strings tolerate garbage entries the same way ids do. */
    @Test
    public void corruptHeightEntriesSkipped() {
        BoardStore store = store();
        store.add(5);
        rawPrefs().edit().putString(BoardStore.KEY_SIZES, "5=abc,9=600").commit();
        assertEquals(BoardStore.DEFAULT_SIZE_DP, store.size(5));
        store.add(9);
        assertEquals(600, store.size(9));
    }

    /** Removing an id cleans its height entry too (no stale heights for
     * future ids, no unbounded size-map growth). */
    @Test
    public void removeCleansHeight() {
        BoardStore store = store();
        store.add(5);
        store.setSize(5, 300);
        store.remove(5);
        store.add(5);
        assertEquals(BoardStore.DEFAULT_SIZE_DP, store.size(5));
    }
}
