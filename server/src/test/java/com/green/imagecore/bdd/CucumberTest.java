package com.green.imagecore.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Entry point for executing the Cucumber BDD test suite.
 * This class uses the JUnit Platform Suite engine to discover and run
 * Gherkin feature files. It acts as the orchestrator that links the
 * human-readable requirements in 'src/test/resources/features'
 * to the automated step definitions in the Java package.
 */
@Suite
@IncludeEngines("cucumber")           // Instructs JUnit to use the Cucumber execution engine
@SelectClasspathResource("features")  // Location of the .feature files
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.green.imagecore.bdd") // Location of step definitions
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:build/reports/cucumber/report.html") // Enables readable console output

public class CucumberTest {
    /* * This class remains empty. Its sole purpose is to hold the annotations
     * required by the JUnit Platform to bootstrap the Cucumber test environment.
     */
}
