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
package tr.com.serkanozal.mysafe.impl.storage;

import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.AllocatedMemoryStorage;
import tr.com.serkanozal.mysafe.ThreadLocalMemoryUsageDecider;

public class ThreadLocalAwareAllocatedMemoryStorage implements AllocatedMemoryStorage {

    private final AllocatedMemoryStorage globalAllocatedMemoryStorage;
    private final AllocatedMemoryStorage threadLocalAllocatedMemoryStorage;
    private final ThreadLocalMemoryUsageDecider threadLocalMemoryUsageDecider;
    
    public ThreadLocalAwareAllocatedMemoryStorage(AllocatedMemoryStorage globalAllocatedMemoryStorage, 
                                                  AllocatedMemoryStorage threadLocalAllocatedMemoryStorage, 
                                                  ThreadLocalMemoryUsageDecider threadLocalMemoryUsageDecider) {
        this.globalAllocatedMemoryStorage = globalAllocatedMemoryStorage;
        this.threadLocalAllocatedMemoryStorage = threadLocalAllocatedMemoryStorage;
        this.threadLocalMemoryUsageDecider = threadLocalMemoryUsageDecider;
    }
    
    private AllocatedMemoryStorage allocatedMemoryStorage() {
        if (threadLocalMemoryUsageDecider.isThreadLocal(Thread.currentThread())) {
            return threadLocalAllocatedMemoryStorage;
        } else {
            return globalAllocatedMemoryStorage;
        }
    }
    
    @Override
    public boolean contains(long address) {
        return allocatedMemoryStorage().contains(address);
    }

    @Override
    public boolean contains(long address, long size) {
        return allocatedMemoryStorage().contains(address, size);
    }

    @Override
    public long get(long address) {
        return allocatedMemoryStorage().get(address);
    }

    @Override
    public void put(long address, long size) {
        allocatedMemoryStorage().put(address, size);
    }

    @Override
    public long remove(long address) {
        return allocatedMemoryStorage().remove(address);
    }

    @Override
    public void iterate(AllocatedMemoryIterator iterator) {
        threadLocalAllocatedMemoryStorage.iterate(iterator);
        globalAllocatedMemoryStorage.iterate(iterator);
    }

}
