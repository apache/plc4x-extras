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
package org.apache.plc4x.merlot.db.api;



import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.epics.pvdata.property.AlarmSeverity;
import org.epics.pvdata.property.AlarmStatus;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdatabase.PVRecord;

@SuppressWarnings("nls")
public class DBRecord extends PVRecord   implements PlcItemListener {   
    protected static final String MONITOR_FIELDS = "field(write_value,"+         
            "id,"+
            "offset,"+
            "description,"+
            "scan_time,"+  
            "scan_enable,"+
            "write_enable,"+               
            "display{limitLow,limitHigh},"+
            "control{limitLow,limitHigh,minStep})";      
    
    protected static final String MONITOR_VALUE_FIELD = "field(value)";
    protected static final String MONITOR_WRITE_FIELD = "field(write_value)"; 
    protected static final String MONITOR_SCALAR_FIELDS = "field(write_enable, write_value)";      
    
    private static final Pattern BYTE_OFFSET_PATTERN = 
             Pattern.compile("(?<byteOffset>\\d{1,5})");
    
    private static final Pattern BIT_OFFSET_PATTERN = 
             Pattern.compile( "(?<byteOffset>\\d{1,5}).(?<bitOffset>\\d{1,5})");
    
    protected static final String BYTE_OFFSET = "byteOffset";
    protected static final String BIT_OFFSET = "bitOffset"; 
    
    protected int byteOffset = -1;
    protected byte bitOffset = -1;  
    protected  ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets;    
    
    
    protected PlcItem plcItem = null; 
    protected ByteBuf innerBuffer = null; 
    protected ByteBuf innerWriteBuffer = null;
    
    protected boolean bFirtsRun = true;
   
    
    public DBRecord(String recordName, PVStructure pvStructure) {
        super(recordName, pvStructure);           
            bFirtsRun = true;            
            fieldOffsets = new ArrayList<>();
           
            fieldOffsets.add(0, null);                      //All structure
            fieldOffsets.add(1, null);                      //write_enable
            fieldOffsets.add(2, new ImmutablePair(0,-1));   //scalar offset
    }
    
    public Optional<PlcItem> getPlcItem(){
        if (null == plcItem) return Optional.empty();
        return Optional.of(plcItem);
    };
    
    public Optional<ByteBuf> getInnerBuffer(){
        if (null == innerBuffer) return Optional.empty();
        return Optional.of(innerBuffer);
    };  

    public Optional<ByteBuf> getWriteBuffer(){
        if (null == innerWriteBuffer) return Optional.empty();
        return Optional.of(innerWriteBuffer);
    };    
    
    public int getByteOffset(){
        return byteOffset;
    }
    
    public byte getBiteOffset(){
        return bitOffset;
    }
    
    public ArrayList<ImmutablePair<Integer, Byte>> getFieldOffsets(){
        return fieldOffsets;
    }
            
    public String getFieldsToMonitor(){
        return MONITOR_VALUE_FIELD;
    };
    
    public void ParseOffset(String strOffset){
        Matcher matcher;
        if ((matcher = BYTE_OFFSET_PATTERN.matcher(strOffset)).matches()){
            byteOffset = Integer.parseInt(matcher.group(BYTE_OFFSET ));
            fieldOffsets.set(2, new ImmutablePair(byteOffset,(byte) -1));//** Check !! Error here. Set index 2 out bound array
        } else if ((matcher = BIT_OFFSET_PATTERN.matcher(strOffset)).matches()){
            byteOffset = Integer.parseInt(matcher.group(BYTE_OFFSET ));
            bitOffset  = (byte) Integer.parseInt(matcher.group(BIT_OFFSET )); 
            fieldOffsets.set(2, new ImmutablePair(byteOffset, bitOffset));            
        }
    }
    
    @Override
    public void atach(PlcItem plcItem) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void detach() {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    @Override
    public void update() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setStaus(AlarmSeverity alrmSeverity, AlarmStatus alrmStatus, String alrmMsg) {
        PVStructure structAlarm = getPVStructure().getStructureField("alarm");
        PVInt pvSeverity = (PVInt) structAlarm.getIntField("severity");
        PVInt pvStatus = (PVInt) structAlarm.getIntField("status");  
        PVString pvMsg = (PVString) structAlarm.getStringField("message"); 
                
        pvSeverity.put(alrmSeverity.ordinal());
        pvStatus.put(alrmStatus.ordinal());
        pvMsg.put(alrmMsg);        
    }  
    
    public  boolean isBitSet(byte b, int bit) {
        return ((b >> bit) & 1 ) == 1;
    }        

    public void setBit(byte b, int bit) {
        b |= 1 << bit;
    }  

    public void clearBit(byte b, int bit) {
        b &= ~(1 << bit);
    }      
       
    
}
