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
}
