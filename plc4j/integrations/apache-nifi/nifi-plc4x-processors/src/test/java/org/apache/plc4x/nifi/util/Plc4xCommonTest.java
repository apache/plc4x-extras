/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.plc4x.nifi.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.nifi.json.JsonTreeRowRecordReader;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.util.MockComponentLog;
import org.apache.nifi.util.MockFlowFile;

public class Plc4xCommonTest {
    // public static final Logger internalLogger = LoggerFactory.getLogger(Plc4xCommonTest.class);
    public static MockComponentLog logger;

    public static final Map<String, Object> originalMap = new HashMap<>();
    public static final Map<String, String> addressMap = new HashMap<>();
    public static final Map<String, Class<?>> typeMap = new HashMap<>();


    // TODO: BOOL, WORD; DWORD and LWORD are commented because random generation is not working with this types 
    // or a because a reverse type mapping between avro and PlcTypes is not implemented
    public static final RecordSchema schema;


    static {
        List<RecordField> recordFields = new ArrayList<>();

        // recordFields.add(new RecordField(null, null, recordFields))
        recordFields.add(new RecordField("BOOL", RecordFieldType.BOOLEAN.getDataType(), true));
        // recordFields.add(new RecordField("BYTE", RecordFieldType.SHORT.getDataType(), new byte[] {1,2})    Bytes
        // recordFields.add(new RecordField("WORD", "4")    String
        recordFields.add(new RecordField("SINT", RecordFieldType.SHORT.getDataType(), -5));
        recordFields.add(new RecordField("USINT", RecordFieldType.SHORT.getDataType(), "6"));
        recordFields.add(new RecordField("INT", RecordFieldType.INT.getDataType(), 2000));
        recordFields.add(new RecordField("UINT", RecordFieldType.INT.getDataType(), "3000"));
        recordFields.add(new RecordField("DINT", RecordFieldType.INT.getDataType(), "4000"));
        recordFields.add(new RecordField("UDINT", RecordFieldType.LONG.getDataType(), "5000"));
        // recordFields.add(new RecordField("DWORD", RecordFieldType.BOOLEAN.getDataType(), "0"));
        // recordFields.add(new RecordField("LI.NT", RecordFieldType.BOOLEAN.getDataType(), 6000L));
        recordFields.add(new RecordField("ULINT", RecordFieldType.BIGINT.getDataType(), "7000"));
        // recordFields.add(new RecordField("LWORD", RecordFieldType.BOOLEAN.getDataType(), "0"));
        recordFields.add(new RecordField("REAL", RecordFieldType.FLOAT.getDataType(), 1.23456F));
        recordFields.add(new RecordField("LREAL", RecordFieldType.DOUBLE.getDataType(), 2.34567));
        recordFields.add(new RecordField("CHAR", RecordFieldType.STRING.getDataType(), "c"));
        recordFields.add(new RecordField("WCHAR", RecordFieldType.STRING.getDataType(), "d"));
        recordFields.add(new RecordField("STRING", RecordFieldType.STRING.getDataType(), "this is a string"));
        
        schema = new SimpleRecordSchema(recordFields);


        // originalMap values are in the type needed to check type mapping between PlcType and Avro
        originalMap.put("BOOL", true);
        originalMap.put("BYTE", "\u0001");
        originalMap.put("WORD", "4");
        originalMap.put("SINT", -5);
        originalMap.put("USINT", "6");
        originalMap.put("INT", 2000);
        originalMap.put("UINT", "3000");
        originalMap.put("DINT", "4000");
        originalMap.put("UDINT", "5000");
        originalMap.put("DWORD", Long.valueOf("0"));
        originalMap.put("LI.NT", 6000L);
        originalMap.put("ULINT", "7000");
        originalMap.put("LWORD", Long.valueOf("0"));
        originalMap.put("REAL", 1.23456F);
        originalMap.put("LREAL", 2.34567);
        originalMap.put("CHAR", "c");
        originalMap.put("WCHAR", "d");
        originalMap.put("STRING", "this is a string");

        addressMap.put("BOOL", "RANDOM/v1:BOOL");
        addressMap.put("BYTE", "RANDOM/v2:BYTE");
        addressMap.put("WORD", "RANDOM/v3:WORD");
        addressMap.put("SINT", "RANDOM/v4:SINT");
        addressMap.put("USINT", "RANDOM/v5:USINT");
        addressMap.put("INT", "RANDOM/v6:INT");
        addressMap.put("UINT", "RANDOM/v7:UINT");
        addressMap.put("DINT", "RANDOM/v8:DINT");
        addressMap.put("UDINT", "RANDOM/v9:UDINT");
        addressMap.put("DWORD", "RANDOM/v10:DWORD");
        addressMap.put("LI.NT", "RANDOM/v11:LINT");
        addressMap.put("ULINT", "RANDOM/v12:ULINT");
        addressMap.put("LWORD", "RANDOM/v13:LWORD");
        addressMap.put("REAL", "RANDOM/v14:REAL");
        addressMap.put("LREAL", "RANDOM/v15:LREAL");
        addressMap.put("CHAR", "RANDOM/v16:CHAR");
        addressMap.put("WCHAR", "RANDOM/v17:WCHAR");
        addressMap.put("STRING", "RANDOM/v18:STRING");

        typeMap.put("BOOL", Boolean.class);
        typeMap.put("BYTE", Short.class);
        typeMap.put("WORD", String.class);
        typeMap.put("SINT", Short.class);
        typeMap.put("USINT", Short.class);
        typeMap.put("INT", Integer.class);
        typeMap.put("UINT", Integer.class);
        typeMap.put("DINT", Integer.class);
        typeMap.put("UDINT", Long.class);
        typeMap.put("DWORD", String.class);
        typeMap.put("LI.NT", Long.class);
        typeMap.put("ULINT", BigInteger.class);
        typeMap.put("LWORD", String.class);
        typeMap.put("REAL", Float.class);
        typeMap.put("LREAL", Double.class);
        typeMap.put("CHAR", String.class);
        typeMap.put("WCHAR", String.class);
        typeMap.put("STRING", String.class);

    }

    public static Map<String, String> getAddressMap(){
        Map<String, String> result = new HashMap<>();

        addressMap.forEach((k,v) -> {
			if (v.startsWith("RANDOM/")) {
				if (!v.endsWith("BYTE") &&
					!v.endsWith("CHAR") &&
                    !v.endsWith("WORD") &&
					!v.endsWith("STRING"))
					result.put(k, v);
			} else {
                result.put(k, v);
            }

		});
        return result;
    }

    public static void assertContent(List<MockFlowFile> flowfiles, boolean checkValue, boolean checkType) {
        flowfiles.forEach(t -> {

            try (InputStream stream = new ByteArrayInputStream(t.getContent().getBytes(StandardCharsets.UTF_8))) {
                try (JsonTreeRowRecordReader reader = new JsonTreeRowRecordReader(stream, logger, schema, null, null, null)) {
                    Record record = reader.nextRecord();
    
                    while (record!=null) {
                        for (String tag : Plc4xCommonTest.addressMap.keySet()) {
                            
                            Object value = record.getValue(tag);
    
                            if (value != null) {
                                // Check value after string conversion
                                if (checkValue) {
                                    logger.info("{} Checking type: {} =? {}", tag, value, Plc4xCommonTest.originalMap.get(tag));
                                    assert value.toString().equalsIgnoreCase(Plc4xCommonTest.originalMap.get(tag).toString());
                                }
    
                                // Check type
                                if (checkType) {
                                    logger.info("{} Checking type: {} ({}) =? {}", tag, value.getClass(), value, Plc4xCommonTest.typeMap.get(tag));
                                    assert value.getClass().equals(Plc4xCommonTest.typeMap.get(tag));
                                }
                            }
                        }
                        record = reader.nextRecord();
                    }
                } catch (IOException | MalformedRecordException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();;
            }
            
        });
    }

    public static Record getTestRecord() {
        Record record = new MapRecord(schema, originalMap);
        return record;
    }


    public static void setLogger(MockComponentLog logg) {
        logger = logg;
    }
}
