package com.st.chatbot_whatsapp.service;

import com.st.chatbot_whatsapp.dto.WebhookRequest;
import com.st.chatbot_whatsapp.model.ConversationContext;
import com.st.chatbot_whatsapp.model.Intent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    @Value("${whatsapp.api.url}")
    private String whatsappApiUrl;

    @Value("${whatsapp.access.token}")
    private String accessToken;

    @Value("${whatsapp.phone.number.id}")
    private String phoneNumberId;

    private final IntentDetectorService intentDetector;
    private final WeatherService weatherService;
    private final ConversationContextService contextService;
    private final WebClient.Builder webClientBuilder;

    /**
     * Traite les messages entrants de manière asynchrone
     */
    @Async
    public void processIncomingMessage(WebhookRequest request) {
        if (request.getEntry() == null || request.getEntry().isEmpty()) {
            return;
        }

        request.getEntry().forEach(entry ->
                entry.getChanges().forEach(change -> {
                    if (change.getValue().getMessages() != null) {
                        change.getValue().getMessages().forEach(this::handleMessage);
                    }
                })
        );
    }

    /**
     * Gère un message individuel
     */
    private void handleMessage(WebhookRequest.Message message) {
        if (message.getType() == null || !message.getType().equals("text")) {
            return;
        }

        String userMessage = message.getText().getBody();
        String userId = message.getFrom();

        log.info("Processing message from {}: {}", userId, userMessage);

        // 1. Récupérer le contexte de l'utilisateur
        ConversationContext context = contextService.getContext(userId);

        // 2. Détecter l'intention
        Intent intent = intentDetector.detectIntent(userMessage);
        log.info("Detected intent: {} for user: {}", intent, userId);

        // 3. Extraire la ville si c'est une demande météo
        Optional<String> cityOpt = intentDetector.extractCity(userMessage);

        // 4. Générer et envoyer la réponse
        if (intent == Intent.WEATHER) {
            handleWeatherIntent(userId, cityOpt, context);
        } else {
            handleOtherIntent(userId, intent, context);
        }

        // 5. Mettre à jour le contexte
        contextService.updateContext(userId, userMessage, intent, cityOpt.orElse(null));
    }

    /**
     * Gère les demandes météo
     */
    private void handleWeatherIntent(String userId, Optional<String> cityOpt, ConversationContext context) {
        if (cityOpt.isPresent()) {
            String city = cityOpt.get();
            log.info("Fetching weather for city: {}", city);

            weatherService.getWeather(city)
                    .subscribe(
                            weatherResponse -> {
                                String response = weatherService.formatWeatherResponse(weatherResponse);
                                sendMessage(userId, response);
                            },
                            error -> {
                                log.error("Error fetching weather", error);
                                sendMessage(userId, "❌ Désolé, je n'ai pas trouvé cette ville. Vérifie l'orthographe ! 🤔");
                            },
                            () -> {
                                // On error resume case (empty)
                                sendMessage(userId, "❌ Désolé, je n'ai pas trouvé cette ville. Vérifie l'orthographe ! 🤔");
                            }
                    );
        } else {
            // Pas de ville détectée - utiliser le contexte ou demander
            String smartResponse = contextService.generateSmartResponse(context, Intent.WEATHER);
            String response = smartResponse != null ? smartResponse : intentDetector.generateContextualResponse(Intent.WEATHER, null);
            sendMessage(userId, response);
        }
    }

    /**
     * Gère les autres intentions (salutations, aide, etc.)
     */
    private void handleOtherIntent(String userId, Intent intent, ConversationContext context) {
        String smartResponse = contextService.generateSmartResponse(context, intent);
        String response = smartResponse != null ? smartResponse : intentDetector.generateContextualResponse(intent, null);
        sendMessage(userId, response);
    }

    /**
     * Envoie un message via l'API WhatsApp
     */
    public void sendMessage(String to, String message) {
        log.info("Sending message to {}: {}", to, message);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "text");

        Map<String, String> text = new HashMap<>();
        text.put("body", message);
        payload.put("text", text);

        WebClient webClient = webClientBuilder
                .baseUrl(whatsappApiUrl)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        webClient.post()
                .uri("/{phone_number_id}/messages", phoneNumberId)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Message sent successfully: {}", response))
                .doOnError(error -> log.error("Error sending message", error))
                .subscribe();
    }
}
