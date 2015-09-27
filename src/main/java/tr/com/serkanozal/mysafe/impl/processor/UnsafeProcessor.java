package tr.com.serkanozal.mysafe.impl.processor;

import tr.com.serkanozal.jillegal.agent.ClassDataProcessor;
import tr.com.serkanozal.mysafe.impl.instrument.UnsafeUsageInstrumenter;
import tr.com.serkanozal.mysafe.impl.instrument.UnsafeUsageInstrumenterFactory;

public class UnsafeProcessor implements ClassDataProcessor {
    
    private final UnsafeUsageInstrumenter unsafeUsageInstrumenter = 
            UnsafeUsageInstrumenterFactory.createUnsafeUsageInstrumenter();
    
    @Override
    public byte[] process(ClassLoader loader, String className, byte[] classData) {
        if (loader != null) {
            return unsafeUsageInstrumenter.instrument(className, classData);
        } else {
            return classData;
        }
    }

}
