package org.systemdesign.splitwise.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import org.systemdesign.splitwise.exception.EntityNotFoundException;
import org.systemdesign.splitwise.exception.ValidationException;
import org.systemdesign.splitwise.model.Group;
import org.systemdesign.splitwise.repository.GroupRepository;

public final class GroupService {
    private final GroupRepository groupRepository;
    private final UserService userService;

    public GroupService(GroupRepository groupRepository, UserService userService) {
        this.groupRepository = groupRepository;
        this.userService = userService;
    }

    public Group createGroup(String id, String name, Collection<String> memberIds) {
        if (groupRepository.existsById(id)) {
            throw new ValidationException("group already exists: " + id);
        }

        LinkedHashSet<String> distinctMembers = normalizeMembers(memberIds);
        distinctMembers.forEach(userService::getRequiredUser);
        return groupRepository.save(new Group(id, name, distinctMembers));
    }

    public void addMember(String groupId, String userId) {
        Group group = getRequiredGroup(groupId);
        userService.getRequiredUser(userId);
        group.addMember(userId);
        groupRepository.save(group);
    }

    public Group getRequiredGroup(String groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new EntityNotFoundException("group not found: " + groupId));
    }

    private LinkedHashSet<String> normalizeMembers(Collection<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new ValidationException("group must contain at least one member");
        }

        LinkedHashSet<String> distinctMembers = new LinkedHashSet<>();
        for (String memberId : memberIds) {
            if (memberId == null || memberId.isBlank()) {
                throw new ValidationException("group member id cannot be blank");
            }
            distinctMembers.add(memberId.trim());
        }
        return distinctMembers;
    }
}

