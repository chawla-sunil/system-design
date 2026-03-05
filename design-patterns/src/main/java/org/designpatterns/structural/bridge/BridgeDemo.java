package org.designpatterns.structural.bridge;

public class BridgeDemo {
    public static void run() {
        System.out.println("=== BRIDGE PATTERN DEMO ===\n");

        System.out.println("--- Basic Remote + TV ---");
        RemoteControl tvRemote = new RemoteControl(new TV());
        tvRemote.togglePower();
        tvRemote.volumeUp();
        tvRemote.volumeDown();

        System.out.println("\n--- Advanced Remote + Radio ---");
        AdvancedRemoteControl radioRemote = new AdvancedRemoteControl(new Radio());
        radioRemote.togglePower();
        radioRemote.volumeUp();
        radioRemote.mute();

        System.out.println();
    }
}
