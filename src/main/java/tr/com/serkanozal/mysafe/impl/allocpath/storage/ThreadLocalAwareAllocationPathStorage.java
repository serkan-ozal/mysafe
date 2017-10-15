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
package tr.com.serkanozal.mysafe.impl.allocpath.storage;

import java.util.concurrent.ScheduledExecutorService;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.ThreadLocalMemoryUsageDecider;

public class ThreadLocalAwareAllocationPathStorage implements AllocationPathStorage {

    private final AllocationPathStorage globalAllocationPathStorage;
    private final AllocationPathStorage threadLocalAllocationPathStorage;
    private final ThreadLocalMemoryUsageDecider threadLocalMemoryUsageDecider;
    
    public ThreadLocalAwareAllocationPathStorage(Unsafe unsafe,
                                                 ThreadLocalMemoryUsageDecider threadLocalMemoryUsageDecider,
                                                 ScheduledExecutorService scheduler) {
        this.globalAllocationPathStorage = new DefaultAllocationPathStorage();
        this.threadLocalAllocationPathStorage = new ThreadLocalDefaultAllocationPathStorage(unsafe, scheduler);
        this.threadLocalMemoryUsageDecider = threadLocalMemoryUsageDecider;
    }
    
    private AllocationPathStorage allocationPathStorage() {
        if (threadLocalMemoryUsageDecider.isThreadLocal(Thread.currentThread())) {
            return threadLocalAllocationPathStorage;
        } else {
            return globalAllocationPathStorage;
        }
    }
    
    @Override
    public long getAllocationPathKey(long address) {
        return allocationPathStorage().getAllocationPathKey(address);
    }

    @Override
    public void connectAddressWithAllocationPath(long address, long allocationPathKey) {
        allocationPathStorage().connectAddressWithAllocationPath(address, allocationPathKey);
    }

    @Override
    public void disconnectAddressFromAllocationPath(long address) {
        allocationPathStorage().disconnectAddressFromAllocationPath(address);
    }
    
    @Override
    public boolean isEmpty() {
        if (threadLocalAllocationPathStorage.isEmpty()) {
            return globalAllocationPathStorage.isEmpty();
        }
        return false;
    }

}
