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

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import sun.misc.Unsafe;
import tr.com.serkanozal.jillegal.agent.JillegalAgent;
import tr.com.serkanozal.mysafe.impl.MySafeDelegator;
import tr.com.serkanozal.mysafe.impl.mx.MySafeMXBeanImpl;
import tr.com.serkanozal.mysafe.impl.processor.MySafeProcessor;

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
        if (Boolean.getBoolean("mysafe.enableMXBean")) {
            try {
                final String MXBEAN_REGISTERED_PROPERTY_NAME = "mysafe.mxBeanRegistered";
                synchronized (System.class) {
                    if (System.getProperty(MXBEAN_REGISTERED_PROPERTY_NAME) == null) {
                        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
                        MySafeMXBean mxBean = new MySafeMXBeanImpl();
                        ObjectName mxBeanObjectName = new ObjectName("tr.com.serkanozal:type=MySafe");
                        mBeanServer.registerMBean(mxBean, mxBeanObjectName);
                        mySafeMXBean = mxBean;
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
            JillegalAgent.init("-p " + MySafeProcessor.class.getName());
        }
    }

    /**
     * Returns <tt>true</tt> if safe memory management (allocate/free/reallocate) 
     * mode is enabled, otherwise returns <tt>false</tt>.
     * 
     * @return <code>true</tt> if safe memory management (allocate/free/reallocate) 
     *         is enabled, otherwise <tt>false</tt>
     */
    public static boolean isSafeMemoryManagementModeEnabled() {
        return MySafeDelegator.isSafeMemoryManagementModeEnabled();
    }
    
    /**
     * Returns <tt>true</tt> if safe memory access (read/write) 
     * mode is enabled, otherwise returns <tt>false</tt>.
     * 
     * @return <code>true</tt> if safe memory access (read/write) 
     *         is enabled, otherwise <tt>false</tt>
     */
    public static boolean isSafeMemoryAccessModeEnabled() {
        return MySafeDelegator.isSafeMemoryAccessModeEnabled();
    }
    
    /**
     * Gets the allocated memory size in bytes.
     * 
     * @return the allocated memory size in bytes
     */
    public static long getAllocatedMemorySize() {
        return MySafeDelegator.getAllocatedMemorySize();
    }
    
    /**
     * Gets the {@link MySafeMXBean} instance exported to JMX.
     * 
     * @return the {@link MySafeMXBean} instance exported to JMX.
     * @throws IllegalStateException if JMX support is disabled 
     *                               by <code>mysafe.disableMXBean</code> property
     */
    public static MySafeMXBean getMySafeMXBean() {
        if (mySafeMXBean == null) {
            throw new IllegalStateException("MXBean support is not enabled!");
        }
        return mySafeMXBean;
    }
    
    /**
     * Registers the given {@link MemoryListener} instance.
     * 
     * @param listener the {@link MemoryListener} instance to be registered
     */
    public static void registerMemoryListener(MemoryListener listener) {
        MySafeDelegator.registerMemoryListener(listener);
    }
    
    /**
     * Deregisters the given {@link MemoryListener} instance.
     * 
     * @param listener the {@link MemoryListener} instance to be deregistered
     */
    public static void deregisterMemoryListener(MemoryListener listener) {
        MySafeDelegator.deregisterMemoryListener(listener);
    }

    /**
     * Gets the used {@link IllegalMemoryAccessListener} instance.
     *
     * @return the used {@link IllegalMemoryAccessListener} instance or
     *         <code>null</code> if not available
     */
    public static IllegalMemoryAccessListener getIllegalMemoryAccessListener() {
        return MySafeDelegator.getIllegalMemoryAccessListener();
    }

    /**
     * Sets the given {@link IllegalMemoryAccessListener} instance.
     *
     * @param illegalMemoryAccessListener the {@link IllegalMemoryAccessListener} instance to be set
     */
    public static void setIllegalMemoryAccessListener(IllegalMemoryAccessListener illegalMemoryAccessListener) {
        MySafeDelegator.setIllegalMemoryAccessListener(illegalMemoryAccessListener);
    }

    /**
     * Iterates on all allocated memories.
     * 
     * @param iterator the {@link AllocatedMemoryIterator} instance to be notified 
     *                 for each allocated memory while iterating
     */
    public static void iterateOnAllocatedMemories(AllocatedMemoryIterator iterator) {
        MySafeDelegator.iterateOnAllocatedMemories(iterator);
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
    public static void dumpAllocatedMemories(PrintStream ps) {
        MySafeDelegator.dumpAllocatedMemories(ps, UNSAFE);
    }
    
    /**
     * Dumps the allocation paths, with allocated memory informations through them,
     * to console (standard output).
     */
    public static void dumpAllocationPaths() {
        dumpAllocationPaths(System.out);
    }
    
    /**
     * Dumps the allocation paths, with allocated memory informations through them,
     * to given {@link PrintStream}.
     * 
     * @param ps the {@link PrintStream} instance to dump allocation paths
     */
    public static void dumpAllocationPaths(PrintStream ps) {
        MySafeDelegator.dumpAllocationPaths(ps);
    }
    
    /**
     * Generates allocation path diagram into default (<code>mysafe-allocation-path.png</code>) file.
     */
    public static void generateAllocationPathDiagrams() {
        MySafeDelegator.generateAllocationPathDiagrams();
    }
    
    /**
     * Generates allocation path diagram into given file.
     * 
     * @param diagramName name of the file where diagram will be generated into
     */
    public static void generateAllocationPathDiagrams(String diagramName) {
        MySafeDelegator.generateAllocationPathDiagrams(diagramName);
    }
    
}
