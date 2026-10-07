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
package org.apache.plc4x.merlot.lector62x.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.apache.plc4x.merlot.lector62x.api.MerlotDecanterProcessorDataLector62X;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotLecto620ShippingPackageRepositoryImpl implements MerlotLecto620ShippingPackageRepository {

    private final static Logger LOGGER = LoggerFactory.getLogger(MerlotLecto620ShippingPackageRepositoryImpl.class);
    private final static String MERLOT_QUERY_CHECK_GUIDE = "SELECT codguia FROM TABLENAME WHERE codguia = ?";
    private final static String MERLOT_QUERY_UPDATE_RECORD = "UPDATE TABLENAME SET guiaconfirmada = ? WHERE codguia = ? LIMIT 1";
    private static final String TABLE_NAME_PROPERTY = "table.name";

    private final MerlotDecanterProcessorDataLector62X dataProcessor;

    public MerlotLecto620ShippingPackageRepositoryImpl(MerlotDecanterProcessorDataLector62X dataProcessor) {
        this.dataProcessor = dataProcessor;
    }

    @Override
    public boolean checkShippingGuide(String codGuide) {

        String sql = MERLOT_QUERY_UPDATE_RECORD.replaceAll("TABLENAME", this.dataProcessor.getConnectionProperties().get(TABLE_NAME_PROPERTY));

        try (Connection conn = this.dataProcessor.getDataSource().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, codGuide);

            if (ps.executeQuery() != null) {
                return true;
            }

        } catch (SQLException e) {
            LOGGER.info(e.getMessage());
            return false;
        }
        return false;
    }

    @Override
    public boolean confirmWithShippingGuide(String codGuide) {

        String sql = MERLOT_QUERY_UPDATE_RECORD.replaceAll("TABLENAME", this.dataProcessor.getConnectionProperties().get(TABLE_NAME_PROPERTY));

        try (Connection conn = this.dataProcessor.getDataSource().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "true");
            ps.setString(2, codGuide);

            return (ps.executeUpdate() > 0);
        } catch (SQLException e) {
            LOGGER.info(e.getMessage());
            return false;
        }
    }

}
