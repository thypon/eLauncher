package me.pompel.elauncher.bdd.steps;

import android.content.Intent;
import android.provider.Settings;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LauncherSteps {
    private final LauncherWorld world;

    public LauncherSteps(LauncherWorld world) {
        this.world = world;
    }

    @Given("usage access onboarding is already done")
    public void usageAccessOnboardingIsAlreadyDone() {
        world.seedOnboardingDone();
    }

    @Given("the launcher has these apps installed:")
    public void launcherHasAppsInstalled(io.cucumber.datatable.DataTable table) {
        world.installApps(table.asList().toArray(new String[0]));
    }

    @Given("the launcher is started")
    public void launcherIsStarted() {
        world.startLauncher();
    }

    @Given("the app drawer is open")
    public void appDrawerIsOpen() {
        world.openDrawer();
        assertTrue(world.drawerOpen());
    }

    @When("{string} is installed")
    public void appIsInstalled(String label) {
        world.installApp(label);
        world.broadcastPackageChange(Intent.ACTION_PACKAGE_ADDED, LauncherWorld.pkgFor(label), false);
    }

    @When("{string} is uninstalled")
    public void appIsUninstalled(String label) {
        world.uninstallPackage(label);
        world.broadcastPackageChange(Intent.ACTION_PACKAGE_REMOVED, LauncherWorld.pkgFor(label), false);
    }

    @When("{string} is updated")
    public void appIsUpdated(String label) {
        // the OS fires REMOVED (with EXTRA_REPLACING) followed by REPLACED on update
        world.broadcastPackageChange(Intent.ACTION_PACKAGE_REMOVED, LauncherWorld.pkgFor(label), true);
        world.broadcastPackageChange(Intent.ACTION_PACKAGE_REPLACED, LauncherWorld.pkgFor(label), true);
    }

    @Then("{string} appears in the drawer")
    public void appAppearsInDrawer(String label) {
        assertTrue(label + " must be listed in the drawer", world.drawerLabels().contains(label));
    }

    @Then("{string} does not appear in the drawer")
    public void appDoesNotAppearInDrawer(String label) {
        assertTrue(label + " must not be listed in the drawer", !world.drawerLabels().contains(label));
    }

    @Then("{string} appears before {string} in the drawer")
    public void appAppearsBefore(String first, String second) {
        assertTrue("drawer must list " + first + " before " + second,
                world.drawerLabels().indexOf(first) < world.drawerLabels().indexOf(second));
    }

    @When("I tap the {string} app in the drawer")
    public void iTapApp(String label) {
        world.tapApp(label);
    }

    @Then("the {string} app is launched")
    public void appIsLaunched(String packageName) {
        Intent[] started = new Intent[1];
        world.await(() -> {
            Intent next = world.nextStartedActivity();
            if (next != null && next.getComponent() != null
                    && packageName.equals(next.getComponent().getPackageName())) {
                started[0] = next;
                return true;
            }
            return false;
        });
        assertNotNull("no launch intent observed for " + packageName, started[0]);
    }

    @Then("the drawer is closed again")
    public void drawerIsClosedAgain() {
        world.await(() -> world.homeVisible());
        assertTrue(world.homeVisible());
    }

    @When("I long-press the {string} app in the drawer")
    public void iLongPressApp(String label) {
        world.longPressApp(label);
    }

    @Then("the system app details screen is opened for {string}")
    public void appDetailsScreenOpened(String packageName) {
        Intent[] started = new Intent[1];
        world.await(() -> {
            Intent next = world.nextStartedActivity();
            if (next != null && Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(next.getAction())
                    && next.getData() != null
                    && ("package:" + packageName).equals(next.getData().toString())) {
                started[0] = next;
                return true;
            }
            return false;
        });
        assertNotNull("no app-details intent observed for " + packageName, started[0]);
    }

    @Then("the home screen is visible")
    public void homeScreenVisible() {
        assertTrue(world.homeVisible());
    }

    @When("I call the home launcher")
    public void callHomeLauncher() {
        world.callHomeLauncher();
    }

    @Then("the main view reclaims the home task over the board")
    public void mainViewReclaimsHomeTask() {
        assertTrue("calling home must land on the existing main view instance "
                        + "(singleTask clears whatever sits above it, like the board)",
                world.mainActivityIsSingleTask());
    }
}