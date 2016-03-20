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
package tr.com.serkanozal.mysafe.impl.callerinfo;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import sun.misc.Unsafe;

abstract class AbstractThreadLocalCallerInfoStorage implements CallerInfoStorage {

    protected final Unsafe unsafe;
    private final ConcurrentMap<SoftReference<Thread>, CallerInfoStorage> allCallerInfoStorages =
            new ConcurrentHashMap<SoftReference<Thread>, CallerInfoStorage>();
    private final ThreadLocal<CallerInfoStorage> threadLocalCallerInfoStorages;
    private final ConcurrentMap<Long, CallerInfo> callerInfoMap;

    public AbstractThreadLocalCallerInfoStorage(final Unsafe unsafe, ScheduledExecutorService scheduler) {
        this(unsafe, null, scheduler);
    }
    
    public AbstractThreadLocalCallerInfoStorage(final Unsafe unsafe, 
                                                ConcurrentMap<Long, CallerInfo> callerInfoMap, 
                                                ScheduledExecutorService scheduler) {
        this.unsafe = unsafe;
        this.threadLocalCallerInfoStorages = new ThreadLocal<CallerInfoStorage>() {
            @Override
            protected CallerInfoStorage initialValue() {
                CallerInfoStorage callerInfoStorage = createInternalThreadLocalCallerInfoStorage(unsafe);
                SoftReference<Thread> threadRef = new SoftReference<Thread>(Thread.currentThread());
                allCallerInfoStorages.put(threadRef, callerInfoStorage);
                return callerInfoStorage;
            };
        };
        if (callerInfoMap == null) {
            this.callerInfoMap = new ConcurrentHashMap<Long, CallerInfo>();
        } else {
            this.callerInfoMap = callerInfoMap;
        }
        scheduler.scheduleAtFixedRate(new IdleThreadLocalCallerInfoStorageCleaner(), 5, 5, TimeUnit.SECONDS);
    }
    
    abstract protected CallerInfoStorage createInternalThreadLocalCallerInfoStorage(Unsafe unsafe);

    @Override
    public final CallerInfo getCallerInfo(long callerInfoKey) {
        return callerInfoMap.get(callerInfoKey);
    }

    @Override
    public final CallerInfo putCallerInfo(long callerInfoKey, CallerInfo callerInfo) {
        return callerInfoMap.putIfAbsent(callerInfoKey, callerInfo);
    }

    @Override
    public final CallerInfo removeCallerInfo(long callerInfoKey) {
        return callerInfoMap.remove(callerInfoKey);
    }

    @Override
    public CallerInfo findCallerInfoByConnectedAddress(long address) {
        Iterator<Map.Entry<SoftReference<Thread>, CallerInfoStorage>> iter = 
                allCallerInfoStorages.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<SoftReference<Thread>, CallerInfoStorage> entry = iter.next();
            if (isIdle(entry)) {
                iter.remove();
            } else {
                CallerInfoStorage callerInfoStorage = entry.getValue();
                CallerInfo callerInfo = callerInfoStorage.findCallerInfoByConnectedAddress(address);
                if (callerInfo != null) {
                    return callerInfo;
                }
            }
        }
        return null;
    }

    @Override
    public void connectAddressWithCallerInfo(long address, long callerInfoKey) {
        threadLocalCallerInfoStorages.get().connectAddressWithCallerInfo(address, callerInfoKey);
    }

    @Override
    public void disconnectAddressFromCallerInfo(long address) {
        threadLocalCallerInfoStorages.get().disconnectAddressFromCallerInfo(address);
    }
    
    @Override
    public boolean isEmpty() {
        Iterator<Map.Entry<SoftReference<Thread>, CallerInfoStorage>> iter = 
                allCallerInfoStorages.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<SoftReference<Thread>, CallerInfoStorage> entry = iter.next();
            if (isIdle(entry)) {
                iter.remove();
            } else {
                CallerInfoStorage callerInfoStorage = entry.getValue();
                if (!callerInfoStorage.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isIdle(Map.Entry<SoftReference<Thread>, CallerInfoStorage> entry) {
        SoftReference<Thread> threadRef = entry.getKey();
        Thread thread = threadRef.get();
        if (thread == null || !thread.isAlive()) {
            return entry.getValue().isEmpty();
        } 
        return false;
    }

    private class IdleThreadLocalCallerInfoStorageCleaner implements Runnable {

        @Override
        public void run() {
            Iterator<Map.Entry<SoftReference<Thread>, CallerInfoStorage>> iter = 
                    allCallerInfoStorages.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<SoftReference<Thread>, CallerInfoStorage> entry = iter.next();
                if (isIdle(entry)) {
                    iter.remove();
                }
            }
        }
        
    }
    
    protected abstract class AbstractInternalThreadLocalCallerInfoStorage implements CallerInfoStorage {

        private static final int AVAILABLE = 0x00;
        private static final int IN_PROGRESS = 0x01;
        
        private final Unsafe UNSAFE;
        private final long stateFieldOffset;
        
        /*
         * This field is used to support happens-before (HB) and synchronized access 
         * between memory allocator/disposer thread and allocated memory iterator thread(s).
         * Note that HB is supported by CAS and volatile write on this field.
         * 
         * There will be no blocking from the allocator/disposer thread's perspective, 
         * because this storage is thread-local and always will be accessed by same thread.
         * So there will be no concurrent mutating accesses into here.
         * 
         * However, there can be different thread(s) to iterate on allocated memories.
         * From their perspective, they can block each other and they be blocked each other.
         * Also, they can block allocator/disposer thread and they be blocked allocator/disposer thread.
         * But since iterating will not be frequently, we can live with locking in these cases.
         */
        @SuppressWarnings("unused")
        private volatile int state = AVAILABLE;
        
        protected AbstractInternalThreadLocalCallerInfoStorage(Unsafe unsafe) {
            UNSAFE = unsafe;
            try {
                stateFieldOffset = 
                        UNSAFE.objectFieldOffset(
                                AbstractInternalThreadLocalCallerInfoStorage.class.getDeclaredField("state"));
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
        
        protected void acquire() {
            while (UNSAFE.compareAndSwapInt(this, stateFieldOffset, AVAILABLE, IN_PROGRESS));
        }
        
        protected void free() {
            state = AVAILABLE;
        }
        
        @Override
        public final CallerInfo getCallerInfo(long callerInfoKey) {
            // Should not be called
            throw new UnsupportedOperationException();
        }
        
        @Override
        public final CallerInfo putCallerInfo(long callerInfoKey, CallerInfo callerInfo) {
            // Should not be called
            throw new UnsupportedOperationException();
        }
        
        @Override
        public final CallerInfo removeCallerInfo(long callerInfoKey) {
            // Should not be called
            throw new UnsupportedOperationException();
        }

    }

}
