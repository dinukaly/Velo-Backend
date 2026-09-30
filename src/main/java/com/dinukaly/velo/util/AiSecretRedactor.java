package com.dinukaly.velo.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort protection for recognizable credentials, not a general secret detector. */
public final class AiSecretRedactor {
    public static final String REDACTED = "[REDACTED_SECRET]";
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----[\\s\\S]*?(?:-----END (?:[A-Z0-9]+ )*PRIVATE KEY-----|\\z)");
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?im)([\\w.-]*(?:password|passwd|pwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|auth[_-]?token|private[_-]?key)[\\w.-]*[\\\"']?\\s*[:=]\\s*)"
                    + "(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s,;}\\r\\n]+)");
    private static final List<Pattern> TOKENS = List.of(
            Pattern.compile("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b"),
            Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b"),
            Pattern.compile("\\b(?:sk|rk)-(?:[A-Za-z0-9_-]{16,})\\b"),
            Pattern.compile("\\b(?:sk|rk)_live_[A-Za-z0-9]{16,}\\b"),
            Pattern.compile("\\bAIza[A-Za-z0-9_-]{30,}\\b"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"),
            Pattern.compile("(?i)\\b(?:Bearer|Basic)\\s+[A-Za-z0-9+/_.=~-]+"),
            Pattern.compile("(?i)[a-z][a-z0-9+.-]*://[^\\s/@:]+:[^\\s/@]+@")
    );

    private AiSecretRedactor() {}

    public static String redact(String content) {
        if (content == null || content.isEmpty()) return content;
        // Preserve newlines so file/proposal line references remain stable.
        String safe = PRIVATE_KEY.matcher(content).replaceAll(match ->
                Matcher.quoteReplacement(REDACTED + match.group().replaceAll("[^\\r\\n]", "")));
        safe = ASSIGNMENT.matcher(safe).replaceAll(match ->
                Matcher.quoteReplacement(match.group(1) + REDACTED));
        for (Pattern token : TOKENS) {
            safe = token.matcher(safe).replaceAll(Matcher.quoteReplacement(REDACTED));
        }
        return safe;
    }
}
