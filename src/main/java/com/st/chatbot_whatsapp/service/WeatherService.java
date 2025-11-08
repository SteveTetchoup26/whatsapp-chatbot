package com.st.chatbot_whatsapp.service;

import com.st.chatbot_whatsapp.dto.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    @Value("${weather.api.key}")
    private String apiKey;

    @Value("${weather.api.url}")
    private String apiUrl;

    private final WebClient.Builder webClientBuilder;

    /**
     * Récupère la météo pour une ville (avec cache de 10 minutes)
     */
    @Cacheable(value = "weather", key = "#city.toLowerCase()")
    public Mono<WeatherResponse> getWeather(String city) {
        log.info("Fetching weather for city: {}", city);

        WebClient webClient = webClientBuilder.baseUrl(apiUrl).build();

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("q", city)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .queryParam("lang", "fr")
                        .build())
                .retrieve()
                .bodyToMono(WeatherResponse.class)
                .doOnSuccess(response -> log.info("Weather data retrieved for {}", city))
                .doOnError(error -> log.error("Error fetching weather for {}: {}", city, error.getMessage()))
                .onErrorResume(error -> {
                    log.error("Failed to fetch weather", error);
                    return Mono.empty();
                });
    }

    /**
     * Formate la réponse météo avec des emojis et informations détaillées
     */
    public String formatWeatherResponse(WeatherResponse weather) {
        if (weather == null) {
            return "❌ Désolé, je n'ai pas pu récupérer les informations météo pour cette ville. Vérifie l'orthographe !";
        }

        String emoji = getWeatherEmoji(weather.getWeather().get(0).getMain());

        return String.format("""
            %s *Météo à %s, %s*
            
            🌡️ *Température :* %.1f°C
            🤔 *Ressenti :* %.1f°C
            📊 *Conditions :* %s
            
            💨 *Vent :* %.1f km/h
            💧 *Humidité :* %d%%
            🔽 *Pression :* %d hPa
            
            _Données en temps réel_ ⏰
            """,
                emoji,
                weather.getName(),
                weather.getSys().getCountry(),
                weather.getMain().getTemp(),
                weather.getMain().getFeelsLike(),
                capitalizeFirst(weather.getWeather().get(0).getDescription()),
                weather.getWind().getSpeed() * 3.6, // Conversion m/s en km/h
                weather.getMain().getHumidity(),
                weather.getMain().getPressure()
        );
    }

    /**
     * Retourne l'emoji approprié selon les conditions météo
     */
    private String getWeatherEmoji(String weatherCondition) {
        return switch (weatherCondition.toLowerCase()) {
            case "clear" -> "☀️";
            case "clouds" -> "☁️";
            case "rain", "drizzle" -> "🌧️";
            case "thunderstorm" -> "⛈️";
            case "snow" -> "❄️";
            case "mist", "fog", "haze" -> "🌫️";
            default -> "🌤️";
        };
    }

    /**
     * Met en majuscule la première lettre
     */
    private String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
