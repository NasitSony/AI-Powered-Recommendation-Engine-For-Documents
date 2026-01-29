package com.veriprotocol.springAI.controller;


import java.util.Arrays;
import java.util.Map;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AIEmbeddingController {


	private final EmbeddingModel embeddingModel;

    public AIEmbeddingController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/embed")
    public Map<String, Object> embed(@RequestParam(name = "q") String q) {
        float[] v = embeddingModel.embed(q);

        return Map.of(
                "q", q,
                "dims", v.length,
                "preview",  Arrays.copyOfRange(v, 0, Math.min(8, v.length))
        );
    }

}






