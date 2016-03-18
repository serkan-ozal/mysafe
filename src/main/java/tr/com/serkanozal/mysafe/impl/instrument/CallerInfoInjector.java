package tr.com.serkanozal.mysafe.impl.instrument;

import org.apache.log4j.Logger;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.MethodInfo;
import tr.com.serkanozal.jillegal.agent.JillegalAgent;
import tr.com.serkanozal.mysafe.impl.MySafeDelegator;

public class CallerInfoInjector {

    private static final Logger LOGGER = Logger.getLogger(CallerInfoInjector.class);
    
    public void injectCallerInfo(Class<?> callerClass, String callerMethodName, int callerLineNumber, long callerInfoKey) {
        try {
            Class<?> clazz = callerClass;
            CtClass ctClazz = ClassPool.getDefault().get(clazz.getName());
            ctClazz.defrost();
            for (CtMethod ctMethod : ctClazz.getDeclaredMethods()) {
                MethodInfo methodInfo = ctMethod.getMethodInfo();
                int startLine = methodInfo.getLineNumber(0);
                int finishLine = methodInfo.getLineNumber(Integer.MAX_VALUE);
                boolean inject = ctMethod.getName().equals(callerMethodName);
                if (startLine != -1 && finishLine != -1) {
                    inject = ctMethod.getName().equals(callerMethodName) 
                             && 
                             callerLineNumber >= startLine 
                             && 
                             callerLineNumber <= finishLine;
                }
                if (inject) {
                    LOGGER.info("Injecting caller info into " + ctMethod.getLongName() + " ...");
                    ctMethod.insertAt(callerLineNumber, 
                                      MySafeDelegator.class.getName() + 
                                          ".startThreadLocalCallTracking(" + callerInfoKey + "L);");
                    ctMethod.insertAfter(MySafeDelegator.class.getName() +
                                         ".finishThreadLocalCallTracking();", true);
                    break;
                }
            }
            LOGGER.info("Redefining " + clazz.getName() +
                        " for caller info injection ...");
            byte[] injectedClassData = ctClazz.toBytecode();
            JillegalAgent.redefineClass(clazz, injectedClassData);
        } catch (Throwable t) {
            throw new RuntimeException("Couldn't inject caller info into " + 
                                       callerClass.getName() + "::" + callerMethodName, t);
        }
    }
    
}
