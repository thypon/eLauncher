package me.pompel.elauncher.unit;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import me.pompel.elauncher.BoardStore;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BoardStoreTest {

    private BoardStore store() {
        SharedPreferences prefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences(BoardStore.PREFS_FILE, Context.MODE_PRIVATE);
        return new BoardStore(prefs);
    }

    private SharedPreferences rawPrefs() {
        return ApplicationProvider.getApplicationContext()
                .getSharedPreferences(BoardStore.PREFS_FILE, Context.MODE_PRIVATE);
    }

    @Test
    public void emptyByDefault() {
        assertEquals(Collections.emptyList(), store().ids());
    }

    @Test
    public void addKeepsInsertionOrder() {
        BoardStore store = store();
        store.add(7);
        store.add(2);
        assertEquals(Arrays.asList(7, 2), store.ids());
    }

    @Test
    public void removeMiddleKeepsOrder() {
        BoardStore store = store();
        store.add(1);
        store.add(2);
        store.add(3);
        store.remove(2);
        assertEquals(Arrays.asList(1, 3), store.ids());
    }

    @Test
    public void removeAbsentIdKeepsOrder() {
        BoardStore store = store();
        store.add(5);
        store.add(9);
        store.remove(4);
        assertEquals(Arrays.asList(5, 9), store.ids());
    }

    @Test
    public void removeLastEmptiesStore() {
        BoardStore store = store();
        store.add(5);
        store.remove(5);
        assertEquals(Collections.emptyList(), store.ids());
    }

    @Test
    public void corruptEntriesAreSkipped() {
        rawPrefs().edit().putString(BoardStore.KEY_IDS, "x,7,,9,").commit();
        assertEquals(Arrays.asList(7, 9), store().ids());
    }
}
