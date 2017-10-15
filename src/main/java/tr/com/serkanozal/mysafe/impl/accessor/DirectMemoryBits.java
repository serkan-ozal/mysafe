/*
 * Original work Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 * Modified work Copyright (c) 2017, Serkan OZAL, All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tr.com.serkanozal.mysafe.impl.accessor;

import sun.misc.Unsafe;

/**
 * Utility class to read/write bits to given location (by base object/offset or native memory address)
 * by specified byte order (little/big endian).
 */
final class DirectMemoryBits {

    private DirectMemoryBits() {
    }

    //////////////////////////////////////////////////////////////////

    static char readChar(Unsafe unsafe, long address, boolean bigEndian) {
        return readChar(unsafe, null, address, bigEndian);
    }

    static char readChar(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readCharB(unsafe, base, offset);
        } else {
            return readCharL(unsafe, base, offset);
        }
    }

    static char readCharB(Unsafe unsafe, Object base, long offset) {
        int byte1 = unsafe.getByte(base, offset) & 0xFF;
        int byte0 = unsafe.getByte(base, offset + 1) & 0xFF;
        return (char) ((byte1 << 8) + byte0);
    }

    static char readCharL(Unsafe unsafe, Object base, long offset) {
        int byte1 = unsafe.getByte(base, offset) & 0xFF;
        int byte0 = unsafe.getByte(base, offset + 1) & 0xFF;
        return (char) ((byte0 << 8) + byte1);
    }

    static void writeChar(Unsafe unsafe, long address, char v, boolean bigEndian) {
        writeChar(unsafe, null, address, v, bigEndian);
    }

    static void writeChar(Unsafe unsafe, Object base, long offset, char v, boolean bigEndian) {
        if (bigEndian) {
            writeCharB(unsafe, base, offset, v);
        } else {
            writeCharL(unsafe, base, offset, v);
        }
    }

    static void writeCharB(Unsafe unsafe, Object base, long offset, char v) {
        unsafe.putByte(base, offset, (byte) ((v >>> 8) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v) & 0xFF));
    }

    static void writeCharL(Unsafe unsafe, Object base, long offset, char v) {
        unsafe.putByte(base, offset, (byte) ((v) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v >>> 8) & 0xFF));
    }

    //////////////////////////////////////////////////////////////////

    static short readShort(Unsafe unsafe, long address, boolean bigEndian) {
        return readShort(unsafe, null, address, bigEndian);
    }

    static short readShort(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readShortB(unsafe, base, offset);
        } else {
            return readShortL(unsafe, base, offset);
        }
    }

    static short readShortB(Unsafe unsafe, Object base, long offset) {
        int byte1 = unsafe.getByte(base, offset) & 0xFF;
        int byte0 = unsafe.getByte(base, offset + 1) & 0xFF;
        return (short) ((byte1 << 8) + byte0);
    }

    static short readShortL(Unsafe unsafe, Object base, long offset) {
        int byte1 = unsafe.getByte(base, offset) & 0xFF;
        int byte0 = unsafe.getByte(base, offset + 1) & 0xFF;
        return (short) ((byte0 << 8) + byte1);
    }

    static void writeShort(Unsafe unsafe, long address, short v, boolean bigEndian) {
        writeShort(unsafe, null, address, v, bigEndian);
    }

    static void writeShort(Unsafe unsafe, Object base, long offset, short v, boolean bigEndian) {
        if (bigEndian) {
            writeShortB(unsafe, base, offset, v);
        } else {
            writeShortL(unsafe, base, offset, v);
        }
    }

    static void writeShortB(Unsafe unsafe, Object base, long offset, short v) {
        unsafe.putByte(base, offset, (byte) ((v >>> 8) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v) & 0xFF));
    }

    static void writeShortL(Unsafe unsafe, Object base, long offset, short v) {
        unsafe.putByte(base, offset, (byte) ((v) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v >>> 8) & 0xFF));
    }

    //////////////////////////////////////////////////////////////////

    static int readInt(Unsafe unsafe, long address, boolean bigEndian) {
        return readInt(unsafe, null, address, bigEndian);
    }

    static int readInt(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readIntB(unsafe, base, offset);
        } else {
            return readIntL(unsafe, base, offset);
        }
    }

    static int readIntB(Unsafe unsafe, Object base, long offset) {
        int byte3 = (unsafe.getByte(base, offset) & 0xFF) << 24;
        int byte2 = (unsafe.getByte(base, offset + 1) & 0xFF) << 16;
        int byte1 = (unsafe.getByte(base, offset + 2) & 0xFF) << 8;
        int byte0 = unsafe.getByte(base, offset + 3) & 0xFF;
        return byte3 + byte2 + byte1 + byte0;
    }

    static int readIntL(Unsafe unsafe, Object base, long offset) {
        int byte3 = unsafe.getByte(base, offset) & 0xFF;
        int byte2 = (unsafe.getByte(base, offset + 1) & 0xFF) << 8;
        int byte1 = (unsafe.getByte(base, offset + 2) & 0xFF) << 16;
        int byte0 = (unsafe.getByte(base, offset + 3) & 0xFF) << 24;
        return byte3 + byte2 + byte1 + byte0;
    }

    static void writeInt(Unsafe unsafe, long address, int v, boolean bigEndian) {
        writeInt(unsafe, null, address, v, bigEndian);
    }

    static void writeInt(Unsafe unsafe, Object base, long offset, int v, boolean bigEndian) {
        if (bigEndian) {
            writeIntB(unsafe, base, offset, v);
        } else {
            writeIntL(unsafe, base, offset, v);
        }
    }

    static void writeIntB(Unsafe unsafe, Object base, long offset, int v) {
        unsafe.putByte(base, offset, (byte) ((v >>> 24) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v >>> 16) & 0xFF));
        unsafe.putByte(base, offset + 2, (byte) ((v >>> 8) & 0xFF));
        unsafe.putByte(base, offset + 3, (byte) ((v) & 0xFF));
    }

    static void writeIntL(Unsafe unsafe, Object base, long offset, int v) {
        unsafe.putByte(base, offset, (byte) ((v) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v >>> 8) & 0xFF));
        unsafe.putByte(base, offset + 2, (byte) ((v >>> 16) & 0xFF));
        unsafe.putByte(base, offset + 3, (byte) ((v >>> 24) & 0xFF));
    }

    //////////////////////////////////////////////////////////////////

    static float readFloat(Unsafe unsafe, long address, boolean bigEndian) {
        return readFloat(unsafe, null, address, bigEndian);
    }

    static float readFloat(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readFloatB(unsafe, base, offset);
        } else {
            return readFloatL(unsafe, base, offset);
        }
    }

    static float readFloatB(Unsafe unsafe, Object base, long offset) {
        return Float.intBitsToFloat(readIntB(unsafe, base, offset));
    }

    static float readFloatL(Unsafe unsafe, Object base, long offset) {
        return Float.intBitsToFloat(readIntL(unsafe, base, offset));
    }

    static void writeFloat(Unsafe unsafe,long address, float v, boolean bigEndian) {
        writeFloat(unsafe, null, address, v, bigEndian);
    }

    static void writeFloat(Unsafe unsafe, Object base, long offset, float v, boolean bigEndian) {
        if (bigEndian) {
            writeFloatB(unsafe, base, offset, v);
        } else {
            writeFloatL(unsafe, base, offset, v);
        }
    }

    static void writeFloatB(Unsafe unsafe, Object base, long offset, float v) {
        writeIntB(unsafe, base, offset, Float.floatToRawIntBits(v));
    }

    static void writeFloatL(Unsafe unsafe, Object base, long offset, float v) {
        writeIntL(unsafe, base, offset, Float.floatToRawIntBits(v));
    }

    //////////////////////////////////////////////////////////////////

    static long readLong(Unsafe unsafe, long address, boolean bigEndian) {
        return readLong(unsafe, null, address, bigEndian);
    }

    static long readLong(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readLongB(unsafe, base, offset);
        } else {
            return readLongL(unsafe, base, offset);
        }
    }

    static long readLongB(Unsafe unsafe, Object base, long offset) {
        long byte7 = (long) unsafe.getByte(base, offset) << 56;
        long byte6 = (long) (unsafe.getByte(base, offset + 1) & 0xFF) << 48;
        long byte5 = (long) (unsafe.getByte(base, offset + 2) & 0xFF) << 40;
        long byte4 = (long) (unsafe.getByte(base, offset + 3) & 0xFF) << 32;
        long byte3 = (long) (unsafe.getByte(base, offset + 4) & 0xFF) << 24;
        long byte2 = (long) (unsafe.getByte(base, offset + 5) & 0xFF) << 16;
        long byte1 = (long) (unsafe.getByte(base, offset + 6) & 0xFF) << 8;
        long byte0 = (long) (unsafe.getByte(base, offset + 7) & 0xFF);
        return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
    }

    static long readLongL(Unsafe unsafe, Object base, long offset) {
        long byte7 = (long) (unsafe.getByte(base, offset) & 0xFF);
        long byte6 = (long) (unsafe.getByte(base, offset + 1) & 0xFF) << 8;
        long byte5 = (long) (unsafe.getByte(base, offset + 2) & 0xFF) << 16;
        long byte4 = (long) (unsafe.getByte(base, offset + 3) & 0xFF) << 24;
        long byte3 = (long) (unsafe.getByte(base, offset + 4) & 0xFF) << 32;
        long byte2 = (long) (unsafe.getByte(base, offset + 5) & 0xFF) << 40;
        long byte1 = (long) (unsafe.getByte(base, offset + 6) & 0xFF) << 48;
        long byte0 = (long) (unsafe.getByte(base, offset + 7) & 0xFF) << 56;
        return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
    }

    static void writeLong(Unsafe unsafe, long address, long v, boolean bigEndian) {
        writeLong(unsafe, null, address, v, bigEndian);
    }

    static void writeLong(Unsafe unsafe, Object base, long offset, long v, boolean bigEndian) {
        if (bigEndian) {
            writeLongB(unsafe, base, offset, v);
        } else {
            writeLongL(unsafe, base, offset, v);
        }
    }

    static void writeLongB(Unsafe unsafe, Object base, long offset, long v) {
        unsafe.putByte(base, offset, (byte) (v >>> 56));
        unsafe.putByte(base, offset + 1, (byte) (v >>> 48));
        unsafe.putByte(base, offset + 2, (byte) (v >>> 40));
        unsafe.putByte(base, offset + 3, (byte) (v >>> 32));
        unsafe.putByte(base, offset + 4, (byte) (v >>> 24));
        unsafe.putByte(base, offset + 5, (byte) (v >>> 16));
        unsafe.putByte(base, offset + 6, (byte) (v >>> 8));
        unsafe.putByte(base, offset + 7, (byte) (v));
    }

    static void writeLongL(Unsafe unsafe, Object base, long offset, long v) {
        unsafe.putByte(base, offset, (byte) (v));
        unsafe.putByte(base, offset + 1, (byte) (v >>> 8));
        unsafe.putByte(base, offset + 2, (byte) (v >>> 16));
        unsafe.putByte(base, offset + 3, (byte) (v >>> 24));
        unsafe.putByte(base, offset + 4, (byte) (v >>> 32));
        unsafe.putByte(base, offset + 5, (byte) (v >>> 40));
        unsafe.putByte(base, offset + 6, (byte) (v >>> 48));
        unsafe.putByte(base, offset + 7, (byte) (v >>> 56));
    }

    //////////////////////////////////////////////////////////////////

    static double readDouble(Unsafe unsafe, long address, boolean bigEndian) {
        return readDouble(unsafe, null, address, bigEndian);
    }

    static double readDouble(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readDoubleB(unsafe, base, offset);
        } else {
            return readDoubleL(unsafe, base, offset);
        }
    }

    static double readDoubleB(Unsafe unsafe, Object base, long offset) {
        return Double.longBitsToDouble(readLongB(unsafe, base, offset));
    }

    static double readDoubleL(Unsafe unsafe, Object base, long offset) {
        return Double.longBitsToDouble(readLongL(unsafe, base, offset));
    }

    static void writeDouble(Unsafe unsafe, long address, double v, boolean bigEndian) {
        writeDouble(unsafe, null, address, v, bigEndian);
    }

    static void writeDouble(Unsafe unsafe, Object base, long offset, double v, boolean bigEndian) {
        if (bigEndian) {
            writeDoubleB(unsafe, base, offset, v);
        } else {
            writeDoubleL(unsafe, base, offset, v);
        }
    }

    static void writeDoubleB(Unsafe unsafe, Object base, long offset, double v) {
        writeLongB(unsafe, base, offset, Double.doubleToRawLongBits(v));
    }

    static void writeDoubleL(Unsafe unsafe, Object base, long offset, double v) {
        writeLongL(unsafe, base, offset, Double.doubleToRawLongBits(v));
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static char readCharVolatile(Unsafe unsafe, long address, boolean bigEndian) {
        return readCharVolatile(unsafe, null, address, bigEndian);
    }

    static char readCharVolatile(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readCharVolatileB(unsafe, base, offset);
        } else {
            return readCharVolatileL(unsafe, base, offset);
        }
    }

    static char readCharVolatileB(Unsafe unsafe, Object base, long offset) {
        int byte1 = unsafe.getByte(base, offset) & 0xFF;
        int byte0 = unsafe.getByte(base, offset + 1) & 0xFF;
        return (char) ((byte1 << 8) + byte0);
    }

    static char readCharVolatileL(Unsafe unsafe, Object base, long offset) {
        int byte1 = unsafe.getByte(base, offset) & 0xFF;
        int byte0 = unsafe.getByte(base, offset + 1) & 0xFF;
        return (char) ((byte0 << 8) + byte1);
    }

    static void writeCharVolatile(Unsafe unsafe, long address, char v, boolean bigEndian) {
        writeCharVolatile(unsafe, null, address, v, bigEndian);
    }

    static void writeCharVolatile(Unsafe unsafe, Object base, long offset, char v, boolean bigEndian) {
        if (bigEndian) {
            writeCharVolatileB(unsafe, base, offset, v);
        } else {
            writeCharVolatileL(unsafe, base, offset, v);
        }
    }

    static void writeCharVolatileB(Unsafe unsafe, Object base, long offset, char v) {
        unsafe.putByte(base, offset, (byte) ((v >>> 8) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v) & 0xFF));
    }

    static void writeCharVolatileL(Unsafe unsafe, Object base, long offset, char v) {
        unsafe.putByte(base, offset, (byte) ((v) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v >>> 8) & 0xFF));
    }

    //////////////////////////////////////////////////////////////////

    static short readShortVolatile(Unsafe unsafe, long address, boolean bigEndian) {
        return readShortVolatile(unsafe, null, address, bigEndian);
    }

    static short readShortVolatile(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readShortVolatileB(unsafe, base, offset);
        } else {
            return readShortVolatileL(unsafe, base, offset);
        }
    }

    static short readShortVolatileB(Unsafe unsafe, Object base, long offset) {
        int byte1 = unsafe.getByte(base, offset) & 0xFF;
        int byte0 = unsafe.getByte(base, offset + 1) & 0xFF;
        return (short) ((byte1 << 8) + byte0);
    }

    static short readShortVolatileL(Unsafe unsafe, Object base, long offset) {
        int byte1 = unsafe.getByte(base, offset) & 0xFF;
        int byte0 = unsafe.getByte(base, offset + 1) & 0xFF;
        return (short) ((byte0 << 8) + byte1);
    }

    static void writeShortVolatile(Unsafe unsafe, long address, short v, boolean bigEndian) {
        writeShortVolatile(unsafe, null, address, v, bigEndian);
    }

    static void writeShortVolatile(Unsafe unsafe, Object base, long offset, short v, boolean bigEndian) {
        if (bigEndian) {
            writeShortVolatileB(unsafe, base, offset, v);
        } else {
            writeShortVolatileL(unsafe, base, offset, v);
        }
    }

    static void writeShortVolatileB(Unsafe unsafe, Object base, long offset, short v) {
        unsafe.putByte(base, offset, (byte) ((v >>> 8) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v) & 0xFF));
    }

    static void writeShortVolatileL(Unsafe unsafe, Object base, long offset, short v) {
        unsafe.putByte(base, offset, (byte) ((v) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v >>> 8) & 0xFF));
    }

    //////////////////////////////////////////////////////////////////

    static int readIntVolatile(Unsafe unsafe, long address, boolean bigEndian) {
        return readIntVolatile(unsafe, null, address, bigEndian);
    }

    static int readIntVolatile(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readIntVolatileB(unsafe, base, offset);
        } else {
            return readIntVolatileL(unsafe, base, offset);
        }
    }

    static int readIntVolatileB(Unsafe unsafe, Object base, long offset) {
        int byte3 = (unsafe.getByte(base, offset) & 0xFF) << 24;
        int byte2 = (unsafe.getByte(base, offset + 1) & 0xFF) << 16;
        int byte1 = (unsafe.getByte(base, offset + 2) & 0xFF) << 8;
        int byte0 = unsafe.getByte(base, offset + 3) & 0xFF;
        return byte3 + byte2 + byte1 + byte0;
    }

    static int readIntVolatileL(Unsafe unsafe, Object base, long offset) {
        int byte3 = unsafe.getByte(base, offset) & 0xFF;
        int byte2 = (unsafe.getByte(base, offset + 1) & 0xFF) << 8;
        int byte1 = (unsafe.getByte(base, offset + 2) & 0xFF) << 16;
        int byte0 = (unsafe.getByte(base, offset + 3) & 0xFF) << 24;
        return byte3 + byte2 + byte1 + byte0;
    }

    static void writeIntVolatile(Unsafe unsafe, long address, int v, boolean bigEndian) {
        writeIntVolatile(unsafe, null, address, v, bigEndian);
    }

    static void writeIntVolatile(Unsafe unsafe, Object base, long offset, int v, boolean bigEndian) {
        if (bigEndian) {
            writeIntVolatileB(unsafe, base, offset, v);
        } else {
            writeIntVolatileL(unsafe, base, offset, v);
        }
    }

    static void writeIntVolatileB(Unsafe unsafe, Object base, long offset, int v) {
        unsafe.putByte(base, offset, (byte) ((v >>> 24) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v >>> 16) & 0xFF));
        unsafe.putByte(base, offset + 2, (byte) ((v >>> 8) & 0xFF));
        unsafe.putByte(base, offset + 3, (byte) ((v) & 0xFF));
    }

    static void writeIntVolatileL(Unsafe unsafe, Object base, long offset, int v) {
        unsafe.putByte(base, offset, (byte) ((v) & 0xFF));
        unsafe.putByte(base, offset + 1, (byte) ((v >>> 8) & 0xFF));
        unsafe.putByte(base, offset + 2, (byte) ((v >>> 16) & 0xFF));
        unsafe.putByte(base, offset + 3, (byte) ((v >>> 24) & 0xFF));
    }

    //////////////////////////////////////////////////////////////////

    static float readFloatVolatile(Unsafe unsafe, long address, boolean bigEndian) {
        return readFloatVolatile(unsafe, null, address, bigEndian);
    }

    static float readFloatVolatile(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readFloatVolatileB(unsafe, base, offset);
        } else {
            return readFloatVolatileL(unsafe, base, offset);
        }
    }

    static float readFloatVolatileB(Unsafe unsafe, Object base, long offset) {
        return Float.intBitsToFloat(readIntVolatileB(unsafe, base, offset));
    }

    static float readFloatVolatileL(Unsafe unsafe, Object base, long offset) {
        return Float.intBitsToFloat(readIntVolatileL(unsafe, base, offset));
    }

    static void writeFloatVolatile(Unsafe unsafe, long address, float v, boolean bigEndian) {
        writeFloatVolatile(unsafe, null, address, v, bigEndian);
    }

    static void writeFloatVolatile(Unsafe unsafe, Object base, long offset, float v, boolean bigEndian) {
        if (bigEndian) {
            writeFloatVolatileB(unsafe, base, offset, v);
        } else {
            writeFloatVolatileL(unsafe, base, offset, v);
        }
    }

    static void writeFloatVolatileB(Unsafe unsafe, Object base, long offset, float v) {
        writeIntVolatileB(unsafe, base, offset, Float.floatToRawIntBits(v));
    }

    static void writeFloatVolatileL(Unsafe unsafe, Object base, long offset, float v) {
        writeIntVolatileL(unsafe, base, offset, Float.floatToRawIntBits(v));
    }

    //////////////////////////////////////////////////////////////////

    static long readLongVolatile(Unsafe unsafe, long address, boolean bigEndian) {
        return readLongVolatile(unsafe, null, address, bigEndian);
    }

    static long readLongVolatile(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readLongVolatileB(unsafe, base, offset);
        } else {
            return readLongVolatileL(unsafe, base, offset);
        }
    }

    static long readLongVolatileB(Unsafe unsafe, Object base, long offset) {
        long byte7 = (long) unsafe.getByte(base, offset) << 56;
        long byte6 = (long) (unsafe.getByte(base, offset + 1) & 0xFF) << 48;
        long byte5 = (long) (unsafe.getByte(base, offset + 2) & 0xFF) << 40;
        long byte4 = (long) (unsafe.getByte(base, offset + 3) & 0xFF) << 32;
        long byte3 = (long) (unsafe.getByte(base, offset + 4) & 0xFF) << 24;
        long byte2 = (long) (unsafe.getByte(base, offset + 5) & 0xFF) << 16;
        long byte1 = (long) (unsafe.getByte(base, offset + 6) & 0xFF) << 8;
        long byte0 = (long) (unsafe.getByte(base, offset + 7) & 0xFF);
        return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
    }

    static long readLongVolatileL(Unsafe unsafe, Object base, long offset) {
        long byte7 = (long) (unsafe.getByte(base, offset) & 0xFF);
        long byte6 = (long) (unsafe.getByte(base, offset + 1) & 0xFF) << 8;
        long byte5 = (long) (unsafe.getByte(base, offset + 2) & 0xFF) << 16;
        long byte4 = (long) (unsafe.getByte(base, offset + 3) & 0xFF) << 24;
        long byte3 = (long) (unsafe.getByte(base, offset + 4) & 0xFF) << 32;
        long byte2 = (long) (unsafe.getByte(base, offset + 5) & 0xFF) << 40;
        long byte1 = (long) (unsafe.getByte(base, offset + 6) & 0xFF) << 48;
        long byte0 = (long) (unsafe.getByte(base, offset + 7) & 0xFF) << 56;
        return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
    }

    static void writeLongVolatile(Unsafe unsafe, long address, long v, boolean bigEndian) {
        writeLongVolatile(unsafe, null, address, v, bigEndian);
    }

    static void writeLongVolatile(Unsafe unsafe, Object base, long offset, long v, boolean bigEndian) {
        if (bigEndian) {
            writeLongVolatileB(unsafe, base, offset, v);
        } else {
            writeLongVolatileL(unsafe, base, offset, v);
        }
    }

    static void writeLongVolatileB(Unsafe unsafe, Object base, long offset, long v) {
        unsafe.putByte(base, offset, (byte) (v >>> 56));
        unsafe.putByte(base, offset + 1, (byte) (v >>> 48));
        unsafe.putByte(base, offset + 2, (byte) (v >>> 40));
        unsafe.putByte(base, offset + 3, (byte) (v >>> 32));
        unsafe.putByte(base, offset + 4, (byte) (v >>> 24));
        unsafe.putByte(base, offset + 5, (byte) (v >>> 16));
        unsafe.putByte(base, offset + 6, (byte) (v >>> 8));
        unsafe.putByte(base, offset + 7, (byte) (v));
    }

    static void writeLongVolatileL(Unsafe unsafe, Object base, long offset, long v) {
        unsafe.putByte(base, offset, (byte) (v));
        unsafe.putByte(base, offset + 1, (byte) (v >>> 8));
        unsafe.putByte(base, offset + 2, (byte) (v >>> 16));
        unsafe.putByte(base, offset + 3, (byte) (v >>> 24));
        unsafe.putByte(base, offset + 4, (byte) (v >>> 32));
        unsafe.putByte(base, offset + 5, (byte) (v >>> 40));
        unsafe.putByte(base, offset + 6, (byte) (v >>> 48));
        unsafe.putByte(base, offset + 7, (byte) (v >>> 56));
    }

    //////////////////////////////////////////////////////////////////

    static double readDoubleVolatile(Unsafe unsafe, long address, boolean bigEndian) {
        return readDoubleVolatile(unsafe, null, address, bigEndian);
    }

    static double readDoubleVolatile(Unsafe unsafe, Object base, long offset, boolean bigEndian) {
        if (bigEndian) {
            return readDoubleVolatileB(unsafe, base, offset);
        } else {
            return readDoubleVolatileL(unsafe, base, offset);
        }
    }

    static double readDoubleVolatileB(Unsafe unsafe, Object base, long offset) {
        return Double.longBitsToDouble(readLongVolatileB(unsafe, base, offset));
    }

    static double readDoubleVolatileL(Unsafe unsafe, Object base, long offset) {
        return Double.longBitsToDouble(readLongVolatileL(unsafe, base, offset));
    }

    static void writeDoubleVolatile(Unsafe unsafe, long address, double v, boolean bigEndian) {
        writeDoubleVolatile(unsafe, null, address, v, bigEndian);
    }

    static void writeDoubleVolatile(Unsafe unsafe, Object base, long offset, double v, boolean bigEndian) {
        if (bigEndian) {
            writeDoubleVolatileB(unsafe, base, offset, v);
        } else {
            writeDoubleVolatileL(unsafe, base, offset, v);
        }
    }

    static void writeDoubleVolatileB(Unsafe unsafe, Object base, long offset, double v) {
        writeLongVolatileB(unsafe, base, offset, Double.doubleToRawLongBits(v));
    }

    static void writeDoubleVolatileL(Unsafe unsafe, Object base, long offset, double v) {
        writeLongVolatileL(unsafe, base, offset, Double.doubleToRawLongBits(v));
    }

}
