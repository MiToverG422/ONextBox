package com.mi.onextbox.refresh;

interface IRefreshRateController {
    List<String> getSupportedModes();
    boolean isRefreshRateDisplayEnabled();
    boolean setRefreshRateDisplayEnabled(boolean enabled);
    boolean setRefreshRateMode(int modeIndex);
    boolean resetRefreshRateMode();
}