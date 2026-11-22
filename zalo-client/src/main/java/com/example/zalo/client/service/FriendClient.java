package com.example.zalo.client.service;

import com.example.zalo.client.net.TcpClient;
import com.example.zalo.common.model.FriendRequestDto;
import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.protocol.MessageType;
import com.example.zalo.common.protocol.Packet;
import com.example.zalo.common.util.JsonUtil;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Asynchronous client API for phone search and friend management. */
public final class FriendClient {
    private final TcpClient tcp;
    private final ConcurrentMap<String,Pending> pending=new ConcurrentHashMap<>();
    private volatile Consumer<Packet> updates=ignored->{};

    public FriendClient(TcpClient tcp) { this.tcp=tcp; tcp.addPacketConsumer(this::receive); tcp.addDisconnectConsumer(this::disconnect); }
    public CompletableFuture<UserDto> search(String phone) { return send(MessageType.SEARCH_USER_BY_PHONE,Map.of("phoneNumber",phone),p -> p.data().path("found").asBoolean(false)?JsonUtil.treeToValue(p.data().path("user"),UserDto.class):null); }
    public CompletableFuture<String> request(long userId) { return send(MessageType.SEND_FRIEND_REQUEST,Map.of("userId",userId),p->p.data().path("message").asText()); }
    public CompletableFuture<List<UserDto>> friends() { return send(MessageType.LIST_FRIENDS,Map.of(),p->{List<UserDto> result=new ArrayList<>();p.data().path("users").forEach(n->result.add(JsonUtil.treeToValue(n,UserDto.class)));return List.copyOf(result);}); }
    public CompletableFuture<List<FriendRequestDto>> requests() { return send(MessageType.LIST_FRIEND_REQUESTS,Map.of(),p->{List<FriendRequestDto> result=new ArrayList<>();p.data().path("requests").forEach(n->result.add(JsonUtil.treeToValue(n,FriendRequestDto.class)));return List.copyOf(result);}); }
    public CompletableFuture<String> respond(long requestId,boolean accept) { return send(MessageType.RESPOND_FRIEND_REQUEST,Map.of("friendRequestId",requestId,"accept",accept),p->p.data().path("message").asText()); }
    public void onUpdate(Consumer<Packet> consumer) { updates=consumer; }

    private <T> CompletableFuture<T> send(MessageType type,Map<String,Object> data,java.util.function.Function<Packet,T> convert) {
        String id=UUID.randomUUID().toString(); CompletableFuture<T> f=new CompletableFuture<>();
        pending.put(id,new Pending(type,(CompletableFuture<Object>)(CompletableFuture<?>)f,p -> convert.apply(p)));
        try { tcp.send(Packet.of(type,id,data)); } catch(IOException|RuntimeException e) { pending.remove(id);f.completeExceptionally(e); }
        return f;
    }
    private void receive(Packet p) {
        if(p.type()==MessageType.FRIEND_REQUEST_UPDATE) { updates.accept(p); return; }
        if(p.requestId()==null) return;
        Pending request=pending.remove(p.requestId()); if(request==null) return;
        if(p.type()==MessageType.ERROR) { request.future.completeExceptionally(new IOException(p.data().path("message").asText("Server error")));return; }
        MessageType expected=switch(request.type) { case SEARCH_USER_BY_PHONE->MessageType.USER_SEARCH_RESULT; case LIST_FRIENDS->MessageType.FRIENDS_LIST; case LIST_FRIEND_REQUESTS->MessageType.FRIEND_REQUESTS; default->MessageType.FRIEND_REQUEST_RESULT; };
        if(p.type()!=expected) { request.future.completeExceptionally(new IOException("Unexpected friend response"));return; }
        if(p.type()==MessageType.FRIEND_REQUEST_RESULT&&!p.data().path("success").asBoolean(false)) { request.future.completeExceptionally(new IOException(p.data().path("message").asText("Request failed")));return; }
        try { request.future.complete(request.convert.apply(p)); } catch(RuntimeException e) { request.future.completeExceptionally(e); }
    }
    private void disconnect(Throwable cause) { IOException e=new IOException("Disconnected from server",cause);pending.values().forEach(p->p.future.completeExceptionally(e));pending.clear(); }
    private record Pending(MessageType type,CompletableFuture<Object> future,java.util.function.Function<Packet,Object> convert) { }
}
