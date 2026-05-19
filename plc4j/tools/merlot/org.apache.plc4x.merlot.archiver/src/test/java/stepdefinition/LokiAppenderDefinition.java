package stepdefinition;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.plc4x.merlot.archiver.impl.MerlotLokiAppender;
import org.apache.plc4x.merlot.archiver.impl.MerlotPvRtCollectorImpl;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;

public class LokiAppenderDefinition {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LokiAppenderDefinition.class);

    private final MerlotLokiAppender mLoki = new MerlotLokiAppender();
    private final WireMockServer wireMockServer = new WireMockServer(3100);
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Map<String, String> labels;


    @Given("Labels supplied for the message {string}, {string}, {string}, {string}")
    public void labelsSuppliedForTheMessage(String label1, String label2, String label3, String label4) {
        mLoki.setHttpClient(httpClient);
        LOGGER.info("Labels: {} {} {} {}", label1, label2, label3, label4);
        //Labels
        labels = Map.of(label1, label2, label3, label4);

    }
    @Given("Url Loki Server {string}")
    public void url(String url) {
        mLoki.setUrl(url);
        wireMockServer.start();
        WireMock.configureFor("localhost", 3100);
    }

    @When("The server returned an HTTP {int} response")
    public void theServerReturnedAnHTTPResponse(Integer code) {
        //Stub for POST requests to the URL /loki/api/v1/push
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/loki/api/v1/push"))
                .willReturn(aResponse()
                        .withStatus(code)
                        .withBody("Successfully pushed")));
        //Send data to the simulated “Loki Server”
        mLoki.sendLokiServer("Test Message", labels);


    }
    @Then("The instance displays “Log sent”")
    public void theInstanceDisplaysLogSent() {
        // Verify that at least one HTTP request was received during the test
        verify(postRequestedFor(urlEqualTo("/loki/api/v1/push"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("Test Message"))
                .withRequestBody(containing("\"job\":\"test\"")));


        //Stop server
        wireMockServer.stop();
        LOGGER.info("Successfully pushed");
    }

}
