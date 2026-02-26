package com.example.ConflArchReport.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Отдаёт favicon по /favicon.png из classpath (static/image/zip.png).
 * Отдельный URL без /image/ избавляет от проблем со слэшем в конце.
 */
@RestController
public class FaviconController {

    @GetMapping(value = "/favicon.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> favicon() throws IOException {
        var resource = new ClassPathResource("static/image/zip.png");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().body(resource.getInputStream().readAllBytes());
    }
}
