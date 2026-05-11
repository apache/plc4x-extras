/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.archiver.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.net.http.HttpClient;
import java.util.Map;
import org.apache.plc4x.merlot.archiver.impl.MerlotLokiAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MerlotLokiAppenderTest {

    private MerlotLokiAppender mLoki;
    private WireMockServer wireMockServer;
    private HttpClient httpClient;
    private String lokiUrl = "http://localhost:3100/loki/api/v1/push";

    @BeforeEach
    public void setUp() {

        mLoki = new MerlotLokiAppender();
        httpClient = HttpClient.newHttpClient();
        mLoki.setHttpClient(httpClient);
        mLoki.setUrl(lokiUrl);

        wireMockServer = new WireMockServer(3100);
        wireMockServer.start();
        WireMock.configureFor("localhost", 3100);
    }

    @Test
    public void pushLogToLoki() {

        wireMockServer.stubFor(WireMock.post(urlEqualTo("/loki/api/v1/push"))
                .willReturn(aResponse()
                        .withStatus(204)
                        .withBody("Successfully pushed")));

        //Labels
        Map<String, String> labels = Map.of("job", "test", "env", "dev");

        //Send data to the simulated “Loki Server”
        mLoki.sendLokiServer("Test Message", labels);

        // Verify that at least one HTTP request was received during the test
        verify(postRequestedFor(urlEqualTo("/loki/api/v1/push"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("Test Message"))
                .withRequestBody(containing("\"job\":\"test\"")));
    }

    @AfterEach
    void tearDown() {
        //Stop server
        wireMockServer.stop();
    }
}
