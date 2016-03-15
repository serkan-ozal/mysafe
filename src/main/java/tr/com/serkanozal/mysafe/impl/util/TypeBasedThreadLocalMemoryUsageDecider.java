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
package tr.com.serkanozal.mysafe.impl.util;

import tr.com.serkanozal.mysafe.ParametricThreadLocalMemoryUsageDecider;

public class TypeBasedThreadLocalMemoryUsageDecider 
        implements ParametricThreadLocalMemoryUsageDecider {

    private Class<?> localThreadType;
    
    @Override
    public boolean isThreadLocal(Thread currentThread) {
        return localThreadType.isAssignableFrom(currentThread.getClass());
    }

    @Override
    public void initWithParameters(String[] params) {
        if (params == null || params.length != 1) {
            throw new IllegalArgumentException(
                    "'TypeBasedThreadLocalMemoryUsageDecider' needs only one argument " + 
                    "and it is type of the thread that will be accepted as it uses memory in thread-local mode!");
        }
        try {
            localThreadType = Class.forName(params[0]);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Couldn't find class of type of the thread " + 
                    "that will be accepted as it uses memory in thread-local mode!", e);
        }
    }

}
