/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.merlot.lector62x.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Queue;
import org.osgi.service.cm.ManagedService;

public interface MerlotLecto620ShippingPackageService {

    /**
     *
     * @param r Deserialized JSON object sent by the server after receiving the
     * data packet from the camera + weight.
     * @return true if the “resultado” property is true, and false if the
     * property contains false.
     * @throws Throws an exception if the properties do not contain any values
     * (null)
     */
    boolean confirmDataReceiptFromExternalServer(MerlotLector620ShippingPackageResponse r) throws Exception;

    /**
     *
     * @return List of payloads sent to the server but not confirmed
     */
    Queue<MerlotLector620ShippingPackageDTO> getGuidesNoProcess();

    /**
     *
     * @param codGuide Tracking number, which will be used to locate the record
     * in the database and update its “guiaconfirmada” attribute from false to
     * true
     * @return true if the update was successful, false otherwise
     * @throws Exception Throws an SQL exception if the update fails due to a
     * syntax or semantic error, or a failure in the database connection.
     */
    boolean updateGuideStatusInDataRecord(String codGuide) throws Exception;

    /**
     *
     * @param data A String array containing the information obtained from the
     * PLC, including data sent from the data matrix read by the camera + the
     * package weight captured by Siwarex
     * @throws JsonProcessingException
     */
    void postEventPackage(String[] data) throws JsonProcessingException;

    /**
     *
     * @param url Retrieves the full URL of the HTTP resource from the server by
     * configuring it through the Decanter processor
     */
    void setUrl(String url);

    /**
     * It reports to the SchedulerService that the connection to the DataSource
     * has been established, so it can now begin the process of querying records
     * without confirmation
     */
    void onDataSourceReady();
}
