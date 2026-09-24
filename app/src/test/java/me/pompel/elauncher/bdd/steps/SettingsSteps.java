package me.pompel.elauncher.bdd.steps;

import android.content.ComponentName;
import android.content.Intent;
import androidx.appcompat.app.AppCompatDelegate;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import me.pompel.elauncher.MainActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Step definitions for the settings screen (gestures, dark mode, slot count, exit). */
public class SettingsSteps {
    private final LauncherWorld world;

    public SettingsSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("the settings screen is open")
    public void settingsScreenIsOpen() {
        world.startSettings();
    }

    @Given("the system is in night mode")
    public void systemInNightMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    @When("I tap the {word} gesture button")
    public void tapGestureButton(String side) {
        world.clickGestureButton(side);
    }

    @When("I press {string} in the dialog")
    public void pressDialogButton(String buttonText) {
        world.confirmDialog(buttonText);
    }

    @Then("the {word} gesture button shows {string}")
    public void gestureButtonShows(String side, String label) {
        assertEquals(label, world.gestureButtonLabel(side));
    }

    @Then("the {word} gesture package is stored as {string}")
    public void gesturePackageStored(String side, String pkg) {
        String stored = world.gesturePackage(side);
        if (stored == null) stored = "";
        assertEquals(pkg, stored);
    }

    @Then("the dark mode switch is {word}")
    public void darkModeSwitchIs(String state) {
        boolean on = "on".equals(state);
        assertEquals(on, world.darkModeSwitchChecked());
    }

    @When("I toggle the dark mode switch")
    public void toggleDarkModeSwitch() {
        world.toggleDarkModeSwitch();
    }

    @Then("the dark mode preference is stored as {string}")
    public void darkModePrefStored(String value) {
        assertEquals(Boolean.parseBoolean(value), world.booleanPref("dark_mode_preference"));
    }

    @When("I set the number of apps to {int}")
    public void setNumberOfApps(int value) {
        world.setNumberOfApps(value);
    }

    @Then("the number of apps preference is stored as {int}")
    public void numberOfAppsStored(int value) {
        assertEquals(value, world.intPref("number_of_apps_preference"));
    }

    @When("I swipe up on the settings screen")
    public void swipeUpOnSettings() {
        world.flingOnSettings("up");
    }

    @When("I long-press the settings screen")
    public void longPressSettings() {
        world.longPressOnSettings();
    }

    @When("I press back on the settings screen")
    public void pressBackOnSettings() {
        world.pressBackOnSettings();
    }

    @Then("the launcher is restarted")
    public void launcherRestarted() {
        final Intent[] started = new Intent[1];
        world.await(() -> {
            Intent intent = world.nextStartedActivity(); // consumes from the shadow queue
            if (intent != null) started[0] = intent;
            return started[0] != null
                    && started[0].getComponent() != null
                    && MainActivity.class.getName().equals(started[0].getComponent().getClassName());
        });
        int flags = started[0].getFlags();
        assertTrue("restart intent must carry NEW_TASK|CLEAR_TASK flags, got " + flags,
                (flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0
                        && (flags & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
    }
}