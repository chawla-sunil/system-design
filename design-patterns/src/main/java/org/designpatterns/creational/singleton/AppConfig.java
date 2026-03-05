package org.designpatterns.creational.singleton;

/**
 * Enum-based Singleton - the recommended approach by Joshua Bloch (Effective Java).
 * Inherently thread-safe, serialization-safe, and reflection-proof.
 */
public enum AppConfig {

    INSTANCE;

    private String appName;
    private String version;
    private int maxRetries;

    AppConfig() {
        // Default configuration
        this.appName = "DesignPatternsApp";
        this.version = "1.0.0";
        this.maxRetries = 3;
        System.out.println("AppConfig initialized.");
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getVersion() {
        return version;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public String toString() {
        return "AppConfig{appName='" + appName + "', version='" + version + "', maxRetries=" + maxRetries + "}";
    }
}
