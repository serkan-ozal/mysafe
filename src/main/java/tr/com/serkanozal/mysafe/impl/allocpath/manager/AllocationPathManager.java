package tr.com.serkanozal.mysafe.impl.allocpath.manager;

import tr.com.serkanozal.mysafe.impl.allocpath.AllocationPath;
import tr.com.serkanozal.mysafe.impl.allocpath.storage.AllocationPathStorage;

/**
 * @author serkan
 */
public interface AllocationPathManager {

    void saveAllocationPathOnAllocation(AllocationPathStorage allocationPathStorage, long address, int skipFrameCount);

    void deleteAllocationPathOnFree(AllocationPathStorage allocationPathStorage, long address);

    AllocationPath getAllocationPath(AllocationPathStorage allocationPathStorage, long address);

    AllocationPath getAllocationPath(long allocationPathKey);

}
