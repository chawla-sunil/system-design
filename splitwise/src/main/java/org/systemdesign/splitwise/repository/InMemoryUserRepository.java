package org.systemdesign.splitwise.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.systemdesign.splitwise.model.User;

public final class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> users = new LinkedHashMap<>();

    @Override
    public synchronized User save(User user) {
        users.put(user.id(), user);
        return user;
    }

    @Override
    public synchronized Optional<User> findById(String userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public synchronized boolean existsById(String userId) {
        return users.containsKey(userId);
    }

    @Override
    public synchronized List<User> findAll() {
        return new ArrayList<>(users.values());
    }
}

