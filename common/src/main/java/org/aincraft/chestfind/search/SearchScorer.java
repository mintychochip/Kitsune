package org.aincraft.chestfind.search;

import org.aincraft.chestfind.model.SearchResult;

import java.util.*;

public class SearchScorer {
    private static final double KEYWORD_BOOST = 0.0; // Disabled - use pure semantic similarity

    // BM25 parameters
    private static final double K1 = 1.5; // Term frequency saturation parameter
    private static final double B = 0.75; // Length normalization parameter

    /**
     * Re-rank search results using hybrid scoring:
     * - Semantic similarity (base score from embeddings)
     * - BM25 keyword matching boost (term frequency + inverse document frequency)
     */
    public static List<SearchResult> hybridRerank(List<SearchResult> results, String query, String expandedQuery) {
        String[] queryTokens = tokenize(expandedQuery);

        if (queryTokens.length == 0 || results.isEmpty()) {
            return results;
        }

        // Calculate average document length across all results (use full content)
        double avgDocLength = results.stream()
            .mapToInt(r -> r.fullContent() != null ? r.fullContent().length() : r.preview().length())
            .average()
            .orElse(100.0);

        // Calculate document frequency (DF) for each term (use full content)
        Map<String, Integer> documentFrequency = calculateDocumentFrequency(results, queryTokens);
        int totalDocuments = results.size();

        // Re-score each result with BM25 using FULL content for keyword matching
        List<SearchResult> reranked = results.stream()
            .map(result -> {
                double semanticScore = result.score();
                String contentForBM25 = result.fullContent() != null ? result.fullContent() : result.preview();
                double bm25Score = calculateBM25Score(
                    contentForBM25,
                    queryTokens,
                    avgDocLength,
                    documentFrequency,
                    totalDocuments
                );

                // Combine: semantic baseline + BM25 keyword boost
                double hybridScore = semanticScore + (bm25Score * KEYWORD_BOOST);

                // Cap at 1.0
                hybridScore = Math.min(1.0, hybridScore);

                return new SearchResult(result.location(), hybridScore, result.preview(), result.fullContent());
            })
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .toList();

        return reranked;
    }

    /**
     * Calculate BM25 score for a document given query terms.
     * Returns a score between 0.0 and 1.0.
     *
     * BM25 formula: sum(IDF(qi) * (tf(qi, D) * (K1 + 1)) / (tf(qi, D) + K1 * (1 - B + B * |D| / avgdl)))
     *
     * This captures:
     * - Term frequency saturation: repeated terms have diminishing returns
     * - Inverse document frequency: rare terms boost scores more
     * - Length normalization: longer documents don't get automatic advantages
     */
    private static double calculateBM25Score(
        String document,
        String[] queryTokens,
        double avgDocLength,
        Map<String, Integer> documentFrequency,
        int totalDocuments
    ) {
        String docLower = document.toLowerCase();
        int docLength = document.length();

        double score = 0.0;

        for (String term : queryTokens) {
            // Term frequency in this document
            int termFreq = countOccurrences(docLower, term);
            if (termFreq == 0) continue;

            // Inverse document frequency (IDF)
            int df = documentFrequency.getOrDefault(term, 1);
            double idf = Math.log((totalDocuments - df + 0.5) / (df + 0.5) + 1.0);

            // BM25 formula
            double numerator = termFreq * (K1 + 1);
            double denominator = termFreq + K1 * (1 - B + B * (docLength / avgDocLength));

            score += idf * (numerator / denominator);
        }

        // Normalize to 0-1 range (max possible BM25 score is approximately queryTerms.length * 5)
        double maxPossibleScore = queryTokens.length * 5.0;
        return Math.min(1.0, score / maxPossibleScore);
    }

    /**
     * Calculate document frequency (how many documents contain each term).
     * Uses full content for accurate term matching.
     */
    private static Map<String, Integer> calculateDocumentFrequency(
        List<SearchResult> results,
        String[] queryTokens
    ) {
        Map<String, Integer> df = new HashMap<>();

        for (String term : queryTokens) {
            int count = 0;
            for (SearchResult result : results) {
                String content = result.fullContent() != null ? result.fullContent() : result.preview();
                if (content.toLowerCase().contains(term)) {
                    count++;
                }
            }
            df.put(term, count);
        }

        return df;
    }

    /**
     * Count occurrences of a term in text.
     */
    private static int countOccurrences(String text, String term) {
        int count = 0;
        int index = 0;

        while ((index = text.indexOf(term, index)) != -1) {
            count++;
            index += term.length();
        }

        return count;
    }

    /**
     * Tokenize query into searchable terms.
     */
    private static String[] tokenize(String query) {
        return Arrays.stream(query.toLowerCase().split("\\s+"))
            .filter(s -> s.length() > 2) // Skip short words
            .toArray(String[]::new);
    }
}
