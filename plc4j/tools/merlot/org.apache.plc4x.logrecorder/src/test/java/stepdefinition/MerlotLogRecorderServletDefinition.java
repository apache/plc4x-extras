package stepdefinition;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class MerlotLogRecorderServletDefinition {

    @Given("The user enters their credentials: username: {string} password: {string}")
    public void theUserEntersTheirCredentialsUsernamePassword(String string, String string2) {
        System.out.println("Username: " + string + " Password: " + string2);
    }
    @Given("The user selects the {string} tag and the {string} category")
    public void theUserSelectsTheMaintenanceTagAndTheCategoryLogbook(String tag, String category) {
        System.out.println("Tag: " + tag + " Category: " + category);
    }
    @Given("Attached image from the maintenance screen {string}")
    public void attachedImageFromTheMaintenanceScreen(String img) {
        System.out.println("Img: "+img);
    }
    @Given("Add a title: {string}")
    public void addATitle(String title) {
        System.out.println("Title: "+title);
    }
    @When("When the user clicks the submit button")
    public void whenTheUserClicksTheSubmitButton() {
        System.out.println("Button submit");
    }
    @Then("The service  saves that report to the database")
    public void theServiceSavesThatReportToTheDatabase() {
        System.out.println("Database");
    }
    @Then("Returns the JSON from the created report and an HTTP {int} response")
    public void returnsTheJSONFromTheCreatedReportAndAnHTTPResponse(Integer httpCode) {
        System.out.println("JSON:" +httpCode);
    }
}
