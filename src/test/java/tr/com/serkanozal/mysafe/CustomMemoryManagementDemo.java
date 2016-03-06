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
package tr.com.serkanozal.mysafe;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.config.AllocationPoint;
import tr.com.serkanozal.mysafe.config.FreePoint;
import tr.com.serkanozal.mysafe.config.ReallocationPoint;

/**
 * Demo application to show how to use <b>MySafe</b> with custom memory management.
 * Custom memory management means that memory allocation/free/reallocation operations
 * are not handled directly over {@link Unsafe} but over custom implementation.
 * For example, user might acquire memory in batch from OS, caches it 
 * and then serves requested memories from there.
 * 
 * <pre>
 * There are 3 ways of running this demo (also <b>MySafe</b>):
 *      - Activate the <b>MySafe</b> by defining its classloader as system classloader 
 *        via `-Djava.system.class.loader=tr.com.serkanozal.mysafe.impl.classloader.UnsafeAwareClassLoader`
 *      - Activate the <b>MySafe</b> through Java agent (<b>Jillegal-Agent</b>) by using `sun.misc.Unsafe` instrumenter of <b>MySafe</b>
 *        via `-javaagent:<path_to_jillegal_agent>\<jillegal_agent_jar>="-p tr.com.serkanozal.mysafe.impl.processor.UnsafeProcessor"`.
 *        For example:
 *              `-javaagent:$M2_HOME\tr\com\serkanozal\jillegal-agent\2.0\jillegal-agent-2.0.jar="-p tr.com.serkanozal.mysafe.impl.processor.UnsafeProcessor"`
 *      - Activate the <b>MySafe</b> programmatically by `MySafe.youAreMine();`
 * </pre>
 * 
 * @author Serkan OZAL
 */
public class CustomMemoryManagementDemo {

    public static void main(String[] args) throws Exception {
        MySafe.youAreMine();

        // Demo code is run on another class.
        // Because, we want to be sure that demo code runner class is loaded 
        // after MySafe is initialized to instrument Unsafe calls.
        DemoRunner.run();
    }
    
    private static class MemoryManager {
        
        private final Unsafe UNSAFE = MySafe.getUnsafe();
        
        @AllocationPoint
        long allocate(long size) {
            return UNSAFE.allocateMemory(size);
        }
        
        @FreePoint
        void free(long address) {
            UNSAFE.freeMemory(address);
        }
        
        @ReallocationPoint
        long reallocate(long address, long newSize) {
            return UNSAFE.reallocateMemory(address, newSize);
        }
        
    }
    
    private static class DemoRunner {
        
        private static void run() {
            MemoryManager memoryManager = new MemoryManager();

            // Create listener to be notified for each allocate/free
            MemoryListener listener = new MemoryListener() {

                @Override
                public void beforeAllocateMemory(long size) {
                    System.out.println("beforeAllocateMemory >>> " + 
                                           "size=" + size);
                }
                
                @Override
                public void afterAllocateMemory(long address, long size) {
                    System.out.println("afterAllocateMemory >>> " + 
                                            "address=" + address + 
                                            ", size=" + size);
                }
                
                @Override
                public void beforeFreeMemory(long address) {
                    System.out.println("beforeFreeMemory >>> " + 
                                            "address=" + address);
                }
                
                @Override
                public void afterFreeMemory(long address, long size, boolean isKnownAddress) {
                    System.out.println("afterFreeMemory >>> " + 
                                            "address=" + address + 
                                            ", size=" + size + 
                                            ", isKnownAddress=" + isKnownAddress);
                }

                @Override
                public void beforeReallocateMemory(long oldAddress, long oldSize) {
                    System.out.println("beforeReallocateMemory >>> " + 
                                            "oldAddress=" + oldAddress + 
                                            ", oldSize=" + oldSize);
                }

                @Override
                public void afterReallocateMemory(long oldAddress, long oldSize, 
                        long newAddress, long newSize, boolean isKnownAddress) {
                    System.out.println("afterReallocateMemory >>> " + 
                                            "oldAddress=" + oldAddress + 
                                            ", oldSize=" + oldSize +
                                            ", newAddress=" + newAddress + 
                                            ", newSize=" + newSize +
                                            ", isKnownAddress=" + isKnownAddress);
                }

            };
            
            // Register listener to be notified for each allocate/free/reallocate
            MySafe.registerUnsafeListener(listener);
            
            // Allocate a sample memory
            long address = memoryManager.allocate(8);
            
            // Write to valid memory
            memoryManager.UNSAFE.putInt(address, 100);
            
            // Read from valid memory
            memoryManager.UNSAFE.getInt(address);
            
            try {
                // Write to invalid memory
                memoryManager.UNSAFE.putInt(address + 16, 100);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            
            try {
                // Read from invalid memory
                memoryManager.UNSAFE.getInt(address + 16);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }

            // Free allocated memory
            memoryManager.free(address);

            try {
                // Free non-allocated memory
                memoryManager.free(1234);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }

            // Allocate a sample memory
            long oldAddress = memoryManager.allocate(16);

            // Reallocate memory
            long newAddress = memoryManager.reallocate(oldAddress, 32);
    
            // Free reallocated memory
            memoryManager.free(newAddress);
            
            // Allocate multiple memory
            for (int i = 1; i <= 32; i++) {
                memoryManager.allocate(i * 8);
            }

            // Iterate on all allocated memories and print them
            MySafe.iterateOnAllocatedMemories(new AllocatedMemoryIterator() {
                
                @Override
                public void onAllocatedMemory(long address, long size) {
                    System.out.println("onAllocatedMemory >>> " + 
                                            "address=" + address + 
                                            ", size=" + size);
                }
                
            });
            
            // Deregister registered listener
            MySafe.deregisterUnsafeListener(listener);
            
            // Dump all allocated memories to console
            MySafe.dumpAllocatedMemories();
        }
        
    }
 
}
