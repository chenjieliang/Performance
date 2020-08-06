package com.jarvis.performance.cpu;

import com.jarvis.performance.cpu.IUsageUpdateCallback;

oneway interface IUsageUpdateService {

    void registerCallback(IUsageUpdateCallback callback);
    
    void unregisterCallback(IUsageUpdateCallback callback);
    
    void stopResident();

    void startResident();
    
    void reloadSettings();
}