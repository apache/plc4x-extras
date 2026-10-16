package org.apache.plc4x.merlot.lector62x.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

//    private final static String createTableQueryNoReadTemplate
//            = "CREATE TABLE lector_no_read (id INTEGER PRIMARY KEY AUTOINCREMENT, fecha_no_read TEXT, peso_no_read REAL)";
    private final static String createTableQueryNoReadTemplate
            = "CREATE TABLE IF NOT EXISTS lector_no_read ( id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, fecha_no_read VARCHAR(30), peso_no_read DOUBLE PRECISION )";
    private final static String insertNoReadQueryTemplate
            = "INSERT INTO lector_no_read(fecha_no_read, peso_no_read) VALUES(?,?)";

    private final String SUM_WEIGHT = "SELECT COALESCE(SUM(peso_no_read), 0) AS total_peso FROM lector_no_read WHERE fecha_no_read BETWEEN ? AND ?";

    private static final String TABLE_NAME_PROPERTY = "table.name";
    private volatile boolean noReadTableReady = false;
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
        if (!noReadTableReady) {
            try (Connection c = ds.getConnection()) {
                createTableNoRead(c);
            }
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

    @Override
    public void insertNoRead(String dateTimeFormatted, String weight) {
        try (Connection conn = connection(); PreparedStatement insertStatement = conn.prepareStatement(insertNoReadQueryTemplate)) {
            insertStatement.setString(1, dateTimeFormatted);
            insertStatement.setDouble(2, Double.parseDouble(weight));

            //Submit the form
            if (insertStatement.executeUpdate() > 0) {
                LOGGER.info("Record inserted with guide no read");
            }
        } catch (SQLException sqle) {
            LOGGER.info(sqle.getMessage());

        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
    }

    @Override
    public Double totalBetween(String start, String end) {
        LOGGER.info("Start date parameter {}\nEnd date and time parameter {}", start, end);
        try (Connection conn = connection(); PreparedStatement pstmt = conn.prepareStatement(SUM_WEIGHT)) {
            pstmt.setString(1, start);
            pstmt.setString(2, end);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    double totalW = rs.getDouble("total_peso");
                    LOGGER.info("The total weight between [{}] and [{}] is {} kg", start, end, totalW);
                    return totalW;
                }
            }
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

        return 0.0;
    }

    private void createTableNoRead(Connection connection) {
        try (Statement createStatement = connection.createStatement()) {
            createStatement.executeUpdate(createTableQueryNoReadTemplate);
            noReadTableReady = true;
            LOGGER.info("Table 'lector_no_read' is ready");
        } catch (SQLException e) {
            LOGGER.error("Can't create table 'lector_no_read'", e);
        }
    }
}
