package com.example.zalo.server.handler;

import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.server.net.PacketWriter;
import com.example.zalo.server.repository.FriendRepository;
import com.example.zalo.server.repository.UserRepository;
import com.example.zalo.server.service.FriendService;
import com.example.zalo.server.session.ClientSession;
import com.example.zalo.server.session.SessionRegistry;
import java.io.IOException;
import java.util.*;

/** Handles exact phone lookup, friend requests, and accepted contacts. */
public final class FriendHandler implements PacketHandler {
    private final FriendService service;
    private final SessionRegistry sessions;
    private final PacketWriter writer;

    public FriendHandler(UserRepository users, FriendRepository friends, SessionRegistry sessions, PacketWriter writer) {
        this.service=new FriendService(users,friends); this.sessions=sessions; this.writer=writer;
    }

    @Override public void handle(ClientSession session, Packet packet) throws IOException {
        long me=session.authenticatedUser().id();
        switch(packet.type()) {
            case SEARCH_USER_BY_PHONE -> search(session,packet,me);
            case SEND_FRIEND_REQUEST -> send(session,packet,me);
            case LIST_FRIENDS -> writer.write(session,Packet.of(MessageType.FRIENDS_LIST,packet.requestId(),Map.of("users",service.listFriends(me))));
            case LIST_FRIEND_REQUESTS -> writer.write(session,Packet.of(MessageType.FRIEND_REQUESTS,packet.requestId(),Map.of("requests",service.incoming(me))));
            case RESPOND_FRIEND_REQUEST -> respond(session,packet,me);
            default -> { }
        }
    }

    private void search(ClientSession session,Packet packet,long me) throws IOException {
        try {
            Optional<UserDto> found=service.search(me,packet.data().path("phoneNumber").asText(null));
            Map<String,Object> result=new HashMap<>(); result.put("found",found.isPresent()); result.put("user",found.orElse(null));
            writer.write(session,Packet.of(MessageType.USER_SEARCH_RESULT,packet.requestId(),result));
        } catch(FriendService.SearchRateLimitException e) {
            error(session,packet,"Bạn tìm kiếm quá nhanh. Hãy thử lại sau một phút.");
        } catch(IllegalArgumentException e) { error(session,packet,"Nhập số điện thoại quốc tế, ví dụ +84901234567."); }
    }

    private void send(ClientSession session,Packet packet,long me) throws IOException {
        long receiver=packet.data().path("userId").asLong(-1);
        try {
            long id=service.request(me,receiver);
            Map<String,Object> body=Map.of("requestId",id,"sender",session.authenticatedUser());
            for(ClientSession target:sessions.findByUserId(receiver)) writer.write(target,Packet.of(MessageType.FRIEND_REQUEST_UPDATE,null,body));
            writer.write(session,Packet.of(MessageType.FRIEND_REQUEST_RESULT,packet.requestId(),Map.of("success",true,"message","Đã gửi lời mời kết bạn.")));
        } catch(FriendService.AlreadyFriendsException e) { result(session,packet,"Hai bạn đã là bạn bè."); }
        catch(FriendService.PendingRequestException e) { result(session,packet,"Lời mời đã được gửi trước đó."); }
        catch(FriendService.IncomingRequestException e) { result(session,packet,"Người này đã gửi lời mời cho bạn. Hãy mở mục Lời mời kết bạn."); }
        catch(IllegalArgumentException e) { error(session,packet,"Không thể gửi lời mời kết bạn."); }
    }

    private void respond(ClientSession session,Packet packet,long me) throws IOException {
        long requestId=packet.data().path("friendRequestId").asLong(-1);
        boolean accept=packet.data().path("accept").asBoolean(false);
        OptionalLong sender=service.respond(requestId,me,accept);
        if(sender.isEmpty()) { error(session,packet,"Lời mời không còn khả dụng."); return; }
        writer.write(session,Packet.of(MessageType.FRIEND_REQUEST_RESULT,packet.requestId(),Map.of("success",true,"message",accept?"Đã chấp nhận lời mời.":"Đã từ chối lời mời.")));
        Map<String,Object> update=Map.of("accepted",accept,"userId",me);
        for(ClientSession target:sessions.findByUserId(sender.getAsLong())) writer.write(target,Packet.of(MessageType.FRIEND_REQUEST_UPDATE,null,update));
        if(accept) for(ClientSession target:sessions.findByUserId(me)) writer.write(target,Packet.of(MessageType.FRIEND_REQUEST_UPDATE,null,update));
    }

    private void result(ClientSession session,Packet packet,String message) throws IOException {
        writer.write(session,Packet.of(MessageType.FRIEND_REQUEST_RESULT,packet.requestId(),Map.of("success",false,"message",message)));
    }
    private void error(ClientSession session,Packet packet,String message) throws IOException {
        writer.write(session,Packet.of(MessageType.ERROR,packet.requestId(),Map.of("code","VALIDATION_FAILED","message",message)));
    }
}
