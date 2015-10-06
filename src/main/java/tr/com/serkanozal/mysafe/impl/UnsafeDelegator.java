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
import java.lang.reflect.Field;
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
    private static final UnsafeLock unsafeLock = new UnsafeLock();
    
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
    
    private static class UnsafeLock {
        
        private static final Unsafe UNSAFE;
        private static final long UNSAFE_STATE_FIELD_OFFSET;
        
        private volatile int unsafeState = 0;

        static {
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                UNSAFE = (Unsafe) field.get(null);
                UNSAFE_STATE_FIELD_OFFSET = UNSAFE.objectFieldOffset(UnsafeLock.class.getDeclaredField("unsafeState"));
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
        
        private void acquireFreeLock() {
            final int minValue = Integer.MIN_VALUE;
            for (;;) {
                int currentState = unsafeState;
                if (currentState <= 0 && currentState != minValue) {
                    if (UNSAFE.compareAndSwapInt(this, UNSAFE_STATE_FIELD_OFFSET, currentState, currentState - 1)) {
                        break;
                    }
                }
            }
        }
        
        private void acquireAccessLock() {
            final int maxValue = Integer.MAX_VALUE;
            for (;;) {
                int currentState = unsafeState;
                if (currentState >= 0 && currentState != maxValue) {
                    if (UNSAFE.compareAndSwapInt(this, UNSAFE_STATE_FIELD_OFFSET, currentState, currentState + 1)) {
                        break;
                    }
                }
            }
        }
        
        private void releaseFreeLock() {
            for (;;) {
                int currentState = unsafeState;
                if (UNSAFE.compareAndSwapInt(this, UNSAFE_STATE_FIELD_OFFSET, currentState, currentState + 1)) {
                    break;
                }
            }
        }
        
        private void releaseAccessLock() {
            for (;;) {
                int currentState = unsafeState;
                if (UNSAFE.compareAndSwapInt(this, UNSAFE_STATE_FIELD_OFFSET, currentState, currentState - 1)) {
                    break;
                }
            }
        }
        
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
            unsafeLock.acquireFreeLock();
            try {
                unsafe.freeMemory(address);
            } finally {
                unsafeLock.acquireFreeLock();
            }
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
                unsafeLock.acquireFreeLock();
                try {
                    unsafe.freeMemory(address);
                } finally {
                    unsafeLock.acquireFreeLock();
                }
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
            long newAddress;
            unsafeLock.acquireFreeLock();
            try {
                newAddress = unsafe.reallocateMemory(oldAddress, newSize);
            } finally {
                unsafeLock.releaseFreeLock();
            }
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
                long newAddress;
                unsafeLock.acquireFreeLock();
                try {
                    newAddress = unsafe.reallocateMemory(oldAddress, newSize);
                } finally {
                    unsafeLock.releaseFreeLock();
                }
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
            unsafeLock.acquireAccessLock();
            if (!ALLOCATED_MEMORY_STORAGE.contains(address, size)) {
                unsafeLock.releaseAccessLock();
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
    
    private static void checkMemoryAccess(long sourceAddress, long destinationAddress, long size) {
        if (safeModeEnabled) {
            unsafeLock.acquireAccessLock();
            if (!ALLOCATED_MEMORY_STORAGE.contains(sourceAddress, size)) {
                unsafeLock.releaseAccessLock();
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            sourceAddress, size,  READ);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + READ + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", sourceAddress) + " with size " + size);
                
            }
            if (!ALLOCATED_MEMORY_STORAGE.contains(destinationAddress, size)) {
                unsafeLock.releaseAccessLock();
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            destinationAddress, size,  WRITE);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + WRITE + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", destinationAddress) + " with size " + size);
            }
        }
    }
    
    private static void onReturnMemoryAccess() {
        if (safeModeEnabled) {
            unsafeLock.releaseAccessLock();
        }    
    }
    
    private static void checkMemoryAccess(Object o, long offset, long size,
            IllegalMemoryAccessListener.MemoryAccessType memoryAccessType) {
        if (safeModeEnabled && o == null) {
            unsafeLock.acquireAccessLock();
            if (!ALLOCATED_MEMORY_STORAGE.contains(offset, size)) {
                unsafeLock.releaseAccessLock();
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
    
    private static void checkMemoryAccess(Object sourceObject, long sourceOffset, 
            Object destinationObject, long destinationOffset, long size) {
        if (safeModeEnabled && sourceObject == null && destinationObject == null) {
            unsafeLock.acquireAccessLock();
            if (!ALLOCATED_MEMORY_STORAGE.contains(sourceOffset, size)) {
                unsafeLock.releaseAccessLock();
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            sourceOffset, size, READ);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + READ + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", sourceOffset) + " with size " + size);
            }
            if (!ALLOCATED_MEMORY_STORAGE.contains(destinationOffset, size)) {
                unsafeLock.releaseAccessLock();
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            destinationOffset, size, WRITE);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + WRITE + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", destinationOffset) + " with size " + size);
            }
        }
    }
    
    private static void onReturnMemoryAccess(Object o) {
        if (safeModeEnabled && o == null) {
            unsafeLock.releaseAccessLock();
        }
    }

    //////////////////////////////////////////////////////////////////////////
    
    @Deprecated
    public static boolean getBoolean(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return unsafe.getBoolean(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putBoolean(Unsafe unsafe, Object o, int offset, boolean x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            unsafe.putBoolean(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static byte getByte(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return unsafe.getByte(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putByte(Unsafe unsafe, Object o, int offset, byte x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            unsafe.putByte(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    @Deprecated
    public static char getChar(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return unsafe.getChar(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putChar(Unsafe unsafe, Object o, int offset, char x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            unsafe.putChar(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static short getShort(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return unsafe.getShort(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putShort(Unsafe unsafe, Object o, int offset, short x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            unsafe.putShort(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static int getInt(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return unsafe.getInt(o, (long)offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putInt(Unsafe unsafe, Object o, int offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            unsafe.putInt(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static float getFloat(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return unsafe.getFloat(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putFloat(Unsafe unsafe, Object o, int offset, float x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            unsafe.putFloat(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static long getLong(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return unsafe.getLong(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putLong(Unsafe unsafe, Object o, int offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            unsafe.putLong(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static double getDouble(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return unsafe.getDouble(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putDouble(Unsafe unsafe, Object o, int offset, double x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            unsafe.putDouble(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static Object getObject(Unsafe unsafe, Object o, int offset) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ);
        try {
            return unsafe.getObject(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putObject(Unsafe unsafe, Object o, int offset, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), WRITE);
        try {
            unsafe.putObject(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    //////////////////////////////////////////////////////////////////////////

    public static boolean getBoolean(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return unsafe.getBoolean(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putBoolean(Unsafe unsafe, Object o, long offset, boolean x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            unsafe.putBoolean(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static byte getByte(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return unsafe.getByte(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putByte(Unsafe unsafe, Object o, long offset, byte x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            unsafe.putByte(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static char getChar(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return unsafe.getChar(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putChar(Unsafe unsafe, Object o, long offset, char x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            unsafe.putChar(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static short getShort(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return unsafe.getShort(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putShort(Unsafe unsafe, Object o, long offset, short x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            unsafe.putShort(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static int getInt(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return unsafe.getInt(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putInt(Unsafe unsafe, Object o, long offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            unsafe.putInt(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static float getFloat(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return unsafe.getFloat(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putFloat(Unsafe unsafe, Object o, long offset, float x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            unsafe.putFloat(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static long getLong(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return unsafe.getLong(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putLong(Unsafe unsafe, Object o, long offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            unsafe.putLong(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static double getDouble(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return unsafe.getDouble(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putDouble(Unsafe unsafe, Object o, long offset, double x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            unsafe.putDouble(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static Object getObject(Unsafe unsafe, Object o, long offset) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ);
        try {
            return unsafe.getObject(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putObject(Unsafe unsafe, Object o, long offset, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), WRITE);
        try {
            unsafe.putObject(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static boolean getBooleanVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return unsafe.getBooleanVolatile(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putBooleanVolatile(Unsafe unsafe, Object o, long offset, boolean x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            unsafe.putBooleanVolatile(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static byte getByteVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return unsafe.getByteVolatile(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putByteVolatile(Unsafe unsafe, Object o, long offset, byte x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            unsafe.putByteVolatile(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static char getCharVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return unsafe.getCharVolatile(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putCharVolatile(Unsafe unsafe, Object o, long offset, char x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            unsafe.putCharVolatile(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static short getShortVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return unsafe.getShortVolatile(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putShortVolatile(Unsafe unsafe, Object o, long offset, short x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            unsafe.putShortVolatile(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static int getIntVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return unsafe.getIntVolatile(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putIntVolatile(Unsafe unsafe, Object o, long offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            unsafe.putIntVolatile(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static float getFloatVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return unsafe.getFloatVolatile(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putFloatVolatile(Unsafe unsafe, Object o, long offset, float x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            unsafe.putFloatVolatile(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static long getLongVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return unsafe.getLongVolatile(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putLongVolatile(Unsafe unsafe, Object o, long offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            unsafe.putLongVolatile(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static double getDoubleVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return unsafe.getDoubleVolatile(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putDoubleVolatile(Unsafe unsafe, Object o, long offset, double x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            unsafe.putDoubleVolatile(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static Object getObjectVolatile(Unsafe unsafe, Object o, long offset) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ);
        try {
            return unsafe.getObjectVolatile(o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putObjectVolatile(Unsafe unsafe, Object o, long offset, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), WRITE);
        try {
            unsafe.putObjectVolatile(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static boolean getBoolean(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 1, READ);
        try {
            return unsafe.getByte(address) == 0 ? false : true;
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putBoolean(Unsafe unsafe, long address, boolean x) {
        checkMemoryAccess(address, 1, WRITE);
        try {
            unsafe.putByte(address, x ? (byte) 0x01 : (byte) 0x00);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static byte getByte(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 1, READ);
        try {
            return unsafe.getByte(address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putByte(Unsafe unsafe, long address, byte x) {
        checkMemoryAccess(address, 1, WRITE);
        try {
            unsafe.putByte(address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static char getChar(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 2, READ);
        try {
            return unsafe.getChar(address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putChar(Unsafe unsafe, long address, char x) {
        checkMemoryAccess(address, 2, WRITE);
        try {
            unsafe.putChar(address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static short getShort(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 2, READ);
        try {
            return unsafe.getShort(address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putShort(Unsafe unsafe, long address, short x) {
        checkMemoryAccess(address, 2, WRITE);
        try {
            unsafe.putShort(address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static int getInt(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 4, READ);
        try {
            return unsafe.getInt(address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putInt(Unsafe unsafe, long address, int x) {
        checkMemoryAccess(address, 4, WRITE);
        try {
            unsafe.putInt(address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static float getFloat(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 4, READ);
        try {
            return unsafe.getFloat(address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putFloat(Unsafe unsafe, long address, float x) {
        checkMemoryAccess(address, 4, WRITE);
        try {
            unsafe.putFloat(address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static long getLong(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 8, READ);
        try {
            return unsafe.getLong(address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putLong(Unsafe unsafe, long address, long x) {
        checkMemoryAccess(address, 8, WRITE);
        try {
            unsafe.putLong(address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static double getDouble(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 8, READ);
        try {
            return unsafe.getDouble(address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putDouble(Unsafe unsafe, long address, double x) {
        checkMemoryAccess(address, 8, WRITE);
        try {
            unsafe.putDouble(address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static long getAddress(Unsafe unsafe, long address) {
        // TODO Consider compressed-references
        checkMemoryAccess(address, unsafe.addressSize(), READ);
        try {
            return unsafe.getAddress(address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putAddress(Unsafe unsafe, long address, long x) {
        // TODO Consider compressed-references
        checkMemoryAccess(address, unsafe.addressSize(), WRITE);
        try {
            unsafe.putAddress(address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    //////////////////////////////////////////////////////////////////////////

    public static void setMemory(Unsafe unsafe, long address, long bytes, byte value) {
        checkMemoryAccess(address, bytes, WRITE);
        try {
            unsafe.setMemory(address, bytes, value);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    /**
     * @since 1.7
     */
    public static void setMemory(Unsafe unsafe, Object o, long offset, long bytes, byte value) {
        checkMemoryAccess(o, offset, bytes, WRITE);
        try {
            unsafe.setMemory(o, offset, bytes, value);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void copyMemory(Unsafe unsafe, long srcAddress, long destAddress, long bytes) {
        checkMemoryAccess(srcAddress, destAddress, bytes);
        try {
            unsafe.copyMemory(srcAddress, destAddress, bytes);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    /**
     * @since 1.7
     */
    public static void copyMemory(Unsafe unsafe, Object srcBase, long srcOffset,
                                  Object destBase, long destOffset, long bytes) {
        checkMemoryAccess(srcBase, srcOffset, destBase, destOffset, bytes);
        try {
            unsafe.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static boolean compareAndSwapInt(Unsafe unsafe, Object o, long offset,
                                            int expected, int x) {
        checkMemoryAccess(o, offset, 4, READ_WRITE);
        try {
            return unsafe.compareAndSwapInt(o, offset, expected, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    public static boolean compareAndSwapLong(Unsafe unsafe, Object o, long offset,
                                             long expected, long x) {
        checkMemoryAccess(o, offset, 8, READ_WRITE);
        try {
            return unsafe.compareAndSwapLong(o, offset, expected, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    public static boolean compareAndSwapObject(Unsafe unsafe, Object o, long offset, 
                                               Object expected, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ_WRITE);
        try {
            return unsafe.compareAndSwapObject(o, offset, expected, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    
    public static void putOrderedInt(Unsafe unsafe, Object o, long offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            unsafe.putOrderedInt(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    public static void putOrderedLong(Unsafe unsafe, Object o, long offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            unsafe.putOrderedLong(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putOrderedObject(Unsafe unsafe, Object o, long offset, Object x) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), WRITE);
        try {
            unsafe.putOrderedObject(o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    
    /**
     * @since 1.8
     */
    public static int getAndAddInt(Unsafe unsafe, Object o, long offset, int delta) {
        checkMemoryAccess(o, offset, 4, READ_WRITE);
        try {
            return unsafe.getAndAddInt(o, offset, delta);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    /**
     * @since 1.8
     */
    public static long getAndAddLong(Unsafe unsafe, Object o, long offset, long delta) {
        checkMemoryAccess(o, offset, 8, READ_WRITE);
        try {
            return unsafe.getAndAddLong(o, offset, delta);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    /**
     * @since 1.8
     */
    public static int getAndSetInt(Unsafe unsafe, Object o, long offset, int newValue) {
        checkMemoryAccess(o, offset, 4, READ_WRITE);
        try {
            return unsafe.getAndSetInt(o, offset, newValue);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    /**
     * @since 1.8
     */
    public static long getAndSetLong(Unsafe unsafe, Object o, long offset, long newValue) {
        checkMemoryAccess(o, offset, 8, READ_WRITE);
        try {
            return unsafe.getAndSetLong(o, offset, newValue);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    /**
     * @since 1.8
     */
    public static Object getAndSetObject(Unsafe unsafe, Object o, long offset, Object newValue) {
        // TODO Consider compressed-references
        checkMemoryAccess(o, offset, unsafe.addressSize(), READ_WRITE);
        try {
            return unsafe.getAndSetObject(o, offset, newValue);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
}
