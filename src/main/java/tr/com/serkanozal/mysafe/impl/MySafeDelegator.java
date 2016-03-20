/*
 * Copyright (c) 1986-2016, Serkan OZAL, All Rights Reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.AllocatedMemoryStorage;
import tr.com.serkanozal.mysafe.IllegalMemoryAccessListener;
import tr.com.serkanozal.mysafe.MySafe;
import tr.com.serkanozal.mysafe.MemoryListener;
import tr.com.serkanozal.mysafe.ParametricThreadLocalMemoryUsageDecider;
import tr.com.serkanozal.mysafe.ThreadLocalMemoryUsageDecider;
import tr.com.serkanozal.mysafe.impl.accessor.UnsafeMemoryAccessor;
import tr.com.serkanozal.mysafe.impl.accessor.UnsafeMemoryAccessorFactory;
import tr.com.serkanozal.mysafe.impl.callerinfo.CallerInfo;
import tr.com.serkanozal.mysafe.impl.callerinfo.CallerInfo.CallerInfoEntry;
import tr.com.serkanozal.mysafe.impl.callerinfo.CallerInfoStorage;
import tr.com.serkanozal.mysafe.impl.callerinfo.DefaultCallerInfoStorage;
import tr.com.serkanozal.mysafe.impl.callerinfo.ThreadLocalAwareCallerInfoStorage;
import tr.com.serkanozal.mysafe.impl.callerinfo.ThreadLocalDefaultCallerInfoStorage;
import tr.com.serkanozal.mysafe.impl.instrument.CallerInfoInjector;
import tr.com.serkanozal.mysafe.impl.storage.DefaultAllocatedMemoryStorage;
import tr.com.serkanozal.mysafe.impl.storage.NavigatableAllocatedMemoryStorage;
import tr.com.serkanozal.mysafe.impl.storage.ThreadLocalAwareAllocatedMemoryStorage;
import tr.com.serkanozal.mysafe.impl.storage.ThreadLocalDefaultAllocatedMemoryStorage;
import tr.com.serkanozal.mysafe.impl.storage.ThreadLocalNavigatableAllocatedMemoryStorage;
import static tr.com.serkanozal.mysafe.AllocatedMemoryStorage.INVALID;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.READ;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.WRITE;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.READ_WRITE;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.FREE;
import static tr.com.serkanozal.mysafe.IllegalMemoryAccessListener.MemoryAccessType.REALLOCATE;

public final class MySafeDelegator {

    private static final Logger LOGGER = Logger.getLogger(MySafeDelegator.class);

    private static final AllocatedMemoryStorage ALLOCATED_MEMORY_STORAGE;
    private static final IllegalMemoryAccessListener ILLEGAL_MEMORY_ACCESS_LISTENER;
    private static final Unsafe DEFAULT_UNSAFE;
    private static final UnsafeMemoryAccessor UNSAFE_MEMORY_ACCESSOR;
    private static final MemoryAccessLock MEMORY_ACCESS_LOCK;
    private static Set<MemoryListener> LISTENERS = 
            Collections.newSetFromMap(new ConcurrentHashMap<MemoryListener, Boolean>());
    private static final CallerInfoStorage CALLER_INFO_STORAGE;
    private static final ThreadLocal<ThreadLocalCallerInfo> THREAD_LOCAL_CALLER_INFO_MAP =
            new ThreadLocal<ThreadLocalCallerInfo>() {
                protected ThreadLocalCallerInfo initialValue() {
                    return new ThreadLocalCallerInfo();
                };
            };
    private static final CallerInfoInjector CALLER_INFO_INJECTOR = new CallerInfoInjector();
    private static final AtomicLong ALLOCATED_MEMORY = new AtomicLong(0L);
    private static final int OBJECT_REFERENCE_SIZE;
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);
    
    private static final boolean SAFE_MEMORY_MANAGEMENT_MODE_ENABLED = 
            Boolean.getBoolean("mysafe.enableSafeMemoryManagementMode");
    private static final boolean SAFE_MEMORY_ACCESS_MODE_ENABLED;
    private static final boolean CALLER_INFO_MONITORING_MODE_ENABLED =
            Boolean.getBoolean("mysafe.enableCallerInfoMonitoringMode");
    private static final boolean THREAD_LOCAL_MEMORY_USAGE_PATTERN_EXIST = 
            Boolean.getBoolean("mysafe.threadLocalMemoryUsagePatternExist");
    private static volatile boolean REGISTERED_LISTENER_EXIST = false;
   
    static {
        MySafe.initialize();

        boolean safeMemoryAccessModeEnabled = Boolean.getBoolean("mysafe.enableSafeMemoryAccessMode");
        if (Boolean.getBoolean("mysafe.useCustomMemoryManagement")) {
            if (safeMemoryAccessModeEnabled) {
                LOGGER.warn("`Custom Memory Management` and `Safe Memory Access Mode` features cannot be used together. " + 
                            "Since `Custom Memory Management` feature is enabled, " + 
                            "`Safe Memory Access Mode` feature is being skipped ...");
            }
            SAFE_MEMORY_ACCESS_MODE_ENABLED = false;
        } else {
            SAFE_MEMORY_ACCESS_MODE_ENABLED = safeMemoryAccessModeEnabled;
        }

        DEFAULT_UNSAFE = MySafe.getUnsafe(); 
        
        UNSAFE_MEMORY_ACCESSOR = UnsafeMemoryAccessorFactory.createUnsafeMemoryAccessor(DEFAULT_UNSAFE);
        OBJECT_REFERENCE_SIZE = DEFAULT_UNSAFE.arrayIndexScale(Object[].class);
        
        boolean concurrentMemoryAccessCheckEnabled;
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED) {
            concurrentMemoryAccessCheckEnabled = Boolean.getBoolean("mysafe.enableConcurrentMemoryAccessCheck");
        } else {
            concurrentMemoryAccessCheckEnabled = false;
        }
        if (concurrentMemoryAccessCheckEnabled) {
            MEMORY_ACCESS_LOCK = new MemoryAccessLock(DEFAULT_UNSAFE);
        } else {
            MEMORY_ACCESS_LOCK = null;
        }
        
        ThreadLocalMemoryUsageDecider threadLocalMemoryUsageDecider = null;
        String threadLocalMemoryUsageDeciderConfig = System.getProperty("mysafe.threadLocalMemoryUsageDeciderImpl");
        if (threadLocalMemoryUsageDeciderConfig != null) {
            String[] threadLocalMemoryUsageDeciderConfigParts = threadLocalMemoryUsageDeciderConfig.split(":"); 
            String threadLocalMemoryUsageDeciderImplClassName = threadLocalMemoryUsageDeciderConfigParts[0];
            try {
                @SuppressWarnings("unchecked")
                Class<? extends ThreadLocalMemoryUsageDecider> threadLocalMemoryUsageDeciderImplClass = 
                        (Class<? extends ThreadLocalMemoryUsageDecider>) ClassLoader.getSystemClassLoader().
                            loadClass(threadLocalMemoryUsageDeciderImplClassName);
                threadLocalMemoryUsageDecider = threadLocalMemoryUsageDeciderImplClass.newInstance();
                if (threadLocalMemoryUsageDecider instanceof ParametricThreadLocalMemoryUsageDecider) {
                    String[] params = new String[threadLocalMemoryUsageDeciderConfigParts.length - 1];
                    System.arraycopy(threadLocalMemoryUsageDeciderConfigParts, 1, 
                                     params, 0, 
                                     params.length);
                    ((ParametricThreadLocalMemoryUsageDecider) threadLocalMemoryUsageDecider).initWithParameters(params);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Couldn't create instance of custom 'ThreadLocalMemoryUsageDecider' implementation: " + 
                        threadLocalMemoryUsageDeciderImplClassName, e);
            }
        }
        
        String allocatedMemoryStorageImplClassName = System.getProperty("mysafe.allocatedMemoryStorageImpl");
        if (allocatedMemoryStorageImplClassName != null) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends AllocatedMemoryStorage> ALLOCATED_MEMORYStorageImplClass = 
                        (Class<? extends AllocatedMemoryStorage>) ClassLoader.getSystemClassLoader().
                            loadClass(allocatedMemoryStorageImplClassName);
                ALLOCATED_MEMORY_STORAGE = ALLOCATED_MEMORYStorageImplClass.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Couldn't create instance of custom 'AllocatedMemoryStorage' implementation: " + 
                                allocatedMemoryStorageImplClassName, e);
            }
        } else {
            if (THREAD_LOCAL_MEMORY_USAGE_PATTERN_EXIST) {
                AllocatedMemoryStorage threadLocalAllocatedMemoryStorage = null;
                if (safeMemoryAccessModeEnabled) {
                    threadLocalAllocatedMemoryStorage = 
                            new ThreadLocalNavigatableAllocatedMemoryStorage(DEFAULT_UNSAFE, SCHEDULER);
                } else {
                    threadLocalAllocatedMemoryStorage = 
                            new ThreadLocalDefaultAllocatedMemoryStorage(DEFAULT_UNSAFE, SCHEDULER);
                }
                if (threadLocalMemoryUsageDecider != null) {
                    AllocatedMemoryStorage globalAllocatedMemoryStorage;
                    if (safeMemoryAccessModeEnabled) {
                        globalAllocatedMemoryStorage = new NavigatableAllocatedMemoryStorage();
                    } else {
                        globalAllocatedMemoryStorage = new DefaultAllocatedMemoryStorage();
                    }
                    ALLOCATED_MEMORY_STORAGE = 
                            new ThreadLocalAwareAllocatedMemoryStorage(
                                    globalAllocatedMemoryStorage, 
                                    threadLocalAllocatedMemoryStorage, 
                                    threadLocalMemoryUsageDecider);
                } else {
                    ALLOCATED_MEMORY_STORAGE = threadLocalAllocatedMemoryStorage;
                }    
            } else {
                if (safeMemoryAccessModeEnabled) {
                    ALLOCATED_MEMORY_STORAGE = new NavigatableAllocatedMemoryStorage();
                } else {
                    ALLOCATED_MEMORY_STORAGE = new DefaultAllocatedMemoryStorage();
                }
            }    
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
        
        if (CALLER_INFO_MONITORING_MODE_ENABLED) {
            if (THREAD_LOCAL_MEMORY_USAGE_PATTERN_EXIST) {
                if (threadLocalMemoryUsageDecider != null) {
                    CALLER_INFO_STORAGE = 
                            new ThreadLocalAwareCallerInfoStorage(DEFAULT_UNSAFE, threadLocalMemoryUsageDecider, SCHEDULER);
                } else {
                    CALLER_INFO_STORAGE = new ThreadLocalDefaultCallerInfoStorage(DEFAULT_UNSAFE, SCHEDULER);
                }    
            } else {
                CALLER_INFO_STORAGE = new DefaultCallerInfoStorage();
            }
        } else {
            CALLER_INFO_STORAGE = null;
        }
    }

    private MySafeDelegator() {
        throw new UnsupportedOperationException("Not avaiable for instantiation!");
    }

    private static class MemoryAccessLock {
        
        private final Unsafe UNSAFE;
        private final long MEMORY_STATE_FIELD_OFFSET;
        private final long WAITERS_FOR_ACCESS_FIELD_OFFSET;
        private final long WAITERS_FOR_FREE_FIELD_OFFSET;
        
        private volatile int memoryState = 0;
        private volatile int waitersForAccess = 0;
        private volatile int waitersForFree = 0;

        private MemoryAccessLock(Unsafe unsafe) {
            try {
                UNSAFE = unsafe;
                MEMORY_STATE_FIELD_OFFSET = 
                        UNSAFE.objectFieldOffset(MemoryAccessLock.class.getDeclaredField("memoryState"));
                WAITERS_FOR_ACCESS_FIELD_OFFSET = 
                        UNSAFE.objectFieldOffset(MemoryAccessLock.class.getDeclaredField("waitersForAccess"));
                WAITERS_FOR_FREE_FIELD_OFFSET = 
                        UNSAFE.objectFieldOffset(MemoryAccessLock.class.getDeclaredField("waitersForFree"));
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
        
        private void operateOnWaitersForAccess(int operation) {
            for (;;) {
                int currentWaitersForAccess = waitersForAccess;
                if (UNSAFE.compareAndSwapInt(this, WAITERS_FOR_ACCESS_FIELD_OFFSET, 
                                             currentWaitersForAccess, currentWaitersForAccess + operation)) {
                    break;
                }
            }   
        }

        private void acquireAccessLock() {
            operateOnWaitersForAccess(+1);
            for (;;) {
                if (waitersForFree > 0) {
                    while (memoryState > 0);
                }
                int currentState = memoryState;
                if (currentState >= 0) {
                    if (UNSAFE.compareAndSwapInt(this, MEMORY_STATE_FIELD_OFFSET, 
                                                 currentState, currentState + 1)) {
                        break;
                    }
                }
            }
            operateOnWaitersForAccess(-1);
        }
        
        private void operateOnWaitersForFree(int operation) {
            for (;;) {
                int currentWaitersForFree = waitersForFree;
                if (UNSAFE.compareAndSwapInt(this, WAITERS_FOR_FREE_FIELD_OFFSET, 
                                             currentWaitersForFree, currentWaitersForFree + operation)) {
                    break;
                }
            }   
        }
        
        private void acquireFreeLock() {
            operateOnWaitersForFree(+1);
            for (;;) {
                if (waitersForAccess > 0) {
                    while (memoryState < 0);
                }
                int currentState = memoryState;
                if (currentState <= 0) {
                    if (UNSAFE.compareAndSwapInt(this, MEMORY_STATE_FIELD_OFFSET, 
                                                 currentState, currentState - 1)) {
                        break;
                    }
                }
            }
            operateOnWaitersForFree(-1);
        }

        private void releaseAccessLock() {
            for (;;) {
                int currentState = memoryState;
                assert currentState > 0 : "Current state must be negative while releasing access lock but it is " + currentState;
                if (UNSAFE.compareAndSwapInt(this, MEMORY_STATE_FIELD_OFFSET, 
                                             currentState, currentState - 1)) {
                    break;
                }
            }
        }
        
        private void releaseFreeLock() {
            for (;;) {
                int currentState = memoryState;
            	assert currentState < 0 : "Current state must be negative while releasing free lock but it is " + currentState;
                if (UNSAFE.compareAndSwapInt(this, MEMORY_STATE_FIELD_OFFSET, 
                                             currentState, currentState + 1)) {
                    break;
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////
    
    private static class ThreadLocalCallerInfo {
        private long callerInfoKey;
        private int callerDepth;
    }
    
    public static void startThreadLocalCallTracking(long callerInfoKey, int callerDepth) {
        ThreadLocalCallerInfo threadLocalCallerInfo = THREAD_LOCAL_CALLER_INFO_MAP.get();
        if (callerDepth >= threadLocalCallerInfo.callerDepth) {
            threadLocalCallerInfo.callerInfoKey = callerInfoKey;
            threadLocalCallerInfo.callerDepth = callerDepth;
        }   
    }
    
    public static void finishThreadLocalCallTracking(int callerDepth) {
        ThreadLocalCallerInfo threadLocalCallerInfo = THREAD_LOCAL_CALLER_INFO_MAP.get();
        if (callerDepth >= threadLocalCallerInfo.callerDepth) {
            threadLocalCallerInfo.callerInfoKey = 0;
            threadLocalCallerInfo.callerDepth = 0;
        }
    }

    private static void saveCallerInfoOnAllocation(long address, int skipCallerCount) {
        skipCallerCount++;
        ThreadLocalCallerInfo threadLocalCallerInfo = THREAD_LOCAL_CALLER_INFO_MAP.get();
        long callerInfoKey = threadLocalCallerInfo.callerInfoKey;
        if (callerInfoKey == 0) {
            // New call path
            LOGGER.debug("A new call path has been detected ...");
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
            Class<?> classAtHeadOfCallPath = null;
            String methodNameAtHeadOfCallPath = null;
            int lineNumberAtHeadOfCallPath = -1;
            int depthAtHeadOfCallPath = -1;
            List<CallerInfoEntry> callerInfoEntries = 
                    new ArrayList<CallerInfoEntry>(CallerInfo.MAX_CALLER_DEPTH);
            
            for (int i = 0; i < CallerInfo.MAX_CALLER_DEPTH && i + skipCallerCount < stackTraceElements.length; i++) {
                StackTraceElement stackTraceElement = stackTraceElements[i + skipCallerCount];
                try {
                    Class<?> clazz = classAtHeadOfCallPath = Class.forName(stackTraceElement.getClassName());
                    // If this method will be instrumented,
                    //      - `MySafeDelegator` should be known by the caller classloader
                    //      - Caller method must not be native method
                    if (classAtHeadOfCallPath.getClassLoader().loadClass(MySafeDelegator.class.getName()) != null
                        &&
                        !stackTraceElement.isNativeMethod()) {
                        classAtHeadOfCallPath = clazz;
                        methodNameAtHeadOfCallPath = stackTraceElement.getMethodName();
                        lineNumberAtHeadOfCallPath = stackTraceElement.getLineNumber();
                        depthAtHeadOfCallPath = i + 1;
                        
                        CallerInfoEntry callerInfoEntry = 
                                new CallerInfoEntry(
                                        stackTraceElement.getClassName(), 
                                        stackTraceElement.getMethodName(),
                                        stackTraceElement.getLineNumber());
                        callerInfoEntries.add(callerInfoEntry);
                        
                        LOGGER.debug("\t- " + stackTraceElement.getClassName() + "." + 
                                     stackTraceElement.getMethodName() + ":" + 
                                     stackTraceElement.getLineNumber());
                    }
                } catch (ClassNotFoundException e) {
                    LOGGER.error(e);
                }
            }
            if (classAtHeadOfCallPath == null) {
                callerInfoKey = CallerInfo.EMPTY_CALLER_INFO_KEY;
                CALLER_INFO_STORAGE.putCallerInfo(callerInfoKey, CallerInfo.EMPTY_CALLER_INFO);
                LOGGER.warn("No available instrumentation point for caller info tracking. " +
                            "Call path will be evaluated as empty call path.");
            } else {
                CallerInfo callerInfo = null;
                for (;;) {
                    callerInfoKey = System.nanoTime();
                    callerInfo = new CallerInfo(callerInfoKey, callerInfoEntries);
                    if (CALLER_INFO_STORAGE.putCallerInfo(callerInfoKey, callerInfo) == null) {
                        break;
                    }
                }
                callerInfoKey = 
                        CALLER_INFO_INJECTOR.injectCallerInfo(classAtHeadOfCallPath, 
                                                              methodNameAtHeadOfCallPath, 
                                                              lineNumberAtHeadOfCallPath, 
                                                              callerInfoKey, 
                                                              depthAtHeadOfCallPath);
            }    
            threadLocalCallerInfo.callerInfoKey = callerInfoKey;
        }
        CALLER_INFO_STORAGE.connectAddressWithCallerInfo(address, callerInfoKey);
    }
    
    private static void deleteCallerInfoOnFree(long address) {
        CALLER_INFO_STORAGE.disconnectAddressFromCallerInfo(address);
    }
    
    //////////////////////////////////////////////////////////////////////////

    public static boolean isSafeMemoryManagementModeEnabled() {
        return SAFE_MEMORY_MANAGEMENT_MODE_ENABLED;
    }
    
    public static boolean isSafeMemoryAccessModeEnabled() {
        return SAFE_MEMORY_ACCESS_MODE_ENABLED;
    }
    
    //////////////////////////////////////////////////////////////////////////

    public static void beforeAllocateMemory(long size) {
        if (REGISTERED_LISTENER_EXIST) {
            for (MemoryListener listener : LISTENERS) {
                listener.beforeAllocateMemory(size);
            }
        }
    }
    
    private static long doAllocateMemory(Unsafe unsafe, long size) {
        return unsafe.allocateMemory(size);
    }
    
    public static void afterAllocateMemory(long size, long address) {
        ALLOCATED_MEMORY_STORAGE.put(address, size);
        ALLOCATED_MEMORY.addAndGet(size);
        if (CALLER_INFO_MONITORING_MODE_ENABLED) {
            saveCallerInfoOnAllocation(address, 2);
        }
        if (REGISTERED_LISTENER_EXIST) {
            for (MemoryListener listener : LISTENERS) {
                listener.afterAllocateMemory(address, size);
            }
        }    
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Allocated memory at address " + 
                         String.format("0x%016x", address) + " with size " + size);
        }     
    }

    public static long allocateMemory(Unsafe unsafe, long size) {
        beforeAllocateMemory(size);
        long address = doAllocateMemory(unsafe, size);
        afterAllocateMemory(size, address);
        return address; 
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static long beforeFreeMemory(long address) {
        if (REGISTERED_LISTENER_EXIST) {
            for (MemoryListener listener : LISTENERS) {
                listener.beforeFreeMemory(address);
            }
        }    
        return ALLOCATED_MEMORY_STORAGE.remove(address);
    }
    
    private static void doFreeMemory(Unsafe unsafe, long address) {
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED && MEMORY_ACCESS_LOCK != null) {
            MEMORY_ACCESS_LOCK.acquireFreeLock();
        }    
        try {
            unsafe.freeMemory(address);
        } finally {
            if (SAFE_MEMORY_ACCESS_MODE_ENABLED && MEMORY_ACCESS_LOCK != null) {
                MEMORY_ACCESS_LOCK.releaseFreeLock();
            }     
        }
    }
    
    public static void afterFreeMemory(long address, long size) {
        if (size != INVALID) {
            ALLOCATED_MEMORY.addAndGet(-size);
            if (CALLER_INFO_MONITORING_MODE_ENABLED) {
                deleteCallerInfoOnFree(address);
            }
            if (REGISTERED_LISTENER_EXIST) {
                for (MemoryListener listener : LISTENERS) {
                    listener.afterFreeMemory(address, size, true);
                }
            }    
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Free memory at address " + String.format("0x%016x", address));
            }    
        } else {
            if (SAFE_MEMORY_MANAGEMENT_MODE_ENABLED) {
                String msg = "Tried to free unallocated (or out of the record) memory at address " + 
                        String.format("0x%016x", address);
                if (REGISTERED_LISTENER_EXIST) {
                    for (MemoryListener listener : LISTENERS) {
                        listener.afterFreeMemory(address, INVALID, false);
                    }
                }    
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(address, INVALID, FREE);
                }
                LOGGER.error(msg); 
                throw new IllegalArgumentException(msg);
            } else {
                if (REGISTERED_LISTENER_EXIST) {
                    for (MemoryListener listener : LISTENERS) {
                        listener.afterFreeMemory(address, INVALID, false);
                    }
                }    
            }
        }
    }
    
    public static void freeMemory(Unsafe unsafe, long address) {  
        long size = beforeFreeMemory(address);
        if (size != INVALID) {
            doFreeMemory(unsafe, address);
            afterFreeMemory(address, size);
        } else {
            if (!SAFE_MEMORY_MANAGEMENT_MODE_ENABLED) {
                String msg = "Trying to free unallocated (or out of the record) memory at address " + 
                             String.format("0x%016x", address);
                LOGGER.warn(msg);
                doFreeMemory(unsafe, address);
            } else {
                afterFreeMemory(address, size);
            }    
        }
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static long beforeReallocateMemory(long oldAddress) {
        long oldSize = ALLOCATED_MEMORY_STORAGE.remove(oldAddress);
        if (oldSize != INVALID) {
            if (REGISTERED_LISTENER_EXIST) {
                for (MemoryListener listener : LISTENERS) {
                    listener.beforeReallocateMemory(oldAddress, oldSize);
                }
            }    
        }
        return oldSize;
    }
    
    private static long doReallocateMemory(Unsafe unsafe, long oldAddress, long newSize) {
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED && MEMORY_ACCESS_LOCK != null) {
            MEMORY_ACCESS_LOCK.acquireFreeLock();
        }   
        try {
            return unsafe.reallocateMemory(oldAddress, newSize);
        } finally {
            if (SAFE_MEMORY_ACCESS_MODE_ENABLED && MEMORY_ACCESS_LOCK != null) {
                MEMORY_ACCESS_LOCK.releaseFreeLock();
            }
        }
    }
    
    public static void afterReallocateMemory(long oldAddress, long oldSize, 
                                             long newAddress, long newSize) {
        if (oldSize != INVALID) {
            ALLOCATED_MEMORY_STORAGE.put(newAddress, newSize);
            ALLOCATED_MEMORY.addAndGet(newSize - oldSize);
            if (CALLER_INFO_MONITORING_MODE_ENABLED) {
                deleteCallerInfoOnFree(oldAddress);
                saveCallerInfoOnAllocation(newAddress, 2);
            }
            if (REGISTERED_LISTENER_EXIST) {
                for (MemoryListener listener : LISTENERS) {
                    listener.afterReallocateMemory(oldAddress, oldSize, newAddress, newSize, true);
                }
            }    
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Reallocate memory from address " + 
                             String.format("0x%016x", oldAddress) + " with size of " + oldSize + 
                             " to address " + String.format("0x%016x", newAddress) + " with size of " + newSize);
            }  
        }  else {
            if (SAFE_MEMORY_MANAGEMENT_MODE_ENABLED) {
                String msg = "Tried to reallocate unallocated (or out of the record) memory at address " + 
                             String.format("0x%016x", oldAddress) + " with new size " + newSize;
                if (REGISTERED_LISTENER_EXIST) {
                    for (MemoryListener listener : LISTENERS) {
                        listener.beforeReallocateMemory(oldAddress, INVALID);
                    }
                }    
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(oldAddress, INVALID, REALLOCATE);
                }
                LOGGER.error(msg); 
                throw new IllegalArgumentException(msg);
            } else {
                if (REGISTERED_LISTENER_EXIST) {
                    for (MemoryListener listener : LISTENERS) {
                        listener.afterReallocateMemory(oldAddress, INVALID, newAddress, newSize, false);
                    }
                }    
            }
        }
    }
    
    public static long reallocateMemory(Unsafe unsafe, long oldAddress, long newSize) {
        long oldSize = beforeReallocateMemory(oldAddress);
        long newAddress = INVALID;
        if (oldSize != INVALID) {
            newAddress = doReallocateMemory(unsafe, oldAddress, newSize);
            afterReallocateMemory(oldAddress, oldSize, newAddress, newSize);

        } else {
            if (!SAFE_MEMORY_MANAGEMENT_MODE_ENABLED) {
                String msg = "Trying to reallocate unallocated (or out of the record) memory at address " + 
                             String.format("0x%016x", oldAddress) + " with new size " + newSize;
                LOGGER.warn(msg);
                newAddress = doReallocateMemory(unsafe, oldAddress, newSize);
            } else {
                afterReallocateMemory(oldAddress, oldSize, newAddress, newSize);
            }
        }
        return newAddress;
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static long getAllocatedMemorySize() {
        return ALLOCATED_MEMORY.get();
    }
    
    public static void iterateOnAllocatedMemories(AllocatedMemoryIterator iterator) {
        ALLOCATED_MEMORY_STORAGE.iterate(iterator);     
    }
    
    public static void dumpAllocatedMemories(final PrintStream ps, final Unsafe unsafe) {
        ALLOCATED_MEMORY_STORAGE.iterate(new AllocatedMemoryIterator() {
            @Override
            public void onAllocatedMemory(long address, long size) {
                ps.println("Address     : " + String.format("0x%016x", address));
                ps.println("Size        : " + size + " bytes");
                ps.println("Dump        :");
                dump(ps, unsafe, address, size);
                if (CALLER_INFO_MONITORING_MODE_ENABLED) {
                    ps.println("Call Path   :");
                    CallerInfo callerInfo = CALLER_INFO_STORAGE.findCallerInfoByConnectedAddress(address);
                    if (callerInfo == null) {
                        ps.println("\tNo related caller info!");
                    } else if (callerInfo == CallerInfo.EMPTY_CALLER_INFO) {
                        ps.println("\tUnknown caller info because of not applicable instrumentation!");
                    } else {
                        for (CallerInfoEntry callerInfoEntry : callerInfo.callerInfoEntries) {
                            ps.println("\t|- " + callerInfoEntry.className + "." + 
                                       callerInfoEntry.methodName + ":" +
                                       callerInfoEntry.lineNumber);
                        }
                    }
                }
                ps.println();
                ps.print("========================================");
                ps.print("========================================");
                ps.println();
                ps.println();
            }
        });   
    }

    private static void dump(PrintStream ps, Unsafe unsafe, long address, long size) {
        for (int i = 0; i < size; i++) {
            if (i % 16 == 0) {
                ps.print(String.format("\t[0x%016x]: ", i));
            }
            ps.print(String.format("%02x ", unsafe.getByte(address + i)));
            if ((i + 1) % 16 == 0) {
                ps.println();
            }
        }  
        if (size % 16 != 0) {
            ps.println();
        }     
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    public static synchronized void registerMemoryListener(MemoryListener listener) {
        LISTENERS.add(listener);
        REGISTERED_LISTENER_EXIST = true;
    }
    
    public static synchronized void deregisterMemoryListener(MemoryListener listener) {
        LISTENERS.remove(listener);
        REGISTERED_LISTENER_EXIST = !LISTENERS.isEmpty();
    }
    
    //////////////////////////////////////////////////////////////////////////
    
    private static void checkMemoryAccess(long address, long size, 
            IllegalMemoryAccessListener.MemoryAccessType memoryAccessType) {
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED) {
            if (MEMORY_ACCESS_LOCK != null) {
                MEMORY_ACCESS_LOCK.acquireAccessLock();
            }    
            if (!ALLOCATED_MEMORY_STORAGE.contains(address, size)) {
                if (MEMORY_ACCESS_LOCK != null) {
                    MEMORY_ACCESS_LOCK.releaseAccessLock();
                }    
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
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED) {
            if (MEMORY_ACCESS_LOCK != null) {
                MEMORY_ACCESS_LOCK.acquireAccessLock();
            }
            if (!ALLOCATED_MEMORY_STORAGE.contains(sourceAddress, size)) {
                if (MEMORY_ACCESS_LOCK != null) {
                    MEMORY_ACCESS_LOCK.releaseAccessLock();
                } 
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            sourceAddress, size,  READ);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + READ + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", sourceAddress) + " with size " + size);
                
            }
            if (!ALLOCATED_MEMORY_STORAGE.contains(destinationAddress, size)) {
                if (MEMORY_ACCESS_LOCK != null) {
                    MEMORY_ACCESS_LOCK.releaseAccessLock();
                } 
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
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED && MEMORY_ACCESS_LOCK != null) {
            MEMORY_ACCESS_LOCK.releaseAccessLock();
        }    
    }
    
    private static void checkMemoryAccess(Object o, long offset, long size,
            IllegalMemoryAccessListener.MemoryAccessType memoryAccessType) {
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED && o == null) {
            if (MEMORY_ACCESS_LOCK != null) {
                MEMORY_ACCESS_LOCK.acquireAccessLock();
            }
            if (!ALLOCATED_MEMORY_STORAGE.contains(offset, size)) {
                if (MEMORY_ACCESS_LOCK != null) {
                    MEMORY_ACCESS_LOCK.releaseAccessLock();
                }
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
    
    private static void onReturnMemoryAccess(Object o) {
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED && MEMORY_ACCESS_LOCK != null && o == null) {
            MEMORY_ACCESS_LOCK.releaseAccessLock();
        }
    }
    
    private static void checkMemoryAccess(Object sourceObject, long sourceOffset, 
            Object destinationObject, long destinationOffset, long size) {
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED && sourceObject == null && destinationObject == null) {
            if (MEMORY_ACCESS_LOCK != null) {
                MEMORY_ACCESS_LOCK.acquireAccessLock();
            }
            if (!ALLOCATED_MEMORY_STORAGE.contains(sourceOffset, size)) {
                if (MEMORY_ACCESS_LOCK != null) {
                    MEMORY_ACCESS_LOCK.releaseAccessLock();
                }
                if (ILLEGAL_MEMORY_ACCESS_LISTENER != null) {
                    ILLEGAL_MEMORY_ACCESS_LISTENER.onIllegalMemoryAccess(
                            sourceOffset, size, READ);
                }
                throw new IllegalArgumentException(
                            "Trying to access (" + READ + ") unallocated (or out of the record) memory " +
                            "at address " + String.format("0x%016x", sourceOffset) + " with size " + size);
            }
            if (!ALLOCATED_MEMORY_STORAGE.contains(destinationOffset, size)) {
                if (MEMORY_ACCESS_LOCK != null) {
                    MEMORY_ACCESS_LOCK.releaseAccessLock();
                }
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

    private static void onReturnMemoryAccess(Object sourceObject, Object destinationObject) {
        if (SAFE_MEMORY_ACCESS_MODE_ENABLED && MEMORY_ACCESS_LOCK != null && 
                sourceObject == null && destinationObject == null) {
            MEMORY_ACCESS_LOCK.releaseAccessLock();
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
            onReturnMemoryAccess(srcBase, destBase);
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
