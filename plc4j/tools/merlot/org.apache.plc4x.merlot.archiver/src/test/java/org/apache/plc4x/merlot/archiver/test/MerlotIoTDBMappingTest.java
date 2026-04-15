package org.apache.plc4x.merlot.archiver.test;

import org.apache.iotdb.pipe.api.type.Type;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarDouble;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarFloat;
import org.apache.plc4x.merlot.archiver.core.MerlotIoTDBMapping;
import org.junit.jupiter.api.Assertions; 
import org.junit.jupiter.api.BeforeEach; // JUnit 5
import org.junit.jupiter.api.Test;

public class MerlotIoTDBMappingTest {
    private MerlotIoTDBMapping.EpicsMetadata testMeta;

    @BeforeEach
    public void setUp() {
        // Inicialización correcta antes de cada test
        testMeta = new MerlotIoTDBMapping.EpicsMetadata(12345678, 999, 1, 2);
    }

    @Test
    public void testConvertFloat() {
        float value = 12.34f;
        Object result = MerlotIoTDBMapping.fromIotdb(Type.FLOAT).convert(value, testMeta);

        Assertions.assertTrue(result instanceof ScalarFloat);
        ScalarFloat scalar = (ScalarFloat) result;
        Assertions.assertEquals(value, scalar.getVal(), 0.001);
        Assertions.assertEquals(testMeta.nano, scalar.getNano());
    }

    @Test
    public void testConvertDouble() {
        double value = 99.999;
        Object result = MerlotIoTDBMapping.fromIotdb(Type.DOUBLE).convert(value, testMeta);

        Assertions.assertTrue(result instanceof ScalarDouble);
        ScalarDouble scalar = (ScalarDouble) result;
        Assertions.assertEquals(value, scalar.getVal(), 0.00001);
        Assertions.assertEquals(testMeta.status, scalar.getStatus());
    }

    @Test
    public void testUnsupportedType() {
        // Verificamos que lance la excepción esperada para tipos como BLOB
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            MerlotIoTDBMapping.fromIotdb(Type.BLOB);
        });
    }
}