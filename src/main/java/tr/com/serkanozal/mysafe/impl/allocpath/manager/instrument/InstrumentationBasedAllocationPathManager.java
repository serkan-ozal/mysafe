package tr.com.serkanozal.mysafe.impl.allocpath.manager.instrument;

import org.apache.log4j.Logger;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;
import tr.com.serkanozal.mysafe.impl.MySafeDelegator;
import tr.com.serkanozal.mysafe.impl.allocpath.AllocationPath;
import tr.com.serkanozal.mysafe.impl.allocpath.manager.AllocationPathManager;
import tr.com.serkanozal.mysafe.impl.allocpath.storage.AllocationPathStorage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author serkan
 */
public class InstrumentationBasedAllocationPathManager implements AllocationPathManager {

    private static final Logger LOGGER = Logger.getLogger(InstrumentationBasedAllocationPathManager.class);

    private static final ThreadLocal<ThreadLocalAllocationPath> THREAD_LOCAL_ALLOCATION_PATH_MAP =
            new ThreadLocal<ThreadLocalAllocationPath>() {
                protected ThreadLocalAllocationPath initialValue() {
                    return new ThreadLocalAllocationPath();
                };
            };

    private final NonBlockingHashMapLong<Boolean> prematureAllocationPaths =
            new NonBlockingHashMapLong<Boolean>(16);
    private final AtomicInteger callPointIdGenerator = new AtomicInteger();
    private final ConcurrentMap<Short , String> callPointId2NameMap =
            new ConcurrentHashMap<Short, String>(16);
    private final ConcurrentMap<String , Short> callPointNameToIdMap =
            new ConcurrentHashMap<String, Short>(16);
    private final AllocationPathInjector allocationPathInjector = new AllocationPathInjector();

    private static class ThreadLocalAllocationPath {

        private long allocationPathKey = 0;
        private int allocationCallPointIndex = 0;

    }

    private static long appendAllocationCallPointId(long allocationPathKey, short callPointId) {
        allocationPathKey = allocationPathKey << 16;
        allocationPathKey = allocationPathKey | callPointId;
        return allocationPathKey;
    }

    private static long removeAllocationCallPointId(long allocationPathKey, short callPointId) {
        short actualCallPointId = (short) (allocationPathKey & 0x0000FFFF);
        assert actualCallPointId == callPointId;
        allocationPathKey = allocationPathKey >>> 16;
        return allocationPathKey;
    }

    public static void pushThreadLocalAllocationCallPoint(short callPointId) {
        ThreadLocalAllocationPath threadLocalAllocationPath = THREAD_LOCAL_ALLOCATION_PATH_MAP.get();
        threadLocalAllocationPath.allocationPathKey =
                appendAllocationCallPointId(threadLocalAllocationPath.allocationPathKey, callPointId);
        threadLocalAllocationPath.allocationCallPointIndex++;
    }

    public static void popThreadLocalAllocationCallPoint(short callPointId) {
        ThreadLocalAllocationPath threadLocalAllocationPath = THREAD_LOCAL_ALLOCATION_PATH_MAP.get();
        assert threadLocalAllocationPath.allocationCallPointIndex > 0;
        threadLocalAllocationPath.allocationPathKey =
                removeAllocationCallPointId(threadLocalAllocationPath.allocationPathKey, callPointId);
        threadLocalAllocationPath.allocationCallPointIndex--;
    }

    private short nextCallPointId() {
        int nextCallPointId = callPointIdGenerator.get();
        if (nextCallPointId > Short.MAX_VALUE) {
            throw new IllegalStateException("No available id left for call point. " +
                    "There can be " + Short.MAX_VALUE + " unique call point at most!");
        }
        nextCallPointId = callPointIdGenerator.incrementAndGet();
        if (nextCallPointId > Short.MAX_VALUE) {
            throw new IllegalStateException("No available id left for call point. " +
                    "There can be " + Short.MAX_VALUE + " unique call point at most!");
        }
        return (short) nextCallPointId;
    }

    private void generateCallPoints(long allocationPathKey, String[] callPoints) {
        int length = callPoints.length;
        for (int i = 0; i < length; i++) {
            short callPointId = (short) (allocationPathKey & 0x0000FFFF);
            assert callPointId >= 0;
            String callPoint = null;
            if (callPointId > 0) {
                callPoint = callPointId2NameMap.get(callPointId);
            }
            if (callPoint != null) {
                callPoints[length - i - 1] = callPoint;
            } else {
                callPoints[length - i - 1] = "Unknown call point!";
            }
            allocationPathKey = allocationPathKey >>> 16;
        }
    }

    // "synchronized" is used for atomicity of whole operation, not for visibility
    private synchronized void backTraceAndInjectCallPoints(AllocationPathStorage allocationPathStorage,
                                                           long address, int skipFrameCount) {
        skipFrameCount++;
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        skipFrameCount++;
        int callPointCount = 0;
        long allocationPathKey = 0L;
        for (int i = 0; callPointCount < AllocationPath.MAX_ALLOCATION_PATH_DEPTH
                && i + skipFrameCount < stackTraceElements.length; i++) {
            StackTraceElement stackTraceElement = stackTraceElements[i + skipFrameCount];
            try {
                String className = stackTraceElement.getClassName();
                String methodName = stackTraceElement.getMethodName();
                Class<?> clazz =  Class.forName(className);
                // If this method will be instrumented,
                //      - `MySafeDelegator` should be known by the caller classloader
                //      - Caller method must not be native method
                if (clazz.getClassLoader().loadClass(MySafeDelegator.class.getName()) != null
                        && !stackTraceElement.isNativeMethod()) {
                    boolean callPointCreated = false;
                    short callPointId;
                    String callPoint = className + "." + methodName;
                    Short oldCallPointId = callPointNameToIdMap.get(callPoint);
                    if (oldCallPointId == null) {
                        callPointId = nextCallPointId();
                        callPointNameToIdMap.put(callPoint, callPointId);
                        callPointId2NameMap.put(callPointId, callPoint);
                        callPointCreated = true;
                    } else {
                        callPointId = oldCallPointId;
                    }

                    if (callPointCreated) {
                        allocationPathInjector.injectAllocationCallPoint(clazz, methodName, callPointId);
                    }

                    callPointCount++;
                    allocationPathKey = appendAllocationCallPointId(allocationPathKey, callPointId);

                    LOGGER.debug("\t- " + className + "." + methodName);
                }
            } catch (ClassNotFoundException e) {
                LOGGER.error(e);
            }
        }

        if (callPointCount < AllocationPath.MAX_ALLOCATION_PATH_DEPTH) {
            LOGGER.debug("A new premature call path has been detected ...");

            prematureAllocationPaths.put(allocationPathKey, Boolean.TRUE);
        }

        allocationPathStorage.connectAddressWithAllocationPath(address, allocationPathKey);
    }

    @Override
    public void saveAllocationPathOnAllocation(AllocationPathStorage allocationPathStorage, long address, int skipFrameCount) {
        skipFrameCount++;
        ThreadLocalAllocationPath threadLocalAllocationPath = THREAD_LOCAL_ALLOCATION_PATH_MAP.get();
        int callPointIndex = threadLocalAllocationPath.allocationCallPointIndex;
        assert callPointIndex >= 0;
        if (callPointIndex == 0) {
            // New call path
            LOGGER.debug("A new call path has been detected ...");

            // Back-trace call path and instrument each method
            // by creating, registering and injecting call points.
            backTraceAndInjectCallPoints(allocationPathStorage, address, skipFrameCount);
        } else {
            long allocationPathKey = threadLocalAllocationPath.allocationPathKey;
            if (callPointIndex < AllocationPath.MAX_ALLOCATION_PATH_DEPTH) {
                if (prematureAllocationPaths.containsKey(allocationPathKey)) {
                    allocationPathStorage.connectAddressWithAllocationPath(address, allocationPathKey);
                } else {
                    /*
                     * Back-trace call path and check
                     * whether current call path is really premature or it is mis-instrumented.
                     *
                     * - If it is really premature, add allocation path key to premature allocation path collections.
                     * - Else, Back-trace call path and instrument each method
                     *   by creating, registering and injecting call points until max allocation path depth.
                     */
                    backTraceAndInjectCallPoints(allocationPathStorage, address, skipFrameCount);
                }
            } else {
                allocationPathStorage.connectAddressWithAllocationPath(address, allocationPathKey);
            }
        }
    }

    @Override
    public void deleteAllocationPathOnFree(AllocationPathStorage allocationPathStorage, long address) {
        allocationPathStorage.disconnectAddressFromAllocationPath(address);
    }

    @Override
    public AllocationPath getAllocationPath(AllocationPathStorage allocationPathStorage, long address) {
        long allocationPathKey = allocationPathStorage.getAllocationPathKey(address);
        if (allocationPathKey <= 0) {
            return null;
        } else {
            final String[] callPoints = new String[AllocationPath.MAX_ALLOCATION_PATH_DEPTH];
            generateCallPoints(allocationPathKey, callPoints);
            return new AllocationPath(allocationPathKey, callPoints);
        }
    }

    @Override
    public AllocationPath getAllocationPath(long allocationPathKey) {
        final String[] callPoints = new String[AllocationPath.MAX_ALLOCATION_PATH_DEPTH];
        generateCallPoints(allocationPathKey, callPoints);
        return new AllocationPath(allocationPathKey, callPoints);
    }

}
