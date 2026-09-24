package me.pompel.elauncher.bdd.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SearchSteps {
    private final LauncherWorld world;

    public SearchSteps(LauncherWorld world) {
        this.world = world;
    }

    @When("I type {string} in the search field")
    public void iTypeInSearch(String text) {
        world.typeSearch(text);
    }

    @When("I clear the search field")
    public void iClearSearch() {
        world.clearSearch();
    }

    @Then("the drawer shows only {string} and {string}")
    public void drawerShowsOnly(String first, String second) {
        List<String> expected = new ArrayList<>();
        expected.add(first);
        expected.add(second);
        world.await(() -> expected.equals(world.drawerLabels()));
        assertEquals(expected, world.drawerLabels());
    }

    @Then("{string}, {string} and {string} are all shown in the drawer")
    public void drawerShowsAll(String a, String b, String c) {
        List<String> expected = new ArrayList<>();
        expected.add(a);
        expected.add(b);
        expected.add(c);
        world.await(() -> world.drawerLabels().containsAll(expected));
        assertTrue(world.drawerLabels().containsAll(expected));
    }

    @Then("the drawer shows no apps")
    public void drawerShowsNoApps() {
        world.await(() -> world.drawerItemCount() == 0);
        assertEquals(0, world.drawerItemCount());
    }

    @Then("no app was auto-launched")
    public void noAppWasAutoLaunched() {
        try { Thread.sleep(300); } catch (InterruptedException ignored) { }
        world.idle();
        assertNull("an unexpected launch intent was observed", world.nextStartedActivity());
    }
}