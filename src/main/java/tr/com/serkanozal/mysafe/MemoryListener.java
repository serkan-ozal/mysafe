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

/**
 * Contract point to be notified for memory usage (allocation/free/reallocation).
 * 
 * @author Serkan OZAL
 */
public interface MemoryListener {
    
    /**
     * Called before memory is allocated.
     * 
     * @param size size of the memory which will be allocated
     */
    void beforeAllocateMemory(long size);
    
    /**
     * Called before memory has been allocated.
     * 
     * @param address   address of the memory which has been allocated
     * @param size      size of the memory which has been allocated
     */
    void afterAllocateMemory(long address, long size);
    
    /**
     * Called before memory is free.
     * 
     * @param address address of the memory which will be free
     */
    
    void beforeFreeMemory(long address);
    
    /**
     * Called after memory has been free.
     * 
     * @param address           address of the memory which has been free
     * @param size              if deallocation is done on know address, it is size of the memory which has been free, 
     *                          otherwise, it is <tt>-1</tt>.     
     * @param isKnownAddress    <code>true</code> if address is one of the known addresses 
     *                          (allocated before and has internal allocation record), otherwise <code>false</code> 
     */
    void afterFreeMemory(long address, long size, boolean isKnownAddress);
    
    /**
     * Called before memory is reallocated.
     * 
     * @param oldAddress    old address of the memory which will be reallocated
     * @param oldSize       old size of the memory which will be reallocated
     */
    void beforeReallocateMemory(long oldAddress, long oldSize);
    
    /**
     * Called before memory has been reallocated.
     * 
     * @param oldAddress        old address of the memory which has been reallocated
     * @param oldSize           old size of the memory which has been reallocated
     * @param newAddress        new address of the memory which has been reallocated
     * @param newSize           new size of the memory which has been reallocated
     * @param isKnownAddress    <code>true</code> if address is one of the known addresses 
     *                          (allocated before and has internal allocation record), otherwise <code>false</code> 
     */
    void afterReallocateMemory(long oldAddress, long oldSize, 
                               long newAddress, long newSize, boolean isKnownAddress);
    
}
