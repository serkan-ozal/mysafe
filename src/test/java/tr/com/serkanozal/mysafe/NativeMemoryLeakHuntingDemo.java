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

import java.util.Random;

import sun.misc.Unsafe;

/**
 * Demo application to show how to use <b>MySafe</b> for hunting native memory leaks. 
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
public class NativeMemoryLeakHuntingDemo {

    static {
        System.setProperty("mysafe.enableSafeMemoryManagementMode", "true");
        System.setProperty("mysafe.enableCallerInfoMonitoringMode", "true");
        System.setProperty("mysafe.threadLocalMemoryUsagePatternExist", "true");
        System.setProperty("mysafe.maxCallerInfoDepth", "2");
    }
    
    public static void main(String[] args) throws Exception {
        /*
         * ***************************************************************************
         * * Accept this challenge and find native memory leak earlier than "MySafe" *
         * ***************************************************************************
         */

        MySafe.youAreMine();

        // Demo code is run on another class.
        // Because, we want to be sure that demo code runner class is loaded 
        // after MySafe is initialized to instrument Unsafe calls.
        DemoRunner.run();
    }
    
    private static class DemoRunner {
        
        private static final long MAX_MEMORY_SIZE = 64 * 1024 * 1024;
        private static final int ENTRY_COUNT = 1000000;
        /*
         * 0  : key pointer   - long
         * 8  : value pointer - long
         * 16 : key size      - int
         * 20 : value size    - int 
         */
        private static final int ENTRY_LENGTH = 24;
        private static final int MAX_KEY_LENGTH = 32;
        private static final int MAX_VALUE_LENGTH = 128;
        
        private static final int EVICTION_PERCENTAGE = 25;
        private static final int ENTRY_COUNT_TO_EVICT = ENTRY_COUNT * EVICTION_PERCENTAGE / 100;
        private static final int ITERATION_COUNT = 5 * ENTRY_COUNT;
        
        private static final Unsafe unsafe = MySafe.getUnsafe();
        
        private static long memoryUsage;
        
        private static long allocate(int size) {
            if (memoryUsage + size > MAX_MEMORY_SIZE) {
                throw new OutOfMemoryError("Memory usage cannot exceed " + MAX_MEMORY_SIZE + " bytes. " + 
                                           "Current memory usage is " + memoryUsage + " bytes and " + 
                                           "requested allocation size is " + size + " bytes.");
            }
            long address = unsafe.allocateMemory(size);
            memoryUsage += size;
            return address;
        }
        
        private static void free(long address, int size) {
            unsafe.freeMemory(address);
            memoryUsage -= size;
        }
        
        private static long allocateIndexTable() {
            // Allocate index table
            long indexTableAddress = allocate(ENTRY_COUNT * ENTRY_LENGTH);
            
            // Initialize index table
            unsafe.setMemory(indexTableAddress, ENTRY_COUNT * ENTRY_LENGTH, (byte) 0);
            
            return indexTableAddress;
        }
        
        private static void freeIndexTable(long indexTableAddress) {
            free(indexTableAddress, ENTRY_COUNT * ENTRY_LENGTH);
        }
        
        private static void putEntry(long indexTableAddress, int index, 
                                     int keySize, int valueSize) {
            long keyPtrAddress = indexTableAddress + (index * ENTRY_LENGTH);
            long valuePtrAddress = keyPtrAddress + 8;
            long keySizeAddress = valuePtrAddress + 8;
            long valueSizeAddress = keySizeAddress + 4;
            
            long newKeyAddress = allocate(keySize);
            long newValueAddress = allocate(valueSize);
            
            long oldKeyAddress = unsafe.getLong(keyPtrAddress);
            if (oldKeyAddress > 0) {
                long oldValueAddress = unsafe.getLong(valuePtrAddress);
                int oldKeySize = unsafe.getInt(keySizeAddress);
                int oldValueSize = unsafe.getInt(valueSizeAddress);
                
                free(oldKeyAddress, oldKeySize);
                unsafe.putLong(keyPtrAddress, 0L);
                unsafe.putInt(keySizeAddress, 0);
                
                free(oldValueAddress, oldValueSize);
                unsafe.putLong(valuePtrAddress, 0L);
                unsafe.putInt(valueSizeAddress, 0);
            }
            
            unsafe.putLong(keyPtrAddress, newKeyAddress);
            unsafe.putLong(valuePtrAddress, newValueAddress);
            unsafe.putInt(keySizeAddress, keySize);
            unsafe.putInt(valueSizeAddress, valueSize);
        }
        
        private static boolean removeEntry(long indexTableAddress, int index) {
            long keyPtrAddress = indexTableAddress + (index * ENTRY_LENGTH);
            long valuePtrAddress = keyPtrAddress + 8;
            long keySizeAddress = valuePtrAddress + 8;
            long valueSizeAddress = keySizeAddress + 4;

            long oldKeyAddress = unsafe.getLong(keyPtrAddress);
            if (oldKeyAddress > 0) {
                long oldValueAddress = unsafe.getLong(valuePtrAddress);
                int oldKeySize = unsafe.getInt(keySizeAddress);
                int oldValueSize = unsafe.getInt(valueSizeAddress);
                
                free(oldKeyAddress, oldKeySize);
                unsafe.putLong(keyPtrAddress, 0L);
                unsafe.putInt(keySizeAddress, 0);
                
                free(oldValueAddress, oldValueSize);
                unsafe.putLong(valuePtrAddress, 0L);
                unsafe.putInt(valueSizeAddress, 0);
                
                return true;
            }
            
            return false;
        }
        
        private static void run() {
            Random random = new Random();
            
            // Allocate index table
            long indexTableAddress = allocateIndexTable();
            
            for (int i = 0; i < ITERATION_COUNT; i++) {
                int index = random.nextInt(ENTRY_COUNT);
                int keySize = 1 + random.nextInt(MAX_KEY_LENGTH);
                int valueSize = 1 + random.nextInt(MAX_VALUE_LENGTH);
                try {
                    // Put an entry with specified key size and value size at the given index
                    putEntry(indexTableAddress, index, keySize, valueSize);
                } catch (OutOfMemoryError e) {
                    System.err.println(e.getMessage());
                    int removedEntryCount = 0;
                    // Apply eviction to make some available space for next puts
                    for (int j = 0; j < ENTRY_COUNT && removedEntryCount < ENTRY_COUNT_TO_EVICT; j++) {
                        if (removeEntry(indexTableAddress, j)) {
                            removedEntryCount++;
                        }
                    }
                }
            }
            
            // Remove and free all entries
            for (int j = 0; j < ENTRY_COUNT; j++) {
                removeEntry(indexTableAddress, j);
            }
            // Free index table
            freeIndexTable(indexTableAddress);

            // Dump all caller paths with allocated memories through them to console
            MySafe.dumpCallerPaths();
            
            // Generate caller path diagram
            MySafe.generateCallerPathDiagrams("native-memory-leak-hunting");
        }

    }
 
}
