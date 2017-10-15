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
 * <code>IllegalMemoryAccessListenerDemo</code> application to show how to use <b>MySafe</b>
 * to be notified on illegal memory accesses.
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
public class IllegalMemoryAccessListenerDemo {

    static {
        System.setProperty("mysafe.enableSafeMemoryManagementMode", "true");
        System.setProperty("mysafe.enableSafeMemoryAccessMode", "true");
    }
    
    public static void main(String[] args) throws Exception {
        MySafe.youAreMine();

        // IllegalMemoryAccessListenerDemo code is run on another class.
        // Because, we want to be sure that demo code runner class is loaded 
        // after MySafe is initialized to instrument Unsafe calls.
        DemoRunner.run();
    }
    
    private static class DemoRunner {
        
        private static void run() {
            Unsafe unsafe = MySafe.getUnsafe(); // Or get unsafe yourself with reflection hack, it doesn't matter

            IllegalMemoryAccessListener illegalMemoryAccessListener = new IllegalMemoryAccessListener() {

                @Override
                public void onIllegalMemoryAccess(long address, long size, MemoryAccessType memoryAccessType) {
                    System.err.println("onIllegalMemoryAccess >>> " +
                            "address=" + address +
                            ", size=" + size +
                            ", memoryAccessType=" + memoryAccessType);
                }

            };

            // Register illegal memory access listener to be notified on illegal memory accesses
            MySafe.setIllegalMemoryAccessListener(illegalMemoryAccessListener);
            
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
        }
        
    }
 
}
