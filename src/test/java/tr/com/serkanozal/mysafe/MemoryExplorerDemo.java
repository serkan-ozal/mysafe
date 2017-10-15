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
import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.MySafe;

/**
 * <code>MemoryExplorerDemo</code> application to show how to use <b>MySafe</b>
 * for iterating on allocated memories and dumping them.
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
public class MemoryExplorerDemo {

    public static void main(String[] args) throws Exception {
        MySafe.youAreMine();

        // MemoryExplorerDemo code is run on another class.
        // Because, we want to be sure that demo code runner class is loaded 
        // after MySafe is initialized to instrument Unsafe calls.
        DemoRunner.run();
    }
    
    private static class DemoRunner {
        
        private static void run() {
            Unsafe unsafe = MySafe.getUnsafe(); // Or get unsafe yourself with reflection hack, it doesn't matter

            // Allocate memory
            long address1 = unsafe.allocateMemory(8);
            System.out.println("Allocated memory: address=" + address1 + ", size=" + 8);

            // Allocate memory
            long address2 = unsafe.allocateMemory(16);
            System.out.println("Allocated memory: address=" + address2 + ", size=" + 16);

            // Allocate memory
            long address3 = unsafe.allocateMemory(24);
            System.out.println("Allocated memory: address=" + address3 + ", size=" + 24);

            // Iterate on all allocated memories and print them
            MySafe.iterateOnAllocatedMemories(new AllocatedMemoryIterator() {
                
                @Override
                public void onAllocatedMemory(long address, long size) {
                    System.out.println("onAllocatedMemory >>> " + 
                                            "address=" + address + 
                                            ", size=" + size);
                }
                
            });

            // Dump all allocated memories to console
            MySafe.dumpAllocatedMemories();

            unsafe.freeMemory(address1);
            System.out.println("Free memory: address=" + address1);

            // Iterate on all allocated memories and print them
            MySafe.iterateOnAllocatedMemories(new AllocatedMemoryIterator() {

                @Override
                public void onAllocatedMemory(long address, long size) {
                    System.out.println("onAllocatedMemory >>> " +
                            "address=" + address +
                            ", size=" + size);
                }

            });

            // Dump all allocated memories to console
            MySafe.dumpAllocatedMemories();

            unsafe.freeMemory(address2);
            System.out.println("Free memory: address=" + address2);

            // Iterate on all allocated memories and print them
            MySafe.iterateOnAllocatedMemories(new AllocatedMemoryIterator() {

                @Override
                public void onAllocatedMemory(long address, long size) {
                    System.out.println("onAllocatedMemory >>> " +
                            "address=" + address +
                            ", size=" + size);
                }

            });

            // Dump all allocated memories to console
            MySafe.dumpAllocatedMemories();

            unsafe.freeMemory(address3);
            System.out.println("Free memory: address=" + address3);

            // Iterate on all allocated memories and print them
            MySafe.iterateOnAllocatedMemories(new AllocatedMemoryIterator() {

                @Override
                public void onAllocatedMemory(long address, long size) {
                    System.out.println("onAllocatedMemory >>> " +
                            "address=" + address +
                            ", size=" + size);
                }

            });

            // Dump all allocated memories to console
            MySafe.dumpAllocatedMemories();
        }
        
    }
 
}
