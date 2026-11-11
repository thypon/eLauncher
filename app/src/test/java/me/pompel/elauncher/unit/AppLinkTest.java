package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for the AppLink sentinel codec. */
@RunWith(RobolectricTestRunner.class)
public class AppLinkTest {
    @Test
    public void encodeDecodeRoundTripsTitleAndUri() {
        String value = AppLink.encode("Hacker News", "https://news.ycombinator.com");
        assertTrue(AppLink.isLink(value));
        AppLink link = AppLink.decode(value);
        assertEquals("Hacker News", link.title());
        assertEquals("https://news.ycombinator.com", link.uri());
    }

    @Test
    public void decodeSurvivesColonsAndSpacesInTheTitle() {
        AppLink link = AppLink.decode(AppLink.encode("News: Round 2", "market://x"));
        assertEquals("News: Round 2", link.title());
        assertEquals("market://x", link.uri());
    }

    @Test
    public void decodeHandlesValueWithoutSeparator() {
        AppLink link = AppLink.decode("@link:just-a-uri");
        assertEquals("", link.title());
        assertEquals("just-a-uri", link.uri());
    }

    @Test
    public void packageNamesAreNeverLinks() {
        assertFalse(AppLink.isLink(null));
        assertFalse(AppLink.isLink(""));
        assertFalse(AppLink.isLink("com.fixtures.cool"));
        assertFalse(AppLink.isLink(GesturePreference.EBOARD_PACKAGE));
    }

    @Test
    public void bareHostsBecomeWebAddresses() {
        assertEquals("https://example.com", AppLink.normalizeUri("example.com"));
        assertEquals("https://example.com/path", AppLink.normalizeUri(" example.com/path "));
    }

    @Test
    public void addressesWithASchemeAreKeptVerbatim() {
        assertEquals("https://example.com", AppLink.normalizeUri("https://example.com"));
        assertEquals("market://details?id=org.fdroid.fdroid", AppLink.normalizeUri("market://details?id=org.fdroid.fdroid"));
        assertEquals("mailto:a@b.c", AppLink.normalizeUri("mailto:a@b.c"));
    }
}
