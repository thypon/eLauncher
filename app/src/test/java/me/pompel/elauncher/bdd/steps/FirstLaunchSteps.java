package me.pompel.elauncher.bdd.steps;

import android.content.Intent;
import android.provider.Settings;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertNotNull;

/** Steps for the first-launch onboarding feature. */
public class FirstLaunchSteps {

    private final LauncherWorld world;

    public FirstLaunchSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("no onboarding has happened yet")
    public void noOnboardingHasHappenedYet() {
        world.seedNoOnboarding();
    }

    @Given("usage stats are denied")
    public void usageStatsAreDenied() {
        world.denyUsageStats();
    }

    @Given("usage stats are allowed")
    public void usageStatsAreAllowed() {
        world.allowUsageStats();
    }

    @Then("the system usage access screen is opened")
    public void usageAccessScreenIsOpened() {
        Intent[] seen = new Intent[1];
        world.await(() -> {
            Intent intent = world.nextStartedActivity();
            if (intent != null && android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS.equals(intent.getAction())) {
                seen[0] = intent;
                return true;
            }
            return false;
        });
        assertNotNull("usage access settings screen was never opened", seen[0]);
    }

    @Then("onboarding is marked as done")
    public void onboardingMarkedDone() {
        world.await(() -> world.booleanPref("firstLaunch"));
    }

    @When("the launcher is started again")
    public void launcherStartedAgain() {
        world.idle();
        world.startLauncher();
        world.idle();
    }
}
