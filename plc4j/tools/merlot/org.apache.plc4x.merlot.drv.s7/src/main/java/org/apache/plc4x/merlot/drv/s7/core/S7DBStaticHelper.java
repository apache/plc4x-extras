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
package org.apache.plc4x.merlot.drv.s7.core;

import io.netty.buffer.ByteBuf;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import static org.apache.plc4x.java.s7.readwrite.utils.StaticHelper.s5TimeToDuration;
import org.apache.plc4x.java.spi.generation.ParseException;
import org.apache.plc4x.java.spi.generation.ReadBuffer;

/**
 *
 * @author cgarcia
 */
public class  S7DBStaticHelper {
    
    
    /*****************************************************
     * S5TIME Support *
     * 
     *  +---------------------------+
     *  | 10^0 | 10^2 | 10^1 | 10^0 1
     *  +---------------------------+
     *      |  |                    |
     *      |  \__________+________/
     *      |             |
     *      |             \ Timer value
     *      \ Time base
     *****************************************************/
    
    /**
     * 
     * @param s5time
     * @return 
     */
    public static Float s5TimeToFloat(short s5time){
        short t = s5time;        
        long tv = (short) (((t & 0x000F)) + ((t & 0x00F0) >> 4) * 10 + ((t & 0x0F00) >> 8) * 100);
        long tb = (short) ((t & 0xF000) >> 12);
        Float res = (float) tv + (tb /10);
        return res;
    }
    
    
    public static short floatToS5Time(float s5time){
        double intPart = Math.round(s5time * 10) / 10;
        double decPart = Math.round((s5time - intPart) * 100 ) / 10;
        if (intPart < 0 ) intPart = 0;
        if (intPart > 999 ) intPart = 999;
        if (decPart < 0 ) decPart = 0;
        if (decPart < 3 ) decPart = 3;
        
        int intValue    = Double.valueOf(intPart).intValue();
        short multValue = Double.valueOf(decPart).shortValue(); 
        
        short res = intToBcd(intValue);
        
        return (short) ((multValue << 12) | (res));
    }    
    
    
    
    /**
     * S5TIME to Duration
     * 
     * @param data
     * @return 
     */    
    public static Duration s5TimeToDuration(Short data) {
        Duration res;
        short t = data;
        long tv = (short) (((t & 0x000F)) + ((t & 0x00F0) >> 4) * 10 + ((t & 0x0F00) >> 8) * 100);
        long tb = (short) (10 * Math.pow(10, ((t & 0xF000) >> 12)));
        long totalms = tv * tb;
        return Duration.ofMillis((totalms <= 9990000)?totalms:9990000);
    }       
    
    
    /**
     * Duration to S5TIME
     * 
     * @param duration
     * @return 
     */
    public static Short durationToS5Time(Duration duration) {
        short tv = 0;
        short tb = 0x0000_0000;
        short s5time = 0x0000;
        long totalms = duration.toMillis();

        if ((totalms >= 0) && (totalms <= 9990000)) {
            if (totalms <= 9990) {
                tb = 0x0000_0000; //10 ms
                tv = (short) (totalms / 10);
            } else if (totalms <= 99900) {
                tb = 0x0000_0001;// 100 ms
                tv = (short) (totalms / 100);
            } else if (totalms <= 999000) {
                tb = 0x0000_0002;//1000 ms
                tv = (short) (totalms / 1000);
            } else if (totalms > 999000) {
                tb = 0x0000_0003;//10000 ms
                tv = (short) (totalms / 10000);
            }

            short uni = (short) (tv % 10);
            short dec = (short) ((tv / 10) % 10);
            short cen = (short) ((tv / 100) % 10);

            return (short) (((tb) << 12) | (cen << 8) | (dec << 4) | (uni));
        }
        return s5time;
    }   
    
    /*****************************************************
     * DATE Support *
     * 
     *  15 14      ...          0
     *  +-----------------------+
     *  |           |           |
     *  +-----------------------+
     *   |\__________+_________/
     *   |        
     *   \ Sign
     * 
     * Examples:
     *          DATE#1990-01-01         (=0000 hex)
     *          DATE#2168-12-31         (=FF62 hex)
     *****************************************************/    
    
    public static LocalDate s7DateToLocalDate(Short data) {
        LocalDate res = LocalDate.of(1990, 1, 1);
        res = res.plusDays((long) data);
        return res;
    }

    public static Short localDateToS7Date(LocalDate data) {
        LocalDate ini = LocalDate.of(1990, 1, 1);
        long resl = ChronoUnit.DAYS.between(ini, data);
        return (short) resl;
    }      
    
    /*****************************************************
     * TIME Support *
     * 
     *  31 30 ... 16 15 14 ...  0
     *  +-----------------------+
     *  |S|         |           |
     *  +-----------------------+
     *   |\__________+_________/
     *   |        
     *   \ Sign
     * 
     * Examples:
     *          TIME#24d20h31m23s647ms  (=7FFF_FFFF hex)
     *          TIME#0ms                (=0000_0000 hex)
     *          TIME#-24d20h11m23s648ms (=8000_0000 hex)
     *****************************************************/ 
    
    public static Duration s7TimeToDuration(Integer data) {
        Duration res = Duration.ZERO;
        if (data >= 0) {
            res = res.plusMillis((long) data);
        } else {
            long ms = 0x8000_0000 - (data & 0x8000_0000);
            res = res.minusMillis((long) data);
        }

        return res;
    }

    public static Integer durationToS7Time(Duration data) {
        Integer res = 0x0000_0000;
        if (data.isNegative()) {
            res = (int) data.toMillis() + 0x8000_0000;
        } else {
            res = (int) data.toMillis();
        }
        return res;
    }    
        
    
    /*****************************************************
     * TIME_OF_DAY Support *
     * 
     * Examples:
     *          TIME_OF_DAY#00:00:00    (=7FFF_FFFF hex)
     *          TOD#23:59:59:999        (=0526_5BFF hex)
     *****************************************************/ 
    
    
    public static LocalTime s7TodToLocalTime(Integer data) {
        if (data > 0x0526_5bff) data = 0x0526_5bff;
        if (data < 0) data = 0x0000_0000;
        return LocalTime.MIDNIGHT.plusNanos((long) data * 1_000_000);
    }

    public static Integer localTimeToS7Tod(LocalTime data) {
        return (int) (data.toNanoOfDay() / 1_000_000);
    }
    
    /*************************************
     * DATE_AND_TIME Support *
     * 
     * Date and time of day (BCD coded).
     *          +----------------+
     * Byte n   | Year   0 to 99 |
     *          +----------------+
     * Byte n+1 | Month  1 to 12 |
     *          +----------------+
     * Byte n+2 | Day    1 to 31 |
     *          +----------------+
     * Byte n+3 | Hour   0 to 23 |
     *          +----------------+
     * Byte n+4 | Minute 0 to 59 |
     *          +----------------+
     * Byte n+5 | Second 0 to 59 |
     *          +----------------+
     * Byte n+6 | ms    0 to 999 |
     * Byte n+7 | X X X X X D O W|
     *          +----------------+
     * DOW: Day of week (last 3 bits)
     ************************************/
    
    public static LocalDateTime s7DateTimeToLocalDateTime(ByteBuf data) {
        //from Plc4XS7Protocol
        int year = bcdToInt(data.readByte());
        int month = bcdToInt(data.readByte());
        int day = bcdToInt(data.readByte());
        int hour = bcdToInt(data.readByte());
        int minute = bcdToInt(data.readByte());
        int second = bcdToInt(data.readByte());
        int millih = bcdToInt(data.readByte()) * 10;

        int milll = (data.readByte() >> 4);

        int milliseconds = millih + milll;
        int nanoseconds = milliseconds * 1000000;
        //At this point a dont need the day of week
        //data-type ranges from 1990 up to 2089
        if (year >= 90) {
            year += 1900;
        } else {
            year += 2000;
        }

        return LocalDateTime.of(year, month, day, hour, minute, second, nanoseconds);
    }

    public static LocalDateTime s7DateAndTimeToLocalDateTime(int year, int month, int day,
                                                             int hour, int min, int sec, int msec) {
        int nanoseconds = msec * 1000000;
        //At this point a dont need the day of week
        //data-type ranges from 1990 up to 2089
        if (year >= 90) {
            year += 1900;
        } else {
            year += 2000;
        }
        return LocalDateTime.of(year, month, day, hour, min, sec, nanoseconds);
    }

    public static byte[] localDateTimeToS7DateTime(LocalDateTime data) {
        byte[] res = new byte[8];

        res[0] = byteToBcd((data.getYear() % 100));
        res[1] = byteToBcd(data.getMonthValue());
        res[2] = byteToBcd(data.getDayOfMonth());
        res[3] = byteToBcd(data.getHour());
        res[4] = byteToBcd(data.getMinute());
        res[5] = byteToBcd(data.getSecond());

        long ms = (long) (data.getNano() / 1_000_000);
        res[6] = (byte) ((int) (((ms / 100) << 4) | ((ms / 10) % 10)));
        //Java:1 (Monday) to 7 (Sunday)->S7:1 (Sunday) to 7 (Saturday)
        byte dayofweek = (byte) ((data.getDayOfWeek().getValue() < 7) ?
            data.getDayOfWeek().getValue() + 1 :
            (byte) 0x01);
        res[7] = (byte) (((ms % 10) << 4) | ((byte) (dayofweek)));

        return res;
    }    
    
    /*************************
     * Support functions     *
     ************************/    
    
    private static byte byteToBcd(int incomingByte) {
        byte dec = (byte) ((incomingByte / 10) % 10);
        return (byte) ((dec << 4) | (incomingByte % 10));
    }

    private static int bcdToInt(byte bcd) {
        return (bcd >> 4) * 10 + (bcd & 0x0f);
    }
    
    
    /**
     * converts incoming byte to an integer regarding used BCD format
     *
     * @param incomingByte the incoming byte
     * @return converted BCD number
     */
    public static int convertByteToBcd(byte incomingByte) {
        int dec = (incomingByte >> 4) * 10;
        return dec + (incomingByte & 0x0f);
    }
    
    /**
     * converts incoming Short to an integer regarding used BCD format
     *
     * @param incomingShort the incoming byte
     * @return converted BCD number
     */
    public static short convertShortToBcd(short incomingShort) {
        return (short) ((incomingShort >> 8) * 100 +
            (incomingShort >> 4) * 10 +
            (incomingShort & 0x0f));
    }
      
    /**
     * Convert a 3 digits integer to bcd.
     * 
     * @param decimal
     * @return 
     */
    public static short intToBcd(int decimal) {
        if (decimal < 0 ) decimal = 0;
        if (decimal > 999 ) decimal = 999;
        short bcd = 0;
        int shift = 0;
        while (decimal > 0) {
            int digit = decimal % 10;
            bcd |= (digit << (shift * 4));
            decimal /= 10;
            shift++;
        }
        return bcd;
    }          
        
    
}
