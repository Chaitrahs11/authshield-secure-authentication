package com.githubProjects.authShield.service;

import com.githubProjects.authShield.dto.OtpPurpose;
import com.githubProjects.authShield.entity.AppUser;
import com.githubProjects.authShield.common.EmailDeliveryException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OtpEmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OtpEmailService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiToken;
    private final String senderEmail;
    private final String senderName;

    public OtpEmailService(ObjectMapper objectMapper,
            @Value("${authshield.mailtrap.api-url}") String apiUrl,
            @Value("${authshield.mailtrap.api-token}") String apiToken,
            @Value("${authshield.mailtrap.sender-email}") String senderEmail,
            @Value("${authshield.mailtrap.sender-name}") String senderName) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    public void send(AppUser user, String code, OtpPurpose purpose, long expiryMinutes) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new EmailDeliveryException("Mailtrap API token is not configured");
        }
        String purposeText = purpose == OtpPurpose.VERIFY_EMAIL ? "verify your email" : "reset your password";
        Map<String, Object> payload = Map.of(
                "from", Map.of("email", senderEmail, "name", senderName),
                "to", List.of(Map.of("email", user.getEmail())),
                "subject", "Your AuthShield security code",
                "text", "Your code to " + purposeText + " is " + code
                        + ". It expires in " + expiryMinutes
                        + " minutes. If you did not request this, ignore this email.",
                "category", "AuthShield");
        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.error("Mailtrap HTTP API rejected the email request with status {}", response.statusCode());
                throw new EmailDeliveryException("Mailtrap HTTP API rejected the email request");
            }
            LOGGER.info("Mailtrap accepted an email request for purpose {}", purpose);
        } catch (JacksonException exception) {
            throw new EmailDeliveryException("Could not encode the email request", exception);
        } catch (IOException exception) {
            throw new EmailDeliveryException("Could not reach the Mailtrap HTTP API", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("Mailtrap HTTP request was interrupted", exception);
        }
    }
}