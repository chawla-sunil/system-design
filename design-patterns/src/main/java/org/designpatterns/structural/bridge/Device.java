package org.designpatterns.structural.bridge;

public interface Device {
    void powerOn();
    void powerOff();
    boolean isEnabled();
    int getVolume();
    void setVolume(int volume);
    String getName();
}
