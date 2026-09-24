package me.pompel.elauncher.bdd;

import io.cucumber.junit.CucumberOptions;

/** Holder for Cucumber options; features live on the test classpath. */
@CucumberOptions(
        glue = { "me.pompel.elauncher.bdd.steps" },
        features = { "classpath:features" },
        plugin = { "pretty" }
)
public class CucumberOptionsHolder {
}
