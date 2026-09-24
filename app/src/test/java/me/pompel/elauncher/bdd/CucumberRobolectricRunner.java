package me.pompel.elauncher.bdd;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.Failure;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.cucumber.junit.Cucumber;

import me.pompel.elauncher.bdd.CucumberOptionsHolder;

import static org.junit.Assert.assertTrue;

/**
 * Bridge: runs each Cucumber feature as a separate Robolectric test so step
 * definitions execute inside the Robolectric sandbox (android classes available).
 *
 * The Cucumber runtime is constructed INSIDE the sandbox (in the @Test body) so
 * glue classes link against Robolectric's instrumented android-all classes.
 * Enumeration happens via the plain JUnit4 Cucumber runner outside the sandbox
 * (class discovery only — no glue execution there).
 */
@RunWith(org.robolectric.ParameterizedRobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34)
public class CucumberRobolectricRunner {

    @org.robolectric.ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
    public static Collection<Object[]> features() throws Exception {
        Cucumber cucumber = new Cucumber(CucumberOptionsHolder.class);
        List<Description> features = cucumber.getDescription().getChildren();
        Collection<Object[]> params = new ArrayList<>();
        for (Description d : features) {
            params.add(new Object[]{ d.getDisplayName() });
        }
        return params;
    }

    private final String featureName;

    public CucumberRobolectricRunner(String featureName) {
        this.featureName = featureName;
    }

    @Test
    public void runFeature() throws Exception {
        Request request = Request.runner(new Cucumber(CucumberOptionsHolder.class)).filterWith(new Filter() {
            @Override
            public boolean shouldRun(Description description) {
                // feature suite name, scenario display names "(Feature name)", and ancestors
                return description.getDisplayName().contains(featureName);
            }

            @Override
            public String describe() {
                return "feature: " + featureName;
            }
        });
        Result result = new JUnitCore().run(request);
        if (!result.wasSuccessful()) {
            Failure failure = result.getFailures().get(0);
            throw new AssertionError("Cucumber feature failed: " + featureName + "\n"
                    + failure.getMessage(), failure.getException());
        }
        assertTrue(result.wasSuccessful());
    }
}
