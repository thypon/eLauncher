package me.pompel.elauncher.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HomeSteps {
    private final LauncherWorld world;

    public HomeSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("the home screen has {int} slots configured")
    public void slotsConfigured(int count) {
        world.configureSlots(count);
    }

    @Given("home slot {int} is assigned to {string}")
    public void slotAssigned(int slot, String label) {
        world.assignSlot(slot, label);
    }

    @When("I long-press home slot {int}")
    public void iLongPressSlot(int slot) {
        world.longPressSlot(slot);
    }

    @When("I select {string} in the app picker")
    public void iSelectInPicker(String label) {
        world.pickAppInDialog(label);
    }

    @When("I confirm the suggested name")
    public void iConfirmSuggestedName() {
        world.confirmDialog("Add");
    }

    @When("I replace the name with {string} and confirm")
    public void iReplaceNameAndConfirm(String text) {
        world.setDialogInput(text);
        world.confirmDialog("Add");
    }

    @When("I tap home slot {int}")
    public void iTapSlot(int slot) {
        world.clickSlot(slot);
    }

    @Then("the home screen shows {int} slots")
    public void homeShowsSlots(int count) {
        assertEquals(count, world.slotCount());
    }

    @Then("home slot {int} is labeled {string}")
    public void slotLabeled(int slot, String label) {
        assertEquals(label, world.slotText(slot));
    }

    @Then("the assignment for slot {int} is stored with app {string}")
    public void assignmentStored(int slot, String packageName) {
        assertEquals("com.fixtures." + packageName.toLowerCase(), world.storedSlotPackage(slot));
    }

    @Then("no app was launched")
    public void noAppLaunched() {
        try { Thread.sleep(300); } catch (InterruptedException ignored) { }
        world.idle();
        assertNull("an unexpected launch intent was observed", world.nextStartedActivity());
    }
}