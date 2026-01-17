package com.veriprotocol.springAI.core;

import java.util.ArrayList;
import java.util.List;

public final class TextChunker {
	
	
	private TextChunker() {}

	public static List<String> chunk(String text, int maxChars) {
	    String normalized = text.replace("\r\n", "\n").trim();
	    String[] paras = normalized.split("\n\\s*\n"); // split on blank lines

	    List<String> out = new ArrayList<>();
	    for (String p : paras) {
	        String para = p.trim();
	        if (para.isEmpty()) continue;

	        // hard-split if a paragraph is too large
	        for (int i = 0; i < para.length(); i += maxChars) {
	            out.add(para.substring(i, Math.min(i + maxChars, para.length())).trim());
	        }
	    }
	    return out;
	}

}
