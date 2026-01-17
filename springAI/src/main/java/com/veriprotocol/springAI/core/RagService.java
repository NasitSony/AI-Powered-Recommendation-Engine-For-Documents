package com.veriprotocol.springAI.core;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import com.veriprotocol.springAI.persistance.ChunkSearchDao;

@Service
public class RagService {
	
	private final DocumentService documentService;
    private final ChatModel chatModel;
    
    public RagService(DocumentService documentService, ChatModel chatModel) {
        this.documentService = documentService;
        this.chatModel = chatModel;
    }
    
    public AskResponse ask(String question, int k) {
        List<ChunkSearchDao.ChunkHit> hits = documentService.semanticSearchChunks(question, k);

        String sources = hits.stream()
                .map(h -> "[source: " + h.docId() + "#" + h.chunkId() + "]\n" + h.chunkText())
                .collect(Collectors.joining("\n\n---\n\n"));
        
        String template = """
                You are a helpful assistant.
                Use ONLY the provided sources to answer the question.
                If the answer is not in the sources, say "I don't know based on the provided sources."
                Add citations like [docId#chunkId] at the end of sentences that use sources.

                Question:
                {question}

                Sources:
                {sources}
                """;
        Prompt prompt = new PromptTemplate(template).create(Map.of(
                "question", question,
                "sources", sources
        ));
        
        
        String answer = chatModel.call(prompt).getResult().getOutput().getText();

        return new AskResponse(question, answer, hits);
    }
    
    public record AskResponse(String question, String answer, List<ChunkSearchDao.ChunkHit> sources) {}




}
