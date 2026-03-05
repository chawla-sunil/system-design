package org.designpatterns.structural.bridge;

public class TV implements Device {
    private boolean on = false;
    private int volume = 30;

    @Override
    public void powerOn() { on = true; System.out.println("  TV is ON"); }
    @Override
    public void powerOff() { on = false; System.out.println("  TV is OFF"); }
    @Override
    public boolean isEnabled() { return on; }
    @Override
    public int getVolume() { return volume; }
    @Override
    public void setVolume(int volume) { this.volume = Math.max(0, Math.min(100, volume)); System.out.println("  TV volume: " + this.volume); }
    @Override
    public String getName() { return "TV"; }
}
