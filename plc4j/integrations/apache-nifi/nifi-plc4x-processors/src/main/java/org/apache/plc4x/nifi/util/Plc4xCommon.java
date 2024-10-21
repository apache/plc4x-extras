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
package org.apache.plc4x.nifi.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.spi.values.PlcBOOL;
import org.apache.plc4x.java.spi.values.PlcBYTE;
import org.apache.plc4x.java.spi.values.PlcCHAR;
import org.apache.plc4x.java.spi.values.PlcDATE;
import org.apache.plc4x.java.spi.values.PlcDATE_AND_TIME;
import org.apache.plc4x.java.spi.values.PlcDINT;
import org.apache.plc4x.java.spi.values.PlcDWORD;
import org.apache.plc4x.java.spi.values.PlcINT;
import org.apache.plc4x.java.spi.values.PlcLINT;
import org.apache.plc4x.java.spi.values.PlcLREAL;
import org.apache.plc4x.java.spi.values.PlcLWORD;
import org.apache.plc4x.java.spi.values.PlcList;
import org.apache.plc4x.java.spi.values.PlcREAL;
import org.apache.plc4x.java.spi.values.PlcSINT;
import org.apache.plc4x.java.spi.values.PlcTIME;
import org.apache.plc4x.java.spi.values.PlcTIME_OF_DAY;
import org.apache.plc4x.java.spi.values.PlcUDINT;
import org.apache.plc4x.java.spi.values.PlcUINT;
import org.apache.plc4x.java.spi.values.PlcULINT;
import org.apache.plc4x.java.spi.values.PlcUSINT;
import org.apache.plc4x.java.spi.values.PlcWCHAR;
import org.apache.plc4x.java.spi.values.PlcWORD;

public class Plc4xCommon {

	private Plc4xCommon (){}

	/**
	 * This method is used to create a NiFi record schema from the PlcReadResponse object. 
	 * It is directly used from the RecordPlc4xWriter.writePlcReadResponse() method.
	 * However, to make sure output schema does not change, it is built from the processor configuration (variable memory addresses).
	 * At the moment this method does not handle the following Object Types: PlcValueAdapter, PlcIECValue<T>, PlcSimpleValue<T>
	 * 
	 * @param responseDataStructure: a map that reflects the structure of the answer given by the PLC when making a Read Request.
	 * @return RecordSchema built from responseDataStructure.
	 */
	public static RecordSchema createSchema(Map<String, ? extends PlcValue> responseDataStructure, String timestampFieldName){
		List<RecordField> recordFields = new ArrayList<>();

		for (Map.Entry<String, ? extends PlcValue> entry : responseDataStructure.entrySet()) {
			RecordField f = new RecordField(entry.getKey(), getDataType(entry.getValue()));
			recordFields.add(f);
		}

		recordFields.add(new RecordField(timestampFieldName, RecordFieldType.BIGINT.getDataType()));

		return new SimpleRecordSchema(recordFields);
	}

	private static DataType getDataType(final Object valueOriginal) {

		PlcValue value = (PlcValue) valueOriginal;
		// 8 bits
		if (value instanceof PlcBOOL && value.isBoolean())
			return RecordFieldType.BOOLEAN.getDataType();
		if (value instanceof PlcBYTE && (value.isByte() || value.isShort()))
			return RecordFieldType.SHORT.getDataType();
		if (value instanceof PlcCHAR && value.isShort())
			return RecordFieldType.STRING.getDataType();
		if ((value instanceof PlcSINT || value instanceof PlcUSINT) && (value.isShort() || value.isInteger()))
			return RecordFieldType.SHORT.getDataType();


		// 16 bits
		if (value instanceof PlcWORD && (value.isInteger() || value.isShort()))
			return RecordFieldType.STRING.getDataType();
		if (value instanceof PlcINT && value.isInteger())
			return RecordFieldType.INT.getDataType();
		if (value instanceof PlcUINT && value.isInteger())
			return RecordFieldType.INT.getDataType();
		if (value instanceof PlcWCHAR || value instanceof PlcDWORD)
			return RecordFieldType.STRING.getDataType();

		// 32 bits
		if (value instanceof PlcREAL && value.isFloat())
			return RecordFieldType.FLOAT.getDataType();
		if ((value instanceof PlcDINT) && value.isInteger())
			return RecordFieldType.INT.getDataType();
		if (value instanceof PlcDWORD && value.isInteger())
			return RecordFieldType.STRING.getDataType();
		
		// 64 bits
		if ((value instanceof PlcLINT || value instanceof PlcUDINT) && value.isLong())
			return RecordFieldType.LONG.getDataType();
		if (value instanceof PlcULINT) 
			return RecordFieldType.BIGINT.getDataType();
		if (value instanceof PlcLREAL && value.isDouble())
			return RecordFieldType.DOUBLE.getDataType();
		if (value instanceof PlcLWORD && (value.isLong() || value.isBigInteger()))
			return RecordFieldType.STRING.getDataType();

		// Dates and time
		if (value instanceof PlcDATE && value.isDate())
			return RecordFieldType.DATE.getDataType();
		if (value instanceof PlcDATE_AND_TIME && value.isDateTime())
			return RecordFieldType.TIME.getDataType();
		if (value instanceof PlcTIME && value.isTime())
			return RecordFieldType.TIME.getDataType();
		if (value instanceof PlcTIME_OF_DAY && value.isTime())
			return RecordFieldType.TIME.getDataType();

		// Everything else to string
		return RecordFieldType.STRING.getDataType();
	}
	
	private static Object normalizeBasicTypes(final Object valueOriginal) {
		if (valueOriginal == null) 
			return null;
			
		if (valueOriginal instanceof PlcValue) {
			PlcValue value = (PlcValue) valueOriginal;
			// 8 bits
			if (value instanceof PlcBOOL && value.isBoolean())
				return value.getBoolean();
			if (value instanceof PlcBYTE && (value.isByte() || value.isShort()))
				return new byte[]{value.getByte()};
			if (value instanceof PlcCHAR && value.isShort())
				return value.getString();
			if ((value instanceof PlcSINT || value instanceof PlcUSINT) && (value.isShort() || value.isInteger()))
				return value.getShort();


			// 16 bits
			if (value instanceof PlcWORD && (value.isInteger() || value.isShort()))
				return value.getString();
			if (value instanceof PlcINT && value.isInteger())
				return value.getInteger();
			if (value instanceof PlcUINT && value.isInteger())
				return value.getInteger();
			if ((value instanceof PlcWCHAR || value instanceof PlcDWORD) && value.isInteger())
				return value.getString();

			// 32 bits
			if (value instanceof PlcREAL && value.isFloat())
				return value.getFloat();
			if ((value instanceof PlcDINT || value instanceof PlcUDINT) && value.isInteger())
				return value.getInteger();
			if (value instanceof PlcDWORD && value.isInteger())
				return value.getString();
			
			// 64 bits
			if ((value instanceof PlcLINT || value instanceof PlcULINT) && value.isLong())
				return value.getLong();
			if (value instanceof PlcLREAL && value.isDouble())
				return value.getDouble();
			if (value instanceof PlcLWORD && (value.isLong() || value.isBigInteger()))
				return value.getString();

			// Dates and time
			if (value instanceof PlcDATE && value.isDate())
				return value.getDate();
			if (value instanceof PlcDATE_AND_TIME && value.isDateTime())
				return value.getDateTime();
			if (value instanceof PlcTIME && value.isTime())
				return value.getTime();
			if (value instanceof PlcTIME_OF_DAY && value.isTime())
				return value.getTime();

			// Everything else to string
			return value.getString();
		} 
		return valueOriginal;
	}
	
	public static Object normalizeValue(final Object valueOriginal) {
        if (valueOriginal == null) {
            return null;
        }
        if (valueOriginal instanceof List) {
            return ((List<?>) valueOriginal).toArray();
        } else  if (valueOriginal instanceof PlcValue) {
			PlcValue value = (PlcValue) valueOriginal;

			if (value.isList() && value instanceof PlcList) {
	        	Object[] r = new Object[value.getList().size()];
	        	int i = 0;
	        	for (Object element : value.getList()) {
	        		r[i] =  normalizeBasicTypes(element);
	        		i++;
				}
	        	return r;
	        } 	
			return normalizeBasicTypes(value);
        } else {
        	return valueOriginal;
        }
    }
	
}

