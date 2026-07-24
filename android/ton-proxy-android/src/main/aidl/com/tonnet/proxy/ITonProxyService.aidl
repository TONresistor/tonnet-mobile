package com.tonnet.proxy;

import com.tonnet.proxy.IProxyStateCallback;

interface ITonProxyService {
    void startSession(int mode);
    void stopSession();
    int getState();
    int getPort();
    String getMessage();
    void registerCallback(IProxyStateCallback callback);
    void unregisterCallback(IProxyStateCallback callback);
}
