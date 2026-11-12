package me.pompel.elauncher.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @When("the launcher is relaunched")
    public void relaunchLauncher() {
        world.relaunchLauncher();
    }

    @When("half an hour passes")
    public void halfAnHourPasses() {
        world.idle(31 * 60 * 1000L);
    }

    @Then("home slot {int} is marked open")
    public void slotMarkedOpen(int slot) {
        assertTrue("home slot " + slot + " should be marked open", world.slotTextIsBold(slot));
    }

    @Then("home slot {int} is not marked open")
    public void slotNotMarkedOpen(int slot) {
        assertTrue("home slot " + slot + " should not be marked open", !world.slotTextIsBold(slot));
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

    @Then("every home slot is visible on screen")
    public void everyHomeSlotVisible() {
        world.await(world::allHomeSlotsVisible);
    }

    @Then("the home slot text is scaled down")
    public void slotTextScaledDown() {
        assertTrue("slot text should shrink when the configured apps overflow the screen", world.homeSlotTextScaledDown());
    }

    @Then("the home slot text keeps its default size")
    public void slotTextDefaultSize() {
        assertTrue("slot text should keep its default size when everything fits", world.homeSlotTextDefaultSize());
    }

    @Then("the app picker shows Cancel left of {string}")
    public void pickerCancelButtonLeftOf(String rightLabel) {
        world.pickerCancelButtonLeftOf(rightLabel);
    }

    @When("I drag home slot {int} down past slot {int}")
    public void iDragSlotDown(int from, int past) {
        world.dragSlotDown(from, past);
    }
}