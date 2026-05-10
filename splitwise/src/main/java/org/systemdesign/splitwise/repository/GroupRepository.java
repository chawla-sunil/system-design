package org.systemdesign.splitwise.repository;

import java.util.List;
import java.util.Optional;
import org.systemdesign.splitwise.model.Group;

public interface GroupRepository {

    Group save(Group group);

    Optional<Group> findById(String groupId);

    boolean existsById(String groupId);

    List<Group> findAll();
}

