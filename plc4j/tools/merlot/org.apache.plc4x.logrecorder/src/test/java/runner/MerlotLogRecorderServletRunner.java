package runner;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "stepdefinition")
@ConfigurationParameter(key = "cucumber.snippet-type", value = "camelcase")
public class MerlotLogRecorderServletRunner {
}
