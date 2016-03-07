package tr.com.serkanozal.mysafe.config;

public class FreePointConfig {

    public static final int DEFAULT_ADDRESS_PARAMETER_ORDER = 1;
    
    public final int addressParameterOrder;
    
    public FreePointConfig() {
        this.addressParameterOrder = DEFAULT_ADDRESS_PARAMETER_ORDER;
    }
    
    public FreePointConfig(int addressParameterOrder) {
        this.addressParameterOrder = addressParameterOrder;
    }
    
}
