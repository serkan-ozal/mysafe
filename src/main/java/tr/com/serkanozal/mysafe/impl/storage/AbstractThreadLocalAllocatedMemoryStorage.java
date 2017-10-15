/*
 * Copyright (c) 2017, Serkan OZAL, All Rights Reserved.
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
package tr.com.serkanozal.mysafe.impl.storage;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.AllocatedMemoryStorage;

abstract class AbstractThreadLocalAllocatedMemoryStorage implements AllocatedMemoryStorage {

    protected final Unsafe unsafe;
    private final ConcurrentMap<SoftReference<Thread>, AllocatedMemoryStorage> allAllocatedMemoryStorages =
            new ConcurrentHashMap<SoftReference<Thread>, AllocatedMemoryStorage>();
    private final ThreadLocal<AllocatedMemoryStorage> threadLocalAllocatedMemoryStorages;
    
    public AbstractThreadLocalAllocatedMemoryStorage(final Unsafe unsafe, ScheduledExecutorService scheduler) {
        this.unsafe = unsafe;
        this.threadLocalAllocatedMemoryStorages = new ThreadLocal<AllocatedMemoryStorage>() {
            @Override
            protected AllocatedMemoryStorage initialValue() {
                AllocatedMemoryStorage allocatedMemoryStorage = createInternalThreadLocalAllocatedMemoryStorage(unsafe);
                SoftReference<Thread> threadRef = new SoftReference<Thread>(Thread.currentThread());
                allAllocatedMemoryStorages.put(threadRef, allocatedMemoryStorage);
                return allocatedMemoryStorage;
            };
        };
        scheduler.scheduleAtFixedRate(new IdleThreadLocalAllocatedMemoryStorageCleaner(), 5, 5, TimeUnit.SECONDS);
    }
    
    abstract protected AllocatedMemoryStorage createInternalThreadLocalAllocatedMemoryStorage(Unsafe unsafe);

    @Override
    public boolean contains(long address) {
        return threadLocalAllocatedMemoryStorages.get().contains(address);
    }
    
    @Override
    public boolean contains(long address, long size) {
        return threadLocalAllocatedMemoryStorages.get().contains(address, size);
    }

    @Override
    public long get(long address) {
        return threadLocalAllocatedMemoryStorages.get().get(address);
    }

    @Override
    public void put(long address, long size) {
        threadLocalAllocatedMemoryStorages.get().put(address, size);
    }

    @Override
    public long remove(long address) {
        return threadLocalAllocatedMemoryStorages.get().remove(address);
    }

    @Override
    public void iterate(AllocatedMemoryIterator iterator) {
        Iterator<Map.Entry<SoftReference<Thread>, AllocatedMemoryStorage>> iter = 
                allAllocatedMemoryStorages.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<SoftReference<Thread>, AllocatedMemoryStorage> entry = iter.next();
            if (isIdle(entry)) {
                iter.remove();
            } else {
                AllocatedMemoryStorage allocatedMemoryStorage = entry.getValue();
                allocatedMemoryStorage.iterate(iterator);
            }
        }
    }
    
    @Override
    public boolean isEmpty() {
        Iterator<Map.Entry<SoftReference<Thread>, AllocatedMemoryStorage>> iter = 
                allAllocatedMemoryStorages.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<SoftReference<Thread>, AllocatedMemoryStorage> entry = iter.next();
            if (isIdle(entry)) {
                iter.remove();
            } else {
                AllocatedMemoryStorage allocatedMemoryStorage = entry.getValue();
                if (!allocatedMemoryStorage.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private boolean isIdle(Map.Entry<SoftReference<Thread>, AllocatedMemoryStorage> entry) {
        SoftReference<Thread> threadRef = entry.getKey();
        Thread thread = threadRef.get();
        if (thread == null || !thread.isAlive()) {
            return entry.getValue().isEmpty();
        } 
        return false;
    }
    
    private class IdleThreadLocalAllocatedMemoryStorageCleaner implements Runnable {

        @Override
        public void run() {
            Iterator<Map.Entry<SoftReference<Thread>, AllocatedMemoryStorage>> iter = 
                    allAllocatedMemoryStorages.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<SoftReference<Thread>, AllocatedMemoryStorage> entry = iter.next();
                if (isIdle(entry)) {
                    iter.remove();
                }
            }
        }
        
    }
    
    protected abstract class AbstractInternalThreadLocalAllocatedMemoryStorage implements AllocatedMemoryStorage {

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
         * 
         */
        @SuppressWarnings("unused")
        private volatile int state = AVAILABLE;
        
        protected AbstractInternalThreadLocalAllocatedMemoryStorage(Unsafe unsafe) {
            UNSAFE = unsafe;
            try {
                stateFieldOffset = 
                        UNSAFE.objectFieldOffset(
                                AbstractInternalThreadLocalAllocatedMemoryStorage.class.getDeclaredField("state"));
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

    }

}

