package com.st.chatbot_whatsapp.service;

import com.st.chatbot_whatsapp.model.ConversationContext;
import com.st.chatbot_whatsapp.model.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Service de gestion du contexte conversationnel
 * Permet de maintenir l'historique et le contexte de chaque utilisateur
 */
@Service
@Slf4j
public class ConversationContextService {

    /**
     * Récupère ou crée un contexte pour un utilisateur
     */
    @Cacheable(value = "context", key = "#userId")
    public ConversationContext getContext(String userId) {
        log.info("Creating new context for user: {}", userId);
        ConversationContext context = new ConversationContext();
        context.setUserId(userId);
        context.setLastInteraction(LocalDateTime.now());
        context.setMessageHistory(new ArrayList<>());
        return context;
    }

    /**
     * Met à jour le contexte d'un utilisateur
     */
    @CachePut(value = "context", key = "#userId")
    public ConversationContext updateContext(String userId, String message, Intent intent, String lastCity) {
        ConversationContext context = getContext(userId);

        // Ajouter le message à l'historique (garder les 10 derniers)
        context.getMessageHistory().add(message);
        if (context.getMessageHistory().size() > 10) {
            context.getMessageHistory().remove(0);
        }

        // Mettre à jour les informations
        context.setLastIntent(intent);
        context.setLastInteraction(LocalDateTime.now());

        if (lastCity != null && !lastCity.isEmpty()) {
            context.setLastCity(lastCity);
        }

        log.debug("Updated context for user {}: {}", userId, context);
        return context;
    }

    /**
     * Vérifie si l'utilisateur a une ville en contexte
     */
    public boolean hasRecentCity(ConversationContext context) {
        if (context.getLastCity() == null) {
            return false;
        }

        // Vérifier si l'interaction est récente (moins de 5 minutes)
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        return context.getLastInteraction().isAfter(fiveMinutesAgo);
    }

    /**
     * Génère une réponse contextuelle intelligente
     */
    public String generateSmartResponse(ConversationContext context, Intent currentIntent) {
        // Si l'utilisateur demande la météo mais n'a pas spécifié de ville
        if (currentIntent == Intent.WEATHER && hasRecentCity(context)) {
            return String.format(
                    "🤔 Tu veux la météo pour *%s* comme la dernière fois ? Ou tu veux une autre ville ?",
                    context.getLastCity()
            );
        }

        // Si c'est une salutation et qu'on a déjà interagi
        if (currentIntent == Intent.GREETING && !context.getMessageHistory().isEmpty()) {
            return String.format(
                    "Re-bonjour ! 👋 Content de te revoir ! Tu veux la météo d'une ville ?%s",
                    context.getLastCity() != null ?
                            String.format(" (La dernière fois c'était %s)", context.getLastCity()) : ""
            );
        }

        return null; // Pas de réponse contextuelle spéciale
    }
}
