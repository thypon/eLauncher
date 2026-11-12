package me.pompel.elauncher.bdd.steps;

import android.content.Intent;
import android.provider.Browser;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import me.pompel.elauncher.AppLink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Steps for the user-added link targets shared by the gesture pickers and
 * the home screen slot picker. */
public class LinkSteps {
    private final LauncherWorld world;

    public LinkSteps(LauncherWorld world) {
        this.world = world;
    }

    @When("I name the link {string} at {string}")
    public void nameTheLink(String title, String address) {
        world.fillLinkDialog(title, address);
    }

    @When("I add the link")
    public void addTheLink() {
        world.confirmDialog("Add");
    }

    @Then("the link address opens")
    public void linkAddressOpens() {
        Intent started = world.nextStartedActivity();
        assertNotNull("no link intent was started", started);
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        assertNotNull("the link intent carries no data uri", started.getData());
        assertNull("the link must not target a concrete component", started.getComponent());
    }

    @Then("the link brings the open browser forward")
    public void linkBringsBrowserForward() {
        Intent started = world.nextStartedActivity();
        assertNotNull("no link intent was started", started);
        assertEquals("the link launch must reuse the browser tab opened by this app",
            world.appContext().getPackageName(), started.getStringExtra(Browser.EXTRA_APPLICATION_ID));
    }

    @Then("the {word} gesture stores the link {string} at {string}")
    public void gestureStoresLink(String side, String title, String address) {
        assertEquals(AppLink.encode(title, address), world.gesturePackage(side));
    }

    @Then("home slot {int} stores the link {string} at {string}")
    public void slotStoresLink(int slot, String title, String address) {
        assertEquals(AppLink.encode(title, address), world.storedSlotPackage(slot));
    }
}
