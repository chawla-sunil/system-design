package org.systemdesign.wms.model;

import org.systemdesign.wms.model.enums.UserRole;

import java.util.UUID;

/**
 * Represents a warehouse staff member.
 */
public class User {

    private final String userId;
    private final String name;
    private final String email;
    private final UserRole role;

    public User(String name, String email, UserRole role) {
        this.userId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }

    @Override
    public String toString() {
        return "User{" + name + ", role=" + role + "}";
    }
}

