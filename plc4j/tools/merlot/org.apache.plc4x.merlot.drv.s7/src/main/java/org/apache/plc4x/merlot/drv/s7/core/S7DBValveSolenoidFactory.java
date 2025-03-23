/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.plc4x.merlot.drv.s7.core;

import io.netty.buffer.Unpooled;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.apache.plc4x.merlot.db.core.DBBaseFactory;
import org.epics.nt.NTScalar;
import org.epics.nt.NTScalarBuilder;
import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.pv.Field;
import org.epics.pvdata.pv.FieldBuilder;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;

/**
 *
 * @author administrador
 */
public class S7DBValveSolenoidFactory extends DBBaseFactory {

    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();

    @Override
    public DBRecord create(String recordName) {

        final NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        final FieldBuilder fb = fieldCreate.createFieldBuilder();

        Field cmd = fb.setId("cmd").
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iErrorCode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iStatus", fieldCreate.createScalar(ScalarType.pvShort)).
                add("bPB_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPB_Home", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPB_Work", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPBEN_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPBEN_Home", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPBEN_Work", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bHomeOn", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bWorkOn", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bSignalHome", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bSignalWork", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bInterlock", fieldCreate.createScalar(ScalarType.pvBoolean)).
                createStructure();

        Field sts = fb.setId("sts").
                add("bNoHomeFeedback", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bNoWorkFeedback", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bHomeFeedbackStillActive", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bWorkFeedbackStillActive", fieldCreate.createScalar(ScalarType.pvBoolean)).
                createStructure();

        Field par = fb.setId("par").
                add("tTimeOut", fieldCreate.createScalar(ScalarType.pvInt)).
                createStructure();

        PVStructure pvStructure = ntScalarBuilder.
                value(ScalarType.pvShort).
                addDescriptor().
                add("cmd", cmd).
                add("sts", sts).
                add("par", par).
                add("id", fieldCreate.createScalar(ScalarType.pvString)).
                add("offset", fieldCreate.createScalar(ScalarType.pvString)).
                add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
                add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("write_value", fieldCreate.createScalar(ScalarType.pvShort)).
                addAlarm().
                addTimeStamp().
                addDisplay().
                addControl().
                createPVStructure();

        DBRecord dbRecord = new DBS7ValSolenoidRecord(recordName, pvStructure);

        return dbRecord;
    }

    class DBS7ValSolenoidRecord extends DBRecord implements PlcItemListener {

        private int BUFFER_SIZE = 14;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, par{tTimeOut})";

        /*
               
         */
        private PVShort value;
        private PVShort write_value;
        private PVBoolean write_enable;

        /*
        cmd
         */
        private PVShort iMode;
        private PVShort iErrorCode;
        private PVShort iStatus;
        private PVBoolean bPB_ResetError;
        private PVBoolean bPB_Home;
        private PVBoolean bPB_Work;
        private PVBoolean bPBEN_ResetError;
        private PVBoolean bPBEN_Home;
        private PVBoolean bPBEN_Work;
        private PVBoolean bHomeOn;
        private PVBoolean bWorkOn;
        private PVBoolean bSignalHome;
        private PVBoolean bSignalWork;
        private PVBoolean bError;
        private PVBoolean bInterlock;

        /*
        sts
         */
        private PVBoolean bNoHomeFeedback;
        private PVBoolean bNoWorkFeedback;
        private PVBoolean bHomeFeedbackStillActive;
        private PVBoolean bWorkFeedbackStillActive;

        /*
        par
         */
        private PVInt tTimeOut;

        byte byTemp;

        public DBS7ValSolenoidRecord(String recordName, PVStructure pvStructure) {
            super(recordName, pvStructure);

            bFirtsRun = true;

            PVStructure pvStructureCmd = pvStructure.getStructureField("cmd");
            iMode = pvStructureCmd.getShortField("iMode");
            iErrorCode = pvStructureCmd.getShortField("iErrorCode");
            iStatus = pvStructureCmd.getShortField("iStatus");
            bPB_ResetError = pvStructureCmd.getBooleanField("bPB_ResetError");
            bPB_Home = pvStructureCmd.getBooleanField("bPB_Home");
            bPB_Work = pvStructureCmd.getBooleanField("bPB_Work");
            bPBEN_ResetError = pvStructureCmd.getBooleanField("bPBEN_ResetError");
            bPBEN_Home = pvStructureCmd.getBooleanField("bPBEN_Home");
            bPBEN_Work = pvStructureCmd.getBooleanField("bPBEN_Work");
            bHomeOn = pvStructureCmd.getBooleanField("bHomeOn");
            bWorkOn = pvStructureCmd.getBooleanField("bWorkOn");
            bSignalHome = pvStructureCmd.getBooleanField("bSignalHome");
            bSignalWork = pvStructureCmd.getBooleanField("bSignalWork");
            bError = pvStructureCmd.getBooleanField("bError");
            bInterlock = pvStructureCmd.getBooleanField("bInterlock");

            PVStructure pvStructureSts = pvStructure.getStructureField("sts");
            bNoHomeFeedback = pvStructureSts.getBooleanField("bNoHomeFeedback");
            bNoWorkFeedback = pvStructureSts.getBooleanField("bNoWorkFeedback");
            bHomeFeedbackStillActive = pvStructureSts.getBooleanField("bHomeFeedbackStillActive");
            bWorkFeedbackStillActive = pvStructureSts.getBooleanField("bWorkFeedbackStillActive");

            PVStructure pvStructurePar = pvStructure.getStructureField("par");
            tTimeOut = pvStructurePar.getIntField("tTimeOut");
        }

        @Override
        public void process() {
        }

        @Override
        public void atach(final PlcItem plcItem) {
            this.plcItem = plcItem;
            ParseOffset(this.getPVStructure().getStringField("offset").get());
            innerBuffer = plcItem.getItemByteBuf().slice(byteOffset, BUFFER_SIZE);
            innerWriteBuffer = Unpooled.copiedBuffer(innerBuffer);
        }

        @Override
        public void detach() {
            this.plcItem = null;
        }

        @Override
        public void update() {
            if (null != plcItem) {
                innerBuffer.resetReaderIndex();

                /*
                cmd
                 */
                iMode.put(innerBuffer.getShort(0));
                iErrorCode.put(innerBuffer.getShort(2));
                iStatus.put(innerBuffer.getShort(4));

                byTemp = innerBuffer.getByte(6);
                bPB_ResetError.put(isBitSet(byTemp, 0));
                bPB_Home.put(isBitSet(byTemp, 1));
                bPB_Work.put(isBitSet(byTemp, 2));
                bPBEN_ResetError.put(isBitSet(byTemp, 3));
                bPBEN_Home.put(isBitSet(byTemp, 4));
                bPBEN_Work.put(isBitSet(byTemp, 5));
                bHomeOn.put(isBitSet(byTemp, 6));
                bWorkOn.put(isBitSet(byTemp, 7));

                byTemp = innerBuffer.getByte(7);
                bSignalHome.put(isBitSet(byTemp, 0));
                bSignalWork.put(isBitSet(byTemp, 1));
                bError.put(isBitSet(byTemp, 2));
                bInterlock.put(isBitSet(byTemp, 3));

                /*
                sts
                 */
                byTemp = innerBuffer.getByte(8);
                bNoHomeFeedback.put(isBitSet(byTemp, 0));
                bNoWorkFeedback.put(isBitSet(byTemp, 1));
                bHomeFeedbackStillActive.put(isBitSet(byTemp, 2));
                bWorkFeedbackStillActive.put(isBitSet(byTemp, 3));
                /*
                par
                 */
                tTimeOut.put(innerBuffer.getInt(10));
            }
        }
    }
}
