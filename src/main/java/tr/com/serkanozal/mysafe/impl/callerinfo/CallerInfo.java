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
package tr.com.serkanozal.mysafe.impl.callerinfo;

import java.util.List;

public class CallerInfo {

    private static final int DEFAULT_MAX_CALLER_DEPTH = 4;
    public static final int MAX_CALLER_DEPTH = 
            Integer.getInteger("mysafe.maxCallerInfoDepth", DEFAULT_MAX_CALLER_DEPTH);
    
    public static final long NON_EXISTING_CALLER_INFO_KEY = 0L;
    public static final CallerInfo NON_EXISTING_CALLER_INFO = new CallerInfo(NON_EXISTING_CALLER_INFO_KEY, null);
    
    public static final long EMPTY_CALLER_INFO_KEY = -1L;
    public static final CallerInfo EMPTY_CALLER_INFO = new CallerInfo(EMPTY_CALLER_INFO_KEY, null);
    
    public final long key;
    public final List<CallerInfoEntry> callerInfoEntries;

    public CallerInfo(long key, List<CallerInfoEntry> callerInfoEntries) {
        this.key = key;
        this.callerInfoEntries = callerInfoEntries;
    }
    
    public static class CallerInfoEntry {
        
        public final String className;
        public final String methodName;
        public final int lineNumber;
        
        public CallerInfoEntry(String className, String methodName, int lineNumber) {
            this.className = className;
            this.methodName = methodName;
            this.lineNumber = lineNumber;
        }
        
        @Override
        public String toString() {
            return className + "." + methodName + ":" + lineNumber;
        }
        
    }

}
