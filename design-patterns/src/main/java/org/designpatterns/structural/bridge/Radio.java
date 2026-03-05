package org.designpatterns.structural.bridge;

public class Radio implements Device {
    private boolean on = false;
    private int volume = 20;

    @Override
    public void powerOn() { on = true; System.out.println("  Radio is ON"); }
    @Override
    public void powerOff() { on = false; System.out.println("  Radio is OFF"); }
    @Override
    public boolean isEnabled() { return on; }
    @Override
    public int getVolume() { return volume; }
    @Override
    public void setVolume(int volume) { this.volume = Math.max(0, Math.min(100, volume)); System.out.println("  Radio volume: " + this.volume); }
    @Override
    public String getName() { return "Radio"; }
}
