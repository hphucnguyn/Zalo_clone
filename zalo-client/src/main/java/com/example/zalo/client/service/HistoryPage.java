package com.example.zalo.client.service;

import com.example.zalo.common.model.MessageDto;

import java.util.List;

/** One chronological history response, with a signal that older data remains. */
public record HistoryPage(List<MessageDto> messages, boolean hasMore) {
}
