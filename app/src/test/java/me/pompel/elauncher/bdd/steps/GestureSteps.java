package me.pompel.elauncher.bdd.steps;

import android.content.Intent;
import android.provider.MediaStore;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import me.pompel.elauncher.SettingsActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GestureSteps {
    private final LauncherWorld world;

    public GestureSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("a phone app {string} is installed")
    public void phoneAppInstalled(String label) {
        world.installPhoneApp(label);
    }

    @Given("a browser app {string} is installed")
    public void browserAppInstalled(String label) {
        world.installBrowserApp(label);
    }

    @Given("another launcher {string} is installed")
    public void otherLauncherInstalled(String label) {
        world.installOtherLauncher(label);
    }

    @Given("the app {string} is assigned to the {word} gesture")
    public void appAssignedToGesture(String label, String side) {
        world.installApps(label);
        world.setGesturePackage(side, LauncherWorld.pkgFor(label));
    }

    @Given("the {word} gesture is assigned to the missing app {string}")
    public void gestureAssignedToMissingApp(String side, String pkg) {
        world.setGesturePackage(side, pkg);
    }

    @When("I fling {word} on the home screen")
    public void flingOnHome(String direction) {
        world.fling(direction);
    }

    @When("I swipe up on the home screen")
    public void swipeUpOnHome() {
        world.fling("up");
    }

    @When("I swipe down on the home screen")
    public void swipeDownOnHome() {
        world.fling("down");
    }

    @When("I double tap the home screen")
    public void doubleTapHome() {
        world.doubleTapHome();
    }

    @When("I long press the home screen")
    public void longPressHome() {
        world.longPressHome();
    }

    @When("I tap the {word} screen edge and press back")
    public void tapEdgeAndPressBack(String side) {
        world.edgeTap(side);
        world.pressBack();
    }

    @When("I swipe inward from the {word} edge and press back")
    public void edgeSwipeAndPressBack(String side) {
        world.edgeSwipe(side);
        world.pressBack();
    }

    @When("I press back")
    public void pressBack() {
        world.pressBack();
    }

    @Then("the app drawer opens")
    public void appDrawerOpens() {
        world.await(world::drawerOpen);
        if (!world.drawerOpen()) {
            throw new AssertionError("app drawer did not open after the swipe");
        }
    }

    @Then("the camera opens")
    public void cameraOpens() {
        Intent started = world.nextStartedActivity();
        assertNotNull("no activity started", started);
        assertEquals("expected the still-image camera intent",
                MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, started.getAction());
    }

    @Then("the dialer opens")
    public void dialerOpens() {
        Intent started = world.nextStartedActivity();
        assertNotNull("no activity started", started);
        assertEquals("expected an ACTION_DIAL intent", Intent.ACTION_DIAL, started.getAction());
    }

    @Then("the browser picker opens")
    public void browserPickerOpens() {
        Intent started = world.nextStartedActivity();
        assertNotNull("no activity started", started);
        assertEquals("expected an ACTION_VIEW intent", Intent.ACTION_VIEW, started.getAction());
        assertNotNull("expected an http: data uri", started.getData());
        assertNull("picker must not target a concrete component", started.getComponent());
    }

    @Then("the settings screen opens")
    public void settingsScreenOpens() {
        Intent started = world.nextStartedActivity();
        assertNotNull("no activity started", started);
        assertNotNull("settings intent must target a component", started.getComponent());
        assertEquals(SettingsActivity.class.getName(), started.getComponent().getClassName());
    }

    @Then("the {word} gesture preference is cleared")
    public void gesturePreferenceCleared(String side) {
        String stored = world.gesturePackage(side);
        if (stored == null || stored.isEmpty()) return;
        throw new AssertionError("expected cleared " + side + "_gesture_package but was: " + stored);
    }
}