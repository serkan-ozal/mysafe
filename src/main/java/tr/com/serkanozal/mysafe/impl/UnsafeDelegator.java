/*
 * Copyright (c) 1986-2015, Serkan OZAL, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tr.com.serkanozal.mysafe.impl;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.AllocatedMemoryStorage;
import tr.com.serkanozal.mysafe.IllegalMemoryAccessListener;
import tr.com.serkanozal.mysafe.UnsafeListener;
import static tr.com.serkanozal.mysafe.AllocatedMemoryStorage.INVALID;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.READ;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.WRITE;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.READ_WRITE;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.FREE;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.REALLOCATE;

public final class UnsafeDelegator {

    private static final Logger LOGGER = Logger.getLogger(UnsafeDelegator.class);
    
    private static final AllocatedMemoryStorage ALLOCATED_MEMORY_STORAGE;
    private static final IllegalMemoryAccessListener ILLEGAL_MEMORY_ACCESS_LISTENER;
    private static Set<UnsafeListener> listeners = 
            Collections.newSetFromMap(new ConcurrentHashMap<UnsafeListener, Boolean>());
    
    private static volatile boolean safeModeEnabled = !Boolean.getBoolean("mysafe.disableSafeMode");
    
    static {
        String allocatedMemoryStorageImplClassName = System.getProperty("mysafe.allocatedMemoryStorageImpl");
        if (allocatedMemoryStorageImplClassName != null) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends AllocatedMemoryStorage> allocatedMemoryStorageImplClass = 
                        (Class<? extends AllocatedMemoryStorage>) ClassLoader.getSystemClassLoader().
                            loadClass(allocatedMemoryStorageImplClassName);
                ALLOCATED_MEMORY_STORAGE = allocatedMemoryStorageImplClass.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Couldn't create instance of custom 'AllocatedMemoryStorage' implementation: " + 
                        allocatedMemoryStorageImplClassName, e);
            }
        } else {
            ALLOCATED_MEMORY_STORAGE = new DefaultAllocatedMemoryStorage();
        }
        
        String illegalMemoryAccessListenerImplClassName = System.getProperty("mysafe.illegalMemoryAccessListenerImpl");
        if (illegalMemoryAccessListenerImplClassName != null) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends IllegalMemoryAccessListener> illegalMemoryAccessListenerImplClass = 
                        (Class<? extends IllegalMemoryAccessListener>) ClassLoader.getSystemClassLoader().
                            loadClass(illegalMemoryAccessListenerImplClassName);
                ILLEGAL_MEMORY_ACCESS_LISTENER = illegalMemoryAccessListenerImplClass.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Couldn't create instance of custom 'IllegalMemoryAccessListener' implementation: " + 
                        illegalMemoryAccessListenerImplClassName, e);
            }
        } else {
            ILLEGAL_MEMORY_ACCESS_LISTENER = null;
        }
    }

    private UnsafeDelegator() {
        throw new UnsupportedOperationException("Not avaiable for instantiation!");
    }
    
    //////////////////////////////////////////////////////////////////////////

    public static boolean isSafeModeEnabled() {
        return safeModeEnabled;
    }
    
    public static void enableSafeMode() {
        safeModeEnabled = true;
    }
    
    public static void disableSafeMode() {
        safeModeEnabled = false;
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static long allocateMemory(Unsafe unsafe, long size) {
        for (UnsafeListener listener : listeners) {
            listener.beforeAllocateMemory(unsafe, size);
        }
        long address = unsafe.allocateMemory(size);
        ALLOCATED_MEMORY_STORAGE.put(address, size);
        for (UnsafeListener listener : listeners) {
            listener.afterAllocateMemory(unsafe, address, size);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Allocated memory at address " + 
                         String.format("0x%016x", address) + " with size " + size);
        }     
        return address; 
    }
    
    public static void freeMemory(Unsafe unsafe, long address) {
        for (UnsafeListener listener : listeners) {
            listener.beforeFreeMemory(unsafe, address);
        }
        long removedSize = ALLOCATED_MEMORY_STORAGE.remove(address);
        if (removedSize != INVALID) {
            unsafe.freeMemory(address);
            for (UnsafeListener listener : listeners) {
                listener.afterFreeMemory(unsafe, address, removedSize, true);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Free memory at address " + String.format("0x%016x", address));
            }    
        } else {
            String msg = "Trying to free unallocated (or out of the record) memory at address " + 
                         String.format("0x%016x", address);
            if (safeModeEnabled) {
                for (UnsafeListener listener : listeners) {
                    listener.afterFreeMemory(unsafe, address, INVALID, false);
                }
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(address, INVALID, FREE);
                }
                LOGGER.error(msg); 
                throw new IllegalArgumentException(msg);
            } else {
                LOGGER.warn(msg);
                unsafe.freeMemory(address);
                for (UnsafeListener listener : listeners) {
                    listener.afterFreeMemory(unsafe, address, INVALID, false);
                }
            }
        }
    }
    
    public static long reallocateMemory(Unsafe unsafe, long oldAddress, long newSize) {
        long oldSize = ALLOCATED_MEMORY_STORAGE.remove(oldAddress);
        if (oldSize != INVALID) {
            for (UnsafeListener listener : listeners) {
                listener.beforeReallocateMemory(unsafe, oldAddress, oldSize);
            }
            long newAddress = unsafe.reallocateMemory(oldAddress, newSize);
            ALLOCATED_MEMORY_STORAGE.put(newAddress, newSize);
            for (UnsafeListener listener : listeners) {
                listener.afterReallocateMemory(unsafe, oldAddress, oldSize, newAddress, newSize, true);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Reallocate memory from address " + 
                             String.format("0x%016x", oldAddress) + " with size of " + oldSize + 
                             " to address " + String.format("0x%016x", newAddress) + " with size of " + newSize);
            }  
            return newAddress;
        }  else {
            String msg = "Trying to reallocate unallocated (or out of the record) memory at address " + 
                         String.format("0x%016x", oldAddress) + " with new size " + newSize;
            if (safeModeEnabled) {
                for (UnsafeListener listener : listeners) {
                    listener.beforeReallocateMemory(unsafe, oldAddress, INVALID);
                }
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(oldAddress, INVALID, REALLOCATE);
                }
                LOGGER.error(msg); 
                throw new IllegalArgumentException(msg);
            } else {
                LOGGER.warn(msg);
                long newAddress = unsafe.reallocateMemory(oldAddress, newSize);
                for (UnsafeListener listener : listeners) {
                    listener.afterReallocateMemory(unsafe, oldAddress, INVALID, newAddress, newSize, false);
                }
                return newAddress;
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static void iterateOnAllocatedMemories(AllocatedMemoryIterator iterator) {
        ALLOCATED_MEMORY_STORAGE.iterate(iterator);     
    }
    
    public static void dumpAllocatedMemories(final PrintStream ps, final Unsafe unsafe) {
        ALLOCATED_MEMORY_STORAGE.iterate(new AllocatedMemoryIterator() {
            @Override
            public void onAllocatedMemory(long address, long size) {
                ps.println("Address : " + String.format("0x%016x", address));
                ps.println("Size    : " + size);
                ps.println("Dump    :");
                dump(ps, unsafe, address, size);
                ps.println();
                ps.print("========================================");
                ps.print("========================================");
                ps.println();
                ps.println();
            }
        });   
    }
    
    private static void dump(PrintStream ps, Unsafe unsafe, long address, long size) {
        ps.print("\t");
        for (int i = 0; i < size; i++) {
            if (i % 16 == 0) {
                ps.print(String.format("[0x%016x]: ", i));
            }
            ps.print(String.format("%02x ", unsafe.getByte(address + i)));
            if ((i + 1) % 16 == 0) {
                ps.println();
                ps.print("\t");
            }
        }  
        if (size % 16 != 0) {
            ps.println();
        }     
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static void registerUnsafeListener(UnsafeListener listener) {
        listeners.add(listener); 
    }
    
    public static void deregisterUnsafeListener(UnsafeListener listener) {
        listeners.remove(listener);
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    private static void checkMemoryAccess(long address, long size, 
            IllegalMemoryAccessListener.MemoryAccessType memoryAccessType) {
        if (safeModeEnabled) {
            if (!ALLOCATED_MEMORY_STORAGE.contains(address, size)) {
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            address, size,  memoryAccessType);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + memoryAccessType + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", address) + " with size " + size);
            }
        }
    }
    
    private static void checkMemoryAccess(Object o, long offset, long size,
            IllegalMemoryAccessListener.MemoryAccessType memoryAccessType) {
        if (safeModeEnabled && o == null) {
            if (!ALLOCATED_MEMORY_STORAGE.contains(offset, size)) {
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            offset, size, memoryAccessType);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + memoryAccessType + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", offset) + " with size " + size);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////
    
    @Deprecated
    public static boolean getBoolean(Unsafe unsafe, Object o, int offset) {
        System.out.println("getBoolean: "+ offset);
        checkMemoryAccess(o, offset, 1, READ);
        return unsafe.getBoolean(o, offset);
    }

    @Deprecated
    public static void putBoolean(Unsafe unsafe, Object o, int offset, boolean x) {
        System.out.println("putBoolean: "+ offset);
        checkMemoryAccess(o, offset, 1, WRITE);
        unsafe.putBoolean(o, offset, x);
    }

    @Deprecated
    public static byte getByte(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 1, READ);
        return unsafe.getByte(o, offset);
    }

    @Deprecated
    public static void putByte(Unsafe unsafe, Object o, int offset, byte x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        unsafe.putByte(o, offset, x);
    }
    
    @Deprecated
    public static char getChar(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 2, READ);
        return unsafe.getChar(o, offset);
    }

    @Deprecated
    public static void putChar(Unsafe unsafe, Object o, int offset, char x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        unsafe.putChar(o, offset, x);
    }

    @Deprecated
    public static short getShort(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 2, READ);
        return unsafe.getShort(o, offset);
    }

    @Deprecated
    public static void putShort(Unsafe unsafe, Object o, int offset, short x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        unsafe.putShort(o, offset, x);
    }

    @Deprecated
    public static int getInt(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 4, READ);
        return unsafe.getInt(o, (long)offset);
    }

    @Deprecated
    public static void putInt(Unsafe unsafe, Object o, int offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        unsafe.putInt(o, offset, x);
    }

    @Deprecated
    public static float getFloat(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 4, READ);
        return unsafe.getFloat(o, offset);
    }

    @Deprecated
    public static void putFloat(Unsafe unsafe, Object o, int offset, float x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        unsafe.putFloat(o, offset, x);
    }

    @Deprecated
    public static long getLong(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 8, READ);
        return unsafe.getLong(o, offset);
    }

    @Deprecated
    public static void putLong(Unsafe unsafe, Object o, int offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        unsafe.putLong(o, offset, x);
    }

    @Deprecated
    public static double getDouble(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 8, READ);
        return unsafe.getDouble(o, offset);
    }

    @Deprecated
    public static void putDouble(Unsafe unsafe, Object o, int offset, double x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        unsafe.putDouble(o, offset, x);
    }

    @Deprecated
    public static Object getObject(Unsafe unsafe, Object o, int offset) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ);
        return unsafe.getObject(o, offset);
    }

    @Deprecated
    public static void putObject(Unsafe unsafe, Object o, int offset, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), WRITE);
        unsafe.putObject(o, offset, x);
    }
    
    //////////////////////////////////////////////////////////////////////////

    public static boolean getBoolean(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        return unsafe.getBoolean(o, offset);
    }
    
    public static void putBoolean(Unsafe unsafe, Object o, long offset, boolean x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        unsafe.putBoolean(o, offset, x);
    }
    
    public static byte getByte(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        return unsafe.getByte(o, offset);
    }
    
    public static void putByte(Unsafe unsafe, Object o, long offset, byte x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        unsafe.putByte(o, offset, x);
    }
    
    public static char getChar(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        return unsafe.getChar(o, offset);
    }
    
    public static void putChar(Unsafe unsafe, Object o, long offset, char x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        unsafe.putChar(o, offset, x);
    }
    
    public static short getShort(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        return unsafe.getShort(o, offset);
    }
    
    public static void putShort(Unsafe unsafe, Object o, long offset, short x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        unsafe.putShort(o, offset, x);
    }
    
    public static int getInt(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        return unsafe.getInt(o, offset);
    }
    
    public static void putInt(Unsafe unsafe, Object o, long offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        unsafe.putInt(o, offset, x);
    }
    
    public static float getFloat(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        return unsafe.getFloat(o, offset);
    }
    
    public static void putFloat(Unsafe unsafe, Object o, long offset, float x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        unsafe.putFloat(o, offset, x);
    }
    
    public static long getLong(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        return unsafe.getLong(o, offset);
    }
    
    public static void putLong(Unsafe unsafe, Object o, long offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        unsafe.putLong(o, offset, x);
    }
    
    public static double getDouble(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        return unsafe.getDouble(o, offset);
    }
    
    public static void putDouble(Unsafe unsafe, Object o, long offset, double x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        unsafe.putDouble(o, offset, x);
    }
    
    public static Object getObject(Unsafe unsafe, Object o, long offset) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ);
        return unsafe.getObject(o, offset);
    }
    
    public static void putObject(Unsafe unsafe, Object o, long offset, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), WRITE);
        unsafe.putObject(o, offset, x);
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static boolean getBooleanVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        return unsafe.getBooleanVolatile(o, offset);
    }
    
    public static void putBooleanVolatile(Unsafe unsafe, Object o, long offset, boolean x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        unsafe.putBooleanVolatile(o, offset, x);
    }
    
    public static byte getByteVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        return unsafe.getByteVolatile(o, offset);
    }
    
    public static void putByteVolatile(Unsafe unsafe, Object o, long offset, byte x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        unsafe.putByteVolatile(o, offset, x);
    }
    
    public static char getCharVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        return unsafe.getCharVolatile(o, offset);
    }
    
    public static void putCharVolatile(Unsafe unsafe, Object o, long offset, char x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        unsafe.putCharVolatile(o, offset, x);
    }
    
    public static short getShortVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        return unsafe.getShortVolatile(o, offset);
    }
    
    public static void putShortVolatile(Unsafe unsafe, Object o, long offset, short x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        unsafe.putShortVolatile(o, offset, x);
    }
    
    public static int getIntVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        return unsafe.getIntVolatile(o, offset);
    }
    
    public static void putIntVolatile(Unsafe unsafe, Object o, long offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        unsafe.putIntVolatile(o, offset, x);
    }
    
    public static float getFloatVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        return unsafe.getFloatVolatile(o, offset);
    }
    
    public static void putFloatVolatile(Unsafe unsafe, Object o, long offset, float x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        unsafe.putFloatVolatile(o, offset, x);
    }
    
    public static long getLongVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        return unsafe.getLongVolatile(o, offset);
    }
    
    public static void putLongVolatile(Unsafe unsafe, Object o, long offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        unsafe.putLongVolatile(o, offset, x);
    }
    
    public static double getDoubleVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        return unsafe.getDoubleVolatile(o, offset);
    }
    
    public static void putDoubleVolatile(Unsafe unsafe, Object o, long offset, double x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        unsafe.putDoubleVolatile(o, offset, x);
    }
    
    public static Object getObjectVolatile(Unsafe unsafe, Object o, long offset) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ);
        return unsafe.getObjectVolatile(o, offset);
    }
    
    public static void putObjectVolatile(Unsafe unsafe, Object o, long offset, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), WRITE);
        unsafe.putObjectVolatile(o, offset, x);
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static boolean getBoolean(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 1, READ);
        return unsafe.getByte(address) == 0 ? false : true;
    }
    
    public static void putBoolean(Unsafe unsafe, long address, boolean x) {
        checkMemoryAccess(address, 1, WRITE);
        unsafe.putByte(address, x ? (byte) 0x01 : (byte) 0x00);
    }
    
    public static byte getByte(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 1, READ);
        return unsafe.getByte(address);
    }
    
    public static void putByte(Unsafe unsafe, long address, byte x) {
        checkMemoryAccess(address, 1, WRITE);
        unsafe.putByte(address, x);
    }
    
    public static char getChar(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 2, READ);
        return unsafe.getChar(address);
    }
    
    public static void putChar(Unsafe unsafe, long address, char x) {
        checkMemoryAccess(address, 2, WRITE);
        unsafe.putChar(address, x);
    }
    
    public static short getShort(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 2, READ);
        return unsafe.getShort(address);
    }
    
    public static void putShort(Unsafe unsafe, long address, short x) {
        checkMemoryAccess(address, 2, WRITE);
        unsafe.putShort(address, x);
    }
    
    public static int getInt(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 4, READ);
        return unsafe.getInt(address);
    }
    
    public static void putInt(Unsafe unsafe, long address, int x) {
        checkMemoryAccess(address, 4, WRITE);
        unsafe.putInt(address, x);
    }
    
    public static float getFloat(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 4, READ);
        return unsafe.getFloat(address);
    }
    
    public static void putFloat(Unsafe unsafe, long address, float x) {
        checkMemoryAccess(address, 4, WRITE);
        unsafe.putFloat(address, x);
    }
    
    public static long getLong(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 8, READ);
        return unsafe.getLong(address);
    }
    
    public static void putLong(Unsafe unsafe, long address, long x) {
        checkMemoryAccess(address, 8, WRITE);
        unsafe.putLong(address, x);
    }
    
    public static double getDouble(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 8, READ);
        return unsafe.getDouble(address);
    }
    
    public static void putDouble(Unsafe unsafe, long address, double x) {
        checkMemoryAccess(address, 8, WRITE);
        unsafe.putDouble(address, x);
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static long getAddress(Unsafe unsafe, long address) {
        // TODO Consider compressed-references
        checkMemoryAccess(address, unsafe.addressSize(), READ);
        return unsafe.getAddress(address);
    }
    
    public static void putAddress(Unsafe unsafe, long address, long x) {
        // TODO Consider compressed-references
        checkMemoryAccess(address, unsafe.addressSize(), WRITE);
        unsafe.putAddress(address, x);
    }
    
    //////////////////////////////////////////////////////////////////////////

    public static void setMemory(Unsafe unsafe, long address, long bytes, byte value) {
        checkMemoryAccess(address, bytes, WRITE);
        unsafe.setMemory(address, bytes, value);
    }
    
    /**
     * @since 1.7
     */
    public static void setMemory(Unsafe unsafe, Object o, long offset, long bytes, byte value) {
        checkMemoryAccess(o, offset, bytes, WRITE);
        unsafe.setMemory(o, offset, bytes, value);
    }
    
    public static void copyMemory(Unsafe unsafe, long srcAddress, long destAddress, long bytes) {
        checkMemoryAccess(srcAddress, bytes, READ);
        checkMemoryAccess(destAddress, bytes, WRITE);
        unsafe.copyMemory(srcAddress, destAddress, bytes);
    }
    
    /**
     * @since 1.7
     */
    public static void copyMemory(Unsafe unsafe, Object srcBase, long srcOffset,
                                  Object destBase, long destOffset, long bytes) {
        checkMemoryAccess(srcBase, srcOffset, bytes, READ);
        checkMemoryAccess(destBase, destOffset, bytes, WRITE);
        unsafe.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static boolean compareAndSwapInt(Unsafe unsafe, Object o, long offset,
                                            int expected, int x) {
        checkMemoryAccess(o, offset, 4, READ_WRITE);
        return unsafe.compareAndSwapInt(o, offset, expected, x);
    }

    public static boolean compareAndSwapLong(Unsafe unsafe, Object o, long offset,
                                             long expected, long x) {
        checkMemoryAccess(o, offset, 8, READ_WRITE);
        return unsafe.compareAndSwapLong(o, offset, expected, x);
    }

    public static boolean compareAndSwapObject(Unsafe unsafe, Object o, long offset, 
                                               Object expected, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ_WRITE);
        return unsafe.compareAndSwapObject(o, offset, expected, x);
    }

    //////////////////////////////////////////////////////////////////////////
    
    public static void putOrderedInt(Unsafe unsafe, Object o, long offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        unsafe.putOrderedInt(o, offset, x);
    }

    public static void putOrderedLong(Unsafe unsafe, Object o, long offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        unsafe.putOrderedLong(o, offset, x);
    }
    
    public static void putOrderedObject(Unsafe unsafe, Object o, long offset, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), WRITE);
        unsafe.putOrderedObject(o, offset, x);
    }

    //////////////////////////////////////////////////////////////////////////
    
    /**
     * @since 1.8
     */
    public static int getAndAddInt(Unsafe unsafe, Object o, long offset, int delta) {
        checkMemoryAccess(o, offset, 4, READ_WRITE);
        return unsafe.getAndAddInt(o, offset, delta);
    }
    
    /**
     * @since 1.8
     */
    public static long getAndAddLong(Unsafe unsafe, Object o, long offset, long delta) {
        checkMemoryAccess(o, offset, 8, READ_WRITE);
        return unsafe.getAndAddLong(o, offset, delta);
    }
    
    /**
     * @since 1.8
     */
    public static int getAndSetInt(Unsafe unsafe, Object o, long offset, int newValue) {
        checkMemoryAccess(o, offset, 4, READ_WRITE);
        return unsafe.getAndSetInt(o, offset, newValue);
    }
    
    /**
     * @since 1.8
     */
    public static long getAndSetLong(Unsafe unsafe, Object o, long offset, long newValue) {
        checkMemoryAccess(o, offset, 8, READ_WRITE);
        return unsafe.getAndSetLong(o, offset, newValue);
    }
    
    /**
     * @since 1.8
     */
    public static Object getAndSetObject(Unsafe unsafe, Object o, long offset, Object newValue) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ_WRITE);
        return unsafe.getAndSetObject(o, offset, newValue);
    }
    
    //////////////////////////////////////////////////////////////////////////
    
}
