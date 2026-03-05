package org.designpatterns.structural.proxy;

public class AccessControlProxy implements Image {
    private final RealImage realImage;
    private final String userRole;

    public AccessControlProxy(String fileName, String userRole) {
        this.realImage = new RealImage(fileName);
        this.userRole = userRole;
    }

    @Override
    public void display() {
        if ("ADMIN".equals(userRole) || "USER".equals(userRole)) {
            System.out.println("  [AccessProxy] Access granted for role: " + userRole);
            realImage.display();
        } else {
            System.out.println("  [AccessProxy] ACCESS DENIED for role: " + userRole);
        }
    }

    @Override
    public String getFileName() {
        return realImage.getFileName();
    }
}
