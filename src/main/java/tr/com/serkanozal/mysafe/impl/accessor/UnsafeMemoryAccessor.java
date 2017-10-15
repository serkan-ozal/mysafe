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
 * Contract point for accessing memory through {@link Unsafe}.
 * 
 * @see UnsafeMemoryAccessorFactory
 */
public interface UnsafeMemoryAccessor {

    /**
     * Copies memory from given source address to given destination address
     * as given size.
     *
     * @param unsafe      the {@link Unsafe} instance to be used for memory access
     * @param srcAddress  the source address to be copied from
     * @param destAddress the destination address to be copied to
     * @param bytes       the number of bytes to be copied
     */
    void copyMemory(Unsafe unsafe, long srcAddress, long destAddress, long bytes);

    /**
     * Copies memory from given source object by given source offset
     * to given destination object by given destination offset as given size.
     * <p>
     * <p>
     * NOTE:
     * Destination object can only be <tt>byte[]</tt> or <tt>null</tt>.
     * But source object can be any object or <tt>null</tt>.
     * </p>
     *
     * @param unsafe     the {@link Unsafe} instance to be used for memory access
     * @param srcObj     the source object to be copied from
     * @param srcOffset  the source offset relative to object itself to be copied from
     * @param destObj    the destination object to be copied to
     * @param destOffset the destination offset relative to object itself to be copied to
     * @param bytes      the number of bytes to be copied
     */
    void copyMemory(Unsafe unsafe, Object srcObj, long srcOffset, Object destObj, long destOffset, long bytes);

    /**
     * Sets memory with given value from specified address as given size.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the start address of the memory region
     *                which will be set with given value
     * @param bytes   the number of bytes to be set
     * @param value   the value to be set
     */
    void setMemory(Unsafe unsafe, long address, long bytes, byte value);
    
    /**
     * Sets memory with given value from the given object by given offset as given size.
     * 
     * @param unsafe    the {@link Unsafe} instance to be used for memory access
     * @param o         the object to be set bytes to
     * @param offset    the destination offset relative to object itself to be set bytes to
     * @param bytes     the number of bytes to be set
     * @param value     the value to be set
     * 
     * @since 1.7
     */
    void setMemory(Unsafe unsafe, Object o, long offset, long bytes, byte value);
    
    /////////////////////////////////////////////////////////////////////////

    /**
     * Reads the boolean value from given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where boolean value will be read from
     * @return the read value
     */
    boolean getBoolean(Unsafe unsafe, long address);

    /**
     * Reads the boolean value from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where boolean value will be read from
     * @param offset the offset of boolean field relative to object itself
     * @return the read value
     */
    boolean getBoolean(Unsafe unsafe, Object o, long offset);

    /**
     * Reads the boolean value as volatile from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where boolean value will be read from
     * @param offset the offset of boolean field relative to object itself
     * @return the read value
     */
    boolean getBooleanVolatile(Unsafe unsafe, Object o, long offset);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Writes the given boolean value to given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where boolean value will be written to
     * @param x       the boolean value to be written
     */
    void putBoolean(Unsafe unsafe, long address, boolean x);

    /**
     * Writes the boolean value to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where boolean value will be written to
     * @param offset the offset of boolean field relative to object itself
     * @param x      the boolean value to be written
     */
    void putBoolean(Unsafe unsafe, Object o, long offset, boolean x);

    /**
     * Writes the boolean value as volatile to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where boolean value will be written to
     * @param offset the offset of boolean field relative to object itself
     * @param x      the boolean value to be written
     */
    void putBooleanVolatile(Unsafe unsafe, Object o, long offset, boolean x);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Reads the byte value from given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where byte value will be read from
     * @return the read value
     */
    byte getByte(Unsafe unsafe, long address);

    /**
     * Reads the byte value from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where byte value will be read from
     * @param offset the offset of byte field relative to object itself
     * @return the read value
     */
    byte getByte(Unsafe unsafe, Object o, long offset);

    /**
     * Reads the byte value as volatile from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where byte value will be read from
     * @param offset the offset of byte field relative to object itself
     * @return the read value
     */
    byte getByteVolatile(Unsafe unsafe, Object o, long offset);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Writes the given byte value to given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where byte value will be written to
     * @param x       the byte value to be written
     */
    void putByte(Unsafe unsafe, long address, byte x);

    /**
     * Writes the byte value to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where byte value will be written to
     * @param offset the offset of byte field relative to object itself
     * @param x      the byte value to be written
     */
    void putByte(Unsafe unsafe, Object o, long offset, byte x);

    /**
     * Writes the byte value as volatile to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where byte value will be written to
     * @param offset the offset of byte field relative to object itself
     * @param x      the byte value to be written
     */
    void putByteVolatile(Unsafe unsafe, Object o, long offset, byte x);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Reads the char value from given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where char value will be read from
     * @return the read value
     */
    char getChar(Unsafe unsafe, long address);

    /**
     * Reads the char value from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where char value will be read from
     * @param offset the offset of char field relative to object itself
     * @return the read value
     */
    char getChar(Unsafe unsafe, Object o, long offset);

    /**
     * Reads the char value as volatile from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where char value will be read from
     * @param offset the offset of char field relative to object itself
     * @return the read value
     */
    char getCharVolatile(Unsafe unsafe, Object o, long offset);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Writes the given char value to given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where char value will be written to
     * @param x       the char value to be written
     */
    void putChar(Unsafe unsafe, long address, char x);

    /**
     * Writes the char value to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where char value will be written to
     * @param offset the offset of char field relative to object itself
     * @param x      the char value to be written
     */
    void putChar(Unsafe unsafe, Object o, long offset, char x);

    /**
     * Writes the char value as volatile to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where char value will be written to
     * @param offset the offset of char field relative to object itself
     * @param x      the char value to be written
     */
    void putCharVolatile(Unsafe unsafe, Object o, long offset, char x);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Reads the short value from given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where short value will be read from
     * @return the read value
     */
    short getShort(Unsafe unsafe, long address);

    /**
     * Reads the short value from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where short value will be read from
     * @param offset the offset of short field relative to object itself
     * @return the read value
     */
    short getShort(Unsafe unsafe, Object o, long offset);

    /**
     * Reads the short value as volatile from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where short value will be read from
     * @param offset the offset of short field relative to object itself
     * @return the read value
     */
    short getShortVolatile(Unsafe unsafe, Object o, long offset);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Writes the given short value to given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where short value will be written to
     * @param x       the short value to be written
     */
    void putShort(Unsafe unsafe, long address, short x);

    /**
     * Writes the short value to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where short value will be written to
     * @param offset the offset of short field relative to object itself
     * @param x      the short value to be written
     */
    void putShort(Unsafe unsafe, Object o, long offset, short x);

    /**
     * Writes the short value as volatile to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where short value will be written to
     * @param offset the offset of short field relative to object itself
     * @param x      the short value to be written
     */
    void putShortVolatile(Unsafe unsafe, Object o, long offset, short x);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Reads the int value from given address.
     * 
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where int value will be read from
     * @return the read value
     */
    int getInt(Unsafe unsafe, long address);

    /**
     * Reads the int value from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where int value will be read from
     * @param offset the offset of int field relative to object itself
     * @return the read value
     */
    int getInt(Unsafe unsafe, Object o, long offset);

    /**
     * Reads the int value as volatile from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where int value will be read from
     * @param offset the offset of int field relative to object itself
     * @return the read value
     */
    int getIntVolatile(Unsafe unsafe, Object o, long offset);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Writes the given int value to given address.
     * 
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where int value will be written to
     * @param x       the int value to be written
     */
    void putInt(Unsafe unsafe, long address, int x);

    /**
     * Writes the int value to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where int value will be written to
     * @param offset the offset of int field relative to object itself
     * @param x      the int value to be written
     */
    void putInt(Unsafe unsafe, Object o, long offset, int x);

    /**
     * Writes the int value as volatile to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where int value will be written to
     * @param offset the offset of int field relative to object itself
     * @param x      the int value to be written
     */
    void putIntVolatile(Unsafe unsafe, Object o, long offset, int x);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Reads the float value from given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where float value will be read from
     * @return the read value
     */
    float getFloat(Unsafe unsafe, long address);

    /**
     * Reads the float value from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where float value will be read from
     * @param offset the offset of float field relative to object itself
     * @return the read value
     */
    float getFloat(Unsafe unsafe, Object o, long offset);

    /**
     * Reads the float value as volatile from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where float value will be read from
     * @param offset the offset of float field relative to object itself
     * @return the read value
     */
    float getFloatVolatile(Unsafe unsafe, Object o, long offset);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Writes the given float value to given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where float value will be written to
     * @param x       the float value to be written
     */
    void putFloat(Unsafe unsafe, long address, float x);

    /**
     * Writes the float value to given object by its offset.
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where float value will be written to
     * @param offset the offset of float field relative to object itself
     * @param x      the float value to be written
     */
    void putFloat(Unsafe unsafe, Object o, long offset, float x);

    /**
     * Writes the float value as volatile to given object by its offset.
     * 
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where float value will be written to
     * @param offset the offset of float field relative to object itself
     * @param x      the float value to be written
     */
    void putFloatVolatile(Unsafe unsafe, Object o, long offset, float x);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Reads the long value from given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where long value will be read from
     * @return the read value
     */
    long getLong(Unsafe unsafe, long address);

    /**
     * Reads the long value from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where long value will be read from
     * @param offset the offset of long field relative to object itself
     * @return the read value
     */
    long getLong(Unsafe unsafe, Object o, long offset);

    /**
     * Reads the long value as volatile from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where long value will be read from
     * @param offset the offset of long field relative to object itself
     * @return the read value
     */
    long getLongVolatile(Unsafe unsafe, Object o, long offset);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Writes the given long value to given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where long value will be written to
     * @param x       the long value to be written
     */
    void putLong(Unsafe unsafe, long address, long x);

    /**
     * Writes the long value to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where long value will be written to
     * @param offset the offset of long field relative to object itself
     * @param x      the long value to be written
     */
    void putLong(Unsafe unsafe, Object o, long offset, long x);

    /**
     * Writes the long value as volatile to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where long value will be written to
     * @param offset the offset of long field relative to object itself
     * @param x      the long value to be written
     */
    void putLongVolatile(Unsafe unsafe, Object o, long offset, long x);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Reads the double value from given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where double value will be read from
     * @return the read value
     */
    double getDouble(Unsafe unsafe, long address);

    /**
     * Reads the double value from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where double value will be read from
     * @param offset the offset of double field relative to object itself
     * @return the read value
     */
    double getDouble(Unsafe unsafe, Object o, long offset);

    /**
     * Reads the double value as volatile from given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where double value will be read from
     * @param offset the offset of double field relative to object itself
     * @return the read value
     */
    double getDoubleVolatile(Unsafe unsafe, Object o, long offset);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Writes the given double value to given address.
     *
     * @param unsafe  the {@link Unsafe} instance to be used for memory access
     * @param address the address where double value will be written to
     * @param x       the double value to be written
     */
    void putDouble(Unsafe unsafe, long address, double x);

    /**
     * Writes the double value to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where double value will be written to
     * @param offset the offset of double field relative to object itself
     * @param x      the double value to be written
     */
    void putDouble(Unsafe unsafe, Object o, long offset, double x);

    /**
     * Writes the double value as volatile to given object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where double value will be written to
     * @param offset the offset of double field relative to object itself
     * @param x      the double value to be written
     */
    void putDoubleVolatile(Unsafe unsafe, Object o, long offset, double x);

    /////////////////////////////////////////////////////////////////////////
    
    /**
     * 
     * 
     * @param unsafe    the {@link Unsafe} instance to be used for memory access
     * @param address
     * @return
     */
    long getAddress(Unsafe unsafe, long address);
    
    /**
     * 
     * 
     * @param unsafe    the {@link Unsafe} instance to be used for memory access
     * @param address
     * @param x
     */
    void putAddress(Unsafe unsafe, long address, long x);
    
    /////////////////////////////////////////////////////////////////////////

    /**
     * Gets the referenced object from given owner object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the owner object where the referenced object will be read from
     * @param offset the offset of the referenced object field relative to owner object itself
     * @return the retrieved referenced object
     */
    Object getObject(Unsafe unsafe, Object o, long offset);

    /**
     * Gets the referenced object from given owner object as volatile by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the owner object where the referenced object will be read from
     * @param offset the offset of the referenced object field relative to owner object itself
     * @return the retrieved referenced object
     */
    Object getObjectVolatile(Unsafe unsafe, Object o, long offset);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Puts the referenced object to given owner object by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the owner object where the referenced object will be written to
     * @param offset the offset of the referenced object field relative to owner object itself
     * @param x      the referenced object to be written
     */
    void putObject(Unsafe unsafe, Object o, long offset, Object x);

    /**
     * Puts the referenced object to given owner object as volatile by its offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the owner object where the referenced object will be written to
     * @param offset the offset of the referenced object field relative to owner object itself
     * @param x      the referenced object to be written
     */
    void putObjectVolatile(Unsafe unsafe, Object o, long offset, Object x);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Compares and swaps int value to specified value atomically
     * based by given object with given offset
     * if and only if its current value equals to specified expected value.
     *
     * @param unsafe   the {@link Unsafe} instance to be used for memory access
     * @param o        the object where int value will be written to
     * @param offset   the offset of int field relative to object itself
     * @param expected the expected current int value to be set new int value
     * @param x        the int value to be written
     * @return
     */
    boolean compareAndSwapInt(Unsafe unsafe, Object o, long offset, int expected, int x);

    /**
     * Compares and swaps long value to specified value atomically
     * based by given object with given offset
     * if and only if its current value equals to specified expected value.
     *
     * @param unsafe   the {@link Unsafe} instance to be used for memory access
     * @param o        the object where long value will be written to
     * @param offset   the offset of long field relative to object itself
     * @param expected the expected current long value to be set new long value
     * @param x        the long value to be written
     * @return <tt>true</tt> if CAS is successful, <tt>false</tt> otherwise
     */
    boolean compareAndSwapLong(Unsafe unsafe, Object o, long offset, long expected, long x);

    /**
     * Compares and swaps referenced object to specified object atomically
     * based by given owner object at given offset
     * if and only if its current object is the specified object.
     *
     * @param unsafe   the {@link Unsafe} instance to be used for memory access
     * @param o        the owner object where the referenced object will be written to
     * @param offset   the offset of the referenced object field relative to owner object itself
     * @param expected the expected current referenced object to be set new referenced object
     * @param x        the referenced object to be written
     * @return <tt>true</tt> if CAS is successful, <tt>false</tt> otherwise
     */
    boolean compareAndSwapObject(Unsafe unsafe, Object o, long offset, Object expected, Object x);

    /////////////////////////////////////////////////////////////////////////

    /**
     * Puts given int value as ordered to CPU write buffer
     * based by given object at given offset.
     * 
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where int value will be written to
     * @param offset the offset of int field relative to object itself
     * @param x      the int value to be written
     */
    void putOrderedInt(Unsafe unsafe, Object o, long offset, int x);

    /**
     * Puts given long value as ordered to CPU write buffer
     * based by given object at given offset.
     * 
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the object where long value will be written to
     * @param offset the offset of long field relative to object itself
     * @param x      the long value to be written
     */
    void putOrderedLong(Unsafe unsafe, Object o, long offset, long x);

    /**
     * Puts given referenced object as ordered to CPU write buffer
     * based by given owner object at given offset.
     *
     * @param unsafe the {@link Unsafe} instance to be used for memory access
     * @param o      the owner object where the referenced object will be written to
     * @param offset the offset of the referenced object field relative to owner object itself
     * @param x      the referenced object to be written
     */
    void putOrderedObject(Unsafe unsafe, Object o, long offset, Object x);
    
    /**
     * Gets the current int value and adds the delta value to int value atomically
     * based by given object with given offset.
     *
     * @param unsafe   the {@link Unsafe} instance to be used for memory access
     * @param o        the object where int value will be added to
     * @param offset   the offset of int field relative to object itself
     * @param delta    the value to be added to int value
     * @return the current int value before adding delta
     * 
     * @since 1.8
     */
    int getAndAddInt(Unsafe unsafe, Object o, long offset, int delta);
    
    /**
     * Gets the current long value and adds the delta value to long value atomically
     * based by given object with given offset.
     *
     * @param unsafe   the {@link Unsafe} instance to be used for memory access
     * @param o        the object where long value will be added to
     * @param offset   the offset of long field relative to object itself
     * @param delta    the value to be added to long value
     * @return the current long value before adding delta
     * 
     * @since 1.8
     */
    long getAndAddLong(Unsafe unsafe, Object o, long offset, long delta);
    
    /**
     * Gets the current int value and set the new value to int value atomically
     * based by given object with given offset.
     *
     * @param unsafe   the {@link Unsafe} instance to be used for memory access
     * @param o        the object where int value will be set to
     * @param offset   the offset of int field relative to object itself
     * @param newValue the new value to be set to int value
     * @return the current int value before setting new value
     * 
     * @since 1.8
     */
    int getAndSetInt(Unsafe unsafe, Object o, long offset, int newValue);
    
    /**
     * Gets the current long value and set the new value to long value atomically
     * based by given object with given offset.
     *
     * @param unsafe   the {@link Unsafe} instance to be used for memory access
     * @param o        the object where long value will be set to
     * @param offset   the offset of long field relative to object itself
     * @param newValue the new value to be set to long value
     * @return the current long value before setting new value
     * 
     * @since 1.8
     */
    long getAndSetLong(Unsafe unsafe, Object o, long offset, long newValue);
    
    /**
     * Gets the current referenced object at given offset from given owner object 
     * and set the new object as referenced object into owner object at given offset 
     * atomically.
     *
     * @param unsafe   the {@link Unsafe} instance to be used for memory access
     * @param o        the owner object where new object value will be set to
     * @param offset   the offset of referenced object field relative to 
     *                 owner object itself
     * @param newValue the new referenced object to be set into owner object 
     *                 as referenced object
     * @return the current referenced object before setting new object
     * 
     * @since 1.8
     */
    Object getAndSetObject(Unsafe unsafe, Object o, long offset, Object newValue);

}
