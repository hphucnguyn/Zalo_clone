package com.example.zalo.server.service;

import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.util.TimeUtil;
import com.example.zalo.server.repository.ConversationRepository;
import com.example.zalo.server.repository.UserRepository;

import java.util.Objects;

/** Group membership rules: creator owns the group; only the owner may add or remove others. */
public final class GroupService {

    private final ConversationRepository conversations;
    private final UserRepository users;

    public GroupService(ConversationRepository conversations, UserRepository users) {
        this.conversations = Objects.requireNonNull(conversations, "conversations must not be null");
        this.users = Objects.requireNonNull(users, "users must not be null");
    }

    public ConversationDto create(UserDto owner, String name) {
        String validName = validateName(name);
        long id = conversations.createGroup(owner.id(), validName, TimeUtil.nowEpochMillis());
        return new ConversationDto(id, "GROUP", validName, null);
    }

    public UserDto addMember(UserDto actor, long groupId, String username) {
        requireOwner(actor, groupId);
        UserDto member = users.findPublicByUsername(username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("User was not found"));
        conversations.addGroupMember(groupId, member.id(), TimeUtil.nowEpochMillis());
        return member;
    }

    public void removeMember(UserDto actor, long groupId, long userId) {
        requireOwner(actor, groupId);
        if (userId == actor.id()) throw new IllegalArgumentException("Owner cannot remove themselves; use leave after ownership transfer");
        conversations.removeGroupMember(groupId, userId);
    }

    public void leave(UserDto member, long groupId) {
        if (!conversations.isGroup(groupId) || !conversations.isMember(groupId, member.id())) {
            throw new IllegalArgumentException("Not a group member");
        }
        if (conversations.isOwner(groupId, member.id())) throw new IllegalArgumentException("Owner cannot leave the group");
        conversations.removeGroupMember(groupId, member.id());
    }

    private void requireOwner(UserDto actor, long groupId) {
        if (!conversations.isGroup(groupId) || !conversations.isOwner(groupId, actor.id())) {
            throw new UnauthorizedGroupException();
        }
    }

    private static String validateName(String name) {
        String valid = name == null ? "" : name.trim();
        if (valid.isEmpty() || valid.length() > 100) throw new IllegalArgumentException("Invalid group name");
        return valid;
    }

    public static final class UnauthorizedGroupException extends RuntimeException { }
}
