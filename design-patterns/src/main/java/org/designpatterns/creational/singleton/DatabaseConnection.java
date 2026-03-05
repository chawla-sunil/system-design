package org.designpatterns.creational.singleton;

/**
 * Singleton Pattern - Database Connection Pool Example
 *
 * Ensures only one instance of DatabaseConnection exists throughout the application.
 * Uses double-checked locking for thread safety with lazy initialization.
 */
public class DatabaseConnection {

    // volatile ensures visibility across threads
    private static volatile DatabaseConnection instance;

    private final String connectionUrl;
    private boolean connected;

    // Private constructor prevents external instantiation
    private DatabaseConnection() {
        this.connectionUrl = "jdbc:mysql://localhost:3306/mydb";
        this.connected = false;
        System.out.println("DatabaseConnection instance created.");
    }

    // Double-checked locking - thread-safe lazy initialization
    public static DatabaseConnection getInstance() {
        if (instance == null) {                     // First check (no lock)
            synchronized (DatabaseConnection.class) {
                if (instance == null) {             // Second check (with lock)
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    public void connect() {
        this.connected = true;
        System.out.println("Connected to: " + connectionUrl);
    }

    public void disconnect() {
        this.connected = false;
        System.out.println("Disconnected from database.");
    }

    public boolean isConnected() {
        return connected;
    }

    public String getConnectionUrl() {
        return connectionUrl;
    }
}
