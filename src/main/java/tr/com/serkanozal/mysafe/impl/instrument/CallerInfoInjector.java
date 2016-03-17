package tr.com.serkanozal.mysafe.impl.instrument;

import org.apache.log4j.Logger;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import tr.com.serkanozal.jillegal.agent.JillegalAgent;
import tr.com.serkanozal.mysafe.impl.MySafeDelegator;

public class CallerInfoInjector {

    private static final Logger LOGGER = Logger.getLogger(CallerInfoInjector.class);
    
    public void injectCallerInfo(Class<?> callerClass, String callerMethodName, long callerInfoKey) {
        try {
            Class<?> clazz = callerClass;
            while (clazz != null && clazz.getClassLoader() != null) {
                CtClass ctClazz = ClassPool.getDefault().get(clazz.getName());
                ctClazz.defrost();
                for (CtMethod ctMethod : ctClazz.getDeclaredMethods()) {
                    if (ctMethod.getName().equals(callerMethodName)) {
                        LOGGER.info("Injecting caller info into " + ctMethod.getLongName() + " ...");
                        ctMethod.insertBefore(MySafeDelegator.class.getName() + 
                                              ".startThreadLocalCallTracking(" + callerInfoKey + "L);");
                        ctMethod.insertAfter(MySafeDelegator.class.getName() +
                                             ".finishThreadLocalCallTracking();", true);
                    }
                }
                LOGGER.info("Redefining " + clazz.getName() +
                            " for caller info injection ...");
                byte[] injectedClassData = ctClazz.toBytecode();
                JillegalAgent.redefineClass(clazz, injectedClassData);
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            throw new RuntimeException("Couldn't inject caller info into " + 
                                       callerClass.getName() + "::" + callerMethodName, t);
        }
    }
    
}
