package org.designpatterns.structural.bridge;

public class AdvancedRemoteControl extends RemoteControl {

    public AdvancedRemoteControl(Device device) {
        super(device);
    }

    public void mute() {
        System.out.println("  [Advanced] Muting " + device.getName());
        device.setVolume(0);
    }
}
