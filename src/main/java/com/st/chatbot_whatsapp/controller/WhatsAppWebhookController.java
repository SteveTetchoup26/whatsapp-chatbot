package com.st.chatbot_whatsapp.controller;

import com.st.chatbot_whatsapp.dto.WebhookRequest;
import com.st.chatbot_whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    @Value("${whatsapp.api.url}")
    private String whatsappApiUrl;

    @Value("${whatsapp.access.token}")
    private String accessToken;

    @Value("${whatsapp.phone.number.id}")
    private String phoneNumberId;

    private final WhatsAppService whatsAppService;

    @Value("${whatsapp.verify.token}")
    private String verifyToken;

    /**
     * Endpoint de vérification pour WhatsApp (GET)
     * Meta l'appelle pour vérifier le webhook
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        log.info("Verification request received - Mode: {}", mode);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Webhook verified successfully!");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Webhook verification failed!");
        return ResponseEntity.status(403).body("Forbidden");
    }

    /**
     * Endpoint pour recevoir les messages WhatsApp (POST)
     */
    @PostMapping
    public ResponseEntity<String> receiveMessage(@RequestBody WebhookRequest request) {
        log.info("Received webhook: {}", request);

        try {
            // Traiter le message de manière asynchrone
            whatsAppService.processIncomingMessage(request);
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.ok("EVENT_RECEIVED"); // Toujours retourner 200 pour Meta
        }
    }

    @GetMapping("/test-config")
    public ResponseEntity<String> testConfig() {
        return ResponseEntity.ok(String.format(
                "Verify Token: %s\nAPI URL: %s\nAccess Token: %s...\nPhone ID: %s",
                verifyToken,
                whatsappApiUrl,
                accessToken.substring(0, 20), // Montre juste le début
                phoneNumberId
        ));
    }
}
