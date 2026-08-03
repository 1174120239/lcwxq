package cn.lcxqy.starfree.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhpassPasswordVerifierTest {
    private final PhpassPasswordVerifier verifier = new PhpassPasswordVerifier();

    @Test
    void verifiesHashProducedByLegacyPhpassImplementation() {
        String hash = "$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/";

        assertTrue(verifier.matches("correct horse battery staple", hash));
        assertFalse(verifier.matches("wrong password", hash));
    }

    @Test
    void rejectsMissingOrMalformedHashes() {
        assertFalse(verifier.matches("password", null));
        assertFalse(verifier.matches("password", "not-a-phpass-hash"));
    }

    @Test
    void generatedHashesUseTheLegacyPortableFormat() {
        String first = verifier.hash("correct horse battery staple");
        String second = verifier.hash("correct horse battery staple");

        assertTrue(first.startsWith("$P$B"));
        assertTrue(first.length() == 34);
        assertTrue(verifier.matches("correct horse battery staple", first));
        assertFalse(verifier.matches("wrong password", first));
        assertNotEquals(first, second);
    }
}
