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

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import sun.misc.Unsafe;
import tr.com.serkanozal.jillegal.agent.JillegalAgent;
import tr.com.serkanozal.mysafe.impl.UnsafeDelegator;
import tr.com.serkanozal.mysafe.impl.mx.MySafeMXBeanImpl;
import tr.com.serkanozal.mysafe.impl.processor.UnsafeProcessor;

/**
 * Entrance point (God class) for <tt>MySafe</tt> framework.
 * Simply drives <tt>MySafe</tt>.
 * 
 * @author Serkan OZAL
 */
public final class MySafe {

    private static final Unsafe UNSAFE;
    private static final AtomicBoolean active = new AtomicBoolean(false);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private static MySafeMXBean mySafeMXBean;
    
    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
            
            initialize();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
    
    private MySafe() {
        throw new UnsupportedOperationException("Not avaiable for instantiation!");
    }
    
    /**
     * Gets the {@link sun.misc.Unsafe} instance.
     * 
     * @return the {@link sun.misc.Unsafe} instance
     */
    public static Unsafe getUnsafe() {
        return UNSAFE;
    }
    
    /**
     * Initialize <tt>MySafe</tt>.
     */
    public static void initialize() {
        if (initialized.compareAndSet(false, true)) {
            initializeMXBean();
        }
    }
    
    private static void initializeMXBean() {
        if (!Boolean.getBoolean("mysafe.disableMXBean")) {
            try {
                final String MXBEAN_REGISTERED_PROPERTY_NAME = "mysafe.mxBeanRegistered";
                synchronized (System.class) {
                    if (System.getProperty(MXBEAN_REGISTERED_PROPERTY_NAME) == null) {
                        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
                        MySafeMXBean mySafeMXBean = new MySafeMXBeanImpl();
                        ObjectName mySafeMXBeanObjectName = new ObjectName("tr.com.serkanozal:type=MySafe");
                        mBeanServer.registerMBean(mySafeMXBean, mySafeMXBeanObjectName);
                        System.setProperty(MXBEAN_REGISTERED_PROPERTY_NAME, "true");
                    }    
                }
            } catch (Throwable t) {
                throw new IllegalStateException("Unable to register MySafe MX Bean", t);
            }
        }    
    }
    
    /**
     * Activate <tt>MySafe</tt> programmatically in the system 
     * on {@link sun.misc.Unsafe} calls.
     */
    public static void youAreMine() {
        if (active.compareAndSet(false, true)) {
            JillegalAgent.init("-p " + UnsafeProcessor.class.getName());
        }
    }

    /**
     * Enables memory check on {@link sun.misc.Unsafe} calls 
     * to prevent illegal memory access.
     */
    public static void enableSafeMode() {
        UnsafeDelegator.enableSafeMode();
    }
    
    /**
     * Disables memory check on {@link sun.misc.Unsafe} calls.
     */
    public static void disableSafeMode() {
        UnsafeDelegator.disableSafeMode();
    }
    
    /**
     * Gets the allocated memory size in bytes.
     * 
     * @return the allocated memory size in bytes
     */
    public static long getAllocatedMemorySize() {
        return UnsafeDelegator.getAllocatedMemorySize();
    }
    
    /**
     * Gets the {@link MySafeMXBean} instance exported to JMX.
     * 
     * @return the {@link MySafeMXBean} instance exported to JMX.
     * @throws IllegalStateException if JMX support is disabled 
     *                               by <code>mysafe.disableMXBean</code> property
     */
    public static MySafeMXBean getMySafeMXBean() {
        if (Boolean.getBoolean("mysafe.disableMXBean")) {
            throw new IllegalStateException("MXBean support is disabled!");
        }
        return mySafeMXBean;
    }
    
    /**
     * Registers the given {@link MemoryListener} instance.
     * 
     * @param listener the {@link MemoryListener} instance to be registered
     */
    public static void registerUnsafeListener(MemoryListener listener) {
        UnsafeDelegator.registerUnsafeListener(listener);
    }
    
    /**
     * Deregisters the given {@link MemoryListener} instance.
     * 
     * @param listener the {@link MemoryListener} instance to be deregistered
     */
    public static void deregisterUnsafeListener(MemoryListener listener) {
        UnsafeDelegator.deregisterUnsafeListener(listener);
    }

    /**
     * Iterates on all allocated memories.
     * 
     * @param iterator the {@link AllocatedMemoryIterator} instance to be notified 
     *                 for each allocated memory while iterating
     */
    public static void iterateOnAllocatedMemories(AllocatedMemoryIterator iterator) {
        UnsafeDelegator.iterateOnAllocatedMemories(iterator);
    }
    
    /**
     * Dumps the allocated memories to console (standard output).
     */
    public static void dumpAllocatedMemories() {
        dumpAllocatedMemories(System.out);
    }
    
    /**
     * Dumps the allocated memories to given {@link PrintStream}.
     * 
     * @param ps the {@link PrintStream} instance to dump allocated memories
     */
    public static void dumpAllocatedMemories(final PrintStream ps) {
        UnsafeDelegator.dumpAllocatedMemories(ps, UNSAFE);
    }
    
}
