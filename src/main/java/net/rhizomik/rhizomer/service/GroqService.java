package net.rhizomik.rhizomer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class GroqService {

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${groq.api.key}")
    private String groqApiKey;

    public String getGroqChatCompletion(String query) throws IOException {
        System.out.println("Groq API Key: " + groqApiKey);
        MediaType mediaType = MediaType.parse("application/json");
        String requestBody = "{"
                + "\"messages\": [{\"role\": \"user\", \"content\": \"" + query + "\"}],"
                + "\"model\": \"llama3-8b-8192\""
                + "}";

        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .post(RequestBody.create(requestBody, mediaType))
                .addHeader("Authorization", "Bearer " + groqApiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            JsonNode jsonResponse = objectMapper.readTree(response.body().string());
            return jsonResponse.path("choices").get(0).path("message").path("content").asText();
        }
    }
}