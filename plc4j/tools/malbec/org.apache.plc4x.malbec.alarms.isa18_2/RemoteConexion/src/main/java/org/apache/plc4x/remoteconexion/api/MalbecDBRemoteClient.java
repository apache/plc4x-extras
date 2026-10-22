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
package org.apache.plc4x.remoteconexion.api;

public interface MalbecDBRemoteClient {
    /**
     * 
     * @return 
     */
    String getAllAlarmRecords();//7 dias a partir e ahora
    /**
     * 
     * @param dateStart 
     * @param dateEnd
     * @return 
     */
    String getAllAlarmRecordsBy(String dateStart, String dateEnd);
    /**
     * 
     * @param actualDate
     * @param numberDays
     * @return 
     */
    String getAllAlarmRecordsBy(String actualDate, int numberDays);
    /**
     * 
     * @param dateStart
     * @param dateEnd
     * @param area
     * @return 
     */
    String getAllAlarmRecordsBy(String dateStart, String dateEnd, String area);

}
