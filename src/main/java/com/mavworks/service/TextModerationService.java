package com.mavworks.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared server-side screening. Never stores or logs submitted text. */
public final class TextModerationService {
    private static final String SEPARATOR = "[\\p{P}\\p{Z}\\s]*";
    private static final List<Pattern> TERMS = loadTerms();
    private TextModerationService() {}

    public static void validate(String field, String text) {
        if (containsProhibitedLanguage(text)) {
            throw new IllegalArgumentException(field + " contains prohibited language. Please revise it before submitting.");
        }
    }

    public static boolean containsProhibitedLanguage(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("[\\p{M}\\p{Cf}]", "").toLowerCase(Locale.ROOT);
        return TERMS.stream().anyMatch(term -> term.matcher(normalized).find());
    }

    private static List<Pattern> loadTerms() {
        var resource = TextModerationService.class.getResourceAsStream("/moderation/prohibited-terms.txt");
        if (resource == null) throw new IllegalStateException("Missing prohibited language list.");
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            var terms = reader.lines().map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
            if (terms.isEmpty()) throw new IllegalStateException("Prohibited language list is empty.");
            return terms.stream().map(TextModerationService::compile).toList();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to load prohibited language list.", ex);
        }
    }

    private static Pattern compile(String term) {
        if (!term.matches("[a-z]+(?: [a-z]+)*"))
            throw new IllegalStateException("Prohibited terms must use lowercase letters and spaces.");
        // Letter boundaries avoid substring matches such as 'class' and 'Scunthorpe'.
        // Digits may surround a term in a username. Terms are not arbitrary regexes.
        StringBuilder regex = new StringBuilder("(?<![\\p{L}])");
        String letters = term.replace(" ", "");
        for (int i = 0; i < letters.length();) {
            char letter = letters.charAt(i);
            int end = i + 1;
            while (end < letters.length() && letters.charAt(end) == letter) end++;
            if (i > 0) regex.append(SEPARATOR);
            String alternatives = switch (letter) {
                case 'a' -> "[a@4]";
                case 'e' -> "[e3]";
                case 'i' -> "[i1!|]";
                case 'o' -> "[o0]";
                case 's' -> "[s$5]";
                case 't' -> "[t7]";
                default -> Pattern.quote(String.valueOf(letter));
            };
            regex.append(alternatives).append('{').append(end - i).append(',').append(end - i + 3).append('}');
            i = end;
        }
        return Pattern.compile(regex.append("(?![\\p{L}])").toString());
    }
}
