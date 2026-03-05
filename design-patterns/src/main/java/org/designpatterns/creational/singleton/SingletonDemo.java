package org.designpatterns.creational.singleton;

public class SingletonDemo {

    public static void run() {
        System.out.println("=== SINGLETON PATTERN DEMO ===\n");

        // 1. Double-checked locking singleton
        System.out.println("--- Double-Checked Locking Singleton ---");
        DatabaseConnection conn1 = DatabaseConnection.getInstance();
        DatabaseConnection conn2 = DatabaseConnection.getInstance();

        conn1.connect();
        System.out.println("conn1 == conn2 ? " + (conn1 == conn2)); // true
        System.out.println("conn2.isConnected() ? " + conn2.isConnected()); // true - same instance

        conn1.disconnect();

        // 2. Enum-based singleton
        System.out.println("\n--- Enum-Based Singleton ---");
        AppConfig config1 = AppConfig.INSTANCE;
        AppConfig config2 = AppConfig.INSTANCE;

        config1.setAppName("MyApp");
        System.out.println("config1 == config2 ? " + (config1 == config2)); // true
        System.out.println("config2.getAppName() = " + config2.getAppName()); // MyApp - same instance
        System.out.println("Config: " + config1);

        System.out.println();
    }
}
