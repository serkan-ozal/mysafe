package tr.com.serkanozal.mysafe.impl.processor;

import tr.com.serkanozal.jillegal.agent.ClassDataProcessor;
import tr.com.serkanozal.mysafe.MySafe;
import tr.com.serkanozal.mysafe.impl.instrument.MySafeInstrumenter;
import tr.com.serkanozal.mysafe.impl.instrument.MySafeInstrumenterFactory;

public class MySafeProcessor implements ClassDataProcessor {
    
    private final MySafeInstrumenter mySafeInstrumenter = 
            MySafeInstrumenterFactory.createMySafeInstrumenter();
    
    static {
        MySafe.initialize();
    }
    
    @Override
    public byte[] process(ClassLoader loader, String className, byte[] classData) {
        if (loader != null) {
            return mySafeInstrumenter.instrument(className, classData);
        } else {
            return classData;
        }
    }

}
