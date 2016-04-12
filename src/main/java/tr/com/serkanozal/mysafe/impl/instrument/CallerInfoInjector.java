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

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import tr.com.serkanozal.jillegal.agent.JillegalAgent;
import tr.com.serkanozal.mysafe.impl.MySafeDelegator;

public class CallerInfoInjector {

    private static final Logger LOGGER = Logger.getLogger(CallerInfoInjector.class);
    
    private boolean initialized = false;
    private final Map<String, Short> instrumentedCallPoints = new HashMap<String, Short>();
    
    private void ensureInitialized() {
        if (!initialized) {
            JillegalAgent.init();
            initialized = true;
        }
    }

    public synchronized void injectCallerPoint(Class<?> callPointClass, String callPointMethodName, short callPointId) {
        ensureInitialized();
        
        String callPointKey = callPointClass.getName() + "." + callPointMethodName;
        Short oldCallPointKey = instrumentedCallPoints.get(callPointKey);
        if (oldCallPointKey == null) {
            try {
                Class<?> clazz = callPointClass;
                CtClass ctClazz = ClassPool.getDefault().get(clazz.getName());
                ctClazz.defrost();
                if ("<clinit>".equals(callPointMethodName)) {
                    CtConstructor ctInitializer = ctClazz.makeClassInitializer();
                    injectCallPoint(ctInitializer,  callPointId);
                } else if ("<init>".equals(callPointMethodName)) {
                    for (CtConstructor ctConstructor : ctClazz.getDeclaredConstructors()) {
                        injectCallPoint(ctConstructor, callPointId);
                    }
                } else {
                    for (CtMethod ctMethod : ctClazz.getDeclaredMethods()) {
                        if (ctMethod.getName().equals(callPointMethodName)) {
                            injectCallPoint(ctMethod, callPointId);
                        }
                    }
                }    
                
                LOGGER.info("Redefining " + clazz.getName() + " for call point injection ...");
                
                byte[] injectedClassData = ctClazz.toBytecode();
                JillegalAgent.redefineClass(clazz, injectedClassData);
                
                instrumentedCallPoints.put(callPointKey, callPointId);
            } catch (Throwable t) {
                throw new RuntimeException("Couldn't inject call point into " + 
                                           callPointClass.getName() + "::" + callPointMethodName, t);
            }
        }
    }
    
    private void injectCallPoint(CtBehavior ctBehavior, short callPointId) 
            throws CannotCompileException {
        LOGGER.info("Injecting call point to " + ctBehavior.getLongName() + " ...");
        
        ctBehavior.insertBefore(MySafeDelegator.class.getName() + 
                                    ".pushThreadLocalCallPoint(" + "(short) " + callPointId +  ");");
        ctBehavior.insertAfter(MySafeDelegator.class.getName() +
                                   ".popThreadLocalCallPoint(" + "(short) " + callPointId + ");", true);
    }
    
}
