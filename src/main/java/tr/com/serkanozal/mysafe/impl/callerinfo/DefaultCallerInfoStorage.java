package tr.com.serkanozal.mysafe.impl.callerinfo;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

public class DefaultCallerInfoStorage implements CallerInfoStorage {

    private final NonBlockingHashMapLong<CallerInfo> callerInfoMap =
            new NonBlockingHashMapLong<CallerInfo>(8, false);
    private final NonBlockingHashMapLong<CallerInfo> allocationCallerInfoMap =
            new NonBlockingHashMapLong<CallerInfo>(1024, false);
    
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
        return allocationCallerInfoMap.get(address);
    }

    @Override
    public void connectAddressWithCallerInfo(long address, CallerInfo callerInfo) {
        allocationCallerInfoMap.put(address, callerInfo);
    }

    @Override
    public void disconnectAddressFromCallerInfo(long address) {
        allocationCallerInfoMap.remove(address);
    }

}
