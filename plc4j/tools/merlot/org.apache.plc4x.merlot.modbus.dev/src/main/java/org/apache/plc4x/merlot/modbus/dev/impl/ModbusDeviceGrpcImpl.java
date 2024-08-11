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
package org.apache.plc4x.merlot.modbus.dev.impl;


import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.apache.plc4x.merlot.api.BufferReply;
import org.apache.plc4x.merlot.api.ModbusArea;
import org.apache.plc4x.merlot.api.ModbusId;
import org.apache.plc4x.merlot.api.ModbusInfo;
import org.apache.plc4x.merlot.api.Plc4xModbusGrpc;
import org.apache.plc4x.merlot.api.WriteModbusRegister;
import org.apache.plc4x.merlot.modbus.dev.api.ModbusDevice;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ModbusDeviceGrpcImpl extends Plc4xModbusGrpc.Plc4xModbusImplBase implements BindableService {

    private static final Logger LOG = LoggerFactory.getLogger(ModbusDeviceGrpcImpl.class); 
    
    private final BundleContext bundleContext;    

    public ModbusDeviceGrpcImpl(org.osgi.framework.BundleContext bundleContext) {
        super();
        this.bundleContext = bundleContext;
    }
    
    @Override
    public void getModbusDevices(ModbusId request, StreamObserver<BufferReply> responseObserver) { 
        BufferReply myReply = null;
        LOG.info("Device: " + request.getUid());
        List<Integer> uids = new ArrayList<>();
                
        ServiceReference<?>[] devicesref = null;
        try {
            devicesref = bundleContext.getAllServiceReferences(ModbusDevice.class.getName(), null);
        } catch (InvalidSyntaxException ex) {
            LOG.error(ex.getMessage());
        } finally {        
            for (ServiceReference devref:devicesref) {
                uids.add(Integer.parseInt(devref.getProperty("modbus.uid").toString()));
            }
        }

        Iterable<Integer> values = uids;
        myReply = BufferReply.newBuilder().addAllData(values).build();
        responseObserver.onNext(myReply);
        responseObserver.onCompleted();        
    }

    @Override
    public void getModbusDevicesInfo(ModbusId request, StreamObserver<ModbusInfo> responseObserver) {
        ModbusInfo myReply = null;
        ServiceReference<?>[] devicesref = null;
        
        try {
            devicesref = bundleContext.getServiceReferences(ModbusDevice.class.getName(), "(modbus.uid=" + request.getUid() +")");
            if (devicesref != null) {
                ModbusDevice device = (ModbusDevice) bundleContext.getService(devicesref[0]);
                myReply = ModbusInfo.newBuilder().
                        setUid(device.getUnitIdentifier()).
                        setDescription(device.getUnitDescription()).
                        setEnable(device.getEnabled()).
                        setDiscreteInputs(device.getDiscreteInputs().capacity() * 8).
                        setCoils(device.getCoils().capacity() * 8).                        
                        setInputRegister(device.getInputRegisters().capacity() / 2).
                        setHoldingRegister(device.getHoldingRegisters().capacity() / 2).
                        build();                        
            }
        } catch (InvalidSyntaxException ex) {
            LOG.error(ex.getMessage());
        }
        
        responseObserver.onNext(myReply);
        responseObserver.onCompleted();          
    }

    @Override
    public void getModbusData(ModbusArea request, StreamObserver<BufferReply> responseObserver) {
        BufferReply myReply = null;
        ServiceReference<?>[] devicesref = null;
        List<Integer> values = new ArrayList<>();        
        
        try {
            devicesref = bundleContext.getServiceReferences(ModbusDevice.class.getName(), "(modbus.uid=" + request.getUid() +")");
            if (devicesref != null) {
                ModbusDevice device = (ModbusDevice) bundleContext.getService(devicesref[0]);
                switch (request.getArea()) {
                    case MB_COILS: {
                            for (int i=request.getInit(); i < (request.getInit() + request.getLength()); i++) {
                                values.add(device.getCoil(i)?1:0);
                            }
                        }
                        break;
                    case MB_DISCRETE_INPUTS: {
                            for (int i=request.getInit(); i < (request.getInit() + request.getLength()); i++) {
                                values.add(device.getDiscreteInput(i)?1:0);
                            }
                        }                        
                        break;
                    case MB_INPUT_REGISTER: {
                            for (int i=request.getInit(); i < (request.getInit() + request.getLength()); i++) {
                                values.add(Integer.valueOf(device.getInputRegister(i)));
                            }
                        }                   
                        break;
                    case MB_HOLDING_REGISTER: {
                            for (int i=request.getInit(); i < (request.getInit() + request.getLength()); i++) {
                                values.add(Integer.valueOf(device.getHoldingRegister(i)));
                            }
                        }     
                        break;
                    case UNRECOGNIZED:
                        break;
                }
                      
            }
        } catch (InvalidSyntaxException ex) {
            LOG.error(ex.getMessage());
        }
        
        myReply = BufferReply.newBuilder().addAllData(values).build();        
        
        responseObserver.onNext(myReply);
        responseObserver.onCompleted();    
    }

    @Override
    public void writeModbusData(WriteModbusRegister request, StreamObserver<BufferReply> responseObserver) {
        BufferReply myReply = null;
        ServiceReference<?>[] devicesref = null;
        List<Integer> values = new ArrayList<>();        
        
        try {
            devicesref = bundleContext.getServiceReferences(ModbusDevice.class.getName(), "(modbus.uid=" + request.getUid() +")");
            if (devicesref != null) {
                ModbusDevice device = (ModbusDevice) bundleContext.getService(devicesref[0]);
                switch (request.getArea()) {
                    case MB_COILS: {
                            device.setCoil(request.getRegister(), request.getValue()>0);
                            values.add(request.getValue());
                        }
                        break;
                    case MB_DISCRETE_INPUTS: {
                            device.setDiscreteInput(request.getRegister(), request.getValue()>0);
                            values.add(request.getValue());                            
                        }                        
                        break;
                    case MB_INPUT_REGISTER: {
                            device.setInputRegister(request.getRegister(), (short) request.getValue());
                            values.add(request.getValue());                            
                        }                   
                        break;
                    case MB_HOLDING_REGISTER: {
                            device.setHoldingRegister(request.getRegister(), (short) request.getValue());
                            values.add(request.getValue());                            
                        }     
                        break;
                    case UNRECOGNIZED:
                        break;
                }
                      
            }
        } catch (InvalidSyntaxException ex) {
            LOG.error(ex.getMessage());
        }
        
        
        myReply = BufferReply.newBuilder().addAllData(values).build();        
        
        responseObserver.onNext(myReply);
        responseObserver.onCompleted();   
    }
    
            
    
}
