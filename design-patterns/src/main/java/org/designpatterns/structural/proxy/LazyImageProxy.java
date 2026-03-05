package org.designpatterns.structural.proxy;

public class LazyImageProxy implements Image {
    private final String fileName;
    private RealImage realImage; // Lazy - created on first use

    public LazyImageProxy(String fileName) {
        this.fileName = fileName;
        System.out.println("  [Proxy] Proxy created for: " + fileName + " (no loading yet)");
    }

    @Override
    public void display() {
        if (realImage == null) {
            System.out.println("  [Proxy] First access - loading now...");
            realImage = new RealImage(fileName);
        } else {
            System.out.println("  [Proxy] Using cached image");
        }
        realImage.display();
    }

    @Override
    public String getFileName() {
        return fileName;
    }
}
