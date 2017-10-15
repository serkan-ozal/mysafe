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

/**
 * Contract point to store allocated memories.
 * 
 * @author Serkan OZAL
 */
public interface AllocatedMemoryStorage {
    
    /**
     * Represents any invalid value.
     */
    long INVALID = -1;
    
    /**
     * Checks if the given address is allocated or not.
     * 
     * @param address the address to be checked if it is allocated or not
     * @return <code>true</code> if if the given address is allocated, 
     *         otherwise <code>false</code>
     */
    boolean contains(long address);
    
    /**
     * Checks if the specified memory region starting from given address 
     * as given size is in allocated memory area or not.
     * 
     * @param address   the start address of the specified memory region
     * @param size      the size of the specified memory region
     * @return <code>true</code> if if the given specified memory region 
     *         is in allocated memory area, otherwise <code>false</code>
     */
    boolean contains(long address, long size);
    
    /**
     * Gets the allocation size of the given address.
     * 
     * @param address the address whose allocation size will be retrieved
     * @return allocation size of the given address if it is exist (allocated), 
     *         otherwise {@link AllocatedMemoryStorage#INVALID}
     */
    long get(long address);
    
    /**
     * Stores given address with its allocation size.
     * 
     * @param address   the address
     * @param size      the allocation size
     */
    void put(long address, long size);
    
    /**
     * Removes the given address.
     * 
     * @param address the address to be removed
     * @return allocation size of the removed address if it is exist (allocated), 
     *         otherwise {@link AllocatedMemoryStorage#INVALID}
     */
    long remove(long address);
    
    /**
     * Iterates on the allocated memory addresses.
     * 
     * @param iterator the {@link AllocatedMemoryIterator} instance to be notified 
     *                 for each allocated memory while iterating
     */
    void iterate(AllocatedMemoryIterator iterator);
    
    /**
     * Returns <tt>true</tt> if storage is empty, otherwise returns <tt>false</tt>.
     * 
     * @return <tt>true</tt> if storage is empty, <tt>false</tt> otherwise
     */
    boolean isEmpty();
    
}
