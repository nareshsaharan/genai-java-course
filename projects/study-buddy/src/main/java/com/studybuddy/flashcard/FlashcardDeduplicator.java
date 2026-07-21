package com.studybuddy.flashcard;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes duplicate/near-duplicate flashcard questions. Deliberately avoids
 * regex and manual delimiter-based string splitting (per project
 * requirements): normalization walks the string character-by-character,
 * and near-duplicate detection is a character-level Levenshtein distance —
 * no {@code split()}, no {@code Pattern}.
 */
final class FlashcardDeduplicator {

    private static final double NEAR_DUPLICATE_SIMILARITY_THRESHOLD = 0.95;

    private FlashcardDeduplicator() {
    }

    static List<GeneratedFlashcard> deduplicate(List<GeneratedFlashcard> cards) {
        List<GeneratedFlashcard> kept = new ArrayList<>();
        List<String> keptNormalizedQuestions = new ArrayList<>();

        for (GeneratedFlashcard card : cards) {
            String normalized = normalize(card.question());
            boolean isDuplicate = keptNormalizedQuestions.stream()
                    .anyMatch(existing -> similarity(normalized, existing) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD);
            if (!isDuplicate) {
                kept.add(card);
                keptNormalizedQuestions.add(normalized);
            }
        }
        return kept;
    }

    private static String normalize(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        boolean previousWasSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                normalized.append(Character.toLowerCase(c));
                previousWasSpace = false;
            } else if (!previousWasSpace) {
                normalized.append(' ');
                previousWasSpace = true;
            }
        }
        return normalized.toString().strip();
    }

    private static double similarity(String a, String b) {
        int maxLength = Math.max(a.length(), b.length());
        if (maxLength == 0) {
            return 1.0;
        }
        return 1.0 - ((double) levenshteinDistance(a, b) / maxLength);
    }

    private static int levenshteinDistance(String a, String b) {
        int[] previousRow = new int[b.length() + 1];
        int[] currentRow = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previousRow[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            currentRow[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitutionCost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                currentRow[j] = Math.min(
                        Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                        previousRow[j - 1] + substitutionCost);
            }
            int[] swap = previousRow;
            previousRow = currentRow;
            currentRow = swap;
        }
        return previousRow[b.length()];
    }
}
