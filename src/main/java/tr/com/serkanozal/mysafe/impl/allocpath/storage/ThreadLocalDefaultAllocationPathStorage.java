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
import tr.com.serkanozal.mysafe.impl.util.HeapMemoryBackedLong2LongHashMap;
import tr.com.serkanozal.mysafe.impl.util.Long2LongMap;
import tr.com.serkanozal.mysafe.impl.util.NativeMemoryBackedLong2LongHashMap;

public class ThreadLocalDefaultAllocationPathStorage extends AbstractThreadLocalAllocationPathStorage {

    private static final boolean USE_NATIVE_MEMORY = Boolean.getBoolean("mysafe.useNativeMemoryForStorageWhenSupported");
    
    public ThreadLocalDefaultAllocationPathStorage(Unsafe unsafe, ScheduledExecutorService scheduler) {
        super(unsafe, scheduler);
    }

    @Override
    protected AllocationPathStorage createInternalThreadLocalAllocationPathStorage(Unsafe unsafe) {
        return new InternalThreadLocalDefaultAllocationPathStorage(unsafe);
    }
    
    private class InternalThreadLocalDefaultAllocationPathStorage
            extends AbstractInternalThreadLocalAllocationPathStorage {

        private final Long2LongMap allocationPathMap =
                USE_NATIVE_MEMORY 
                    ? new NativeMemoryBackedLong2LongHashMap(unsafe, -1)
                    : new HeapMemoryBackedLong2LongHashMap(-1);
 
        private InternalThreadLocalDefaultAllocationPathStorage(Unsafe unsafe) {
            super(unsafe);
        }
        
        @Override
        public long getAllocationPathKey(long address) {
            return allocationPathMap.get(address);
        }

        @Override
        public void connectAddressWithAllocationPath(long address, long allocationPathKey) {
            acquire();
            try {
                allocationPathMap.put(address, allocationPathKey);
            } finally {
                free();
            }
        }

        @Override
        public void disconnectAddressFromAllocationPath(long address) {
            acquire();
            try {
                allocationPathMap.remove(address);
            } finally {
                free();
            }
        }
        
        @Override
        public boolean isEmpty() {
            acquire();
            try {
                return allocationPathMap.isEmpty();
            } finally {
                free();
            }
        }

    }

}

