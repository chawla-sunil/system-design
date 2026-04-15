package org.systemdesign.carrentalsystem.service;

import org.systemdesign.carrentalsystem.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserService {
    private final List<User> users;

    public UserService() {
        this.users = new ArrayList<>();
    }

    public User registerUser(String name, String email, String phone, String drivingLicense) {
        // Check for duplicate email
        Optional<User> existing = users.stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                .findFirst();

        if (existing.isPresent()) {
            throw new IllegalArgumentException("User with email " + email + " already exists.");
        }

        User user = new User(name, email, phone, drivingLicense);
        users.add(user);
        return user;
    }

    public Optional<User> getUserById(String userId) {
        return users.stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst();
    }

    public Optional<User> getUserByEmail(String email) {
        return users.stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users);
    }
}

