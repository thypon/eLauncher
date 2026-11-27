package me.pompel.elauncher.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Steps for the Olauncher widget behaviors: per-widget tap modes
 * (on/double/off), the configure round-trip, and drag-to-reorder. */
public class WidgetBoardSteps {
    private final LauncherWorld world;

    public WidgetBoardSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("a widget is on the board")
    public void widgetOnBoard() {
        world.tapAddWidgetButton();
        assertTrue("expected the widget adder popover to open", world.adderOpen());
        world.clickFirstAdderKind();
        world.resolveAdderOutcome();
        world.await(() -> world.rowCount() == 1);
    }

    @Given("a {string} widget provider with a configure screen is installed")
    public void configurableWidgetProviderInstalled(String label) {
        world.installConfigurableWidgetProvider(label, "");
    }

    @Then("the system configure activity opens")
    public void configureActivityOpens() {
        world.assertConfigureStarted();
    }

    @When("the widget is configured")
    public void widgetConfigured() {
        world.resolveConfigureOutcome();
    }

    @When("the adder binding resolves")
    public void adderBindingResolves() {
        world.resolveAdderOutcome();
    }

    @Then("the widget taps are {string}")
    public void widgetTapsAre(String mode) {
        assertEquals(mode, world.storedTaps(0));
    }

    @When("the widget taps are set to {string}")
    public void widgetTapsSetTo(String mode) {
        world.setWidgetTaps(0, mode);
    }

    @When("I watch the widget's home gestures")
    public void watchHomeGestures() {
        world.spyHomeGestures(0);
    }

    @Then("the widget's home gestures saw the tap")
    public void homeGesturesSawTap() {
        assertTrue("a taps-off widget must forward its touches to the board",
                world.homeGestureCount() > 0);
    }

    @Then("the widget's home gestures saw nothing")
    public void homeGesturesSawNothing() {
        assertTrue("a taps-on widget must keep its touches",
                world.homeGestureCount() == 0);
    }

    @When("I tap the tap mode button")
    public void tapModeButton() {
        world.rowChildPerformClick(0, me.pompel.elauncher.R.id.taps_button);
    }

    @When("I drag the widget row down past the next one")
    public void dragWidgetRowDown() {
        world.dragBoardRowDown(0);
    }

    @When("the widget enters edit mode")
    public void widgetEntersEditMode() {
        world.enterEditModeNoIdle();
    }

    @When("the edit bar settles")
    public void editBarSettles() {
        world.idle(400);
    }

    @Then("the edit bar starts invisible")
    public void editBarStartsInvisible() {
        assertEquals("the edit bar must start its entrance invisible",
                0f, world.editBarAlphaAtEntrance, 0.01f);
    }

    @Then("the edit bar is fully visible")
    public void editBarFullyVisible() {
        assertEquals("the edit bar must ease in to fully visible",
                1f, world.editBarAlpha(), 0.01f);
    }

    @Then("every edit bar button is text only")
    public void editBarButtonsTextOnly() {
        assertTrue(world.editBarButtonsAreTextOnly());
    }

    @Then("the edit bar buttons are separated")
    public void editBarButtonsSeparated() {
        assertTrue(world.editBarHasSeparators());
    }

    @Then("the rows below are back in place")
    public void rowsBelowBackInPlace() {
        assertTrue(world.rowsBelowSettled());
    }

    @When("I long press widget row {int}")
    public void longPressWidgetRow(int row) {
        world.holdBoardRow(row);
    }

    @Then("every widget row shows an edit bar")
    public void everyWidgetRowShowsEditBar() {
        assertTrue("every widget row must have its own edit bar",
                world.editBarsAboveAllRows());
    }

    @Then("a plus row sits on top of the board")
    public void plusRowSitsOnTop() {
        assertTrue("the board must show the \"+\" add row on top",
                world.plusRowVisible());
    }

    @When("I tap the plus row")
    public void tapPlusRow() {
        world.clickPlusRow();
    }

    @When("I tap the second widget's height button")
    public void tapSecondHeightButton() {
        world.rowChildPerformClick(1, me.pompel.elauncher.R.id.resize_handle);
    }

    @When("I drag the second widget above the first by its frame")
    public void dragSecondAboveFirst() {
        world.dragRowAboveNeighbour(1);
    }
}
