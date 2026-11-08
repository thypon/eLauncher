package me.pompel.elauncher.bdd.steps;

import android.content.pm.ActivityInfo;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertEquals;

/** Steps for the orientation-lock feature: the lock_portrait_preference drives
 * setRequestedOrientation on both the launcher and the settings screen. */
public class OrientationSteps {

    private final LauncherWorld world;

    public OrientationSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("portrait lock is turned on")
    public void portraitLockOn() {
        world.setBooleanPref("lock_portrait_preference", true);
    }

    @Given("portrait lock is turned off")
    public void portraitLockOff() {
        world.setBooleanPref("lock_portrait_preference", false);
    }

    @When("I toggle the lock portrait switch")
    public void toggleLockPortraitSwitch() {
        world.toggleLockPortraitPreference();
    }

    @Then("the launcher requests portrait orientation")
    public void launcherRequestsPortrait() {
        world.await(() -> world.mainRequestedOrientation()
                == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Then("the launcher follows the system orientation")
    public void launcherFollowsSystem() {
        world.await(() -> world.mainRequestedOrientation()
                == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Then("the settings screen requests portrait orientation")
    public void settingsRequestsPortrait() {
        world.await(() -> world.settingsRequestedOrientation()
                == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Then("the settings screen follows the system orientation")
    public void settingsFollowsSystem() {
        world.await(() -> world.settingsRequestedOrientation()
                == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Then("the lock portrait switch is on")
    public void lockPortraitSwitchOn() {
        assertEquals(true, world.lockPortraitPreferenceChecked());
    }

    @Then("the lock portrait preference is stored as {string}")
    public void lockPortraitPrefStored(String value) {
        assertEquals(Boolean.parseBoolean(value),
                world.booleanPref("lock_portrait_preference"));
    }
}
