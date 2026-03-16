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
package org.apache.plc4x.merlot.api;

import io.grpc.netty.shaded.io.netty.util.internal.ObjectUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static io.netty.buffer.Unpooled.compositeBuffer;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;


public class ByteBufJUnitTest {
    
    private final ByteOrder order = ByteOrder.BIG_ENDIAN;    
    
    public ByteBufJUnitTest() {
    }
    
    @BeforeAll
    public static void setUpClass() {
    }
    
    @AfterAll
    public static void tearDownClass() {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }

    // TODO add test methods here.
    // The methods must be annotated with annotation @Test. For example:
    //
     @Test
     public void CompositeByteBufTest() {
         //CompositeByteBuf cByteBuf = 
     }
     
    @Test
    public void testIsContiguous() {
        ByteBuf buf = newBuffer(4);
        assertFalse(buf.isContiguous());
        buf.release();
    }
    
    @Test
    public void SimpleDBTtest() {
        ByteBuf db = newBuffer(255);
        db.writeByte(255);
        System.out.println(ByteBufUtil.prettyHexDump(db));
        assertFalse(db.isContiguous());
        db.release();
    }    
    
    @Test
    public void CompuestoDBTtest() {
        ByteBuf seg01 = Unpooled.buffer(100);
        ByteBuf seg02 = Unpooled.buffer(100);
        
        CompositeByteBuf db = Unpooled.compositeBuffer(1024);
        
        seg01.writeByte(0x2e);
        seg02.writeByte(0x7b);
        
        db.addComponent(0, seg01);
        db.addComponent(1, seg02);
                 
        db.writerIndex(db.capacity());
                      
        assertEquals(0x2e, db.getByte(0));
        assertEquals(0x7b, db.getByte(1));        
        System.out.println(ByteBufUtil.prettyHexDump(db.asByteBuf()));
        
        seg02.writeByte(0x4f);
 
        db.addComponent(1, seg02);        
        db.writerIndex(db.capacity());
        
        assertEquals(0x4f, db.getByte(2));  
        System.out.println(ByteBufUtil.prettyHexDump(db.asByteBuf())); 
        
        ByteBuf seg03 = Unpooled.buffer(50);        
        seg03.writeByte(0x0a);        
        seg03.writeByte(0x0f);
        db.addComponent(1, seg03); 
        
        db.writerIndex(db.capacity());        
        
        assertEquals(0x0a, db.getByte(1));
        assertEquals(0x0f, db.getByte(2));   
        System.out.println(ByteBufUtil.prettyHexDump(db.asByteBuf()));  
        
        db.removeComponent(1);
        db.writerIndex(db.capacity());         
        
        assertEquals(0x7b, db.getByte(1));   
        assertEquals(0x7b, db.getByte(3));           
        System.out.println(ByteBufUtil.prettyHexDump(db.asByteBuf()));         
                
    }    
      
    protected CompositeByteBuf newCompositeBuffer() {
        return compositeBuffer();
    } 

    protected final ByteBuf newBuffer(int capacity) {
        return newBuffer(capacity, Integer.MAX_VALUE);
    }    
    
    protected ByteBuf newBuffer(int length, int maxCapacity) {
        Assumptions.assumeTrue(maxCapacity == Integer.MAX_VALUE);

        List<ByteBuf> buffers = new ArrayList<ByteBuf>();
        for (int i = 0; i < length + 45; i += 45) {
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[1]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[2]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[3]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[4]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[5]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[6]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[7]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[8]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[9]));
            buffers.add(EMPTY_BUFFER);
        }   
        
        ByteBuf buffer;
        // Ensure that we are really testing a CompositeByteBuf
        switch (buffers.size()) {
            case 0:
                buffer = compositeBuffer(Integer.MAX_VALUE);
                break;
            case 1:
                buffer = compositeBuffer(Integer.MAX_VALUE).addComponent(buffers.get(0));
                break;
            default:
                buffer = wrappedBuffer(Integer.MAX_VALUE, buffers.toArray(new ByteBuf[0]));
                break;
        }
        buffer = buffer.order(order);

        // Truncate to the requested capacity.
        buffer.capacity(length);

        assertEquals(length, buffer.capacity());
        assertEquals(length, buffer.readableBytes());
        assertFalse(buffer.isWritable());
        buffer.writerIndex(0);
        return buffer;
    }        
}
