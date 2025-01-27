package net.rhizomik.rhizomer.service;

import okhttp3.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.apache.jena.rdf.model.impl.RDFDefaultErrorHandler.logger;

public class FlaskService {
    public static void sendPostRequest() throws IOException {
        /*
        String url = "http://localhost:5000/api/askGroq";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String jsonBody = "{first: hello, second: world}";
        logger.info("jsonBody: {}", jsonBody);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            logger.info("AAAAAAA");
            String response = restTemplate.postForObject(url, request, String.class);
            logger.info("FLASK Responce : {}", response);
        } catch (Exception e) {
            System.out.println("Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
        */
        String requestUrl = "http://localhost:5050/api/askGroq";
        String jsonData = "{ \"first\": \"data\", \"last\": \"data\" }";

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        MediaType mediaType = MediaType.get("application/json");
        RequestBody body = RequestBody.create(jsonData, mediaType);

        Request request = new Request.Builder()
                .url(requestUrl)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        System.out.println("Response body: " + response.body().string());

    }
}