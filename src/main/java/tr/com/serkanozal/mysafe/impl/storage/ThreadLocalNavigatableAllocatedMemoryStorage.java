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

import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongSortedMap;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.AllocatedMemoryStorage;

public class ThreadLocalNavigatableAllocatedMemoryStorage extends AbstractThreadLocalAllocatedMemoryStorage {

    public ThreadLocalNavigatableAllocatedMemoryStorage(Unsafe unsafe, ScheduledExecutorService scheduler) {
        super(unsafe, scheduler);
    }

    @Override
    protected AllocatedMemoryStorage createInternalThreadLocalAllocatedMemoryStorage(Unsafe unsafe) {
        return new InternalThreadLocalNavigatableAllocatedMemoryStorage(unsafe);
    }
    
    private class InternalThreadLocalNavigatableAllocatedMemoryStorage 
            extends AbstractInternalThreadLocalAllocatedMemoryStorage {

        private final Long2LongSortedMap allocatedMemories = new Long2LongAVLTreeMap();
 
        private InternalThreadLocalNavigatableAllocatedMemoryStorage(Unsafe unsafe) {
            super(unsafe);
        }

        @Override
        public boolean contains(long address) {
            Long2LongMap.Entry entry = allocatedMemories.tailMap(address).long2LongEntrySet().first();
            if (entry == null) {
                return false;
            }
            long startAddress = entry.getLongKey();
            long endAddress = startAddress + entry.getLongValue();
            return address >= startAddress && address <= endAddress;
        }
        
        @Override
        public boolean contains(long address, long size) {
            Long2LongMap.Entry entry = allocatedMemories.tailMap(address).long2LongEntrySet().first();
            if (entry == null) {
                return false;
            }
            long startAddress = entry.getLongKey();
            long endAddress = startAddress + entry.getLongValue();
            return address >= startAddress && (address + size) <= endAddress;
        }

        @Override
        public long get(long address) {
            long size = allocatedMemories.get(address);
            return size != 0 ? size : INVALID;
        }

        @Override
        public void put(long address, long size) {
            acquire();
            try {
                allocatedMemories.put(address, size);
            } finally {
                free();
            }
        }

        @Override
        public long remove(long address) {
            acquire();
            try {
                long size = allocatedMemories.remove(address);
                return size != 0 ? size : INVALID;
            } finally {
                free();
            }    
        }

        @Override
        public void iterate(final AllocatedMemoryIterator iterator) {
            acquire();
            try {
                for (Map.Entry<Long, Long> entry : allocatedMemories.entrySet()) {
                    long address = entry.getKey();
                    long size = entry.getValue();
                    iterator.onAllocatedMemory(address, size);
                }
            } finally {
                free();
            }    
        }
        
        @Override
        public boolean isEmpty() {
            acquire();
            try {
                return allocatedMemories.isEmpty();
            } finally {
                free();
            }
        }
        
    }

}

