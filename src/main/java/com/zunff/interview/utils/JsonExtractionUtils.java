package com.zunff.interview.utils;

/**
 * JSON extraction helpers for LLM responses.
 */
public final class JsonExtractionUtils {

    private JsonExtractionUtils() {
    }

    /**
     * Extract a JSON object string from model output.
     * Tries markdown fenced block first, then falls back to first '{' ... last '}'.
     */
    public static String extractJsonObjectString(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }

        String trimmed = response.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        int fencedIndex = trimmed.indexOf("```");
        if (fencedIndex >= 0) {
            int fencedEnd = trimmed.indexOf("```", fencedIndex + 3);
            if (fencedEnd > fencedIndex) {
                String fenced = trimmed.substring(fencedIndex + 3, fencedEnd).trim();
                if (fenced.startsWith("json")) {
                    fenced = fenced.substring(4).trim();
                }
                if (fenced.startsWith("{") && fenced.endsWith("}")) {
                    return fenced;
                }
            }
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }
}

