package net.rhizomik.rhizomer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class SearchController {

    @GetMapping("/search")
    public ResponseEntity<Map<String, String>> search(@RequestParam String q) {
        Map<String, String> response = new HashMap<>();
        response.put("result", "Search result for query: " + q);
        return ResponseEntity.ok(response);
    }
}

