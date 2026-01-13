package com.veriprotocol.springAI.core;


import java.util.Arrays;


import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

	private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String text) {
        EmbeddingResponse resp = embeddingModel.embedForResponse(Arrays.asList(text));
        float[] out = resp.getResults().get(0).getOutput();
        float[] v = new float[out.length];
        for (int i = 0; i < out.length; i++) v[i] = out[i];
        return v;
    }
}
