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

/**
 * <code>MemoryListenerDemo</code> application to show how to use <b>MySafe</b>
 * for tracking memory allocation, reallocation and free operations.
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
public class MemoryListenerDemo {

    public static void main(String[] args) throws Exception {
        MySafe.youAreMine();

        // MemoryListenerDemo code is run on another class.
        // Because, we want to be sure that demo code runner class is loaded 
        // after MySafe is initialized to instrument Unsafe calls.
        DemoRunner.run();
    }
    
    private static class DemoRunner {
        
        private static void run() {
            Unsafe unsafe = MySafe.getUnsafe(); // Or get unsafe yourself with reflection hack, it doesn't matter

            // Create listener to be notified for each allocate/free
            MemoryListener memoryListener = new MemoryListener() {

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
            MySafe.registerMemoryListener(memoryListener);

            // Allocate memory
            long address = unsafe.allocateMemory(8);

            // Reallocate memory
            address = unsafe.reallocateMemory(address, 16);

            unsafe.freeMemory(address);

            // Deregister registered listener
            MySafe.deregisterMemoryListener(memoryListener);
        }
        
    }
 
}
