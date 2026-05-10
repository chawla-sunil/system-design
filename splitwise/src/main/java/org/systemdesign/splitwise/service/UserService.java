package org.systemdesign.splitwise.service;

import java.util.List;
import org.systemdesign.splitwise.exception.EntityNotFoundException;
import org.systemdesign.splitwise.exception.ValidationException;
import org.systemdesign.splitwise.model.User;
import org.systemdesign.splitwise.repository.UserRepository;

public final class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User registerUser(String id, String name, String email) {
        if (userRepository.existsById(id)) {
            throw new ValidationException("user already exists: " + id);
        }

        User user = new User(id, name, email);
        return userRepository.save(user);
    }

    public User getRequiredUser(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("user not found: " + userId));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}

