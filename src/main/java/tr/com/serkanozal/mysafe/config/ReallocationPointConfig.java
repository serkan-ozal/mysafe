package tr.com.serkanozal.mysafe.config;

public class ReallocationPointConfig {

    public static final int DEFAULT_OLD_ADDRESS_PARAMETER_ORDER = 1;
    public static final int DEFAULT_NEW_SIZE_PARAMETER_ORDER = 2;
    
    public final int oldAddressParameterOrder;
    public final int newSizeParameterOrder;
    
    public ReallocationPointConfig() {
        this.oldAddressParameterOrder = DEFAULT_OLD_ADDRESS_PARAMETER_ORDER;
        this.newSizeParameterOrder = DEFAULT_NEW_SIZE_PARAMETER_ORDER;
    }
    
    public ReallocationPointConfig(int oldAddressParameterOrder) {
        this.oldAddressParameterOrder = oldAddressParameterOrder;
        this.newSizeParameterOrder = DEFAULT_NEW_SIZE_PARAMETER_ORDER;
    }
    
    public ReallocationPointConfig(int oldAddressParameterOrder, int newSizeParameterOrder) {
        this.oldAddressParameterOrder = oldAddressParameterOrder;
        this.newSizeParameterOrder = newSizeParameterOrder;
    }

}
