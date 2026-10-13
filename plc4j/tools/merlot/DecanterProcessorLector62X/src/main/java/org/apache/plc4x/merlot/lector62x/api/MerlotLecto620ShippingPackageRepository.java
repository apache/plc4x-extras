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

import java.util.List;

public interface MerlotLecto620ShippingPackageRepository {

    /**
     * 
     * @param codGuide A reference number that will be used to determine whether the record is stored in the database
     * @return  true if it finds a record that matches the waybill number, false otherwise
     * @throws Exception SQL-type 
     */
    boolean checkShippingGuide(String codGuide) throws Exception;


    /**
     * 
     * @param codGuide A reference number that will be used to determine whether the record is stored in the database
     * @return true  if the record in the database is successfully updated, false otherwise
     * @throws Exception SQL-type 
     */
    boolean confirmWithShippingGuide(String codGuide) throws Exception;

    /** Guías con guiaconfirmada='false' (máximo 100 por ciclo). */
    
    /**
     * 
     * @return Records that have the “guiaconfirmada” attribute set to false
     * @throws Exception SQL-type 
     */
    List<MerlotLector620ShippingPackageDTO> findUnconfirmed() throws Exception;
    
    
    
   /**
     * 
     * @param dateTimeFormatted Date and Time of the Failed Reading
     * @param weight Weight of the package that failed the scan
     */
    void insertNoRead(String dateTimeFormatted, String weight);
    
    
    /**
     * 
     * @param start Date and Start Time
     * @param end Date and End Time
     * @return Sum of all weights between the start and end dates
     */
    Double totalBetween(String start, String end);
}
