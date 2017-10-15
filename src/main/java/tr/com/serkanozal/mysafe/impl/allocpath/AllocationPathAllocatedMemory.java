package tr.com.serkanozal.mysafe.impl.allocpath;

public class AllocationPathAllocatedMemory {

    public final AllocationPath allocationPath;
    public final long allocatedMemory;
    
    public AllocationPathAllocatedMemory(AllocationPath allocationPath, long allocatedMemory) {
        this.allocationPath = allocationPath;
        this.allocatedMemory = allocatedMemory;
    }

}
