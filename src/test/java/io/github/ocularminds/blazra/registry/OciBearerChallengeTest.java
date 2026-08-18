package io.github.ocularminds.blazra.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class OciBearerChallengeTest {
    private static final URI REGISTRY = URI.create("https://registry.example.com/");

    @Test
    void parsesAndEncodesAValidBearerChallenge() throws Exception {
        OciBearerChallenge challenge = OciBearerChallenge.parse(
                "Bearer realm=\"https://registry.example.com/token?account=anonymous\","
                        + "service=\"registry.example.com\","
                        + "scope=\"repository:team/app:pull\",error=\"ignored\"",
                REGISTRY);

        assertEquals(URI.create("https://registry.example.com/token?account=anonymous"),
                challenge.realm());
        assertEquals("registry.example.com", challenge.service());
        assertEquals("repository:team/app:pull", challenge.scope());
        assertEquals(
                URI.create("https://registry.example.com/token?account=anonymous"
                        + "&service=registry.example.com"
                        + "&scope=repository%3Ateam%2Fapp%3Apull"),
                challenge.tokenUri());
    }

    @Test
    void acceptsCaseInsensitiveSchemeAndEscapedValues() throws Exception {
        OciBearerChallenge challenge = OciBearerChallenge.parse(
                "bEaReR realm=\"https://registry.example.com/token\", "
                        + "service=\"registry.example.com\", scope=\"aws\", note=\"a\\\"b\"",
                REGISTRY);

        assertEquals("aws", challenge.scope());
    }

    @Test
    void rejectsMalformedAuthenticationChallenges() {
        String validRealm = "realm=\"https://registry.example.com/token\"";
        String validService = "service=\"registry.example.com\"";
        String validScope = "scope=\"repository:team/app:pull\"";
        for (String header : new String[]{
                null,
                "Basic " + validRealm,
                "Bearer" + validRealm,
                "Bearer =\"bad\"," + validService + "," + validScope,
                "Bearer realm\"bad\"," + validService + "," + validScope,
                "Bearer realm=bad," + validService + "," + validScope,
                "Bearer realm=\"unterminated," + validService + "," + validScope,
                "Bearer realm=\"bad\\",
                "Bearer realm=\"bad\u0001\"," + validService + "," + validScope,
                "Bearer " + validRealm + " " + validService + "," + validScope,
                "Bearer " + validRealm + "," + validRealm + "," + validScope,
                "Bearer " + validRealm + "," + validService,
                "Bearer " + validRealm + "," + validScope,
                "Bearer " + validRealm + ",service=\"bad service\"," + validScope,
                "Bearer " + validRealm + "," + validService + ",scope=\"bad scope\"",
                "Bearer " + validRealm + "," + validService + ",scope=\"\"",
                "Bearer " + validRealm + "," + validService + "," + validScope + ","
        }) {
            assertThrows(
                    RegistryException.class,
                    () -> OciBearerChallenge.parse(header, REGISTRY),
                    () -> "accepted challenge " + header);
        }
        assertThrows(
                RegistryException.class,
                () -> OciBearerChallenge.parse("Bearer " + "x".repeat(2049), REGISTRY));
    }

    @Test
    void rejectsUnsafeAuthenticationRealms() {
        for (String realm : new String[]{
                "/token",
                "http://registry.example.com/token",
                "https://attacker.example/token",
                "https://user@registry.example.com/token",
                "https://registry.example.com/token#fragment",
                "not a URI"
        }) {
            String header = "Bearer realm=\"" + realm
                    + "\",service=\"registry.example.com\",scope=\"aws\"";
            assertThrows(
                    RegistryException.class,
                    () -> OciBearerChallenge.parse(header, REGISTRY),
                    () -> "accepted realm " + realm);
        }
    }
}
