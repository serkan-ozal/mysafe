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

public final class MySafeInstrumenterFactory {

    private MySafeInstrumenterFactory() {
        
    }
    
    public static MySafeInstrumenter createMySafeInstrumenter() {
        MySafeInstrumenter[] instrumenters = 
                new MySafeInstrumenter[] {
                    new UnsafeInterceptorInstrumenter(),
                    new CustomMemoryManagementInstrumenter()
                };
        return new CompositeMySafeInstrumenter(instrumenters);
    }
    
}
