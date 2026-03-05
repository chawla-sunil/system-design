package org.designpatterns.structural.proxy;

public class ProxyDemo {
    public static void run() {
        System.out.println("=== PROXY PATTERN DEMO ===\n");

        // 1. Virtual Proxy (Lazy Loading)
        System.out.println("--- Virtual Proxy (Lazy Loading) ---");
        Image image1 = new LazyImageProxy("photo1.jpg");
        Image image2 = new LazyImageProxy("photo2.jpg");

        // Images not loaded yet - only proxies created
        System.out.println("\nFirst display of image1:");
        image1.display();  // Loads and displays

        System.out.println("\nSecond display of image1:");
        image1.display();  // Uses cached - no reload

        System.out.println("\nFirst display of image2:");
        image2.display();  // Now image2 loads

        // 2. Protection Proxy (Access Control)
        System.out.println("\n--- Protection Proxy (Access Control) ---");
        Image adminImage = new AccessControlProxy("confidential.jpg", "ADMIN");
        Image guestImage = new AccessControlProxy("confidential.jpg", "GUEST");

        System.out.println("\nAdmin access:");
        adminImage.display();

        System.out.println("\nGuest access:");
        guestImage.display();

        System.out.println();
    }
}
