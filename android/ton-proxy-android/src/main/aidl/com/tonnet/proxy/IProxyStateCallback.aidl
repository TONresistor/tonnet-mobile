package com.tonnet.proxy;

interface IProxyStateCallback {
    void onStateChanged(int state, int port, String message);
}
