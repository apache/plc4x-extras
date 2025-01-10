/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.plc4x.merlot.drv.s7.core;

import io.netty.buffer.ByteBuf;
import static io.netty.buffer.Unpooled.buffer;
import java.util.UUID;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.spi.values.PlcRawByteArray;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.impl.PlcItemImpl;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVByte;
import org.epics.pvdata.pv.PVFloat;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author lerb
 */
public class S7DBAoTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBAoTest.class);
    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;

    private PVShort iMode;
    private PVShort iErrorCode;

    private PVFloat rValue;
    private PVFloat rAutoValue;
    private PVFloat rManualValue;
    private PVFloat rEstopValue;

    private PVBoolean bPB_ResetError;
    private PVBoolean bPBEN_ResetError;
    private PVBoolean bError;
    private PVBoolean bInterlock;

    private PVShort iEstopFunction;

    private PVBoolean bOutOfRange;
    private PVBoolean bConfigurationError;

    private PVByte bySpare;
    private ByteBuf byteBuf;

    public S7DBAoTest() {
    }

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
        /*
        Create an object Bytebuf
         */
        logger.info("Creating data buffer...");
        byteBuf = buffer(512);
        byteBuf.setShort(0, 1234);   //imode
        byteBuf.setShort(2, 4321);   //iErrorCode
        byteBuf.setFloat(4, 3.1416F); //rValue
        byteBuf.setFloat(8, 3.1416F * 2); //rAutoValue
        byteBuf.setFloat(12, 3.1416F * 3); //rManualValue
        byteBuf.setFloat(16, 3.1416F * 4); //rEstopValue

        byteBuf.setByte(20, 15);//bPB_ResetError=bPBEN_ResetError=bError=bInterlock
        byteBuf.setShort(22, 1234);//iEstopFunction

        byteBuf.setByte(24, 3);//bOutOfRange=bConfigurationError

        byteBuf.setByte(26, 111);//bySpare

    }

    @AfterEach
    public void tearDown() {
        logger.info("Closing buffer...");
        byteBuf=null;
    }

    @Test
    public void dbAoRecord() {

        PlcValue plcValue = new PlcRawByteArray(byteBuf.array());
        /*
        defining an id and PlcItem
         */
        String uuid = UUID.randomUUID().toString();

        PlcItem plcItem = new PlcItemImpl.PlcItemBuilder("ITEM_DB42").
                setItemDescription("SIM DB42 S7").
                setItemId(uuid).
                setItemUid(UUID.fromString(uuid)).
                build();

        S7DBAoFactory AIFactory = new S7DBAoFactory();
        DBRecord AO_00 = AIFactory.create("AO_00");
        
        logger.info(String.format("DbRecord analog output: %s", AO_00.toString()));
        PVString pvStrOffset = AO_00.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");

        AO_00.atach(plcItem);

        plcItem.addItemListener(AO_00);
        plcItem.setPlcValue(plcValue);

        PVStructure pvStructureCmd = AO_00.getPVRecordStructure().getPVStructure().getStructureField("cmd");
        PVStructure pvStructureSts = AO_00.getPVRecordStructure().getPVStructure().getStructureField("sts");
        PVStructure pvStructurePar = AO_00.getPVRecordStructure().getPVStructure().getStructureField("par");

        value = AO_00.getPVRecordStructure().getPVStructure().getShortField("value");
        write_value = AO_00.getPVRecordStructure().getPVStructure().getShortField("write_value");
        write_enable = AO_00.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        /*
        cmd
         */
        iMode = pvStructureCmd.getShortField("iMode");
        iErrorCode = pvStructureCmd.getShortField("iErrorCode");
        rValue = pvStructureCmd.getFloatField("rValue");
        rAutoValue = pvStructureCmd.getFloatField("rAutoValue");
        rManualValue = pvStructureCmd.getFloatField("rManualValue");
        rEstopValue = pvStructureCmd.getFloatField("rEstopValue");
        bPB_ResetError = pvStructureCmd.getBooleanField("bPB_ResetError");
        bPBEN_ResetError = pvStructureCmd.getBooleanField("bPBEN_ResetError");
        bError = pvStructureCmd.getBooleanField("bError");
        bInterlock = pvStructureCmd.getBooleanField("bInterlock");
        iEstopFunction = pvStructureCmd.getShortField("iEstopFunction");

        /*
        sts
         */
        bOutOfRange = pvStructureSts.getBooleanField("bOutOfRange");
        bConfigurationError = pvStructureSts.getBooleanField("bConfigurationError");

        /*
        par
         */
        bySpare = pvStructurePar.getByteField("bySpare");

        //Assertions
        assertEquals(1234, iMode.get());
        assertEquals(4321, iErrorCode.get());
        assertEquals(3.1416F, rValue.get());
        assertEquals(3.1416F * 2, rAutoValue.get());
        assertEquals(3.1416F * 3, rManualValue.get());
        assertEquals(3.1416F * 4, rEstopValue.get());
        assertEquals(true, bPB_ResetError.get());
        assertEquals(true, bPBEN_ResetError.get());
        assertEquals(true, bError.get());
        assertEquals(true, bInterlock.get());
        assertEquals(1234, iEstopFunction.get());
        assertEquals(true, bOutOfRange.get());
        assertEquals(true, bConfigurationError.get());
        assertEquals(111, bySpare.get());

        plcItem.setPlcValue(plcValue);
    }
}
