/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.api.impl;


import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static java.util.stream.Collectors.toList;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriver;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.model.PlcTag;
import org.apache.plc4x.java.api.types.PlcValueType;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.simulated.tag.SimulatedTag;
import org.apache.plc4x.merlot.api.PlcDevice;
import org.osgi.framework.BundleContext;
import org.osgi.service.dal.Device;
import org.osgi.service.dal.DeviceException;
import org.osgi.service.device.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.osgi.framework.ServiceReference;

/*
*
*/
public class PlcDeviceImpl implements PlcDevice {	
     
    private static final Logger LOGGER = LoggerFactory.getLogger(PlcDeviceImpl.class);
    private static final int BUFFER_SIZE = 1024;
    private static final int DEFAULT_WRITE_BATCH_SIZE = 9;
    private static final String WRITE_TASK_NAME = "write task";
    private static final String READ_TASK_NAME = "read task";    
    
    private static final String FILTER_DEVICE =  "(&(" + 
            org.osgi.framework.Constants.OBJECTCLASS + 
            "=" + PlcDevice.class.getName() + ")" +
            "(" + PlcDevice.SERVICE_UID + "=*))";   
    
    protected final BundleContext bc;
    protected boolean enable  = false;      
    protected boolean autostart = false;  
    
    protected PlcDriver plcDriver = null;    
    AtomicReference<PlcConnection> refPlcConnection;
    PlcConnection plcConnection = null;
    
    protected Hashtable<String, Object> deviceProperties;
    
    private final Map<UUID, PlcGroup> deviceGroups;
    
    private StopWatch watch = new StopWatch(); 
    int[] messageCounter = new int[2];
    
    //
    Disruptor<PlcDeviceReadEvent> readDisruptor = 
                new Disruptor<>(PlcDeviceReadEvent::new, BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
    RingBuffer<PlcDeviceReadEvent> readRingBuffer = readDisruptor.getRingBuffer(); 
    private Thread threadReadProcessor = null;
    
    Disruptor<PlcDeviceWriteEvent> writeDisruptor = 
                new Disruptor<>(PlcDeviceWriteEvent::new, BUFFER_SIZE, DaemonThreadFactory.INSTANCE); 
    RingBuffer<PlcDeviceWriteEvent> writeRingBuffer = writeDisruptor.getRingBuffer();     
    private Thread threadWriteProcessor = null;
    
    
    
    public PlcDeviceImpl(PlcDeviceBuilder builder) {
        this.refPlcConnection = new AtomicReference<PlcConnection>();
        this.deviceProperties = new Hashtable<String, Object>();
        this.deviceGroups = new HashMap<UUID, PlcGroup>();
        this.bc = builder.bc;
        deviceProperties.put(PlcDevice.SERVICE_DRIVER, builder.service_driver);         
        deviceProperties.put(PlcDevice.SERVICE_NAME, builder.service_name);
        deviceProperties.put(PlcDevice.SERVICE_DESCRIPTION, builder.service_description); 
        if (null != builder.service_uid) {
            deviceProperties.put(PlcDevice.SERVICE_UID, builder.service_uid.toString());
        } else {
            deviceProperties.put(PlcDevice.SERVICE_UID, UUID.randomUUID().toString());            
        }        
        
        if (null != builder.device_category) deviceProperties.put(Constants.DEVICE_CATEGORY, builder.device_category);
        if (null != builder.service_firmware_vendor) deviceProperties.put(Device.SERVICE_FIRMWARE_VENDOR, builder.service_firmware_vendor);  
        if (null != builder.service_firmware_version) deviceProperties.put(Device.SERVICE_FIRMWARE_VERSION, builder.service_firmware_version);  
        if (null != builder.service_hardware_vendor) deviceProperties.put(Device.SERVICE_HARDWARE_VENDOR, builder.service_hardware_vendor); 
        if (null != builder.service_hardware_version) deviceProperties.put(Device.SERVICE_HARDWARE_VERSION, builder.service_hardware_version);            
        if (null != builder.service_model) deviceProperties.put(Device.SERVICE_MODEL, builder.service_model); 
        if (null != builder.service_reference_uids) deviceProperties.put(Device.SERVICE_REFERENCE_UIDS, builder.service_reference_uids);
        if (null != builder.service_serial_number) deviceProperties.put(Device.SERVICE_SERIAL_NUMBER, builder.service_serial_number);
        if (null != builder.service_status) deviceProperties.put(Device.SERVICE_STATUS, builder.service_status);
        if (null != builder.service_status_detail) deviceProperties.put(Device.SERVICE_STATUS_DETAIL, builder.service_status_detail);
        if (null != builder.service_types) deviceProperties.put(Device.SERVICE_TYPES, builder.service_types);      
    }
        
    @Override
    public void init() throws Exception {
        //Prepare Disruptor for execute and launches the threads.
        final SequenceBarrier readSequenceBarrier = readRingBuffer.newBarrier();
        
        final PlcBatchEventProcessor<PlcDeviceReadEvent> readProcessor = 
                new PlcBatchEventProcessor<PlcDeviceReadEvent>(
                readRingBuffer,
                readSequenceBarrier,
                (event, sequence, endofbatch)->{

                    if (null != event.getPlcGroup()){
                        if (null != plcConnection) {
                            if (refPlcConnection.get().isConnected()) {
                                watch.start();
                                final PlcReadRequest.Builder builder = refPlcConnection.get().readRequestBuilder();
                                event.getPlcGroup().getGroupItems().forEach((u,i) ->{
                                    if (i.isEnable()) {
                                        builder.addTagAddress(u.toString(), i.getItemId());
                                    }
                                });     
                                final PlcReadRequest readRequest = builder.build();
                                try {        
                                    final PlcReadResponse syncResponse = readRequest.execute().get();
                                        event.getPlcGroup().getGroupItems().forEach((u,i) -> {
                                        final PlcValue plcValue = syncResponse.getPlcValue(u.toString());
                                        if (null == plcValue) {
                                            LOGGER.info(u.toString() + " Null value");
                                        } else
                                        i.setPlcValue(plcValue);
                                    });

                                } catch (Exception ex) {
                                    LOGGER.info(ex.getMessage());
                                }                                
                                watch.stop();
                                LOGGER.debug("Elapse time: " + watch.getTime());
                                watch.reset();
                            } else {
                                LOGGER.info("The driver is disconnected.");
                            }
                        } else {
                            LOGGER.info("Unassigned connection.");
                        }                        
                    }
                },
                4,
                null);
                        
        readRingBuffer.addGatingSequences(readProcessor.getSequence()); 

        final SequenceBarrier writeSequenceBarrier = writeRingBuffer.newBarrier();
        
        messageCounter[0] = 0;
        final PlcBatchEventProcessor<PlcDeviceWriteEvent> writeProcessor = 
                new PlcBatchEventProcessor<PlcDeviceWriteEvent>(
                writeRingBuffer,
                writeSequenceBarrier,
                (event, sequence, endofbatch)->{
                    if (null != event.getPlcItem()){
                        if (null != plcConnection) {
                            if (refPlcConnection.get().isConnected()) {
                                watch.start();
                                if (messageCounter[0] == 0) {
                                    readProcessor.pause();
                                }                                

                                messageCounter[0]++;   
                                
                                final PlcItem plcItem = event.getPlcItem();
                                final PlcTag plcTag = plcItem.getItemPlcTag();
                                
                                String strTag = getWritePlcTag(plcTag,
                                                    event.getByteBuf());
                                
                                final PlcWriteRequest writeRequest = 
                                        refPlcConnection.get().writeRequestBuilder().
                                        addTagAddress("bar", strTag, event.getByteBuf().array()).
                                        build();
                                        
                                //TODO: Agregar tiempo de supervision
                                PlcWriteResponse writeResponse = 
                                        writeRequest.execute().get();
                                        
                                LOGGER.info("Write to: " + event.getByteBuf().array());
                                watch.stop();
                                LOGGER.debug("Elapse time: " + watch.getTime());
                                watch.reset();
                                if ((messageCounter[0] > DEFAULT_WRITE_BATCH_SIZE) || (endofbatch)) {
                                    readProcessor.pause();
                                    messageCounter[0] = 0;
                                }                                
                                
                            }
                        }
                    }
                },
                4,
                null);
                        
        writeRingBuffer.addGatingSequences(writeProcessor.getSequence()); 
       
        //TODO: Lanzar las tareas.
        threadWriteProcessor = new Thread(writeProcessor);
        threadWriteProcessor.setName(this.getDeviceName() + WRITE_TASK_NAME);
        threadWriteProcessor.start();   

        threadReadProcessor = new Thread(readProcessor);
        threadReadProcessor.setName(this.getDeviceName() + READ_TASK_NAME);        
        threadReadProcessor.start();           

    }

    @Override
    public void destroy() throws Exception {
        //Shutdown Disruptor and threads.
        threadReadProcessor.interrupt();
        threadWriteProcessor.interrupt();
        
        writeDisruptor.shutdown();
        readDisruptor.shutdown();
        
    }

    @Override
    public void enable() {
        if (null != plcDriver) {
            //Try to connect
            final String url = (String) deviceProperties.get(Device.SERVICE_DRIVER);
            try {
                plcConnection = plcDriver.getConnection(url);
                plcConnection.connect();
                refPlcConnection.set(plcConnection);
                if (plcConnection.isConnected()) {
                    enable = true;
                    LOGGER.info("Device [{}] was enbale.", deviceProperties.get(Device.SERVICE_NAME));
                } else {
                    LOGGER.info("The connection could not be established, check the url.");
                }               
            } catch (PlcConnectionException ex) {
                LOGGER.info(ex.getLocalizedMessage());
                enable = false;
            }
        } else {
            LOGGER.info("The PlcDriver has not been assigned to the device.");
        }
    }

    @Override
    public void disable() {
        enable = false;
        LOGGER.info("Device [{}] was disable.", deviceProperties.get(Device.SERVICE_NAME));        
        try {
            if (null != plcConnection) {
                //All groups are disabled for security, they are activated 
                //individually manually. Simple job to do when the IDE 
                //is available.                 
                deviceGroups.forEach((u, d) -> d.disable());                
                plcConnection.close();
                if (!plcConnection.isConnected()) {
                    enable = false;
                    LOGGER.info("Device [{}] connection was close.", deviceProperties.get(Device.SERVICE_NAME));
                }
            }
        } catch (Exception ex) {
            LOGGER.info(ex.getLocalizedMessage());
        }
    }    
            
    @Override
    public boolean isEnable() {
        return enable;
    }    
    
    @Override
    public Hashtable<String, ?> getProperties() {
        return deviceProperties;
    }
        
    @Override
    public String getDeviceName() {
        return (String) deviceProperties.get(Device.SERVICE_NAME);
    }

    @Override
    public void setDeviceName(String devicename) {
        deviceProperties.put(Device.SERVICE_NAME, devicename);
    }

    @Override
    public String getDeviceDescription() {
        return (String) deviceProperties.get(Device.SERVICE_DESCRIPTION);
    }

    @Override
    public void setDeviceDescription(String devicedescription) {
        deviceProperties.put(Device.SERVICE_DESCRIPTION, devicedescription); 
    }

    @Override
    public void setUid(UUID uid) {
        deviceProperties.put(PlcDevice.SERVICE_UID, uid.toString());
    }

    @Override
    public UUID getUid() {
        return UUID.fromString((String) deviceProperties.get(PlcDevice.SERVICE_UID));
    }

    @Override
    public void setUrl(String url) {
        if (!enable) {
            deviceProperties.put(Device.SERVICE_DRIVER, url); 
        }
    }

    @Override
    public String getUrl() {
        return (String) deviceProperties.get(Device.SERVICE_DRIVER); 
    }

    @Override
    public void putGroup(PlcGroup group) {
        if ((!enable) && (!deviceGroups.containsKey(group.getGroupUid()))) {
                group.setGroupDeviceUid(UUID.fromString((String) deviceProperties.get(PlcDevice.SERVICE_UID)));
                group.setPlcConnection(refPlcConnection);
                deviceGroups.put(group.getGroupUid(), group);
                bc.registerService(new String[]{Job.class.getName(), 
                    PlcGroup.class.getName()}, 
                  group, 
               group.getProperties());
        } else {
            LOGGER.info("The device is enabled or the group identifier already exists.");
        }
    }

    @Override
    public PlcGroup getGroup(UUID uid) {
        return deviceGroups.get(uid);
    }

    @Override
    public void removeGroup(UUID uid) {
        String filter = FILTER_DEVICE.replace("*", uid.toString()); 
        ServiceReference<?> sr = bc.getServiceReference(filter);
        bc.ungetService(sr); 
        deviceGroups.remove(uid);
    }

    @Override
    public List<PlcGroup> getGroups() { 
        return deviceGroups.values().stream().
                collect(toList());
    }
            
    @Override
    public void noDriverFound() {
        LOGGER.info("The associated driver is not found. go to IDLE.");
    }

    @Override
    public Object getServiceProperty(String propKey) {
        return deviceProperties.get(propKey);
    }

    @Override
    public String[] getServicePropertyKeys() {
        return deviceProperties.keySet().toArray(new String[deviceProperties.size()]);
    }

    @Override
    public void remove() throws DeviceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void attach(PlcDriver driver) {
        LOGGER.info("Device: {} attach to driver {} ", deviceProperties.get(Device.SERVICE_NAME),  driver.getProtocolCode());
        this.plcDriver = driver;
    }

    @Override
    public String getWritePlcTag(PlcTag plcTag, ByteBuf byteBuf, String... args) {
        String strTag = null;
        if (plcTag instanceof SimulatedTag) {
            final SimulatedTag localTag = (SimulatedTag) plcTag;
            switch (plcTag.getPlcValueType()) { 
                case BOOL:;
                    strTag =    localTag.getType().name() + "/" + 
                                localTag.getName() +                             
                                ":BOOL" + 
                                "[" + 
                                byteBuf.capacity() +
                                "]";
                break;
                case BYTE:;
                    strTag =    localTag.getType().name() + "/" + 
                                localTag.getName() + 
                                ":BYTE" +                             
                                "[" + 
                                byteBuf.capacity() +
                                "]";                
                break;

            };
        }
        
        return strTag;
    }
    
    public static class PlcDeviceBuilder {
        private final BundleContext bc;        
        private final String service_name;
        private final String service_description;
        private final String service_driver;         
        private UUID service_uid;          
        private String device_category;
        private String service_firmware_vendor;  
        private String service_firmware_version;  
        private String service_hardware_vendor; 
        private String service_hardware_version;            
        private String service_model; 
        private String[] service_reference_uids;
        private String service_serial_number;
        private String service_status;
        private String service_status_detail;
        private String[] service_types;         

        public PlcDeviceBuilder(BundleContext bc, String service_driver, String service_name, String service_description) {
            this.bc = bc;
            this.service_name = service_name;
            this.service_description = service_description;
            this.service_driver = service_driver;
            String[] drv = service_driver.split(":");
            this.device_category = drv[0];
        }

        public PlcDeviceBuilder setServiceUid(UUID serviceuid) {
            this.service_uid = serviceuid;
            return this;
        }

        public PlcDeviceBuilder setDeviceCategory(String device_category) {
            this.device_category = device_category;
            return this;            
        }

        public PlcDeviceBuilder setServiceFirmwareVendor(String service_firmware_vendor) {
            this.service_firmware_vendor = service_firmware_vendor;
            return this;            
        }

        public PlcDeviceBuilder setServiceFirmwareVersion(String service_firmware_version) {
            this.service_firmware_version = service_firmware_version;
            return this;            
        }

        public PlcDeviceBuilder setServiceHardwareVendor(String service_hardware_vendor) {
            this.service_hardware_vendor = service_hardware_vendor;
            return this;            
        }

        public PlcDeviceBuilder setServiceHardwareVersion(String service_hardware_version) {
            this.service_hardware_version = service_hardware_version;
            return this;            
        }

        public PlcDeviceBuilder setServiceModel(String service_model) {
            this.service_model = service_model;
            return this;            
        }

        public PlcDeviceBuilder setServiceReferenceUids(String[] service_reference_uids) {
            this.service_reference_uids = service_reference_uids;
            return this;            
        }

        public PlcDeviceBuilder setServiceSerialNumber(String service_serial_number) {
            this.service_serial_number = service_serial_number;
            return this;            
        }

        public PlcDeviceBuilder setServiceStatus(String service_status) {
            this.service_status = service_status;
            return this;            
        }

        public PlcDeviceBuilder setServiceStatusDetail(String service_status_detail) {
            this.service_status_detail = service_status_detail;
            return this;            
        }

        public PlcDeviceBuilder setServiceTypes(String[] service_types) {
            this.service_types = service_types;
            return this;            
        }
        

        public PlcDevice build() {
            PlcDevice plcdevice = new PlcDeviceImpl(this);
            validateBaseDeviceObject(plcdevice);
            return plcdevice;
        }
        
        private void validateBaseDeviceObject(PlcDevice plcdevice) {
            //
        }
    }
    

}
