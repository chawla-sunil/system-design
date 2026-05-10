package org.systemdesign.splitwise.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.systemdesign.splitwise.model.Group;

public final class InMemoryGroupRepository implements GroupRepository {
    private final Map<String, Group> groups = new LinkedHashMap<>();

    @Override
    public synchronized Group save(Group group) {
        groups.put(group.id(), group);
        return group;
    }

    @Override
    public synchronized Optional<Group> findById(String groupId) {
        return Optional.ofNullable(groups.get(groupId));
    }

    @Override
    public synchronized boolean existsById(String groupId) {
        return groups.containsKey(groupId);
    }

    @Override
    public synchronized List<Group> findAll() {
        return new ArrayList<>(groups.values());
    }
}

