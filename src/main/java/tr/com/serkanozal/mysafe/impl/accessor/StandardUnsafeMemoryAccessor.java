/*
 * Original work Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 * Modified work Copyright (c) 1986-2015, Serkan OZAL, All Rights Reserved.
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
 * Standard {@link UnsafeMemoryAccessor} implementation
 * that directly uses {@link sun.misc.Unsafe} for accessing to memory.
 */
class StandardUnsafeMemoryAccessor implements UnsafeMemoryAccessor {

    @Override
    public void copyMemory(Unsafe unsafe, long srcAddress, long destAddress, long bytes) {
        unsafe.copyMemory(srcAddress, destAddress, bytes);
    }

    @Override
    public void copyMemory(Unsafe unsafe, Object srcObj, long srcOffset, Object destObj, long destOffset, long bytes) {
        unsafe.copyMemory(srcObj, srcOffset, destObj, destOffset, bytes);
    }

    @Override
    public void setMemory(Unsafe unsafe, long address, long bytes, byte value) {
        unsafe.setMemory(address, bytes, value);
    }
    
    @Override
    public void setMemory(Unsafe unsafe, Object o, long offset, long bytes, byte value) {
        unsafe.setMemory(o, offset, bytes, value);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public boolean getBoolean(Unsafe unsafe, long address) {
        return unsafe.getBoolean(null, address);
    }

    @Override
    public boolean getBoolean(Unsafe unsafe, Object o, long offset) {
        return unsafe.getBoolean(o, offset);
    }

    @Override
    public boolean getBooleanVolatile(Unsafe unsafe, Object o, long offset) {
        return unsafe.getBooleanVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putBoolean(Unsafe unsafe, long address, boolean x) {
        unsafe.putBoolean(null, address, x);
    }

    @Override
    public void putBoolean(Unsafe unsafe, Object o, long offset, boolean x) {
        unsafe.putBoolean(o, offset, x);
    }

    @Override
    public void putBooleanVolatile(Unsafe unsafe, Object o, long offset, boolean x) {
        unsafe.putBooleanVolatile(o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public byte getByte(Unsafe unsafe, long address) {
        return unsafe.getByte(address);
    }

    @Override
    public byte getByte(Unsafe unsafe, Object o, long offset) {
        return unsafe.getByte(o, offset);
    }

    @Override
    public byte getByteVolatile(Unsafe unsafe, Object o, long offset) {
        return unsafe.getByteVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putByte(Unsafe unsafe, long address, byte x) {
        unsafe.putByte(address, x);
    }

    @Override
    public void putByte(Unsafe unsafe, Object o, long offset, byte x) {
        unsafe.putByte(o, offset, x);
    }

    @Override
    public void putByteVolatile(Unsafe unsafe, Object o, long offset, byte x) {
        unsafe.putByteVolatile(o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public char getChar(Unsafe unsafe, long address) {
        return unsafe.getChar(address);
    }

    @Override
    public char getChar(Unsafe unsafe, Object o, long offset) {
        return unsafe.getChar(o, offset);
    }

    @Override
    public char getCharVolatile(Unsafe unsafe, Object o, long offset) {
        return unsafe.getCharVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putChar(Unsafe unsafe, long address, char x) {
        unsafe.putChar(address, x);
    }

    @Override
    public void putChar(Unsafe unsafe, Object o, long offset, char x) {
        unsafe.putChar(o, offset, x);
    }

    @Override
    public void putCharVolatile(Unsafe unsafe, Object o, long offset, char x) {
        unsafe.putCharVolatile(o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public short getShort(Unsafe unsafe, long address) {
        return unsafe.getShort(address);
    }

    @Override
    public short getShort(Unsafe unsafe, Object o, long offset) {
        return unsafe.getShort(o, offset);
    }

    @Override
    public short getShortVolatile(Unsafe unsafe, Object o, long offset) {
        return unsafe.getShortVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putShort(Unsafe unsafe, long address, short x) {
        unsafe.putShort(address, x);
    }

    @Override
    public void putShort(Unsafe unsafe, Object o, long offset, short x) {
        unsafe.putShort(o, offset, x);
    }

    @Override
    public void putShortVolatile(Unsafe unsafe, Object o, long offset, short x) {
        unsafe.putShortVolatile(o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public int getInt(Unsafe unsafe, long address) {
        return unsafe.getInt(address);
    }

    @Override
    public int getInt(Unsafe unsafe, Object o, long offset) {
        return unsafe.getInt(o, offset);
    }

    @Override
    public int getIntVolatile(Unsafe unsafe, Object o, long offset) {
        return unsafe.getIntVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putInt(Unsafe unsafe, long address, int x) {
        unsafe.putInt(address, x);
    }

    @Override
    public void putInt(Unsafe unsafe, Object o, long offset, int x) {
        unsafe.putInt(o, offset, x);
    }

    @Override
    public void putIntVolatile(Unsafe unsafe, Object o, long offset, int x) {
        unsafe.putIntVolatile(o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public float getFloat(Unsafe unsafe, long address) {
        return unsafe.getFloat(address);
    }

    @Override
    public float getFloat(Unsafe unsafe, Object o, long offset) {
        return unsafe.getFloat(o, offset);
    }

    @Override
    public float getFloatVolatile(Unsafe unsafe, Object o, long offset) {
        return unsafe.getFloatVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putFloat(Unsafe unsafe, long address, float x) {
        unsafe.putFloat(address, x);
    }

    @Override
    public void putFloat(Unsafe unsafe, Object o, long offset, float x) {
        unsafe.putFloat(o, offset, x);
    }

    @Override
    public void putFloatVolatile(Unsafe unsafe, Object o, long offset, float x) {
        unsafe.putFloatVolatile(o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public long getLong(Unsafe unsafe, long address) {
        return unsafe.getLong(address);
    }

    @Override
    public long getLong(Unsafe unsafe, Object o, long offset) {
        return unsafe.getLong(o, offset);
    }

    @Override
    public long getLongVolatile(Unsafe unsafe, Object o, long offset) {
        return unsafe.getLongVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putLong(Unsafe unsafe, long address, long x) {
        unsafe.putLong(address, x);
    }

    @Override
    public void putLong(Unsafe unsafe, Object o, long offset, long x) {
        unsafe.putLong(o, offset, x);
    }

    @Override
    public void putLongVolatile(Unsafe unsafe, Object o, long offset, long x) {
        unsafe.putLongVolatile(o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public double getDouble(Unsafe unsafe, long address) {
        return unsafe.getDouble(address);
    }

    @Override
    public double getDouble(Unsafe unsafe, Object o, long offset) {
        return unsafe.getDouble(o, offset);
    }

    @Override
    public double getDoubleVolatile(Unsafe unsafe, Object o, long offset) {
        return unsafe.getDoubleVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putDouble(Unsafe unsafe, long address, double x) {
        unsafe.putDouble(address, x);
    }

    @Override
    public void putDouble(Unsafe unsafe, Object o, long offset, double x) {
        unsafe.putDouble(o, offset, x);
    }

    @Override
    public void putDoubleVolatile(Unsafe unsafe, Object o, long offset, double x) {
        unsafe.putDoubleVolatile(o, offset, x);
    }
    
    /////////////////////////////////////////////////////////////////////////
    
    @Override
    public long getAddress(Unsafe unsafe, long address) {
        return unsafe.getAddress(address);
    }
    
    @Override
    public void putAddress(Unsafe unsafe, long address, long x) {
        unsafe.putAddress(address, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public Object getObject(Unsafe unsafe, Object o, long offset) {
        return unsafe.getObject(o, offset);
    }

    @Override
    public Object getObjectVolatile(Unsafe unsafe, Object o, long offset) {
        return unsafe.getObjectVolatile(o, offset);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putObject(Unsafe unsafe, Object o, long offset, Object x) {
        unsafe.putObject(o, offset, x);
    }

    @Override
    public void putObjectVolatile(Unsafe unsafe, Object o, long offset, Object x) {
        unsafe.putObjectVolatile(o, offset, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public boolean compareAndSwapInt(Unsafe unsafe, Object o, long offset, int expected, int x) {
        return unsafe.compareAndSwapInt(o, offset, expected, x);
    }

    @Override
    public boolean compareAndSwapLong(Unsafe unsafe, Object o, long offset, long expected, long x) {
        return unsafe.compareAndSwapLong(o, offset, expected, x);
    }

    @Override
    public boolean compareAndSwapObject(Unsafe unsafe, Object o, long offset, Object expected, Object x) {
        return unsafe.compareAndSwapObject(o, offset, expected, x);
    }

    /////////////////////////////////////////////////////////////////////////

    @Override
    public void putOrderedInt(Unsafe unsafe, Object o, long offset, int x) {
        unsafe.putOrderedInt(o, offset, x);
    }

    @Override
    public void putOrderedLong(Unsafe unsafe, Object o, long offset, long x) {
        unsafe.putOrderedLong(o, offset, x);
    }

    @Override
    public void putOrderedObject(Unsafe unsafe, Object o, long offset, Object x) {
        unsafe.putOrderedObject(o, offset, x);
    }
    
    /////////////////////////////////////////////////////////////////////////
    
    @Override
    public int getAndAddInt(Unsafe unsafe, Object o, long offset, int delta) {
        return unsafe.getAndAddInt(o, offset, delta);
    }
    
    @Override
    public int getAndSetInt(Unsafe unsafe, Object o, long offset, int newValue) {
        return unsafe.getAndSetInt(o, offset, newValue);
    }
    
    @Override
    public long getAndAddLong(Unsafe unsafe, Object o, long offset, long delta) {
        return unsafe.getAndAddLong(o, offset, delta);
    }
    
    @Override
    public long getAndSetLong(Unsafe unsafe, Object o, long offset, long newValue) {
        return unsafe.getAndSetLong(o, offset, newValue);
    }

    @Override
    public Object getAndSetObject(Unsafe unsafe, Object o, long offset, Object newValue) {
        return unsafe.getAndSetObject(o, offset, newValue);
    }

}
