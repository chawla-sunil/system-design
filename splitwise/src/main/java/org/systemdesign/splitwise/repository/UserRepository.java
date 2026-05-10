package org.systemdesign.splitwise.repository;

import java.util.List;
import java.util.Optional;
import org.systemdesign.splitwise.model.User;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(String userId);

    boolean existsById(String userId);

    List<User> findAll();
}

