package me.pompel.elauncher.bdd.steps;

import android.content.Intent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import me.pompel.elauncher.DashboardActivity;
import me.pompel.elauncher.GesturePreference;
import me.pompel.elauncher.MainActivity;
import me.pompel.elauncher.R;
import me.pompel.elauncher.SettingsActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GestureSteps {
    private final LauncherWorld world;

    public GestureSteps(LauncherWorld world) {
        this.world = world;
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

    @Given("the {word} gesture is assigned to eBoard")
    public void gestureAssignedToEboard(String side) {
        world.setGesturePackage(side, GesturePreference.EBOARD_PACKAGE);
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

    @When("I fling inward from the {word} screen edge")
    public void flingInwardFromEdge(String side) {
        world.edgeFling(side, true);
    }

    @When("I fling outward from the {word} screen edge")
    public void flingOutwardFromEdge(String side) {
        world.edgeFling(side, false);
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

    @Then("the eBoard dashboard opens")
    public void eboardDashboardOpens() {
        Intent started = world.nextStartedActivity();
        assertNotNull("no activity started", started);
        assertTrue("eBoard intent must target a component, got: " + started
                + " action=" + started.getAction(),
                started.getComponent() != null);
        assertEquals(DashboardActivity.class.getName(), started.getComponent().getClassName());
    }

    /** Asserts the launcher launched the dashboard and brings it on screen
     * with the exact recorded intent, preserving the entry direction extra. */
    @Then("the eBoard dashboard opens on screen")
    public void eboardDashboardOpensOnScreen() {
        Intent started = world.nextStartedActivity();
        assertNotNull("no activity started", started);
        assertTrue("eBoard intent must target a component, got: " + started
                + " action=" + started.getAction(),
                started.getComponent() != null);
        assertEquals(DashboardActivity.class.getName(), started.getComponent().getClassName());
        world.launchDashboardWith(started);
    }

    @Given("the eBoard dashboard is opened directly")
    public void eboardDashboardOpenedDirectly() {
        world.startDashboard();
    }

    @When("I fling {word} on the eBoard dashboard")
    public void flingOnEboardDashboard(String direction) {
        world.flingOnDashboard(direction);
    }

    @When("I press back on the eBoard dashboard")
    public void pressBackOnEboardDashboard() {
        world.pressBackOnDashboard();
    }

    @Then("the eBoard dashboard stays open")
    public void eboardDashboardStaysOpen() {
        world.await(() -> world.dashboardScenario != null);
        assertFalse("dashboard finished unexpectedly", world.dashboard().isFinishing());
    }

    /** Asserts the board finished with the reverse directional animation:
     * the board slides out to the side it entered from, the launcher home
     * returns from the opposite side. */
    @Then("the eBoard dashboard animates out to the {word}")
    public void eboardDashboardAnimatesOut(String side) {
        boolean left = "left".equals(side);
        int expectedBoardExit = left ? R.anim.slide_out_left : R.anim.slide_out_right;
        int expectedHomeEnter = left ? R.anim.slide_in_right : R.anim.slide_in_left;
        DashboardActivity target = world.dashboardFlingTarget;
        assertNotNull("no dashboard captured before the fling", target);
        org.robolectric.shadows.ShadowActivity shadow = org.robolectric.Shadows.shadowOf(target);
        world.await(() -> shadow.getPendingTransitionExitAnimationResourceId() != -1);
        assertEquals("wrong board exit animation (out to the " + side + ")",
                expectedBoardExit, shadow.getPendingTransitionExitAnimationResourceId());
        assertEquals("wrong launcher home return animation",
                expectedHomeEnter, shadow.getPendingTransitionEnterAnimationResourceId());
    }

    /** Asserts the board's system gesture exclusion strips: the entry side's
     * edge gesture (the system back/forward swipe) is suppressed together
     * with the disabled fling; a direct launch excludes nothing. */
    @Then("the board excludes the {word} screen edge from system gestures")
    public void boardExcludesEdge(String side) {
        boolean left = "left".equals(side);
        DashboardActivity target = world.dashboard();
        android.view.View[] root = new android.view.View[1];
        world.await(() -> {
            root[0] = target.findViewById(android.R.id.content);
            return root[0] != null && !root[0].getSystemGestureExclusionRects().isEmpty();
        });
        java.util.List<android.graphics.Rect> rects = root[0].getSystemGestureExclusionRects();
        assertTrue("no gesture exclusion rects on the board root", !rects.isEmpty());
        for (android.graphics.Rect r : rects) {
            assertTrue("exclusion rect is empty", !r.isEmpty());
            if (left) {
                assertEquals("exclusion not anchored to the left edge", 0, r.left);
            } else {
                assertEquals("exclusion not anchored to the right edge",
                        root[0].getWidth(), r.right);
            }
        }
    }

    @Then("the board excludes no screen edge from system gestures")
    public void boardExcludesNoEdge() {
        DashboardActivity target = world.dashboard();
        android.view.View[] root = new android.view.View[1];
        world.await(() -> {
            root[0] = target.findViewById(android.R.id.content);
            return root[0] != null;
        });
        assertTrue("expected no gesture exclusion rects, got: "
                + root[0].getSystemGestureExclusionRects(),
                root[0].getSystemGestureExclusionRects().isEmpty());
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

    @Given("animations are disabled")
    public void animationsAreDisabled() {
        world.setAnimationsEnabled(false);
    }

    /** e-ink default: the launch switches instantly — even the system's
     * default window animation is cancelled (pending transitions 0,0). */
    @Then("the launched activity uses no animation")
    public void launchedActivityUsesNoAnimation() {
        MainActivity[] ref = new MainActivity[1];
        world.scenario.onActivity(a -> ref[0] = a);
        org.robolectric.shadows.ShadowActivity shadow = org.robolectric.Shadows.shadowOf(ref[0]);
        world.await(() -> shadow.getPendingTransitionEnterAnimationResourceId() != -1);
        assertEquals("expected cancelled enter animation",
                0, shadow.getPendingTransitionEnterAnimationResourceId());
        assertEquals("expected cancelled exit animation",
                0, shadow.getPendingTransitionExitAnimationResourceId());
    }

    /** e-ink default: the board exit switches instantly (pending transitions 0,0). */
    @Then("the eBoard dashboard exits without animation")
    public void eboardDashboardExitsWithoutAnimation() {
        DashboardActivity target = world.dashboardFlingTarget;
        assertNotNull("no dashboard captured before the fling", target);
        org.robolectric.shadows.ShadowActivity shadow = org.robolectric.Shadows.shadowOf(target);
        world.await(() -> shadow.getPendingTransitionExitAnimationResourceId() != -1);
        assertEquals("expected cancelled board exit animation",
                0, shadow.getPendingTransitionExitAnimationResourceId());
        assertEquals("expected cancelled launcher home return animation",
                0, shadow.getPendingTransitionEnterAnimationResourceId());
    }

    /** Asserts the swipe/edge launch slides the new activity in from the given
     * side while the launcher slides out to the opposite side. */
    @Then("the launched activity animates in from the {word}")
    public void launchedActivityAnimatesInFrom(String side) {
        boolean left = "left".equals(side);
        int expectedEnter = left ? R.anim.slide_in_left : R.anim.slide_in_right;
        int expectedExit = left ? R.anim.slide_out_right : R.anim.slide_out_left;
        MainActivity[] ref = new MainActivity[1];
        world.scenario.onActivity(a -> ref[0] = a);
        org.robolectric.shadows.ShadowActivity shadow = org.robolectric.Shadows.shadowOf(ref[0]);
        world.await(() -> shadow.getPendingTransitionEnterAnimationResourceId() != -1);
        assertEquals("wrong enter animation (from the " + side + ")",
                expectedEnter, shadow.getPendingTransitionEnterAnimationResourceId());
        assertEquals("wrong exit animation (from the " + side + ")",
                expectedExit, shadow.getPendingTransitionExitAnimationResourceId());
    }

    @Then("the launched activity uses the default system animation")
    public void launchedActivityUsesDefaultAnimation() {
        world.await(() -> org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).getNextStartedActivity() != null);
        MainActivity[] ref = new MainActivity[1];
        world.scenario.onActivity(a -> ref[0] = a);
        org.robolectric.shadows.ShadowActivity shadow = org.robolectric.Shadows.shadowOf(ref[0]);
        assertEquals("expected no custom enter animation",
                -1, shadow.getPendingTransitionEnterAnimationResourceId());
        assertEquals("expected no custom exit animation",
                -1, shadow.getPendingTransitionExitAnimationResourceId());
    }
}