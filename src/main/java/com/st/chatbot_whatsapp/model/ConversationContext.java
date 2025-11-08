package com.st.chatbot_whatsapp.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationContext {
    private String userId;
    private Intent lastIntent;
    private String lastCity;
    private LocalDateTime lastInteraction;
    private List<String> messageHistory = new ArrayList<>();
    private int messageCount = 0;
}
