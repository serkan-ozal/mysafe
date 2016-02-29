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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.AllocatedMemoryStorage;
import tr.com.serkanozal.mysafe.IllegalMemoryAccessListener;
import tr.com.serkanozal.mysafe.MySafe;
import tr.com.serkanozal.mysafe.UnsafeListener;
import tr.com.serkanozal.mysafe.impl.accessor.UnsafeMemoryAccessor;
import tr.com.serkanozal.mysafe.impl.accessor.UnsafeMemoryAccessorFactory;
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
    private static final Unsafe DEFAULT_UNSAFE;
    private static final UnsafeMemoryAccessor UNSAFE_MEMORY_ACCESSOR;
    private static final UnsafeLock UNSAFE_LOCK;
    private static Set<UnsafeListener> LISTENERS = 
            Collections.newSetFromMap(new ConcurrentHashMap<UnsafeListener, Boolean>());
    private static final AtomicLong ALLOCATED_MEMORY = new AtomicLong(0L);
    private static final int OBJECT_REFERENCE_SIZE;
    
    private static volatile boolean registeredListenerExist = false;
    private static volatile boolean safeModeEnabled = !Boolean.getBoolean("mysafe.disableSafeMode");

    static {
        MySafe.initialize();
        
        DEFAULT_UNSAFE = MySafe.getUnsafe(); 
        
        UNSAFE_MEMORY_ACCESSOR = UnsafeMemoryAccessorFactory.createUnsafeMemoryAccessor(DEFAULT_UNSAFE);
        UNSAFE_LOCK = new UnsafeLock(DEFAULT_UNSAFE);
        OBJECT_REFERENCE_SIZE = DEFAULT_UNSAFE.arrayIndexScale(Object[].class);
        
        String ALLOCATED_MEMORYStorageImplClassName = System.getProperty("mysafe.ALLOCATED_MEMORYStorageImpl");
        if (ALLOCATED_MEMORYStorageImplClassName != null) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends AllocatedMemoryStorage> ALLOCATED_MEMORYStorageImplClass = 
                        (Class<? extends AllocatedMemoryStorage>) ClassLoader.getSystemClassLoader().
                            loadClass(ALLOCATED_MEMORYStorageImplClassName);
                ALLOCATED_MEMORY_STORAGE = ALLOCATED_MEMORYStorageImplClass.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Couldn't create instance of custom 'AllocatedMemoryStorage' implementation: " + 
                        ALLOCATED_MEMORYStorageImplClassName, e);
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
        
        private final Unsafe UNSAFE;
        private final long UNSAFE_STATE_FIELD_OFFSET;
        
        private volatile int unsafeState = 0;

        private UnsafeLock(Unsafe unsafe) {
            try {
                UNSAFE = unsafe;
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
    
    public static long getAllocatedMemorySize() {
        return ALLOCATED_MEMORY.get();
    }
    
    public static long allocateMemory(Unsafe unsafe, long size) {
        if (registeredListenerExist) {
            for (UnsafeListener listener : LISTENERS) {
                listener.beforeAllocateMemory(unsafe, size);
            }
        }    
        long address = unsafe.allocateMemory(size);
        ALLOCATED_MEMORY_STORAGE.put(address, size);
        ALLOCATED_MEMORY.addAndGet(size);
        if (registeredListenerExist) {
            for (UnsafeListener listener : LISTENERS) {
                listener.afterAllocateMemory(unsafe, address, size);
            }
        }    
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Allocated memory at address " + 
                         String.format("0x%016x", address) + " with size " + size);
        }     
        return address; 
    }
    
    public static void freeMemory(Unsafe unsafe, long address) {
        if (registeredListenerExist) {
            for (UnsafeListener listener : LISTENERS) {
                listener.beforeFreeMemory(unsafe, address);
            }
        }    
        long removedSize = ALLOCATED_MEMORY_STORAGE.remove(address);
        if (removedSize != INVALID) {
            if (safeModeEnabled) {
                UNSAFE_LOCK.acquireFreeLock();
            }    
            try {
                unsafe.freeMemory(address);
            } finally {
                if (safeModeEnabled) {
                    UNSAFE_LOCK.acquireFreeLock();
                }     
            }
            ALLOCATED_MEMORY.addAndGet(-removedSize);
            if (registeredListenerExist) {
                for (UnsafeListener listener : LISTENERS) {
                    listener.afterFreeMemory(unsafe, address, removedSize, true);
                }
            }    
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Free memory at address " + String.format("0x%016x", address));
            }    
        } else {
            String msg = "Trying to free unallocated (or out of the record) memory at address " + 
                         String.format("0x%016x", address);
            if (safeModeEnabled) {
                if (registeredListenerExist) {
                    for (UnsafeListener listener : LISTENERS) {
                        listener.afterFreeMemory(unsafe, address, INVALID, false);
                    }
                }    
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(address, INVALID, FREE);
                }
                LOGGER.error(msg); 
                throw new IllegalArgumentException(msg);
            } else {
                LOGGER.warn(msg);
                if (safeModeEnabled) {
                    UNSAFE_LOCK.acquireFreeLock();
                }    
                try {
                    unsafe.freeMemory(address);
                } finally {
                    if (safeModeEnabled) {
                        UNSAFE_LOCK.acquireFreeLock();
                    }    
                }
                if (registeredListenerExist) {
                    for (UnsafeListener listener : LISTENERS) {
                        listener.afterFreeMemory(unsafe, address, INVALID, false);
                    }
                }    
            }
        }
    }
    
    public static long reallocateMemory(Unsafe unsafe, long oldAddress, long newSize) {
        long oldSize = ALLOCATED_MEMORY_STORAGE.remove(oldAddress);
        if (oldSize != INVALID) {
            if (registeredListenerExist) {
                for (UnsafeListener listener : LISTENERS) {
                    listener.beforeReallocateMemory(unsafe, oldAddress, oldSize);
                }
            }    
            long newAddress;
            if (safeModeEnabled) {
                UNSAFE_LOCK.acquireFreeLock();
            }    
            try {
                newAddress = unsafe.reallocateMemory(oldAddress, newSize);
            } finally {
                if (safeModeEnabled) {
                    UNSAFE_LOCK.releaseFreeLock();
                }    
            }
            ALLOCATED_MEMORY_STORAGE.put(newAddress, newSize);
            ALLOCATED_MEMORY.addAndGet(newSize - oldSize);
            if (registeredListenerExist) {
                for (UnsafeListener listener : LISTENERS) {
                    listener.afterReallocateMemory(unsafe, oldAddress, oldSize, newAddress, newSize, true);
                }
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
                if (registeredListenerExist) {
                    for (UnsafeListener listener : LISTENERS) {
                        listener.beforeReallocateMemory(unsafe, oldAddress, INVALID);
                    }
                }    
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(oldAddress, INVALID, REALLOCATE);
                }
                LOGGER.error(msg); 
                throw new IllegalArgumentException(msg);
            } else {
                LOGGER.warn(msg);
                long newAddress;
                if (safeModeEnabled) {
                    UNSAFE_LOCK.acquireFreeLock();
                }    
                try {
                    newAddress = unsafe.reallocateMemory(oldAddress, newSize);
                } finally {
                    if (safeModeEnabled) {
                        UNSAFE_LOCK.releaseFreeLock();
                    }    
                }
                if (registeredListenerExist) {
                    for (UnsafeListener listener : LISTENERS) {
                        listener.afterReallocateMemory(unsafe, oldAddress, INVALID, newAddress, newSize, false);
                    }
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
    
    public static synchronized void registerUnsafeListener(UnsafeListener listener) {
        LISTENERS.add(listener);
        registeredListenerExist = true;
    }
    
    public static synchronized void deregisterUnsafeListener(UnsafeListener listener) {
        LISTENERS.remove(listener);
        registeredListenerExist = !LISTENERS.isEmpty();
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    private static void checkMemoryAccess(long address, long size, 
            IllegalMemoryAccessListener.MemoryAccessType memoryAccessType) {
        if (safeModeEnabled) {
            UNSAFE_LOCK.acquireAccessLock();
            if (!ALLOCATED_MEMORY_STORAGE.contains(address, size)) {
                UNSAFE_LOCK.releaseAccessLock();
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
            UNSAFE_LOCK.acquireAccessLock();
            if (!ALLOCATED_MEMORY_STORAGE.contains(sourceAddress, size)) {
                UNSAFE_LOCK.releaseAccessLock();
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            sourceAddress, size,  READ);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + READ + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", sourceAddress) + " with size " + size);
                
            }
            if (!ALLOCATED_MEMORY_STORAGE.contains(destinationAddress, size)) {
                UNSAFE_LOCK.releaseAccessLock();
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
            UNSAFE_LOCK.releaseAccessLock();
        }    
    }
    
    private static void checkMemoryAccess(Object o, long offset, long size,
            IllegalMemoryAccessListener.MemoryAccessType memoryAccessType) {
        if (safeModeEnabled && o == null) {
            UNSAFE_LOCK.acquireAccessLock();
            if (!ALLOCATED_MEMORY_STORAGE.contains(offset, size)) {
                UNSAFE_LOCK.releaseAccessLock();
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
            UNSAFE_LOCK.acquireAccessLock();
            if (!ALLOCATED_MEMORY_STORAGE.contains(sourceOffset, size)) {
                UNSAFE_LOCK.releaseAccessLock();
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            sourceOffset, size, READ);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + READ + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", sourceOffset) + " with size " + size);
            }
            if (!ALLOCATED_MEMORY_STORAGE.contains(destinationOffset, size)) {
                UNSAFE_LOCK.releaseAccessLock();
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
            UNSAFE_LOCK.releaseAccessLock();
        }
    }

    //////////////////////////////////////////////////////////////////////////
    
    @Deprecated
    public static boolean getBoolean(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getBoolean(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putBoolean(Unsafe unsafe, Object o, int offset, boolean x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putBoolean(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static byte getByte(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getByte(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putByte(Unsafe unsafe, Object o, int offset, byte x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putByte(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    @Deprecated
    public static char getChar(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getChar(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putChar(Unsafe unsafe, Object o, int offset, char x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putChar(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static short getShort(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getShort(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putShort(Unsafe unsafe, Object o, int offset, short x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putShort(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static int getInt(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getInt(unsafe, o, (long)offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putInt(Unsafe unsafe, Object o, int offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putInt(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static float getFloat(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getFloat(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putFloat(Unsafe unsafe, Object o, int offset, float x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putFloat(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static long getLong(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getLong(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putLong(Unsafe unsafe, Object o, int offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putLong(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static double getDouble(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getDouble(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putDouble(Unsafe unsafe, Object o, int offset, double x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putDouble(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static Object getObject(Unsafe unsafe, Object o, int offset) {
        checkMemoryAccess(o, offset, OBJECT_REFERENCE_SIZE, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getObject(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    @Deprecated
    public static void putObject(Unsafe unsafe, Object o, int offset, Object x) {
        checkMemoryAccess(o, offset, OBJECT_REFERENCE_SIZE, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putObject(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    //////////////////////////////////////////////////////////////////////////

    public static boolean getBoolean(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getBoolean(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putBoolean(Unsafe unsafe, Object o, long offset, boolean x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putBoolean(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static byte getByte(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getByte(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putByte(Unsafe unsafe, Object o, long offset, byte x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putByte(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static char getChar(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getChar(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putChar(Unsafe unsafe, Object o, long offset, char x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putChar(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static short getShort(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getShort(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putShort(Unsafe unsafe, Object o, long offset, short x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putShort(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static int getInt(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getInt(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putInt(Unsafe unsafe, Object o, long offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putInt(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static float getFloat(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getFloat(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putFloat(Unsafe unsafe, Object o, long offset, float x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putFloat(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static long getLong(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getLong(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putLong(Unsafe unsafe, Object o, long offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putLong(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static double getDouble(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getDouble(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putDouble(Unsafe unsafe, Object o, long offset, double x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putDouble(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static Object getObject(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, OBJECT_REFERENCE_SIZE, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getObject(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putObject(Unsafe unsafe, Object o, long offset, Object x) {
        checkMemoryAccess(o, offset, OBJECT_REFERENCE_SIZE, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putObject(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static boolean getBooleanVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getBooleanVolatile(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putBooleanVolatile(Unsafe unsafe, Object o, long offset, boolean x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putBooleanVolatile(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static byte getByteVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 1, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getByteVolatile(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putByteVolatile(Unsafe unsafe, Object o, long offset, byte x) {
        checkMemoryAccess(o, offset, 1, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putByteVolatile(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static char getCharVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getCharVolatile(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putCharVolatile(Unsafe unsafe, Object o, long offset, char x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putCharVolatile(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static short getShortVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 2, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getShortVolatile(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putShortVolatile(Unsafe unsafe, Object o, long offset, short x) {
        checkMemoryAccess(o, offset, 2, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putShortVolatile(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static int getIntVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getIntVolatile(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putIntVolatile(Unsafe unsafe, Object o, long offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putIntVolatile(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static float getFloatVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 4, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getFloatVolatile(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putFloatVolatile(Unsafe unsafe, Object o, long offset, float x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putFloatVolatile(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static long getLongVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getLongVolatile(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putLongVolatile(Unsafe unsafe, Object o, long offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putLongVolatile(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static double getDoubleVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, 8, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getDoubleVolatile(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putDoubleVolatile(Unsafe unsafe, Object o, long offset, double x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putDoubleVolatile(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static Object getObjectVolatile(Unsafe unsafe, Object o, long offset) {
        checkMemoryAccess(o, offset, OBJECT_REFERENCE_SIZE, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getObjectVolatile(unsafe, o, offset);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putObjectVolatile(Unsafe unsafe, Object o, long offset, Object x) {
        checkMemoryAccess(o, offset, OBJECT_REFERENCE_SIZE, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putObjectVolatile(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static boolean getBoolean(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 1, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getByte(unsafe, address) == 0 ? false : true;
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putBoolean(Unsafe unsafe, long address, boolean x) {
        checkMemoryAccess(address, 1, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putByte(unsafe, address, x ? (byte) 0x01 : (byte) 0x00);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static byte getByte(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 1, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getByte(unsafe, address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putByte(Unsafe unsafe, long address, byte x) {
        checkMemoryAccess(address, 1, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putByte(unsafe, address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static char getChar(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 2, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getChar(unsafe, address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putChar(Unsafe unsafe, long address, char x) {
        checkMemoryAccess(address, 2, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putChar(unsafe, address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static short getShort(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 2, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getShort(unsafe, address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putShort(Unsafe unsafe, long address, short x) {
        checkMemoryAccess(address, 2, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putShort(unsafe, address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static int getInt(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 4, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getInt(unsafe, address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putInt(Unsafe unsafe, long address, int x) {
        checkMemoryAccess(address, 4, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putInt(unsafe, address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static float getFloat(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 4, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getFloat(unsafe, address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putFloat(Unsafe unsafe, long address, float x) {
        checkMemoryAccess(address, 4, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putFloat(unsafe, address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static long getLong(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 8, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getLong(unsafe, address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putLong(Unsafe unsafe, long address, long x) {
        checkMemoryAccess(address, 8, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putLong(unsafe, address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static double getDouble(Unsafe unsafe, long address) {
        checkMemoryAccess(address, 8, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getDouble(unsafe, address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putDouble(Unsafe unsafe, long address, double x) {
        checkMemoryAccess(address, 8, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putDouble(unsafe, address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static long getAddress(Unsafe unsafe, long address) {
        checkMemoryAccess(address, OBJECT_REFERENCE_SIZE, READ);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getAddress(unsafe, address);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    public static void putAddress(Unsafe unsafe, long address, long x) {
        checkMemoryAccess(address, OBJECT_REFERENCE_SIZE, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putAddress(unsafe, address, x);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    //////////////////////////////////////////////////////////////////////////

    public static void setMemory(Unsafe unsafe, long address, long bytes, byte value) {
        checkMemoryAccess(address, bytes, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.setMemory(unsafe, address, bytes, value);
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
            UNSAFE_MEMORY_ACCESSOR.setMemory(unsafe, o, offset, bytes, value);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void copyMemory(Unsafe unsafe, long srcAddress, long destAddress, long bytes) {
        checkMemoryAccess(srcAddress, destAddress, bytes);
        try {
            UNSAFE_MEMORY_ACCESSOR.copyMemory(unsafe, srcAddress, destAddress, bytes);
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
            UNSAFE_MEMORY_ACCESSOR.copyMemory(unsafe, srcBase, srcOffset, destBase, destOffset, bytes);
        } finally {
            onReturnMemoryAccess();
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static boolean compareAndSwapInt(Unsafe unsafe, Object o, long offset,
                                            int expected, int x) {
        checkMemoryAccess(o, offset, 4, READ_WRITE);
        try {
            return UNSAFE_MEMORY_ACCESSOR.compareAndSwapInt(unsafe, o, offset, expected, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    public static boolean compareAndSwapLong(Unsafe unsafe, Object o, long offset,
                                             long expected, long x) {
        checkMemoryAccess(o, offset, 8, READ_WRITE);
        try {
            return UNSAFE_MEMORY_ACCESSOR.compareAndSwapLong(unsafe, o, offset, expected, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    public static boolean compareAndSwapObject(Unsafe unsafe, Object o, long offset, 
                                               Object expected, Object x) {
        checkMemoryAccess(o, offset, OBJECT_REFERENCE_SIZE, READ_WRITE);
        try {
            return UNSAFE_MEMORY_ACCESSOR.compareAndSwapObject(unsafe, o, offset, expected, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    
    public static void putOrderedInt(Unsafe unsafe, Object o, long offset, int x) {
        checkMemoryAccess(o, offset, 4, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putOrderedInt(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }

    public static void putOrderedLong(Unsafe unsafe, Object o, long offset, long x) {
        checkMemoryAccess(o, offset, 8, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putOrderedLong(unsafe, o, offset, x);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    public static void putOrderedObject(Unsafe unsafe, Object o, long offset, Object x) {
        checkMemoryAccess(o, offset, OBJECT_REFERENCE_SIZE, WRITE);
        try {
            UNSAFE_MEMORY_ACCESSOR.putOrderedObject(unsafe, o, offset, x);
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
            return UNSAFE_MEMORY_ACCESSOR.getAndAddInt(unsafe, o, offset, delta);
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
            return UNSAFE_MEMORY_ACCESSOR.getAndAddLong(unsafe, o, offset, delta);
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
            return UNSAFE_MEMORY_ACCESSOR.getAndSetInt(unsafe, o, offset, newValue);
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
            return UNSAFE_MEMORY_ACCESSOR.getAndSetLong(unsafe, o, offset, newValue);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    /**
     * @since 1.8
     */
    public static Object getAndSetObject(Unsafe unsafe, Object o, long offset, Object newValue) {
        checkMemoryAccess(o, offset, OBJECT_REFERENCE_SIZE, READ_WRITE);
        try {
            return UNSAFE_MEMORY_ACCESSOR.getAndSetObject(unsafe, o, offset, newValue);
        } finally {
            onReturnMemoryAccess(o);
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
}
