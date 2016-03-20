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

import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.AllocatedMemoryStorage;

public class NavigatableAllocatedMemoryStorage implements AllocatedMemoryStorage {

    private final NavigableMap<Long, Long> allocatedMemories;
    
    public NavigatableAllocatedMemoryStorage() {
        this.allocatedMemories = new ConcurrentSkipListMap<Long, Long>();
    }

    @Override
    public boolean contains(long address) {
        Map.Entry<Long, Long> entry = allocatedMemories.floorEntry(address);
        if (entry == null) {
            return false;
        }
        long startAddress = entry.getKey();
        long endAddress = startAddress + entry.getValue();
        return address >= startAddress && address <= endAddress;
    }
    
    @Override
    public boolean contains(long address, long size) {
        Map.Entry<Long, Long> entry = allocatedMemories.floorEntry(address);
        if (entry == null) {
            return false;
        }
        long startAddress = entry.getKey();
        long endAddress = startAddress + entry.getValue();
        return address >= startAddress && (address + size) <= endAddress;
    }

    @Override
    public long get(long address) {
        Long size = allocatedMemories.get(address);
        return size != null ? size : INVALID;
    }

    @Override
    public void put(long address, long size) {
        allocatedMemories.put(address, size);
    }

    @Override
    public long remove(long address) {
        Long size = allocatedMemories.remove(address);
        return size != null ? size : INVALID;
    }

    @Override
    public void iterate(AllocatedMemoryIterator iterator) {
        for (Map.Entry<Long, Long> entry : allocatedMemories.entrySet()) {
            long address = entry.getKey();
            long size = entry.getValue();
            iterator.onAllocatedMemory(address, size);
        }
    }
    
    @Override
    public boolean isEmpty() {
        return allocatedMemories.isEmpty();
    }

}
