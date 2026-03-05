package org.designpatterns.structural.proxy;

public class RealImage implements Image {
    private final String fileName;

    public RealImage(String fileName) {
        this.fileName = fileName;
        loadFromDisk(); // Expensive operation
    }

    private void loadFromDisk() {
        System.out.println("  [RealImage] Loading image from disk: " + fileName + " (slow operation...)");
    }

    @Override
    public void display() {
        System.out.println("  [RealImage] Displaying: " + fileName);
    }

    @Override
    public String getFileName() {
        return fileName;
    }
}
