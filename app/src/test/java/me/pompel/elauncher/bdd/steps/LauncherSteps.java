package me.pompel.elauncher.bdd.steps;

import android.view.View;

import androidx.test.core.app.ActivityScenario;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import me.pompel.elauncher.MainActivity;
import me.pompel.elauncher.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LauncherSteps {

    private ActivityScenario<MainActivity> scenario;

    @Given("the launcher is started")
    public void theLauncherIsStarted() {
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @Then("the home screen is visible")
    public void theHomeScreenIsVisible() {
        scenario.onActivity(activity -> {
            View home = activity.findViewById(R.id.HomeScreen);
            assertNotNull("HomeScreen should be present", home);
            assertEquals(View.VISIBLE, home.getVisibility());
        });
    }
}
