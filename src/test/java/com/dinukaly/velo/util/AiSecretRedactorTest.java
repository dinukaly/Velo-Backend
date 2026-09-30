package com.dinukaly.velo.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiSecretRedactorTest {
    @Test
    void proposalsCannotCopyRedactionMarkersIntoWorkspaceEdits() {
        var parser = new AgentProposalParser(new com.fasterxml.jackson.databind.ObjectMapper());
        for (String marker : new String[]{"[REDACTED_SECRET]", "\\u005bREDACTED_SECRET]"}) {
            var failure = assertThrows(AgentProposalParser.AgentParseException.class, () -> parser.parse(
                    "{\"files\":[{\"fullContent\":\"" + marker + "\"}]}"));
            assertTrue(failure.getMessage().contains("redacted secret text"));
        }
    }

    @Test
    void removesRecognizableTokensAndCredentialAssignments() {
        for (String value : new String[]{
                "AKIA" + "A".repeat(16), "ghp_" + "b".repeat(36),
                "sk-or-v1-" + "c".repeat(64), "AIza" + "d".repeat(35),
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature",
                "Bearer test-token", "Basic dXNlcjpwYXNz",
                "postgres://user:private-password@"}) {
            String safe = AiSecretRedactor.redact("before " + value + " after");
            assertFalse(safe.contains(value));
            assertTrue(safe.contains(AiSecretRedactor.REDACTED));
        }
        for (String line : new String[]{"DB_PASSWORD=hidden-value", "\"api_key\": \"hidden-value\",",
                "jwt.secret: 'hidden-value'", "accessToken = \"hidden-value\";"}) {
            assertFalse(AiSecretRedactor.redact(line).contains("hidden-value"));
        }
    }

    @Test
    void multilinePrivateKeysAndUnterminatedKeysPreserveLineCount() {
        for (String ending : new String[]{"-----END RSA PRIVATE KEY-----", ""}) {
            String original = "before\r\n-----BEGIN RSA PRIVATE KEY-----\r\nprivate-material\r\n" + ending;
            String safe = AiSecretRedactor.redact(original);
            assertFalse(safe.contains("private-material"));
            assertEquals(original.chars().filter(c -> c == '\n').count(),
                    safe.chars().filter(c -> c == '\n').count());
        }
    }

    @Test
    void ordinaryCodeIsUnchangedAndRedactionIsIdempotent() {
        String code = "public int add(int a, int b) { return a + b; }\n// normal comment";
        assertEquals(code, AiSecretRedactor.redact(code));
        String safe = AiSecretRedactor.redact("password=confidential\nnext();");
        assertEquals(safe, AiSecretRedactor.redact(safe));
        assertNull(AiSecretRedactor.redact(null));
        assertEquals("", AiSecretRedactor.redact(""));
    }
}
