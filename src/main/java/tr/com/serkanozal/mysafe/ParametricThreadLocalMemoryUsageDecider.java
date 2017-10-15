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
 * Contract point for parametric {@link ThreadLocalMemoryUsageDecider} implementations.
 * Parameters are specific to the implementation, 
 * so their interpretation depends on the actual implementation.
 * 
 * @author Serkan OZAL
 */
public interface ParametricThreadLocalMemoryUsageDecider 
        extends ThreadLocalMemoryUsageDecider {
    
    /**
     * Initializes {@link ThreadLocalMemoryUsageDecider} 
     * implementation with given parameters.
     * 
     * @param params parameters to be used for initializing
     */
    void initWithParameters(String[] params);
    
}
