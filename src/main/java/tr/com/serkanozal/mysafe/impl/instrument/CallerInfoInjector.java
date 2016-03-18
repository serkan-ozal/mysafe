package tr.com.serkanozal.mysafe.impl.instrument;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.bytecode.MethodInfo;
import tr.com.serkanozal.jillegal.agent.JillegalAgent;
import tr.com.serkanozal.mysafe.impl.MySafeDelegator;

public class CallerInfoInjector {

    private static final Logger LOGGER = Logger.getLogger(CallerInfoInjector.class);
    
    private boolean initialized = false;
    private final Map<String, Long> instrumentedCallers = new HashMap<String, Long>();
    
    private void ensureInitialized() {
        if (!initialized) {
            JillegalAgent.init();
            initialized = true;
        }
    }

    public synchronized long injectCallerInfo(Class<?> callerClass, String callerMethodName, int callerLineNumber, 
                                              long callerInfoKey, int callerDepth) {
        ensureInitialized();
        
        String instrumentationKey = callerClass.getName() + "." + callerMethodName + ":" + callerLineNumber;
        Long oldCallerInfoKey = instrumentedCallers.get(instrumentationKey);
        if (oldCallerInfoKey != null) {
            return callerInfoKey;
        }
        try {
            Class<?> clazz = callerClass;
            CtClass ctClazz = ClassPool.getDefault().get(clazz.getName());
            ctClazz.defrost();
            if ("<cinit>".equals(callerMethodName)) {
                CtConstructor ctInitializer = ctClazz.makeClassInitializer();
                injectCallerInfo(ctInitializer, callerMethodName, callerLineNumber, callerInfoKey, callerDepth);
            } else if ("<init>".equals(callerMethodName)) {
                for (CtConstructor ctConstructor : ctClazz.getDeclaredConstructors()) {
                    if (injectCallerInfo(ctConstructor, callerMethodName, callerLineNumber, callerInfoKey, callerDepth)) {
                        break;
                    }
                }
            } else {
                for (CtMethod ctMethod : ctClazz.getDeclaredMethods()) {
                    if (injectCallerInfo(ctMethod, callerMethodName, callerLineNumber, callerInfoKey, callerDepth)) {
                        break;
                    }
                }
            }    
            LOGGER.info("Redefining " + clazz.getName() +
                        " for caller info injection ...");
            byte[] injectedClassData = ctClazz.toBytecode();
            JillegalAgent.redefineClass(clazz, injectedClassData);
            
            instrumentedCallers.put(instrumentationKey, callerInfoKey);
            return callerInfoKey;
        } catch (Throwable t) {
            throw new RuntimeException("Couldn't inject caller info into " + 
                                       callerClass.getName() + "::" + callerMethodName, t);
        }
    }
    
    private boolean injectCallerInfo(CtBehavior ctBehavior, String callerMethodName, int callerLineNumber, 
                                     long callerInfoKey, int callerDepth) throws CannotCompileException {
        MethodInfo methodInfo = ctBehavior.getMethodInfo();
        int startLine = methodInfo.getLineNumber(0);
        int finishLine = methodInfo.getLineNumber(Integer.MAX_VALUE);
        boolean inject = ctBehavior.getName().equals(callerMethodName);
        boolean lineNumberExist = startLine != -1 && finishLine != -1; 
        if (lineNumberExist) {
            inject = ctBehavior.getName().equals(callerMethodName) 
                     && 
                     callerLineNumber >= startLine 
                     && 
                     callerLineNumber <= finishLine;
        }
        if (inject) {
            LOGGER.info("Injecting caller info at " + ctBehavior.getLongName() + ":" + callerLineNumber + " ...");
            ctBehavior.insertAt(callerLineNumber,
                                MySafeDelegator.class.getName() + 
                                ".startThreadLocalCallTracking(" + callerInfoKey + "L, " + callerDepth + ");");
            ctBehavior.insertAfter(MySafeDelegator.class.getName() +
                                   ".finishThreadLocalCallTracking(" + callerDepth + ");", true);
            if (lineNumberExist) {
                return true;
            }    
        }
        return false;
    }
    
}
