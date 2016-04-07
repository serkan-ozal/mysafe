package tr.com.serkanozal.mysafe.impl.callerinfo;

public class CallerInfoAllocatedMemory {

    public final CallerInfo callerInfo;
    public final long allocatedMemory;
    
    public CallerInfoAllocatedMemory(CallerInfo callerInfo, long allocatedMemory) {
        this.callerInfo = callerInfo;
        this.allocatedMemory = allocatedMemory;
    }

}
