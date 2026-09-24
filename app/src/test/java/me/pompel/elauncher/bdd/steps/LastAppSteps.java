package me.pompel.elauncher.bdd.steps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Steps for the last-used-app feature (usage-stats driven home screen row). */
public class LastAppSteps {

    private final LauncherWorld world;

    public LastAppSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("usage access is granted")
    public void usageAccessIsGranted() {
        world.allowUsageStats();
    }

    @Given("the app {string} was used {int} minutes ago")
    public void appWasUsed(String label, int minutesAgo) {
        world.addUsageStats(label, minutesAgo);
    }

    @Given("the system settings app was used {int} minutes ago")
    public void systemSettingsAppWasUsed(int minutesAgo) {
        long lastTimeUsed = System.currentTimeMillis() - minutesAgo * 60_000L;
        android.app.usage.UsageStats stats = org.robolectric.shadows.ShadowUsageStatsManager.UsageStatsBuilder.newBuilder()
                .setPackageName("com.android.settings")
                .setLastTimeUsed(lastTimeUsed)
                .setLastTimeStamp(lastTimeUsed)
                .build();
        android.app.usage.UsageStatsManager usm =
                (android.app.usage.UsageStatsManager)
                        world.appContext().getSystemService(android.content.Context.USAGE_STATS_SERVICE);
        org.robolectric.Shadows.shadowOf(usm)
                .addUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, stats);
    }

    @When("I tap the last app row")
    public void tapLastAppRow() {
        world.tapLastAppRow();
    }

    @Then("the last app row shows {string}")
    public void lastAppRowShows(String label) {
        world.await(() -> world.lastAppText().equals(label));
        assertEquals("last app row", label, world.lastAppText());
    }

    @Then("the home screen slot {int} shows {string} in bold")
    public void slotShowsInBold(int slot, String label) {
        assertEquals(label, world.slotText(slot));
        assertTrue("slot should be bold", world.slotTextIsBold(slot));
    }

    @Then("no last app row is shown")
    public void noLastAppRowIsShown() {
        assertEquals(world.intPref("number_of_apps_preference"), world.homeRowCount());
    }
}
