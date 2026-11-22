package com.example.zalo.server.service;

import com.example.zalo.common.model.UserDto;
import com.example.zalo.common.util.TimeUtil;
import com.example.zalo.server.repository.FriendRepository;
import com.example.zalo.server.repository.UserRepository;
import com.example.zalo.server.security.InputValidator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Friend search and request rules, including per-account search throttling. */
public final class FriendService {
    private static final long WINDOW_MS=60_000;
    private static final int SEARCH_LIMIT=10;
    private final UserRepository users;
    private final FriendRepository friends;
    private final Map<Long, ArrayDeque<Long>> searches=new ConcurrentHashMap<>();

    public FriendService(UserRepository users, FriendRepository friends) { this.users=users; this.friends=friends; }

    public Optional<UserDto> search(long requesterId,String phone) {
        long now=System.currentTimeMillis();
        ArrayDeque<Long> times=searches.computeIfAbsent(requesterId,ignored->new ArrayDeque<>());
        synchronized(times) {
            while(!times.isEmpty() && now-times.peekFirst()>=WINDOW_MS) times.removeFirst();
            if(times.size()>=SEARCH_LIMIT) throw new SearchRateLimitException();
            times.addLast(now);
        }
        return users.findPublicByPhone(InputValidator.phoneNumber(phone)).filter(user->user.id()!=requesterId);
    }

    public long request(long senderId,long receiverId) {
        if(receiverId<=0 || senderId==receiverId || users.findPublicById(receiverId).isEmpty()) throw new IllegalArgumentException("Invalid friend target");
        if(friends.areFriends(senderId,receiverId)) throw new AlreadyFriendsException();
        if(friends.findPending(senderId,receiverId).isPresent()) throw new PendingRequestException();
        if(friends.findPending(receiverId,senderId).isPresent()) throw new IncomingRequestException();
        try { return friends.createRequest(senderId,receiverId,TimeUtil.nowEpochMillis()); }
        catch(FriendRepository.RequestAlreadyPendingException e) { throw new PendingRequestException(); }
    }

    public List<UserDto> listFriends(long userId) { return users.findFriends(userId); }
    public List<Map<String,Object>> incoming(long userId) { return friends.incoming(userId); }
    public OptionalLong respond(long requestId,long receiverId,boolean accept) {
        return friends.respond(requestId,receiverId,accept,TimeUtil.nowEpochMillis());
    }

    public static final class SearchRateLimitException extends RuntimeException { }
    public static final class AlreadyFriendsException extends RuntimeException { }
    public static final class PendingRequestException extends RuntimeException { }
    public static final class IncomingRequestException extends RuntimeException { }
}
