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
 * Contract point to be notified when illegal memory access occurred.
 * 
 * @author Serkan OZAL
 */
public interface IllegalMemoryAccessListener {

    /**
     * Called when illegal memory access occurred.
     * 
     * @param address           address which has been tried to be accessed
     * @param size              size from the given address to access
     * @param memoryAccessType  type of the memory access
     */
    void onIllegalMemoryAccess(long address, long size, MemoryAccessType memoryAccessType);
    
    enum MemoryAccessType {
        
        /**
         * Represents the memory access to read data
         */
        READ,
        
        /**
         * Represents the memory access to write data
         */
        WRITE,
        
        /**
         * Represents the memory access to read&write data (for example CAS, atomic increment, etc ...)
         */
        READ_WRITE,
        
        /**
         * Represents the memory access to free it
         */
        FREE,
        
        /**
         * Represents the memory access to reallocate it
         */
        REALLOCATE
        
    }
    
}
