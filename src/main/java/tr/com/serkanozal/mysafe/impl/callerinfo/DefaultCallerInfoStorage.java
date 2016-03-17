package tr.com.serkanozal.mysafe.impl.callerinfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

public class DefaultCallerInfoStorage implements CallerInfoStorage {

    private final ConcurrentMap<Long, CallerInfo> callerInfoMap;
    private final NonBlockingHashMapLong<Long> allocationCallerInfoMap =
            new NonBlockingHashMapLong<Long>(1024, false);
    
    public DefaultCallerInfoStorage() {
        this.callerInfoMap = new ConcurrentHashMap<Long, CallerInfo>();
    }
    
    public DefaultCallerInfoStorage(ConcurrentMap<Long, CallerInfo> callerInfoMap) {
        this.callerInfoMap = callerInfoMap;
    }
    
    @Override
    public CallerInfo getCallerInfo(long callerInfoKey) {
        return callerInfoMap.get(callerInfoKey);
    }

    @Override
    public CallerInfo putCallerInfo(long callerInfoKey, CallerInfo callerInfo) {
        return callerInfoMap.putIfAbsent(callerInfoKey, callerInfo);
    }

    @Override
    public CallerInfo removeCallerInfo(long callerInfoKey) {
        return callerInfoMap.remove(callerInfoKey);
    }
    
    @Override
    public CallerInfo findCallerInfoByConnectedAddress(long address) {
        Long callerInfoKey = allocationCallerInfoMap.get(address);
        if (callerInfoKey != null) {
            return callerInfoMap.get(callerInfoKey);
        } else {
            return null;
        }
    }

    @Override
    public void connectAddressWithCallerInfo(long address, long callerInfoKey) {
        allocationCallerInfoMap.put(address, (Long) callerInfoKey);
    }

    @Override
    public void disconnectAddressFromCallerInfo(long address) {
        allocationCallerInfoMap.remove(address);
    }

}
