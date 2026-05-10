package org.systemdesign.splitwise.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class Group {
    private final String id;
    private final String name;
    private final Set<String> memberIds;

    public Group(String id, String name, Collection<String> memberIds) {
        if (isBlank(id)) {
            throw new IllegalArgumentException("group id cannot be blank");
        }
        if (isBlank(name)) {
            throw new IllegalArgumentException("group name cannot be blank");
        }
        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException("group must have at least one member");
        }

        this.id = id.trim();
        this.name = name.trim();
        this.memberIds = new LinkedHashSet<>(memberIds);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Set<String> memberIds() {
        return Collections.unmodifiableSet(memberIds);
    }

    public void addMember(String userId) {
        if (isBlank(userId)) {
            throw new IllegalArgumentException("user id cannot be blank");
        }
        memberIds.add(userId.trim());
    }

    public boolean hasMember(String userId) {
        return memberIds.contains(userId);
    }

    private static boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}

