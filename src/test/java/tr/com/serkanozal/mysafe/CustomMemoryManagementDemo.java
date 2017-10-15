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
package tr.com.serkanozal.mysafe;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.config.AllocationPoint;
import tr.com.serkanozal.mysafe.config.FreePoint;
import tr.com.serkanozal.mysafe.config.ReallocationPoint;

import java.util.*;

/**
 * <code>CustomMemoryManagementDemo</code> application to show how to use <b>MySafe</b> with custom memory management.
 * Custom memory management means that memory allocation/free/reallocation operations
 * are not handled directly over {@link Unsafe} but over custom implementation.
 * For example, user might acquire memory in batch from OS, caches it 
 * and then serves requested memories from there.
 * 
 * <pre>
 * There are 3 ways of running this demo (also <b>MySafe</b>):
 *      - Activate the <b>MySafe</b> by defining its classloader as system classloader 
 *        via `-Djava.system.class.loader=tr.com.serkanozal.mysafe.impl.classloader.MySafeClassLoader`
 *      - Activate the <b>MySafe</b> through Java agent (<b>Jillegal-Agent</b>) by using `sun.misc.Unsafe` instrumenter of <b>MySafe</b>
 *        via `-javaagent:<path_to_jillegal_agent>\<jillegal_agent_jar>="-p tr.com.serkanozal.mysafe.impl.processor.MySafeProcessor"`.
 *        For example:
 *              `-javaagent:$M2_HOME\tr\com\serkanozal\jillegal-agent\2.0\jillegal-agent-2.0.jar="-p tr.com.serkanozal.mysafe.impl.processor.MySafeProcessor"`
 *      - Activate the <b>MySafe</b> programmatically by `MySafe.youAreMine();`
 * </pre>
 * 
 * @author Serkan OZAL
 */
public class CustomMemoryManagementDemo {

    static {
        System.setProperty("mysafe.enableSafeMemoryManagementMode", "true");
        System.setProperty("mysafe.useCustomMemoryManagement", "true");
    }

    public static void main(String[] args) throws Exception {
        MySafe.youAreMine();

        // CustomMemoryManagementDemo code is run on another class.
        // Because, we want to be sure that demo code runner class is loaded 
        // after MySafe is initialized to instrument Unsafe calls.
        DemoRunner.run();
    }

    static class MyAwesomeMemoryManager {
        
        static final Unsafe UNSAFE = MySafe.getUnsafe();
        static final int BLOCK_SIZE = 64;

        final int capacity;
        final int blockCount;
        final BitSet bitSet;
        long address;
        final Map<Long, Long> address2Size = new HashMap<Long, Long>();

        MyAwesomeMemoryManager(int capacity) {
            this.capacity = capacity;
            this.blockCount =
                    (capacity / BLOCK_SIZE) +
                    (capacity % BLOCK_SIZE == 0 ? 0 : 1);
            this.bitSet = new BitSet(blockCount);
            this.address = UNSAFE.allocateMemory(capacity);
        }
        
        @AllocationPoint
        synchronized long allocate(long size) {
            if (address == -1) {
                throw new IllegalStateException("Already destroyed");
            }

            int allocationBlockCount = (int) size / BLOCK_SIZE;
            if (size % BLOCK_SIZE != 0) {
                allocationBlockCount++;
            }
            for (int i = 0; i <= blockCount - allocationBlockCount; i++) {
                boolean inUse = false;
                for (int j = 0; j < allocationBlockCount; j++) {
                    if (bitSet.get(i + j)) {
                        inUse = true;
                        break;
                    }
                }
                if (!inUse) {
                    for (int j = 0; j < allocationBlockCount; j++) {
                        bitSet.set(i + j, true);
                    }
                    long allocatedAddress = address + (i * BLOCK_SIZE);
                    address2Size.put(allocatedAddress, size);
                    return allocatedAddress;
                }
            }
            throw new OutOfMemoryError("No available native memory left to allocate " + size + " bytes");
        }
        
        @FreePoint
        synchronized void free(long address) {
            if (address == -1) {
                throw new IllegalStateException("Already destroyed");
            }

            Long size = address2Size.remove(address);
            if (size == null) {
                throw new IllegalArgumentException(
                        "Unallocated address: " + address +
                        ". Should not reach here because MySafe should handle this before here");
            }

            int allocationBlockCount = size.intValue() / BLOCK_SIZE;
            if (size % BLOCK_SIZE != 0) {
                allocationBlockCount++;
            }
            int firstAllocationBlockNo = (int) ((address - this.address) / BLOCK_SIZE);
            for (int i = 0; i < allocationBlockCount; i++) {
                bitSet.set(firstAllocationBlockNo + i, false);
            }
        }
        
        @ReallocationPoint
        long reallocate(long address, long newSize) {
            throw new UnsupportedOperationException();
        }

        synchronized void destroy() {
            UNSAFE.freeMemory(address);
            address = -1;
        }
        
    }
    
    private static class DemoRunner {
        
        private static void run() {
            MyAwesomeMemoryManager myAwesomeMemoryManager = new MyAwesomeMemoryManager(1024);

            List<Long> allocatedAddresses = new ArrayList<Long>();

            // Allocate some memory
            for (int i = 1; i <= 8; i++) {
                long allocatedAddress = myAwesomeMemoryManager.allocate(i * 8);
                System.out.println("Allocated memory: address=" + allocatedAddress + ", size=" + i * 8);
                allocatedAddresses.add(allocatedAddress);
            }

            // Dump all allocated memories to console
            MySafe.dumpAllocatedMemories();

            // Free half of the allocated memories
            Iterator<Long> iter = allocatedAddresses.iterator();
            int allocatedAddressCount = allocatedAddresses.size();
            for (int i = 0; i < allocatedAddressCount / 2 && iter.hasNext(); i++) {
                long allocatedAddress = iter.next();
                myAwesomeMemoryManager.free(allocatedAddress);
                System.out.println("Free memory: address=" + allocatedAddress);
                iter.remove();
            }

            // Dump all allocated memories to console
            MySafe.dumpAllocatedMemories();

            // Free the remaining half of the allocated memories
            iter = allocatedAddresses.iterator();
            while (iter.hasNext()) {
                long allocatedAddress = iter.next();
                myAwesomeMemoryManager.free(allocatedAddress);
                System.out.println("Free memory: address=" + allocatedAddress);
                iter.remove();
            }

            // Dump all allocated memories to console
            MySafe.dumpAllocatedMemories();

            myAwesomeMemoryManager.destroy();
        }
        
    }
 
}
