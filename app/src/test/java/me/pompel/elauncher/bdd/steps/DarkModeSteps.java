package me.pompel.elauncher.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import static org.junit.Assert.assertEquals;

/** Steps for the dark-mode feature: theme driven by the dark_mode preference. */
public class DarkModeSteps {

    private static final String DARK_MODE = "dark_mode_preference";

    private final LauncherWorld world;

    public DarkModeSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("dark mode is turned on")
    public void darkModeOn() {
        world.setBooleanPref("dark_mode_preference", true);
    }

    @Given("dark mode is turned off")
    public void darkModeTurnedOff() {
        world.setBooleanPref("dark_mode_preference", false);
    }

    @Then("the home screen uses the {word} theme")
    public void homeScreenUsesTheme(String theme) {
        int expected = "dark".equals(theme) ? 0xFFFFFFFF : 0xFF000000;
        world.await(() -> world.slotTextColor() == expected);
    }
}
