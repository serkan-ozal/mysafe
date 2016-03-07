package tr.com.serkanozal.mysafe.config;

public class AllocationPointConfig {

    public static final int DEFAULT_SIZE_PARAMETER_ORDER = 1;
    
    public final int sizeParameterOrder;
    
    public AllocationPointConfig() {
        this.sizeParameterOrder = DEFAULT_SIZE_PARAMETER_ORDER;
    }
    
    public AllocationPointConfig(int sizeParameterOrder) {
        this.sizeParameterOrder = sizeParameterOrder;
    }

}
