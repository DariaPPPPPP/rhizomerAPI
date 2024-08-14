package net.rhizomik.rhizomer.controller;

import net.rhizomik.rhizomer.service.GroqService;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    @Autowired
    private GroqService groqService;

    @GetMapping("/search")
    public ResponseEntity<Map<String, String>> search(@RequestParam String q) {
        Map<String, String> response = new HashMap<>();
        try {
            System.out.println(q);
            logger.info("Received search query: {}", q);
            String groqResponse = groqService.getGroqChatCompletion(q);
            response.put("result", groqResponse);
            return ResponseEntity.ok(response);
        }
        catch (Exception e) {
            response.put("error", "Error processing request: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);

        }
    }
}


