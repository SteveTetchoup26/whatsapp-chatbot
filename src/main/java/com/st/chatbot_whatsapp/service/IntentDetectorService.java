package com.st.chatbot_whatsapp.service;

import com.st.chatbot_whatsapp.model.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service de détection d'intentions avec NLP basique
 */
@Service
@Slf4j
public class IntentDetectorService {

    // Patterns pour la détection de ville
    private static final List<Pattern> CITY_PATTERNS = Arrays.asList(
            Pattern.compile("(?:météo|meteo|temps|température|temperature)\\s+(?:à|a|de|pour|sur)\\s+([a-zàâäéèêëïîôùûüÿç\\s-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:à|a|de|pour|sur)\\s+([a-zàâäéèêëïîôùûüÿç\\s-]+)\\s+(?:météo|meteo|temps)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-zàâäéèêëïîôùûüÿç\\s-]+)\\s+(?:météo|meteo|temps|température)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:quel(?:le)?\\s+(?:est|temps|météo|meteo)).*?(?:à|a|de|sur)\\s+([a-zàâäéèêëïîôùûüÿç\\s-]+)", Pattern.CASE_INSENSITIVE)
    );

    // Mots-clés pour chaque intention
    private static final Map<Intent, List<String>> INTENT_KEYWORDS = Map.of(
            Intent.WEATHER, Arrays.asList("météo", "meteo", "temps", "température", "temperature", "climat", "pluie", "soleil", "nuage", "vent"),
            Intent.GREETING, Arrays.asList("bonjour", "salut", "hello", "hi", "bonsoir", "hey", "coucou"),
            Intent.HELP, Arrays.asList("aide", "help", "comment", "commande", "utiliser", "menu", "fonctionnalités", "fonctionnalites", "quoi faire", "que peux-tu"),
            Intent.THANKS, Arrays.asList("merci", "thanks", "super", "génial", "cool", "parfait", "excellent"),
            Intent.GOODBYE, Arrays.asList("au revoir", "bye", "salut", "adieu", "à plus", "a plus", "tchao")
    );

    /**
     * Détecte l'intention principale du message
     */
    public Intent detectIntent(String message) {
        if (message == null || message.trim().isEmpty()) {
            return Intent.UNKNOWN;
        }

        String normalizedMessage = normalizeText(message);
        log.debug("Analyzing message: {}", normalizedMessage);

        // Vérifier chaque intention
        Map<Intent, Integer> scores = new HashMap<>();

        for (Map.Entry<Intent, List<String>> entry : INTENT_KEYWORDS.entrySet()) {
            int score = calculateIntentScore(normalizedMessage, entry.getValue());
            if (score > 0) {
                scores.put(entry.getKey(), score);
            }
        }

        // Retourner l'intention avec le score le plus élevé
        Intent detectedIntent = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Intent.UNKNOWN);

        log.info("Detected intent: {} with scores: {}", detectedIntent, scores);
        return detectedIntent;
    }

    /**
     * Extrait le nom de ville du message
     */
    public Optional<String> extractCity(String message) {
        if (message == null || message.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalizedMessage = normalizeText(message);

        // Essayer chaque pattern
        for (Pattern pattern : CITY_PATTERNS) {
            Matcher matcher = pattern.matcher(normalizedMessage);
            if (matcher.find()) {
                String city = matcher.group(1).trim();
                log.info("Extracted city: {} from message: {}", city, message);
                return Optional.of(capitalizeCity(city));
            }
        }

        // Si aucun pattern ne correspond, essayer de détecter une ville seule
        String[] words = normalizedMessage.split("\\s+");
        if (words.length >= 1 && words.length <= 3) {
            // Si le message est court, il pourrait être juste un nom de ville
            String potentialCity = String.join(" ", words);
            if (potentialCity.length() > 2 && !containsWeatherKeywords(potentialCity)) {
                log.info("Potential city detected: {}", potentialCity);
                return Optional.of(capitalizeCity(potentialCity));
            }
        }

        log.debug("No city found in message: {}", message);
        return Optional.empty();
    }

    /**
     * Calcule le score d'une intention basé sur les mots-clés
     */
    private int calculateIntentScore(String message, List<String> keywords) {
        int score = 0;
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                score += 10;
                // Bonus si le mot-clé est au début
                if (message.startsWith(keyword)) {
                    score += 5;
                }
            }
        }
        return score;
    }

    /**
     * Normalise le texte pour la détection
     */
    private String normalizeText(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-zàâäéèêëïîôùûüÿç0-9\\s-]", "")
                .trim();
    }

    /**
     * Met en majuscule la première lettre de chaque mot de la ville
     */
    private String capitalizeCity(String city) {
        String[] words = city.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Vérifie si le texte contient des mots-clés météo
     */
    private boolean containsWeatherKeywords(String text) {
        return INTENT_KEYWORDS.get(Intent.WEATHER).stream()
                .anyMatch(text::contains);
    }

    /**
     * Génère une réponse contextuelle basée sur l'intention
     */
    public String generateContextualResponse(Intent intent, String userName) {
        String name = (userName != null && !userName.isEmpty()) ? userName : "l'ami";

        return switch (intent) {
            case GREETING -> String.format("Salut %s ! 👋 Je suis ton assistant météo. Donne-moi une ville et je te dis le temps qu'il fait ! ☀️🌧️", name);
            case HELP -> """
                🤖 *Voici comment m'utiliser :*
                
                📍 Demande la météo : 
                • "Météo à Paris"
                • "Quel temps fait-il à Lyon ?"
                • "Température Londres"
                • Ou juste "Paris"
                
                💬 Tu peux aussi me dire :
                • Bonjour / Salut
                • Merci
                • Au revoir
                
                Je comprends le langage naturel ! 🧠
                """;
            case THANKS -> "De rien ! 😊 N'hésite pas si tu veux la météo d'une autre ville !";
            case GOODBYE -> "À bientôt ! 👋 Reviens quand tu veux pour la météo !";
            case WEATHER -> "🌤️ Donne-moi le nom d'une ville et je te dirai la météo ! (Ex: Paris, Londres, Tokyo...)";
            default -> """
                🤔 Je n'ai pas bien compris... 
                
                Demande-moi la météo d'une ville (Ex: "Météo à Paris")
                Ou tape "aide" pour voir ce que je peux faire !
                """;
        };
    }
}
