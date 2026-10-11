package org.apache.plc4x.merlot.lector62x.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.plc4x.merlot.lector62x.api.MerlotDecanterProcessorDataLector62X;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageRepository;
import org.apache.plc4x.merlot.lector62x.api.MerlotLector620ShippingPackageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotLecto620ShippingPackageRepositoryImpl implements MerlotLecto620ShippingPackageRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotLecto620ShippingPackageRepositoryImpl.class);

    private static final String QUERY_CHECK_GUIDE = "SELECT 1 FROM TABLENAME WHERE codguia = ? LIMIT 1";
    private static final String QUERY_UPDATE_RECORD = "UPDATE TABLENAME SET guiaconfirmada = ? WHERE codguia = ?";
    private static final String QUERY_FIND_UNCONFIRMED
            = "SELECT DISTINCT codguia, numeropieza, totalpiezas, oficinaorigen, oficinadestino, "
            + "servicio, casillero, ruta, peso, id_tipoprocesoplc FROM TABLENAME WHERE guiaconfirmada = 'false' LIMIT 100";
    private static final String TABLE_NAME_PROPERTY = "table.name";

    private final MerlotDecanterProcessorDataLector62X dataProcessor;

    public MerlotLecto620ShippingPackageRepositoryImpl(MerlotDecanterProcessorDataLector62X dataProcessor) {
        this.dataProcessor = dataProcessor;
    }

    private String sql(String template) {
        String table = dataProcessor.getConnectionProperties().get(TABLE_NAME_PROPERTY);
        return template.replace("TABLENAME", table);
    }

    private Connection connection() throws SQLException {
        DataSource ds = dataProcessor.getDataSource();
        if (ds == null) {
            throw new IllegalStateException("DataSource is not yet available");
        }
        return ds.getConnection();
    }

    @Override
    public boolean checkShippingGuide(String codGuide) throws Exception {
        try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql(QUERY_CHECK_GUIDE))) {
            ps.setString(1, codGuide);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean confirmWithShippingGuide(String codGuide) throws Exception {
        try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql(QUERY_UPDATE_RECORD))) {
            ps.setString(1, "true");
            ps.setString(2, codGuide);
            int rows = ps.executeUpdate();
            LOGGER.info("Guide {} confirmed ({} row(s)))", codGuide, rows);
            return rows > 0;
        }
    }

    @Override
    public List<MerlotLector620ShippingPackageDTO> findUnconfirmed() throws Exception {
        List<MerlotLector620ShippingPackageDTO> list = new ArrayList<>();
        try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql(QUERY_FIND_UNCONFIRMED)); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new MerlotLector620ShippingPackageDTO(
                        rs.getString("codguia"),
                        rs.getString("numeropieza"),
                        rs.getString("totalpiezas"),
                        rs.getString("oficinaorigen"),
                        rs.getString("oficinadestino"),
                        rs.getString("servicio"),
                        rs.getString("casillero"),
                        rs.getString("ruta"),
                        rs.getDouble("peso"),
                        rs.getString("id_tipoprocesoplc")));
            }
        }
        if (list.isEmpty()) {
             LOGGER.info("Database Response: There are no unconfirmed records.");
        }
        return list;
    }
}
