package me.pompel.elauncher.bdd.steps;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import org.robolectric.shadows.ShadowActivity;

public class DashboardSteps {
    private final LauncherWorld world;

    public DashboardSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("a widget provider is installed")
    public void widgetProviderInstalled() {
        world.installWidgetProvider("", "");
    }

    @Given("a {string} widget provider offering {string} is installed")
    public void namedWidgetProviderInstalled(String label, String description) {
        world.installWidgetProvider(label, description);
    }

    @Given("binding widgets is allowed")
    public void bindingWidgetsAllowed() {
        world.allowWidgetBinding(true);
    }

    @Given("the eBoard is started")
    public void eboardStarted() {
        world.startDashboard();
    }

    @Given("the board remembers a widget without a provider")
    public void boardRemembersUnboundWidget() {
        world.seedBoardIds(424242);
    }

    @When("I tap the add widget button")
    public void tapAddWidget() {
        world.tapAddWidgetButton();
    }

    /** Emulates the system widget picker: it binds the reserved appWidgetId to the
     * picked provider and returns RESULT_OK with the id. */
    @When("the widget picker binds a widget")
    public void pickerBindsWidget() {
        ShadowActivity.IntentForResult request = shadowOf(world.dashboard()).getNextStartedActivityForResult();
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_PICK, request.intent.getAction());
        int id = request.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        assertNotEquals("picker request must carry a reserved widget id", -1, id);
        shadowOf(world.widgetManager()).bindAppWidgetId(id, world.widgetProvider);
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        shadowOf(world.dashboard()).receiveResult(request.intent, Activity.RESULT_OK, result);
        world.idle();
    }

    @When("the widget picker is cancelled")
    public void pickerCancelled() {
        ShadowActivity.IntentForResult request = shadowOf(world.dashboard()).getNextStartedActivityForResult();
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_PICK, request.intent.getAction());
        shadowOf(world.dashboard()).receiveResult(request.intent, Activity.RESULT_CANCELED, null);
        world.idle();
    }

    @When("the eBoard is restarted")
    public void eboardRestarted() {
        world.restartDashboard();
    }

    @When("I long press the widget row")
    public void longPressWidgetRow() {
        world.longPressBoardRow(0);
    }

    @When("I hold the widget row")
    public void holdWidgetRow() {
        world.holdBoardRow(0);
    }

    @Then("a widget row is on the board")
    public void widgetRowOnBoard() {
        world.await(() -> world.rowCount() == 1 && !world.emptyHintVisible());
    }

    @Then("the board shows the empty hint")
    public void boardShowsEmptyHint() {
        world.await(() -> world.rowCount() == 0 && world.emptyHintVisible());
    }

    @Then("the add widget button is visible")
    public void addWidgetButtonVisible() {
        assertTrue("add widget button should be visible on an empty board",
                world.addWidgetButtonVisible());
    }

    @Then("the add widget button is hidden")
    public void addWidgetButtonHidden() {
        assertTrue("add widget button should be hidden when widgets are on the board",
                !world.addWidgetButtonVisible());
    }

    @Then("the widget is remembered by the board")
    public void widgetRemembered() {
        assertEquals(1, world.storedBoardIds().size());
    }

    @Then("the widget is forgotten by the board")
    public void widgetForgotten() {
        assertTrue("expected empty board store, was: " + world.storedBoardIds(),
                world.storedBoardIds().isEmpty());
    }

    @Given("the widget kind requires configuration")
    public void widgetKindRequiresConfiguration() {
        world.requireWidgetConfiguration();
    }

    @Then("the widget configuration is requested")
    public void widgetConfigurationRequested() {
        world.configureRequested();
    }

    @When("the configuration succeeds")
    public void configurationSucceeds() {
        world.deliverConfigureResult(true);
    }

    @When("the configuration is cancelled")
    public void configurationCancelled() {
        world.deliverConfigureResult(false);
    }

    @Given("another app's widget provider offering {string} is installed")
    public void anotherAppsProvider(String description) {
        world.installForeignWidgetProvider(description);
    }

    @Then("the adder groups the kinds under their apps")
    public void adderGroupsByApp() {
        assertTrue("expected the adder to group kinds under app headers",
                world.adderSectionCount() >= 2
                        && world.adderSectionsHaveOneKindEach());
    }

    @Then("the adder previews sit in a 2-column grid")
    public void adderPreviewGrid() {
        assertEquals(2, world.adderGridColumns());
    }

    @Then("no launcher lists an {string} entry")
    public void noLauncherListsEntry(String label) {
        assertFalse("expected no launcher entry labeled " + label, world.hasLauncherEntry(label));
    }

    @Then("the eBoard activity is not exported")
    public void eboardActivityNotExported() {
        assertFalse("eBoard must not be exported: no external caller may start it",
                world.dashboardExported());
    }

    @When("I add a widget from the widget adder")
    public void addWidgetFromAdder() {
        tapAddWidget();
        widgetAdderPopoverOpens();
        selectWidgetKindFromAdder();
        resolveAdderOutcome();
    }

    @When("the widget adder popover opens")
    public void widgetAdderPopoverOpens() {
        assertTrue("expected the widget adder popover to open", world.adderOpen());
    }

    @Then("the widget adder popover is closed")
    public void widgetAdderPopoverClosed() {
        assertTrue("expected the widget adder popover to be closed", !world.adderOpen());
    }

    @Then("it covers half of the screen, anchored at the bottom")
    public void adderCoversHalfBottom() {
        world.assertAdderCoversHalfBottom();
    }

    @Then("it lists the {string} widget kind {string}")
    public void adderListsKind(String label, String description) {
        assertTrue("popover should list widget " + label, world.adderListsKind(label, description));
    }

    @When("I long press the empty board space")
    public void longPressEmptyBoardSpace() {
        world.longPressBoardEmpty();
    }

    @When("I select the widget kind from the widget adder")
    public void selectWidgetKindFromAdder() {
        world.clickFirstAdderKind();
    }

    @Then("each kind row shows a big preview with a small description")
    public void adderRowsShowPreview() {
        assertTrue("each adder kind row must include a big preview with a small description",
                world.adderRowsShowBigPreview());
    }

    @Then("the widget view has a transparent background")
    public void widgetTransparentBackground() {
        assertTrue("the widget view must have a transparent background (no system white container)",
                world.widgetTransparentBackground(0));
    }

    @Then("the board stays clear of the system status bar")
    public void boardStaysClearOfStatusBar() {
        assertTrue("the board must leave room for the system header",
                world.boardFitsSystemWindows());
    }

    @Then("the remove handle is clearly visible")
    public void removeHandleClearlyVisible() {
        assertTrue("the (x) handle must draw on a dark badge (visible on any widget)",
                world.removeHandleVisibleAndDark(0));
    }

    @Given("the dark mode preference is on")
    public void darkModePreferenceOn() {
        world.setDarkModePreference(true);
    }

    @Then("the board background is the dark theme background")
    public void boardBackgroundIsThemeBackground() {
        assertTrue("board background must follow the theme background",
                world.boardBackgroundIsThemeBackground());
    }

    @When("I press the empty board space")
    public void pressEmptyBoardSpace() {
        world.pressBoardEmpty();
    }

    @Then("edit mode is off")
    public void editModeOff() {
        assertFalse("edit mode must be off", world.dashboard().isEditMode());
    }

    @Then("the widget shows no handles")
    public void widgetShowsNoHandles() {
        assertFalse("handles must be hidden outside edit mode",
                world.rowChildVisible(0, me.pompel.elauncher.R.id.remove_handle));
    }

    @When("I tap the widget")
    public void tapWidget() {
        world.tapWidget();
    }

    @When("I hold the empty board space")
    public void holdEmptyBoardSpace() {
        world.holdBoardEmptyOpenAdder();
    }

    @When("I hold the empty board space below the widgets")
    public void holdEmptyBoardSpaceBelowWidgets() {
        world.holdBoardEmptyBelowWidgets();
    }

    @Then("the widget row is built on a plain widget context")
    public void widgetRowPlainContext() {
        assertTrue("widget rows must be built on the plain widget context",
                world.widgetRowBuiltOnPlainContext());
    }

    @Then("the adder preview backing is transparent")
    public void adderPreviewTransparent() {
        assertEquals("the adder preview backing must not be a solid panel",
                0, world.adderPreviewBackingAlpha());
    }

    @Then("the edit mode hint is hidden")
    public void editHintHidden() {
        assertFalse("edit hint must be hidden outside edit mode",
                world.editHintVisible());
    }

    @Then("the edit mode hint explains resizing and removal")
    public void editHintExplains() {
        assertTrue("edit hint must be visible in edit mode",
                world.editHintVisible());
        assertTrue("edit hint must mention resizing and removal",
                world.editHintText().toLowerCase().contains("resize")
                        && world.editHintText().toLowerCase().contains("remov"));
    }

    @Then("the widget adder popover opens again")
    public void widgetAdderPopoverOpensAgain() {
        assertTrue("expected the widget adder popover to open again",
                world.adderOpen());
    }

    @When("I remove the only widget row")
    public void removeOnlyWidgetRow() {
        world.removeAt(0);
    }

    @Then("the widget has no blocking overlay")
    public void widgetHasNoBlockingOverlay() {
        assertTrue("the widget must not have a blocking overlay",
                world.dashboard().boardRowAt(0).findViewWithTag(me.pompel.elauncher.R.id.hold_overlay) == null);
    }

    @Then("the board shows widgets in two columns")
    public void boardShowsTwoColumns() {
        assertEquals(2, world.dashboard().gridColumns());
    }

    @Then("the widget occupies the full board row")
    public void widgetSpansFullBoardRow() {
        assertEquals(world.dashboard().gridColumns(), world.rowSpan(0));
    }

    @Then("the resized widget is not clipped to its column")
    public void widgetNotClippedToColumn() {
        assertTrue("the row must not clip the widget while it is dragged "
                + "past the column", world.rowNotClipped(0));
    }

    @When("I start dragging the resize handle sideways to {int}dp")
    public void startSideResize(int dp) {
        world.beginSideResize(dp);
    }

    @Then("the resized row floats above the other rows")
    public void resizeRowFloatsAbove() {
        assertTrue("the dragged row's cell must paint above its neighbors",
                world.resizeRowFloats());
    }

    @Given("binding widgets is not allowed")
    public void bindingWidgetsNotAllowed() {
        world.allowWidgetBinding(false);
    }

    @Then("the system bind confirmation opens")
    public void systemBindConfirmationOpens() {
        world.assertSystemBindConfirmationStarted();
    }

    @Then("the widget binding is confirmed")
    public void widgetBindingConfirmed() {
        world.confirmSystemBinding();
    }

    @When("I select the {string} widget kind")
    public void selectNamedWidgetKind(String label) {
        world.clickAdderKind(label);
    }

    @When("I dismiss the widget adder popover")
    public void dismissWidgetAdder() {
        world.dismissAdder();
    }

    /** Whatever the binding path produced (direct bind or system picker),
     * completes it so the board reaches a bound-widget state. */
    private void resolveAdderOutcome() {
        world.resolveAdderOutcome();
    }

    @Then("the system widget picker opens")
    public void systemWidgetPickerOpens() {
        world.assertSystemPickerStarted();
    }

    @Then("the widget shows a remove handle")
    public void removeHandleVisible() {
        assertTrue("expected an (x) remove handle in edit mode",
                world.rowChildVisible(0, me.pompel.elauncher.R.id.remove_handle));
    }

    @When("I tap the remove handle")
    public void tapRemoveHandle() {
        world.rowChildPerformClick(0, me.pompel.elauncher.R.id.remove_handle);
    }

    @Given("the board has 2 widgets")
    public void boardHasTwoWidgets() {
        world.seedTwoWidgets();
    }

    @When("the second widget is moved before the first")
    public void moveSecondBeforeFirst() {
        world.dashboard().moveBoardRow(1, 0);
        world.idle();
    }

    @Then("the board order is {string}")
    public void boardOrderIs(String order) {
        assertEquals(order, world.storedBoardIdsAsString());
    }

    @When("the widget is resized to {int}dp")
    public void resizeWidget(int dp) {
        world.dashboard().applyRowHeight(0, dp);
        world.idle();
    }

    @Then("the widget height is stored as {int}dp")
    public void widgetHeightStored(int dp) {
        assertEquals(dp, world.storedHeight(0));
    }

    @Then("the widget is rendered {int}dp tall")
    public void widgetRenderedTall(int dp) {
        assertEquals(dp, world.renderedRowHeightDp(0));
    }

    @When("I note the current widget view")
    public void noteCurrentWidgetView() {
        world.noteInitialWidgetView();
    }

    @When("I drag the resize handle to {int}dp")
    public void dragResizeHandleTo(int dp) {
        world.dragResizeHandleTo(dp);
    }

    @When("I drag the resize handle sideways to {int}dp")
    public void dragResizeHandleSideways(int dp) {
        world.dragResizeHandleWidthTo(dp);
    }

    @When("the widget provider advises a maximum size of {int}x{int}dp")
    public void adviseProviderSize(int w, int h) {
        world.adviseProviderSize(w, h);
    }

    @When("I load scrollable content into the widget")
    public void loadScrollableContent() {
        world.pushScrollableWidgetContent(0);
    }

    @When("I scroll inside the widget")
    public void scrollInsideWidget() {
        world.scrollInsideWidget(0, 240);
    }

    @Then("the widget content has scrolled")
    public void widgetContentScrolled() {
        assertTrue("widget content must have scrolled inside the board, scrollTop="
                        + world.widgetInnerScrollTop(0),
                world.widgetInnerScrollTop(0) > 0);
    }

    @Then("the widget is rendered {int}dp wide")
    public void widgetRenderedWide(int dp) {
        assertEquals(dp, world.renderedRowWidthDp(0));
    }

    @Then("the widget width is stored as {int}dp")
    public void widgetWidthStored(int dp) {
        assertEquals(dp, world.storedWidth(0));
    }

    @Then("the widget row shows a fresh widget view")
    public void widgetRowShowsFreshView() {
        assertNotSame("the widget view must be re-created on release (the provider's "
                        + "RemoteViews must re-layout at the new size)",
                world.initialWidgetView(), world.boardWidgetView(0));
    }

    @Then("the fresh widget view is {int}dp tall")
    public void freshWidgetViewTall(int dp) {
        float density = world.density();
        assertEquals((int) (dp * density), world.boardWidgetView(0).getLayoutParams().height);
    }

    @Given("the widget provider is unbound")
    public void widgetProviderUnbound() {
        world.forgetWidgetBinding(0);
    }

    @When("I watch the widget touches")
    public void watchWidgetTouches() {
        world.spyCurrentWidgetTouches();
    }

    @Then("the widget received the tap")
    public void widgetReceivedTap() {
        assertTrue("the hold proxy must forward taps to the CURRENT widget view "
                + "(a released resize re-creates it)", world.widgetTouchSpied());
    }
}
