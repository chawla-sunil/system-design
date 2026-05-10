package org.systemdesign.splitwise.model;

import java.util.Objects;

public record User(String id, String name, String email) {

    public User {
        if (isBlank(id)) {
            throw new IllegalArgumentException("user id cannot be blank");
        }
        if (isBlank(name)) {
            throw new IllegalArgumentException("user name cannot be blank");
        }
        if (isBlank(email) || !email.contains("@")) {
            throw new IllegalArgumentException("email must be valid");
        }

        id = id.trim();
        name = name.trim();
        email = email.trim().toLowerCase();
    }

    private static boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}

