package tr.com.serkanozal.mysafe;

import java.nio.ByteOrder;

import sun.misc.Unsafe;

/**
 * Aligned {@link com.UnsafeMemoryAccessor.internal.memory.MemoryAccessor} implementation
 * that checks and handles unaligned memory accesses if possible
 * by using {@link sun.misc.Unsafe} for accessing to memory.
 *
 * A few notes on this implementation:
 *      - There is no atomicity guarantee for unaligned memory accesses.
 *        In fact, even on platforms which support unaligned memory accesses, 
 *        there is no guarantee for atomicity when there is unaligned memory accesses.
 *        On the other hand, on later Intel processor unaligned access 
 *        within the cache line is atomic, but access across the line is not.
 *        See http://psy-lob-saw.blogspot.com.tr/2013/07/atomicity-of-unaligned-memory-access-in.html for more details
 *        
 *      - Unaligned memory accesses are not supported for CAS operations
 *      
 *      - Unaligned memory accesses are not supported for ordered writes
 */
public class AlignedMemoryAccessor {

    private static final boolean AVAILABLE = true;
    private static final Unsafe UNSAFE = null;
    
    
    private static final int OBJECT_REFERENCE_ALIGN = UNSAFE.arrayIndexScale(Object[].class);
    private static final int OBJECT_REFERENCE_MASK = OBJECT_REFERENCE_ALIGN - 1;

    private final UnalignedMemoryAccessor unalignedMemoryAccessor;

    public AlignedMemoryAccessor() {
        if (!AVAILABLE) {
            throw new IllegalStateException(getClass().getName() + " can only be used only when Unsafe is available!");
        }
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            unalignedMemoryAccessor = new LittleEndianUnalignedMemoryAccessor();
        } else {
            unalignedMemoryAccessor = new BigEndianUnalignedMemoryAccessor();
        }
    }

    private boolean is2BytesAligned(long value) {
        return (value & 0x01) != 0;
    }

    private boolean is4BytesAligned(long value) {
        return (value & 0x03) != 0;
    }

    private boolean is8BytesAligned(long value) {
        return (value & 0x07) != 0;
    }

    private boolean isReferenceAligned(long reference) {
        return (reference & OBJECT_REFERENCE_MASK) != 0;
    }

    private void checkReferenceAligned(long offset) {
        if (!isReferenceAligned(offset)) {
            throw new IllegalArgumentException("Memory accesses to references must be " 
                                               + OBJECT_REFERENCE_ALIGN + "-bytes aligned, but it is " + offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    /**
     * Contract point for internal memory accessor implementations for unaligned memory accesses.
     */
    private interface UnalignedMemoryAccessor {

        char getChar(long address);
        char getChar(Object o, long offset);
        char getCharVolatile(Object o, long offset);

        short getShort(long address);
        short getShort(Object o, long offset);
        short getShortVolatile(Object o, long offset);

        int getInt(long address);
        int getInt(Object o, long offset);
        int getIntVolatile(Object o, long offset);

        float getFloat(long address);
        float getFloat(Object o, long offset);
        float getFloatVolatile(Object o, long offset);

        long getLong(long address);
        long getLong(Object o, long offset);
        long getLongVolatile(Object o, long offset);

        double getDouble(long address);
        double getDouble(Object o, long offset);
        double getDoubleVolatile(Object o, long offset);

        ////////////////////////////////////////////////////

        void putChar(long address, char x);
        void putChar(Object o, long offset, char x);
        void putCharVolatile(Object o, long offset, char x);

        void putShort(long address, short x);
        void putShort(Object o, long offset, short x);
        void putShortVolatile(Object o, long offsets, short x);

        void putInt(long address, int x);
        void putInt(Object o, long offset, int x);
        void putIntVolatile(Object o, long offset, int x);

        void putFloat(long address, float x);
        void putFloat(Object o, long offset, float x);
        void putFloatVolatile(Object o, long offset, float x);

        void putLong(long address, long x);
        void putLong(Object o, long offset, long x);
        void putLongVolatile(Object o, long offset, long x);

        void putDouble(long address, double x);
        void putDouble(Object o, long offset, double x);
        void putDoubleVolatile(Object o, long offset, double x);

    }

    /**
     * Little-endian based {@link UnalignedMemoryAccessor} implementation.
     */
    private class LittleEndianUnalignedMemoryAccessor implements UnalignedMemoryAccessor {

        @Override
        public char getChar(long address) {
            int byte1 = UNSAFE.getByte(address) & 0xFF;
            int byte0 = UNSAFE.getByte(address + 1) & 0xFF;
            return (char) ((byte0 << 8) + byte1);
        }
        
        @Override
        public char getChar(Object o, long offset) {
            int byte1 = UNSAFE.getByte(o, offset) & 0xFF;
            int byte0 = UNSAFE.getByte(o, offset + 1) & 0xFF;
            return (char) ((byte0 << 8) + byte1);
        }

        @Override
        public char getCharVolatile(Object o, long offset) {
            int byte1 = UNSAFE.getByteVolatile(o, offset) & 0xFF;
            int byte0 = UNSAFE.getByteVolatile(o, offset + 1) & 0xFF;
            return (char) ((byte0 << 8) + byte1);
        }

        @Override
        public short getShort(long address) {
            int byte1 = UNSAFE.getByte(address) & 0xFF;
            int byte0 = UNSAFE.getByte(address + 1) & 0xFF;
            return (short) ((byte0 << 8) + byte1);
        }
        
        @Override
        public short getShort(Object o, long offset) {
            int byte1 = UNSAFE.getByte(o, offset) & 0xFF;
            int byte0 = UNSAFE.getByte(o, offset + 1) & 0xFF;
            return (short) ((byte0 << 8) + byte1);
        }

        @Override
        public short getShortVolatile(Object o, long offset) {
            int byte1 = UNSAFE.getByteVolatile(o, offset) & 0xFF;
            int byte0 = UNSAFE.getByteVolatile(o, offset + 1) & 0xFF;
            return (short) ((byte0 << 8) + byte1);
        }

        @Override
        public int getInt(long address) {
            int byte3 = (UNSAFE.getByte(address) & 0xFF);
            int byte2 = (UNSAFE.getByte(address + 1) & 0xFF) << 8;
            int byte1 = (UNSAFE.getByte(address + 2) & 0xFF) << 16;
            int byte0 = (UNSAFE.getByte(address + 3) & 0xFF) << 24;
            return byte3 + byte2 + byte1 + byte0;
        }
        
        @Override
        public int getInt(Object o, long offset) {
            int byte3 = (UNSAFE.getByte(o, offset) & 0xFF);
            int byte2 = (UNSAFE.getByte(o, offset + 1) & 0xFF) << 8;
            int byte1 = (UNSAFE.getByte(o, offset + 2) & 0xFF) << 16;
            int byte0 = (UNSAFE.getByte(o, offset + 3) & 0xFF) << 24;
            return byte3 + byte2 + byte1 + byte0;
        }

        @Override
        public int getIntVolatile(Object o, long offset) {
            int byte3 = (UNSAFE.getByteVolatile(o, offset) & 0xFF);
            int byte2 = (UNSAFE.getByteVolatile(o, offset + 1) & 0xFF) << 8;
            int byte1 = (UNSAFE.getByteVolatile(o, offset + 2) & 0xFF) << 16;
            int byte0 = (UNSAFE.getByteVolatile(o, offset + 3) & 0xFF) << 24;
            return byte3 + byte2 + byte1 + byte0;
        }

        @Override
        public float getFloat(long address) {
            return Float.intBitsToFloat(getInt(address));
        }
        
        @Override
        public float getFloat(Object o, long offset) {
            return Float.intBitsToFloat(getInt(o, offset));
        }

        @Override
        public float getFloatVolatile(Object o, long offset) {
            return Float.intBitsToFloat(getIntVolatile(o, offset));
        }

        @Override
        public long getLong(long address) {
            long byte7 = (long) (UNSAFE.getByte(address) & 0xFF);
            long byte6 = (long) (UNSAFE.getByte(address + 1) & 0xFF) << 8;
            long byte5 = (long) (UNSAFE.getByte(address + 2) & 0xFF) << 16;
            long byte4 = (long) (UNSAFE.getByte(address + 3) & 0xFF) << 24;
            long byte3 = (long) (UNSAFE.getByte(address + 4) & 0xFF) << 32;
            long byte2 = (long) (UNSAFE.getByte(address + 5) & 0xFF) << 40;
            long byte1 = (long) (UNSAFE.getByte(address + 6) & 0xFF) << 48;
            long byte0 = (long) (UNSAFE.getByte(address + 7) & 0xFF) << 56;
            return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
        }
        
        @Override
        public long getLong(Object o, long offset) {
            long byte7 = (long) (UNSAFE.getByte(o, offset) & 0xFF);
            long byte6 = (long) (UNSAFE.getByte(o, offset + 1) & 0xFF) << 8;
            long byte5 = (long) (UNSAFE.getByte(o, offset + 2) & 0xFF) << 16;
            long byte4 = (long) (UNSAFE.getByte(o, offset + 3) & 0xFF) << 24;
            long byte3 = (long) (UNSAFE.getByte(o, offset + 4) & 0xFF) << 32;
            long byte2 = (long) (UNSAFE.getByte(o, offset + 5) & 0xFF) << 40;
            long byte1 = (long) (UNSAFE.getByte(o, offset + 6) & 0xFF) << 48;
            long byte0 = (long) (UNSAFE.getByte(o, offset + 7) & 0xFF) << 56;
            return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
        }

        @Override
        public long getLongVolatile(Object o, long offset) {
            long byte7 = (long) (UNSAFE.getByteVolatile(o, offset) & 0xFF);
            long byte6 = (long) (UNSAFE.getByteVolatile(o, offset + 1) & 0xFF) << 8;
            long byte5 = (long) (UNSAFE.getByteVolatile(o, offset + 2) & 0xFF) << 16;
            long byte4 = (long) (UNSAFE.getByteVolatile(o, offset + 3) & 0xFF) << 24;
            long byte3 = (long) (UNSAFE.getByteVolatile(o, offset + 4) & 0xFF) << 32;
            long byte2 = (long) (UNSAFE.getByteVolatile(o, offset + 5) & 0xFF) << 40;
            long byte1 = (long) (UNSAFE.getByteVolatile(o, offset + 6) & 0xFF) << 48;
            long byte0 = (long) (UNSAFE.getByteVolatile(o, offset + 7) & 0xFF) << 56;
            return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
        }

        @Override
        public double getDouble(long address) {
            return Double.longBitsToDouble(getLong(address));
        }
        
        @Override
        public double getDouble(Object o, long offset) {
            return Double.longBitsToDouble(getLong(o, offset));
        }

        @Override
        public double getDoubleVolatile(Object o, long offset) {
            return Double.longBitsToDouble(getLongVolatile(o, offset));
        }

        ////////////////////////////////////////////////////

        @Override
        public void putChar(long address, char x) {
            UNSAFE.putByte(address, (byte) ((x) & 0xFF));
            UNSAFE.putByte(address + 1, (byte) ((x >>> 8) & 0xFF));
        }
        
        @Override
        public void putChar(Object o, long offset, char x) {
            UNSAFE.putByte(o, offset, (byte) ((x) & 0xFF));
            UNSAFE.putByte(o, offset + 1, (byte) ((x >>> 8) & 0xFF));
        }

        @Override
        public void putCharVolatile(Object o, long offset, char x) {
            UNSAFE.putByteVolatile(o, offset, (byte) ((x) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 1, (byte) ((x >>> 8) & 0xFF));
        }

        @Override
        public void putShort(long address, short x) {
            UNSAFE.putByte(address, (byte) ((x) & 0xFF));
            UNSAFE.putByte(address + 1, (byte) ((x >>> 8) & 0xFF));
        }
        
        @Override
        public void putShort(Object o, long offset, short x) {
            UNSAFE.putByte(o, offset, (byte) ((x) & 0xFF));
            UNSAFE.putByte(o, offset + 1, (byte) ((x >>> 8) & 0xFF));
        }

        @Override
        public void putShortVolatile(Object o, long offset, short x) {
            UNSAFE.putByteVolatile(o, offset, (byte) ((x) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 1, (byte) ((x >>> 8) & 0xFF));
        }

        @Override
        public void putInt(long address, int x) {
            UNSAFE.putByte(address, (byte) ((x) & 0xFF));
            UNSAFE.putByte(address + 1, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByte(address + 2, (byte) ((x >>> 16) & 0xFF));
            UNSAFE.putByte(address + 3, (byte) ((x >>> 24) & 0xFF));
        }
        
        @Override
        public void putInt(Object o, long offset, int x) {
            UNSAFE.putByte(o, offset, (byte) ((x) & 0xFF));
            UNSAFE.putByte(o, offset + 1, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByte(o, offset + 2, (byte) ((x >>> 16) & 0xFF));
            UNSAFE.putByte(o, offset + 3, (byte) ((x >>> 24) & 0xFF));
        }

        @Override
        public void putIntVolatile(Object o, long offset, int x) {
            UNSAFE.putByteVolatile(o, offset, (byte) ((x) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 1, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 2, (byte) ((x >>> 16) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 3, (byte) ((x >>> 24) & 0xFF));
        }

        @Override
        public void putFloat(long address, float x) {
            putInt(address, Float.floatToRawIntBits(x));
        }
        
        @Override
        public void putFloat(Object o, long offset, float x) {
            putInt(o, offset, Float.floatToRawIntBits(x));
        }

        @Override
        public void putFloatVolatile(Object o, long offset, float x) {
            putIntVolatile(o, offset, Float.floatToRawIntBits(x));
        }

        @Override
        public void putLong(long address, long x) {
            UNSAFE.putByte(address, (byte) (x));
            UNSAFE.putByte(address + 1, (byte) (x >>> 8));
            UNSAFE.putByte(address + 2, (byte) (x >>> 16));
            UNSAFE.putByte(address + 3, (byte) (x >>> 24));
            UNSAFE.putByte(address + 4, (byte) (x >>> 32));
            UNSAFE.putByte(address + 5, (byte) (x >>> 40));
            UNSAFE.putByte(address + 6, (byte) (x >>> 48));
            UNSAFE.putByte(address + 7, (byte) (x >>> 56));
        }
        
        @Override
        public void putLong(Object o, long offset, long x) {
            UNSAFE.putByte(o, offset, (byte) (x));
            UNSAFE.putByte(o, offset + 1, (byte) (x >>> 8));
            UNSAFE.putByte(o, offset + 2, (byte) (x >>> 16));
            UNSAFE.putByte(o, offset + 3, (byte) (x >>> 24));
            UNSAFE.putByte(o, offset + 4, (byte) (x >>> 32));
            UNSAFE.putByte(o, offset + 5, (byte) (x >>> 40));
            UNSAFE.putByte(o, offset + 6, (byte) (x >>> 48));
            UNSAFE.putByte(o, offset + 7, (byte) (x >>> 56));
        }

        @Override
        public void putLongVolatile(Object o, long offset, long x) {
            UNSAFE.putByteVolatile(o, offset, (byte) (x));
            UNSAFE.putByteVolatile(o, offset + 1, (byte) (x >>> 8));
            UNSAFE.putByteVolatile(o, offset + 2, (byte) (x >>> 16));
            UNSAFE.putByteVolatile(o, offset + 3, (byte) (x >>> 24));
            UNSAFE.putByteVolatile(o, offset + 4, (byte) (x >>> 32));
            UNSAFE.putByteVolatile(o, offset + 5, (byte) (x >>> 40));
            UNSAFE.putByteVolatile(o, offset + 6, (byte) (x >>> 48));
            UNSAFE.putByteVolatile(o, offset + 7, (byte) (x >>> 56));
        }

        @Override
        public void putDouble(long address, double x) {
            putLong(address, Double.doubleToRawLongBits(x));
        }
        
        @Override
        public void putDouble(Object o, long offset, double x) {
            putLong(o, offset, Double.doubleToRawLongBits(x));
        }

        @Override
        public void putDoubleVolatile(Object o, long offset, double x) {
            putLongVolatile(o, offset, Double.doubleToRawLongBits(x));
        }

    }

    /**
     * Big-endian based {@link UnalignedMemoryAccessor} implementation.
     */
    private class BigEndianUnalignedMemoryAccessor implements UnalignedMemoryAccessor {

        @Override
        public char getChar(long address) {
            int byte1 = UNSAFE.getByte(address) & 0xFF;
            int byte0 = UNSAFE.getByte(address + 1) & 0xFF;
            return (char) ((byte1 << 8) + byte0);
        }
        
        @Override
        public char getChar(Object o, long offset) {
            int byte1 = UNSAFE.getByte(o, offset) & 0xFF;
            int byte0 = UNSAFE.getByte(o, offset + 1) & 0xFF;
            return (char) ((byte1 << 8) + byte0);
        }

        @Override
        public char getCharVolatile(Object o, long offset) {
            int byte1 = UNSAFE.getByteVolatile(o, offset) & 0xFF;
            int byte0 = UNSAFE.getByteVolatile(o, offset + 1) & 0xFF;
            return (char) ((byte1 << 8) + byte0);
        }

        @Override
        public short getShort(long address) {
            int byte1 = UNSAFE.getByte(address) & 0xFF;
            int byte0 = UNSAFE.getByte(address + 1) & 0xFF;
            return (short) ((byte1 << 8) + byte0);
        }
        
        @Override
        public short getShort(Object o, long offset) {
            int byte1 = UNSAFE.getByte(o, offset) & 0xFF;
            int byte0 = UNSAFE.getByte(o, offset + 1) & 0xFF;
            return (short) ((byte1 << 8) + byte0);
        }

        @Override
        public short getShortVolatile(Object o, long offset) {
            int byte1 = UNSAFE.getByteVolatile(o, offset) & 0xFF;
            int byte0 = UNSAFE.getByteVolatile(o, offset + 1) & 0xFF;
            return (short) ((byte1 << 8) + byte0);
        }

        @Override
        public int getInt(long address) {
            int byte3 = (UNSAFE.getByte(address) & 0xFF) << 24;
            int byte2 = (UNSAFE.getByte(address + 1) & 0xFF) << 16;
            int byte1 = (UNSAFE.getByte(address + 2) & 0xFF) << 8;
            int byte0 = (UNSAFE.getByte(address + 3) & 0xFF);
            return byte3 + byte2 + byte1 + byte0;
        }
        
        @Override
        public int getInt(Object o, long offset) {
            int byte3 = (UNSAFE.getByte(o, offset) & 0xFF) << 24;
            int byte2 = (UNSAFE.getByte(o, offset + 1) & 0xFF) << 16;
            int byte1 = (UNSAFE.getByte(o, offset + 2) & 0xFF) << 8;
            int byte0 = (UNSAFE.getByte(o, offset + 3) & 0xFF);
            return byte3 + byte2 + byte1 + byte0;
        }

        @Override
        public int getIntVolatile(Object o, long offset) {
            int byte3 = (UNSAFE.getByteVolatile(o, offset) & 0xFF) << 24;
            int byte2 = (UNSAFE.getByteVolatile(o, offset + 1) & 0xFF) << 16;
            int byte1 = (UNSAFE.getByteVolatile(o, offset + 2) & 0xFF) << 8;
            int byte0 = (UNSAFE.getByteVolatile(o, offset + 3) & 0xFF);
            return byte3 + byte2 + byte1 + byte0;
        }

        @Override
        public float getFloat(long address) {
            return Float.intBitsToFloat(getInt(address));
        }
        
        @Override
        public float getFloat(Object o, long offset) {
            return Float.intBitsToFloat(getInt(o, offset));
        }

        @Override
        public float getFloatVolatile(Object o, long offset) {
            return Float.intBitsToFloat(getIntVolatile(o, offset));
        }

        @Override
        public long getLong(long address) {
            long byte7 = (long) (UNSAFE.getByte(address) & 0xFF) << 56;
            long byte6 = (long) (UNSAFE.getByte(address + 1) & 0xFF) << 48;
            long byte5 = (long) (UNSAFE.getByte(address + 2) & 0xFF) << 40;
            long byte4 = (long) (UNSAFE.getByte(address + 3) & 0xFF) << 32;
            long byte3 = (long) (UNSAFE.getByte(address + 4) & 0xFF) << 24;
            long byte2 = (long) (UNSAFE.getByte(address + 5) & 0xFF) << 16;
            long byte1 = (long) (UNSAFE.getByte(address + 6) & 0xFF) << 8;
            long byte0 = (long) (UNSAFE.getByte(address + 7) & 0xFF);
            return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
        }
        
        @Override
        public long getLong(Object o, long offset) {
            long byte7 = (long) (UNSAFE.getByte(o, offset) & 0xFF) << 56;
            long byte6 = (long) (UNSAFE.getByte(o, offset + 1) & 0xFF) << 48;
            long byte5 = (long) (UNSAFE.getByte(o, offset + 2) & 0xFF) << 40;
            long byte4 = (long) (UNSAFE.getByte(o, offset + 3) & 0xFF) << 32;
            long byte3 = (long) (UNSAFE.getByte(o, offset + 4) & 0xFF) << 24;
            long byte2 = (long) (UNSAFE.getByte(o, offset + 5) & 0xFF) << 16;
            long byte1 = (long) (UNSAFE.getByte(o, offset + 6) & 0xFF) << 8;
            long byte0 = (long) (UNSAFE.getByte(o, offset + 7) & 0xFF);
            return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
        }

        @Override
        public long getLongVolatile(Object o, long offset) {
            long byte7 = (long) (UNSAFE.getByteVolatile(o, offset) & 0xFF) << 56;
            long byte6 = (long) (UNSAFE.getByteVolatile(o, offset + 1) & 0xFF) << 48;
            long byte5 = (long) (UNSAFE.getByteVolatile(o, offset + 2) & 0xFF) << 40;
            long byte4 = (long) (UNSAFE.getByteVolatile(o, offset + 3) & 0xFF) << 32;
            long byte3 = (long) (UNSAFE.getByteVolatile(o, offset + 4) & 0xFF) << 24;
            long byte2 = (long) (UNSAFE.getByteVolatile(o, offset + 5) & 0xFF) << 16;
            long byte1 = (long) (UNSAFE.getByteVolatile(o, offset + 6) & 0xFF) << 8;
            long byte0 = (long) (UNSAFE.getByteVolatile(o, offset + 7) & 0xFF);
            return byte7 + byte6 + byte5 + byte4 + byte3 + byte2 + byte1 + byte0;
        }

        @Override
        public double getDouble(long address) {
            return Double.longBitsToDouble(getLong(address));
        }
        
        @Override
        public double getDouble(Object o, long offset) {
            return Double.longBitsToDouble(getLong(o, offset));
        }

        @Override
        public double getDoubleVolatile(Object o, long offset) {
            return Double.longBitsToDouble(getLongVolatile(o, offset));
        }

        ////////////////////////////////////////////////////

        @Override
        public void putChar(long address, char x) {
            UNSAFE.putByte(address, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByte(address + 1, (byte) ((x) & 0xFF));
        }
        
        @Override
        public void putChar(Object o, long offset, char x) {
            UNSAFE.putByte(o, offset, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByte(o, offset + 1, (byte) ((x) & 0xFF));
        }

        @Override
        public void putCharVolatile(Object o, long offset, char x) {
            UNSAFE.putByteVolatile(o, offset, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 1, (byte) ((x) & 0xFF));
        }

        @Override
        public void putShort(long address, short x) {
            UNSAFE.putByte(address, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByte(address + 1, (byte) ((x) & 0xFF));
        }
        
        @Override
        public void putShort(Object o, long offset, short x) {
            UNSAFE.putByte(o, offset, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByte(o, offset + 1, (byte) ((x) & 0xFF));
        }

        @Override
        public void putShortVolatile(Object o, long offset, short x) {
            UNSAFE.putByteVolatile(o, offset, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 1, (byte) ((x) & 0xFF));
        }

        @Override
        public void putInt(long address, int x) {
            UNSAFE.putByte(address, (byte) ((x >>> 24) & 0xFF));
            UNSAFE.putByte(address + 1, (byte) ((x >>> 16) & 0xFF));
            UNSAFE.putByte(address + 2, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByte(address + 3, (byte) ((x) & 0xFF));
        }
        
        @Override
        public void putInt(Object o, long offset, int x) {
            UNSAFE.putByte(o, offset, (byte) ((x >>> 24) & 0xFF));
            UNSAFE.putByte(o, offset + 1, (byte) ((x >>> 16) & 0xFF));
            UNSAFE.putByte(o, offset + 2, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByte(o, offset + 3, (byte) ((x) & 0xFF));
        }

        @Override
        public void putIntVolatile(Object o, long offset, int x) {
            UNSAFE.putByteVolatile(o, offset, (byte) ((x >>> 24) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 1, (byte) ((x >>> 16) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 2, (byte) ((x >>> 8) & 0xFF));
            UNSAFE.putByteVolatile(o, offset + 3, (byte) ((x) & 0xFF));
        }

        @Override
        public void putFloat(long address, float x) {
            putInt(address, Float.floatToRawIntBits(x));
        }
        
        @Override
        public void putFloat(Object o, long offset, float x) {
            putInt(o, offset, Float.floatToRawIntBits(x));
        }

        @Override
        public void putFloatVolatile(Object o, long offset, float x) {
            putIntVolatile(o, offset, Float.floatToRawIntBits(x));
        }

        @Override
        public void putLong(long address, long x) {
            UNSAFE.putByte(address, (byte) (x >>> 56));
            UNSAFE.putByte(address + 1, (byte) (x >>> 48));
            UNSAFE.putByte(address + 2, (byte) (x >>> 40));
            UNSAFE.putByte(address + 3, (byte) (x >>> 32));
            UNSAFE.putByte(address + 4, (byte) (x >>> 24));
            UNSAFE.putByte(address + 5, (byte) (x >>> 16));
            UNSAFE.putByte(address + 6, (byte) (x >>> 8));
            UNSAFE.putByte(address + 7, (byte) (x));
        }
        
        @Override
        public void putLong(Object o, long offset, long x) {
            UNSAFE.putByte(o, offset, (byte) (x >>> 56));
            UNSAFE.putByte(o, offset + 1, (byte) (x >>> 48));
            UNSAFE.putByte(o, offset + 2, (byte) (x >>> 40));
            UNSAFE.putByte(o, offset + 3, (byte) (x >>> 32));
            UNSAFE.putByte(o, offset + 4, (byte) (x >>> 24));
            UNSAFE.putByte(o, offset + 5, (byte) (x >>> 16));
            UNSAFE.putByte(o, offset + 6, (byte) (x >>> 8));
            UNSAFE.putByte(o, offset + 7, (byte) (x));
        }

        @Override
        public void putLongVolatile(Object o, long offset, long x) {
            UNSAFE.putByteVolatile(o, offset, (byte) (x >>> 56));
            UNSAFE.putByteVolatile(o, offset + 1, (byte) (x >>> 48));
            UNSAFE.putByteVolatile(o, offset + 2, (byte) (x >>> 40));
            UNSAFE.putByteVolatile(o, offset + 3, (byte) (x >>> 32));
            UNSAFE.putByteVolatile(o, offset + 4, (byte) (x >>> 24));
            UNSAFE.putByteVolatile(o, offset + 5, (byte) (x >>> 16));
            UNSAFE.putByteVolatile(o, offset + 6, (byte) (x >>> 8));
            UNSAFE.putByteVolatile(o, offset + 7, (byte) (x));
        }

        @Override
        public void putDouble(long address, double x) {
            putLong(address, Double.doubleToRawLongBits(x));
        }
        
        @Override
        public void putDouble(Object o, long offset, double x) {
            putLong(o, offset, Double.doubleToRawLongBits(x));
        }

        @Override
        public void putDoubleVolatile(Object o, long offset, double x) {
            putLongVolatile(o, offset, Double.doubleToRawLongBits(x));
        }

    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public void copyMemory(long srcAddress, long destAddress, long bytes) {
        // TODO Should we check and handle alignment???
        UNSAFE.copyMemory(srcAddress, destAddress, bytes);
    }

    // @Override
    public void setMemory(long address, long bytes, byte value) {
        // TODO Should we check and handle alignment???
        UNSAFE.setMemory(address, bytes, value);
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public char getChar(long address) {
        if (is2BytesAligned(address)) {
            return UNSAFE.getChar(address);
        } else {
            return unalignedMemoryAccessor.getChar(address);
        }
    }

    // @Override
    public char getChar(Object o, long offset) {
        if (is2BytesAligned(offset)) {
            return UNSAFE.getChar(o, offset);
        } else {
            return unalignedMemoryAccessor.getChar(o, offset);
        }
    }

    // @Override
    public char getCharVolatile(Object o, long offset) {
        if (is2BytesAligned(offset)) {
            return UNSAFE.getCharVolatile(o, offset);
        } else {
            return unalignedMemoryAccessor.getCharVolatile(o, offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public void putChar(long address, char x) {
        if (is2BytesAligned(address)) {
            UNSAFE.putChar(address, x);
        } else {
            unalignedMemoryAccessor.putChar(address, x);
        }
    }

    // @Override
    public void putChar(Object o, long offset, char x) {
        if (is2BytesAligned(offset)) {
            UNSAFE.putChar(o, offset, x);
        } else {
            unalignedMemoryAccessor.putChar(o, offset, x);
        }
    }

    // @Override
    public void putCharVolatile(Object o, long offset, char x) {
        if (is2BytesAligned(offset)) {
            UNSAFE.putChar(o, offset, x);
        } else {
            unalignedMemoryAccessor.putCharVolatile(o, offset, x);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public short getShort(long address) {
        if (is2BytesAligned(address)) {
            return UNSAFE.getShort(address);
        } else {
            return unalignedMemoryAccessor.getShort(address);
        }
    }

    // @Override
    public short getShort(Object o, long offset) {
        if (is2BytesAligned(offset)) {
            return UNSAFE.getShort(o, offset);
        } else {
            return unalignedMemoryAccessor.getShort(o, offset);
        }
    }

    // @Override
    public short getShortVolatile(Object o, long offset) {
        if (is2BytesAligned(offset)) {
            return UNSAFE.getShortVolatile(o, offset);
        } else {
            return unalignedMemoryAccessor.getShortVolatile(o, offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public void putShort(long address, short x) {
        if (is2BytesAligned(address)) {
            UNSAFE.putShort(address, x);
        } else {
            unalignedMemoryAccessor.putShort(address, x);
        }
    }

    // @Override
    public void putShort(Object o, long offset, short x) {
        if (is2BytesAligned(offset)) {
            UNSAFE.putShort(o, offset, x);
        } else {
            unalignedMemoryAccessor.putShort(o, offset, x);
        }
    }

    // @Override
    public void putShortVolatile(Object o, long offset, short x) {
        if (is2BytesAligned(offset)) {
            UNSAFE.putShortVolatile(o, offset, x);
        } else {
            unalignedMemoryAccessor.putShortVolatile(o, offset, x);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public int getInt(long address) {
        if (is4BytesAligned(address)) {
            return UNSAFE.getInt(address);
        } else {
            return unalignedMemoryAccessor.getInt(address);
        }
    }

    // @Override
    public int getInt(Object o, long offset) {
        if (is4BytesAligned(offset)) {
            return UNSAFE.getInt(o, offset);
        } else {
            return unalignedMemoryAccessor.getInt(o, offset);
        }
    }

    // @Override
    public int getIntVolatile(Object o, long offset) {
        if (is4BytesAligned(offset)) {
            return UNSAFE.getIntVolatile(o, offset);
        } else {
            return unalignedMemoryAccessor.getIntVolatile(o, offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public void putInt(long address, int x) {
        if (is4BytesAligned(address)) {
            UNSAFE.putInt(address, x);
        } else {
            unalignedMemoryAccessor.putInt(address, x);
        }
    }

    // @Override
    public void putInt(Object o, long offset, int x) {
        if (is4BytesAligned(offset)) {
            UNSAFE.putInt(o, offset, x);
        } else {
            unalignedMemoryAccessor.putInt(o, offset, x);
        }
    }

    // @Override
    public void putIntVolatile(Object o, long offset, int x) {
        if (is4BytesAligned(offset)) {
            UNSAFE.putIntVolatile(o, offset, x);
        } else {
            unalignedMemoryAccessor.putIntVolatile(o, offset, x);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public float getFloat(long address) {
        if (is4BytesAligned(address)) {
            return UNSAFE.getFloat(address);
        } else {
            return unalignedMemoryAccessor.getFloat(address);
        }
    }

    // @Override
    public float getFloat(Object o, long offset) {
        if (is4BytesAligned(offset)) {
            return UNSAFE.getFloat(o, offset);
        } else {
            return unalignedMemoryAccessor.getFloat(o, offset);
        }
    }

    // @Override
    public float getFloatVolatile(Object o, long offset) {
        if (is4BytesAligned(offset)) {
            return UNSAFE.getFloatVolatile(o, offset);
        } else {
            return unalignedMemoryAccessor.getFloatVolatile(o, offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public void putFloat(long address, float x) {
        if (is4BytesAligned(address)) {
            UNSAFE.putFloat(address, x);
        } else {
            unalignedMemoryAccessor.putFloat(address, x);
        }
    }

    // @Override
    public void putFloat(Object o, long offset, float x) {
        if (is4BytesAligned(offset)) {
            UNSAFE.putFloat(o, offset, x);
        } else {
            unalignedMemoryAccessor.putFloat(o, offset, x);
        }
    }

    // @Override
    public void putFloatVolatile(Object o, long offset, float x) {
        if (is4BytesAligned(offset)) {
            UNSAFE.putFloatVolatile(o, offset, x);
        } else {
            unalignedMemoryAccessor.putFloatVolatile(o, offset, x);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public long getLong(long address) {
        if (is8BytesAligned(address)) {
            return UNSAFE.getLong(address);
        } else {
            return unalignedMemoryAccessor.getLong(address);
        }
    }

    // @Override
    public long getLong(Object o, long offset) {
        if (is8BytesAligned(offset)) {
            return UNSAFE.getLong(o, offset);
        } else {
            return unalignedMemoryAccessor.getLong(o, offset);
        }
    }

    // @Override
    public long getLongVolatile(Object o, long offset) {
        if (is8BytesAligned(offset)) {
            return UNSAFE.getLongVolatile(o, offset);
        } else {
            return unalignedMemoryAccessor.getLongVolatile(o, offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public void putLong(long address, long x) {
        if (is8BytesAligned(address)) {
            UNSAFE.putLong(address, x);
        } else {
            unalignedMemoryAccessor.putLong(address, x);
        }
    }

    // @Override
    public void putLong(Object o, long offset, long x) {
        if (is8BytesAligned(offset)) {
            UNSAFE.putLong(o, offset, x);
        } else {
            unalignedMemoryAccessor.putLong(o, offset, x);
        }
    }

    // @Override
    public void putLongVolatile(Object o, long offset, long x) {
        if (is8BytesAligned(offset)) {
            UNSAFE.putLongVolatile(o, offset, x);
        } else {
            unalignedMemoryAccessor.putLongVolatile(o, offset, x);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public double getDouble(long address) {
        if (is8BytesAligned(address)) {
            return UNSAFE.getDouble(address);
        } else {
            return unalignedMemoryAccessor.getDouble(address);
        }
    }

    // @Override
    public double getDouble(Object o, long offset) {
        if (is8BytesAligned(offset)) {
            return UNSAFE.getDouble(o, offset);
        } else {
            return unalignedMemoryAccessor.getDouble(o, offset);
        }
    }

    // @Override
    public double getDoubleVolatile(Object o, long offset) {
        if (is8BytesAligned(offset)) {
            return UNSAFE.getDoubleVolatile(o, offset);
        } else {
            return unalignedMemoryAccessor.getDoubleVolatile(o, offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public void putDouble(long address, double x) {
        if (is8BytesAligned(address)) {
            UNSAFE.putDouble(address, x);
        } else {
            unalignedMemoryAccessor.putDouble(address, x);
        }
    }

    // @Override
    public void putDouble(Object o, long offset, double x) {
        if (is8BytesAligned(offset)) {
            UNSAFE.putDouble(o, offset, x);
        } else {
            unalignedMemoryAccessor.putDouble(o, offset, x);
        }
    }

    // @Override
    public void putDoubleVolatile(Object o, long offset, double x) {
        if (is8BytesAligned(offset)) {
            UNSAFE.putDoubleVolatile(o, offset, x);
        } else {
            unalignedMemoryAccessor.putDoubleVolatile(o, offset, x);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public Object getObject(Object o, long offset) {
        checkReferenceAligned(offset);
        return UNSAFE.getObject(o, offset);
    }

    // @Override
    public Object getObjectVolatile(Object o, long offset) {
        checkReferenceAligned(offset);
        return UNSAFE.getObjectVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public void putObject(Object o, long offset, Object x) {
        checkReferenceAligned(offset);
        UNSAFE.putObject(o, offset, x);
    }

    // @Override
    public void putObjectVolatile(Object o, long offset, Object x) {
        checkReferenceAligned(offset);
        UNSAFE.putObjectVolatile(o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public boolean compareAndSwapInt(Object o, long offset, int expected, int x) {
        if (is4BytesAligned(offset)) {
            return UNSAFE.compareAndSwapInt(o, offset, expected, x);
        } else {
            throw new UnsupportedOperationException("Unaligned memory accesses are not supported for CAS operations. " 
                                                    + "Offset must be 4-bytes aligned for integer typed CAS, but it is " + offset);
        }
    }

    // @Override
    public boolean compareAndSwapLong(Object o, long offset, long expected, long x) {
        if (is4BytesAligned(offset)) {
            return UNSAFE.compareAndSwapLong(o, offset, expected, x);
        } else {
            throw new UnsupportedOperationException("Unaligned memory accesses are not supported for CAS operations. " 
                                                    + "Offset must be 8-bytes aligned for long typed CAS, but it is " + offset);
        }
    }

    // @Override
    public boolean compareAndSwapObject(Object o, long offset, Object expected, Object x) {
        if (isReferenceAligned(offset)) {
            return UNSAFE.compareAndSwapObject(o, offset, expected, x);
        } else {
            throw new UnsupportedOperationException("Unaligned memory accesses are not supported for CAS operations. " 
                                                    + "Offset must be " + OBJECT_REFERENCE_ALIGN + "-bytes " 
                                                    + "aligned for object reference typed CAS, but it is " + offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    // @Override
    public void putOrderedInt(Object o, long offset, int x) {
        if (is4BytesAligned(offset)) {
            UNSAFE.putOrderedInt(o, offset, x);
        } else {
            throw new UnsupportedOperationException("Unaligned memory accesses are not supported for ordered writes. " 
                                                    + "Offset must be 4-bytes aligned for integer typed ordered write, but it is " + offset);
        }    
    }

    // @Override
    public void putOrderedLong(Object o, long offset, long x) {
        if (is8BytesAligned(offset)) {
            UNSAFE.putOrderedLong(o, offset, x);
        } else {
            throw new UnsupportedOperationException("Unaligned memory accesses are not supported for ordered writes. " 
                                                    + "Offset must be 8-bytes aligned for long typed ordered write, but it is " + offset);
        }     
    }

    // @Override
    public void putOrderedObject(Object o, long offset, Object x) {
        if (isReferenceAligned(offset)) {
            UNSAFE.putOrderedObject(o, offset, x);
        } else {
            throw new UnsupportedOperationException("Unaligned memory accesses are not supported for CAS operations. " 
                                                    + "Offset must be " + OBJECT_REFERENCE_ALIGN + "-bytes " 
                                                    + "aligned for object reference typed ordered writes, but it is " + offset);
        }
    }

}
