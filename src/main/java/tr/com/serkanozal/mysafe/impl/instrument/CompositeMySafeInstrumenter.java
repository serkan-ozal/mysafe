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
package tr.com.serkanozal.mysafe.impl.instrument;

import java.util.HashSet;
import java.util.Set;

class CompositeMySafeInstrumenter implements MySafeInstrumenter {

    private final Set<String> ignoredOnes;
    private final MySafeInstrumenter[] instrumenters;
    
    CompositeMySafeInstrumenter(MySafeInstrumenter[] instrumenters) {
        this.instrumenters = instrumenters;
        String ignoreByMySafeProperty = System.getProperty("mysafe.ignoreByMySafe");
        if (ignoreByMySafeProperty != null) {
            String[] ignoreByMySafePropertyParts = ignoreByMySafeProperty.split(",");
            ignoredOnes = new HashSet<String>(ignoreByMySafePropertyParts.length);
            for (String ignoredClass : ignoreByMySafePropertyParts) {
                ignoredOnes.add(ignoredClass);
            }
        } else {
            ignoredOnes = null;
        }
    }
    
    @Override
    public byte[] instrument(String className, byte[] classData) {
        if (ignoredOnes != null) {
            for (String ignoredClass : ignoredOnes) {
                if (className.startsWith(ignoredClass)) {
                    return classData;
                }
            }
        }
        
        for (MySafeInstrumenter instrumenter : instrumenters) {
            classData = instrumenter.instrument(className, classData);
        }
        return classData;
    }

}
