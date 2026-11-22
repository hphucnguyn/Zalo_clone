package com.example.zalo.server.handler;

import com.example.zalo.common.model.ConversationDto;
import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.protocol.ErrorCode;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.service.GroupService;
import com.example.zalo.server.session.ClientSession;
import com.example.zalo.server.session.SessionRegistry;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Group creation and membership operations, plus system notifications to remaining members. */
public final class GroupHandler implements PacketHandler {
    private final GroupService groups;
    private final com.example.zalo.server.repository.ConversationRepository conversations;
    private final SessionRegistry sessions;
    private final PacketWriter writer;

    public GroupHandler(GroupService groups, com.example.zalo.server.repository.ConversationRepository conversations,
                        SessionRegistry sessions, PacketWriter writer) {
        this.groups = Objects.requireNonNull(groups); this.conversations = Objects.requireNonNull(conversations);
        this.sessions = Objects.requireNonNull(sessions); this.writer = Objects.requireNonNull(writer);
    }

    @Override public void handle(ClientSession session, Packet packet) throws IOException {
        try {
            switch (packet.type()) {
                case CREATE_GROUP -> create(session, packet);
                case ADD_MEMBER -> add(session, packet);
                case REMOVE_MEMBER -> remove(session, packet);
                case LEAVE_GROUP -> leave(session, packet);
                default -> throw new IllegalArgumentException("Unsupported group operation");
            }
        } catch (GroupService.UnauthorizedGroupException exception) {
            error(session, packet, ErrorCode.UNAUTHORIZED, "Only the group owner can do that.");
        } catch (IllegalArgumentException exception) {
            error(session, packet, ErrorCode.VALIDATION_FAILED, "Invalid group operation.");
        }
    }

    private void create(ClientSession session, Packet packet) throws IOException {
        ConversationDto group = groups.create(session.authenticatedUser(), packet.data().path("name").asText(null));
        writer.write(session, Packet.of(MessageType.GROUP_CREATED, packet.requestId(), Map.of("conversation", group)));
    }
    private void add(ClientSession session, Packet packet) throws IOException {
        long groupId = packet.data().path("conversationId").asLong(-1);
        UserDto added = groups.addMember(session.authenticatedUser(), groupId, packet.data().path("username").asText(null));
        notifyMembers(groupId, session.authenticatedUser().displayName() + " added " + added.displayName());
        writer.write(session, Packet.of(MessageType.CONVERSATION_UPDATED, packet.requestId(), Map.of("conversationId", groupId, "action", "MEMBER_ADDED")));
    }
    private void remove(ClientSession session, Packet packet) throws IOException {
        long groupId = packet.data().path("conversationId").asLong(-1);
        long userId = packet.data().path("userId").asLong(-1);
        groups.removeMember(session.authenticatedUser(), groupId, userId);
        notifyMembers(groupId, session.authenticatedUser().displayName() + " removed a member");
    }
    private void leave(ClientSession session, Packet packet) throws IOException {
        long groupId = packet.data().path("conversationId").asLong(-1);
        String name = session.authenticatedUser().displayName();
        groups.leave(session.authenticatedUser(), groupId);
        notifyMembers(groupId, name + " left the group");
    }
    private void notifyMembers(long groupId, String text) {
        Packet system = Packet.of(MessageType.SYSTEM, null, Map.of("conversationId", groupId, "content", text));
        for (long memberId : conversations.memberIds(groupId)) for (ClientSession member : sessions.findByUserId(memberId)) {
            try { writer.write(member, system); } catch (IOException exception) { sessions.unregister(member); }
        }
    }
    private void error(ClientSession session, Packet packet, ErrorCode code, String message) throws IOException {
        writer.write(session, Packet.of(MessageType.ERROR, packet.requestId(), Map.of("code", code.name(), "message", message)));
    }
}
