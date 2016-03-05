/*
 * Original work Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 * Modified work Copyright (c) 1986-2016, Serkan OZAL, All Rights Reserved.
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

import java.nio.ByteOrder;

import sun.misc.Unsafe;

/**
 * <p>
 * Aligned {@link MemoryAccessor} which checks for and handles unaligned memory access
 * by splitting a larger-size memory operation into several smaller-size ones
 * (which have finer-grained alignment requirements).
 * </p><p>
 * A few notes on this implementation:
 * <ul>
 *      <li>
 *        There is no atomicity guarantee for unaligned memory accesses.
 *        In fact, even on platforms which support unaligned memory accesses,
 *        there is no guarantee for atomicity when there is unaligned memory accesses.
 *        On later Intel processors, unaligned access within the cache line is atomic,
 *        but access that straddles cache lines is not.
 *        See http://psy-lob-saw.blogspot.com.tr/2013/07/atomicity-of-unaligned-memory-access-in.html
 *        for more details.
 *      </li>
 *      <li>Unaligned memory access is not supported for CAS operations. </li>
 *      <li>Unaligned memory access is not supported for ordered writes. </li>
 * </ul>
 * </p>
 */
class AlignmentAwareUnsafeMemoryAccessor extends StandardUnsafeMemoryAccessor {

    private final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    private final int INT_SIZE_IN_BYTES = Integer.SIZE / Byte.SIZE;
    private final int OBJECT_REFERENCE_ALIGN;
    private final int OBJECT_REFERENCE_MASK;
    private final int ADDRESS_SIZE;
    private final int ADDRESS_MASK;

    AlignmentAwareUnsafeMemoryAccessor(Unsafe unsafe) {
        ADDRESS_SIZE = unsafe.addressSize();
        ADDRESS_MASK = ADDRESS_SIZE - 1;
        OBJECT_REFERENCE_ALIGN = unsafe.arrayIndexScale(Object[].class);
        OBJECT_REFERENCE_MASK = OBJECT_REFERENCE_ALIGN - 1;
    }

    private boolean is2BytesAligned(long value) {
        return (value & 0x01) == 0;
    }

    private boolean is4BytesAligned(long value) {
        return (value & 0x03) == 0;
    }

    private boolean is8BytesAligned(long value) {
        return (value & 0x07) == 0;
    }
    
    private boolean isAddressAligned(long address) {
        return (address & ADDRESS_MASK) == 0;
    }

    private boolean isReferenceAligned(long offset) {
        return (offset & OBJECT_REFERENCE_MASK) == 0;
    }

    private void checkReferenceAligned(long offset) {
        if (!isReferenceAligned(offset)) {
            throw new IllegalArgumentException("Memory accesses to references must be "
                    + OBJECT_REFERENCE_ALIGN + "-bytes aligned, but it is " + offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public char getChar(Unsafe unsafe, long address) {
        if (is2BytesAligned(address)) {
            return super.getChar(unsafe, address);
        } else {
            return DirectMemoryBits.readChar(unsafe, address, BIG_ENDIAN);
        }
    }

    @Override
    public char getChar(Unsafe unsafe, Object o, long offset) {
        if (is2BytesAligned(offset)) {
            return super.getChar(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readChar(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    @Override
    public char getCharVolatile(Unsafe unsafe, Object o, long offset) {
        if (is2BytesAligned(offset)) {
            return super.getCharVolatile(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readCharVolatile(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putChar(Unsafe unsafe, long address, char x) {
        if (is2BytesAligned(address)) {
            super.putChar(unsafe, address, x);
        } else {
            DirectMemoryBits.writeChar(unsafe, address, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putChar(Unsafe unsafe, Object o, long offset, char x) {
        if (is2BytesAligned(offset)) {
            super.putChar(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeChar(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putCharVolatile(Unsafe unsafe, Object o, long offset, char x) {
        if (is2BytesAligned(offset)) {
            super.putChar(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeCharVolatile(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public short getShort(Unsafe unsafe, long address) {
        if (is2BytesAligned(address)) {
            return super.getShort(unsafe, address);
        } else {
            return DirectMemoryBits.readShort(unsafe, unsafe, address, BIG_ENDIAN);
        }
    }

    @Override
    public short getShort(Unsafe unsafe, Object o, long offset) {
        if (is2BytesAligned(offset)) {
            return super.getShort(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readShort(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    @Override
    public short getShortVolatile(Unsafe unsafe, Object o, long offset) {
        if (is2BytesAligned(offset)) {
            return super.getShortVolatile(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readShortVolatile(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putShort(Unsafe unsafe, long address, short x) {
        if (is2BytesAligned(address)) {
            super.putShort(unsafe, address, x);
        } else {
            DirectMemoryBits.writeShort(unsafe, address, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putShort(Unsafe unsafe, Object o, long offset, short x) {
        if (is2BytesAligned(offset)) {
            super.putShort(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeShort(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putShortVolatile(Unsafe unsafe, Object o, long offset, short x) {
        if (is2BytesAligned(offset)) {
            super.putShortVolatile(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeShortVolatile(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public int getInt(Unsafe unsafe, long address) {
        if (is4BytesAligned(address)) {
            return super.getInt(unsafe, address);
        } else {
            return DirectMemoryBits.readInt(unsafe, address, BIG_ENDIAN);
        }
    }

    @Override
    public int getInt(Unsafe unsafe, Object o, long offset) {
        if (is4BytesAligned(offset)) {
            return super.getInt(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readInt(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    @Override
    public int getIntVolatile(Unsafe unsafe, Object o, long offset) {
        if (is4BytesAligned(offset)) {
            return super.getIntVolatile(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readIntVolatile(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putInt(Unsafe unsafe, long address, int x) {
        if (is4BytesAligned(address)) {
            super.putInt(unsafe, address, x);
        } else {
            DirectMemoryBits.writeInt(unsafe, address, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putInt(Unsafe unsafe, Object o, long offset, int x) {
        if (is4BytesAligned(offset)) {
            super.putInt(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeInt(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putIntVolatile(Unsafe unsafe, Object o, long offset, int x) {
        if (is4BytesAligned(offset)) {
            super.putIntVolatile(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeIntVolatile(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public float getFloat(Unsafe unsafe, long address) {
        if (is4BytesAligned(address)) {
            return super.getFloat(unsafe, address);
        } else {
            return DirectMemoryBits.readFloat(unsafe, address, BIG_ENDIAN);
        }
    }

    @Override
    public float getFloat(Unsafe unsafe, Object o, long offset) {
        if (is4BytesAligned(offset)) {
            return super.getFloat(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readFloat(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    @Override
    public float getFloatVolatile(Unsafe unsafe, Object o, long offset) {
        if (is4BytesAligned(offset)) {
            return super.getFloatVolatile(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readFloatVolatile(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putFloat(Unsafe unsafe, long address, float x) {
        if (is4BytesAligned(address)) {
            super.putFloat(unsafe, address, x);
        } else {
            DirectMemoryBits.writeFloat(unsafe, address, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putFloat(Unsafe unsafe, Object o, long offset, float x) {
        if (is4BytesAligned(offset)) {
            super.putFloat(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeFloat(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putFloatVolatile(Unsafe unsafe, Object o, long offset, float x) {
        if (is4BytesAligned(offset)) {
            super.putFloatVolatile(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeFloatVolatile(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public long getLong(Unsafe unsafe, long address) {
        if (is8BytesAligned(address)) {
            return super.getLong(unsafe, address);
        } else {
            return DirectMemoryBits.readLong(unsafe, address, BIG_ENDIAN);
        }
    }

    @Override
    public long getLong(Unsafe unsafe, Object o, long offset) {
        if (is8BytesAligned(offset)) {
            return super.getLong(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readLong(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    @Override
    public long getLongVolatile(Unsafe unsafe, Object o, long offset) {
        if (is8BytesAligned(offset)) {
            return super.getLongVolatile(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readLongVolatile(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putLong(Unsafe unsafe, long address, long x) {
        if (is8BytesAligned(address)) {
            super.putLong(unsafe, address, x);
        } else {
            DirectMemoryBits.writeLong(unsafe, address, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putLong(Unsafe unsafe, Object o, long offset, long x) {
        if (is8BytesAligned(offset)) {
            super.putLong(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeLong(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putLongVolatile(Unsafe unsafe, Object o, long offset, long x) {
        if (is8BytesAligned(offset)) {
            super.putLongVolatile(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeLongVolatile(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public double getDouble(Unsafe unsafe, long address) {
        if (is8BytesAligned(address)) {
            return super.getDouble(unsafe, address);
        } else {
            return DirectMemoryBits.readDouble(unsafe, address, BIG_ENDIAN);
        }
    }

    @Override
    public double getDouble(Unsafe unsafe, Object o, long offset) {
        if (is8BytesAligned(offset)) {
            return super.getDouble(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readDouble(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    @Override
    public double getDoubleVolatile(Unsafe unsafe, Object o, long offset) {
        if (is8BytesAligned(offset)) {
            return super.getDoubleVolatile(unsafe, o, offset);
        } else {
            return DirectMemoryBits.readDoubleVolatile(unsafe, o, offset, BIG_ENDIAN);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putDouble(Unsafe unsafe, long address, double x) {
        if (is8BytesAligned(address)) {
            super.putDouble(unsafe, address, x);
        } else {
            DirectMemoryBits.writeDouble(unsafe, address, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putDouble(Unsafe unsafe, Object o, long offset, double x) {
        if (is8BytesAligned(offset)) {
            super.putDouble(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeDouble(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }

    @Override
    public void putDoubleVolatile(Unsafe unsafe, Object o, long offset, double x) {
        if (is8BytesAligned(offset)) {
            super.putDoubleVolatile(unsafe, o, offset, x);
        } else {
            DirectMemoryBits.writeDoubleVolatile(unsafe, o, offset, x, BIG_ENDIAN);
        }
    }
    
    /////////////////////////////////////////////////////////////////////////
    
    @Override
    public long getAddress(Unsafe unsafe, long address) {
        if (isAddressAligned(address)) {
            return super.getAddress(unsafe, address);
        } else {
            if (ADDRESS_SIZE == INT_SIZE_IN_BYTES) {
                return DirectMemoryBits.readInt(unsafe, address, BIG_ENDIAN);
            } else {
                return DirectMemoryBits.readLong(unsafe, address, BIG_ENDIAN);
            } 
        }
    }
    
    @Override
    public void putAddress(Unsafe unsafe, long address, long x) {
        if (isAddressAligned(address)) {
            super.putAddress(unsafe, address, x);
        } else {
            if (ADDRESS_SIZE == INT_SIZE_IN_BYTES) {
                DirectMemoryBits.writeInt(unsafe, address, (int) x, BIG_ENDIAN);
            } else {
                DirectMemoryBits.writeLong(unsafe, address, x, BIG_ENDIAN);
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public Object getObject(Unsafe unsafe, Object o, long offset) {
        checkReferenceAligned(offset);
        return super.getObject(unsafe, o, offset);
    }

    @Override
    public Object getObjectVolatile(Unsafe unsafe, Object o, long offset) {
        checkReferenceAligned(offset);
        return super.getObjectVolatile(unsafe, o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putObject(Unsafe unsafe, Object o, long offset, Object x) {
        checkReferenceAligned(offset);
        super.putObject(unsafe, o, offset, x);
    }

    @Override
    public void putObjectVolatile(Unsafe unsafe, Object o, long offset, Object x) {
        checkReferenceAligned(offset);
        super.putObjectVolatile(unsafe, o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public boolean compareAndSwapInt(Unsafe unsafe, Object o, long offset, int expected, int x) {
        if (is4BytesAligned(offset)) {
            return super.compareAndSwapInt(unsafe, o, offset, expected, x);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for CAS operations. "
                    + "Offset must be 4-bytes aligned for integer typed CAS, but it is " + offset);
        }
    }

    @Override
    public boolean compareAndSwapLong(Unsafe unsafe, Object o, long offset, long expected, long x) {
        if (is4BytesAligned(offset)) {
            return super.compareAndSwapLong(unsafe, o, offset, expected, x);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for CAS operations. "
                    + "Offset must be 8-bytes aligned for long typed CAS, but it is " + offset);
        }
    }

    @Override
    public boolean compareAndSwapObject(Unsafe unsafe, Object o, long offset, Object expected, Object x) {
        if (isReferenceAligned(offset)) {
            return super.compareAndSwapObject(unsafe, o, offset, expected, x);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for CAS operations. "
                    + "Offset must be " + OBJECT_REFERENCE_ALIGN + "-bytes "
                    + "aligned for object reference typed CAS, but it is " + offset);
        }
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putOrderedInt(Unsafe unsafe, Object o, long offset, int x) {
        if (is4BytesAligned(offset)) {
            super.putOrderedInt(unsafe, o, offset, x);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for ordered writes. "
                    + "Offset must be 4-bytes aligned for integer typed ordered write, but it is " + offset);
        }
    }

    @Override
    public void putOrderedLong(Unsafe unsafe, Object o, long offset, long x) {
        if (is8BytesAligned(offset)) {
            super.putOrderedLong(unsafe, o, offset, x);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for ordered writes. "
                    + "Offset must be 8-bytes aligned for long typed ordered write, but it is " + offset);
        }
    }

    @Override
    public void putOrderedObject(Unsafe unsafe, Object o, long offset, Object x) {
        if (isReferenceAligned(offset)) {
            super.putOrderedObject(unsafe, o, offset, x);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for CAS operations. "
                    + "Offset must be " + OBJECT_REFERENCE_ALIGN + "-bytes "
                    + "aligned for object reference typed ordered writes, but it is " + offset);
        }
    }
    
    /////////////////////////////////////////////////////////////////////////
    
    @Override
    public int getAndAddInt(Unsafe unsafe, Object o, long offset, int delta) {
        if (is4BytesAligned(offset)) {
            return super.getAndAddInt(unsafe, o, offset, delta);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for getAndSet operations. "
                    + "Offset must be 4-bytes aligned for integer typed ordered write, but it is " + offset);
        }
    }
    
    @Override
    public int getAndSetInt(Unsafe unsafe, Object o, long offset, int newValue) {
        if (is4BytesAligned(offset)) {
            return super.getAndSetInt(unsafe, o, offset, newValue);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for getAndSet operations. "
                    + "Offset must be 4-bytes aligned for integer typed ordered write, but it is " + offset);
        }
    }
    
    @Override
    public long getAndAddLong(Unsafe unsafe, Object o, long offset, long delta) {
        if (is8BytesAligned(offset)) {
            return super.getAndAddLong(unsafe, o, offset, delta);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for getAndSet operations. "
                    + "Offset must be 8-bytes aligned for long typed ordered write, but it is " + offset);
        }
    }
    
    @Override
    public long getAndSetLong(Unsafe unsafe, Object o, long offset, long newValue) {
        if (is8BytesAligned(offset)) {
            return super.getAndSetLong(unsafe, o, offset, newValue);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for getAndSet operations. "
                    + "Offset must be 8-bytes aligned for long typed ordered write, but it is " + offset);
        }
    }

    @Override
    public Object getAndSetObject(Unsafe unsafe, Object o, long offset, Object newValue) {
        if (isReferenceAligned(offset)) {
            return super.getAndSetObject(unsafe, o, offset, newValue);
        } else {
            throw new IllegalArgumentException("Unaligned memory accesses are not supported for getAndSet operations. "
                    + "Offset must be " + OBJECT_REFERENCE_ALIGN + "-bytes "
                    + "aligned for object reference typed ordered writes, but it is " + offset);
        }
    }

}
