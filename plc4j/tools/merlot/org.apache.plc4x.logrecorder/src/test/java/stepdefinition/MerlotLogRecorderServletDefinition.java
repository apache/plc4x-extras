package stepdefinition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.plc4x.merlot.logrecorder.core.MerlotLogRecorderSecurityAction;
import org.apache.plc4x.merlot.logrecorder.servlets.MerlotLogRecorderLogMultipart;
import org.json.JSONObject;

import javax.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.plc4x.merlot.logrecorder.api.MerlotLogRecorderAction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//TODO: Hacer las implementacion de cada metodo
public class MerlotLogRecorderServletDefinition {

    private String username;
    private String password;
    private String level;
    private String tag;
    private String category;
    private String imagePath;
    private String description;
    private String title;

    private String serverResponse;
    //

    //Scenario 1: Create a fault report (servlet logMultipart)
    @Given("The user enters their credentials: username: {string} password: {string}")
    public void theUserEntersTheirCredentialsUsernamePassword(String username, String password) {

        this.username = username;
        this.password = password;
    }

    @Given("The user selects the {string} reporting level")
    public void theUserSelectsTheReportingLevel(String level) {
        this.level = level;
    }

    @Given("The user selects the {string} tag and the {string} category")
    public void theUserSelectsTheMaintenanceTagAndTheCategoryLogbook(String tag, String category) {
        this.tag = tag;
        this.category = category;
    }

    @Given("Attached image from the maintenance screen {string}")
    public void attachedImageFromTheMaintenanceScreen(String img) {
        this.imagePath = img;
    }

    @Given("The user adds a description: {string}")
    public void theUserAddsADescription(String description) {
        this.description = description;
    }

    @Given("Add a title: {string}")
    public void addATitle(String title) {
        this.title = title;
    }

    @When("The user clicks the submit button")
    public void whenTheUserClicksTheSubmitButton() throws IOException, ServletException {
       
        //Mock that collects and prepares the message
        MerlotLogRecorderAction merlotAction = mock(MerlotLogRecorderAction.class);

        //Mock of the servlet responsible for creating the log
        MerlotLogRecorderLogMultipart servlet = new MerlotLogRecorderLogMultipart(merlotAction);

        //Mock of the servlet request and response
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        //Log Builder
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode payloadNode = mapper.createObjectNode();

        payloadNode.put("owner", this.username != null ? this.username : "operator");
        payloadNode.put("level", this.level != null ? this.level : "INFO");
        payloadNode.put("title", this.title != null ? this.title : "Test Title");
        payloadNode.put("description", this.description != null ? this.description : "Test Description");

        payloadNode.put("status", "Active");
        payloadNode.put("id", 123456789L);
        payloadNode.put("createdDate", System.currentTimeMillis());

        //Tags
        com.fasterxml.jackson.databind.node.ArrayNode tagsArray = mapper.createArrayNode();
        com.fasterxml.jackson.databind.node.ObjectNode tagObj = mapper.createObjectNode();
        tagObj.put("name", this.tag != null ? this.tag : "default-tag");
        tagsArray.add(tagObj);
        payloadNode.set("tags", tagsArray);

        //Logbooks
        com.fasterxml.jackson.databind.node.ArrayNode logbooksArray = mapper.createArrayNode();
        com.fasterxml.jackson.databind.node.ObjectNode logbookObj = mapper.createObjectNode();
        logbookObj.put("name", this.category != null ? this.category : "default-logbook");
        logbooksArray.add(logbookObj);
        payloadNode.set("logbooks", logbooksArray);

        //Attachments
        com.fasterxml.jackson.databind.node.ArrayNode attachmentsArray = mapper.createArrayNode();
        com.fasterxml.jackson.databind.node.ObjectNode attachmentObj = mapper.createObjectNode();
        attachmentObj.put("uniqueFilename", this.imagePath != null ? this.imagePath : "screenshot.png");
        attachmentsArray.add(attachmentObj);
        payloadNode.set("attachments", attachmentsArray);

        //Request JSON
        final String jsonString = mapper.writeValueAsString(payloadNode);

        System.out.println("\nLog created before sending: " + new ObjectMapper().readTree(jsonString).toPrettyString());

        //Multi-part
        Part jsonPart = mock(Part.class);
        when(jsonPart.getContentType()).thenReturn("application/json");
        when(jsonPart.getInputStream()).thenAnswer(invocation
                -> new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8))
        );

        Part imagePart = mock(Part.class);
        when(imagePart.getContentType()).thenReturn("image/png");
        when(imagePart.getSubmittedFileName()).thenReturn(this.imagePath != null ? this.imagePath : "screenshot.png");
        when(imagePart.getInputStream()).thenAnswer(invocation
                -> new ByteArrayInputStream("olog_2132687163876".getBytes(StandardCharsets.UTF_8))
        );

        //Request Multipart (Complete)
        when(request.getParts()).thenReturn(List.of(jsonPart, imagePart));

        //Output flow configuration
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream servletOutput = new ServletOutputStream() {
            @Override
            public void write(int b) {
                baos.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }
        };

        when(response.getOutputStream()).thenReturn(servletOutput);

        //Simulate (mock) the basic security system of Karaf
        try (MockedStatic<MerlotLogRecorderSecurityAction> securityMock = mockStatic(MerlotLogRecorderSecurityAction.class)) {

            securityMock.when(() -> MerlotLogRecorderSecurityAction.validateCredentials(any(String.class), any(String.class)))
                    //Forcing it to return 'true', simulating a successful login
                    .thenReturn(true);

            //Request to the servlet
            servlet.doPut(request, response);
        }

        String responseBodyStr = baos.toString(StandardCharsets.UTF_8);
        assertFalse("The body of the response should not be empty", responseBodyStr.isEmpty());

        JSONObject jsonResponse = new JSONObject(responseBodyStr);

        assertEquals(payloadNode.get("owner").asText(), jsonResponse.getString("owner"));
        assertEquals(payloadNode.get("level").asText(), jsonResponse.getString("level"));
        assertEquals(payloadNode.get("title").asText(), jsonResponse.getString("title"));
        assertEquals(payloadNode.get("description").asText(), jsonResponse.getString("description"));

        //Expected Behavior of the Servlet
        verify(response).setContentType("application/json");
        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(merlotAction).prepareAndSendMessage(any(JSONObject.class));

        this.serverResponse = new ObjectMapper().readTree(responseBodyStr).toPrettyString();

    }
    @Then("Returns the JSON from the created report and an HTTP {int} response")
    public void returnsTheJSONFromTheCreatedReportAndAnHTTPResponse(Integer httpCode) {
        System.out.println("Simplified log as a response: " + this.serverResponse + "\nHttp Code: " + httpCode + "\n");
    }


}
