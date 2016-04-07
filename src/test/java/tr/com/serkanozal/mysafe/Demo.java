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

/**
 * Demo application to show how to use <b>MySafe</b>. 
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
public class Demo {

    static {
        System.setProperty("mysafe.enableSafeMemoryManagementMode", "true");
        System.setProperty("mysafe.enableSafeMemoryAccessMode", "true");
        System.setProperty("mysafe.enableCallerInfoMonitoringMode", "true");
    }
    
    public static void main(String[] args) throws Exception {
        MySafe.youAreMine();

        // Demo code is run on another class.
        // Because, we want to be sure that demo code runner class is loaded 
        // after MySafe is initialized to instrument Unsafe calls.
        DemoRunner.run();
    }
    
    private static class DemoRunner {
        
        private static void run() {
            Unsafe unsafe = MySafe.getUnsafe(); // Or get unsafe yourself with reflection hack, it doesn't matter

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
            MySafe.registerMemoryListener(listener);
            
            // Allocate a sample memory
            long address = unsafe.allocateMemory(8);
            
            // Write to valid memory
            unsafe.putInt(address, 100);
            
            // Read from valid memory
            unsafe.getInt(address);
            
            try {
                // Write to invalid memory
                unsafe.putInt(address + 16, 100);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            
            try {
                // Read from invalid memory
                unsafe.getInt(address + 16);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }

            // Free allocated memory
            unsafe.freeMemory(address);

            try {
                // Free non-allocated memory
                unsafe.freeMemory(1234);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }

            // Allocate a sample memory
            long oldAddress = unsafe.allocateMemory(16);

            // Reallocate memory
            long newAddress = unsafe.reallocateMemory(oldAddress, 32);
    
            // Free reallocated memory
            unsafe.freeMemory(newAddress);
            
            // Allocate multiple memory
            for (int i = 1; i <= 32; i++) {
                unsafe.allocateMemory(i * 8);
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
            MySafe.deregisterMemoryListener(listener);
            
            // Dump all allocated memories to console
            MySafe.dumpAllocatedMemories();
            
            // Dump all caller path with allocated memories through them to console
            MySafe.dumpCallerPaths();
            
            // Generate caller path diagram
            MySafe.generateCallerPathDiagrams();
        }
        
    }
 
}
