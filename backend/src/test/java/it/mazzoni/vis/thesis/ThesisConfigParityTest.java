package it.mazzoni.vis.thesis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Schema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the Java-side decoding config and response schema match
 * vis-model-training/config/vertex-gemini-v1.json's pinned values — a checked-in fixture
 * ({@code src/test/resources/thesis/vertex-gemini-v1-fixture.json}), manually kept in sync
 * with the Python config (two separate deployable artifacts; see
 * ThesisResponseSchema/ThesisPromptLoader's own javadoc for the same disclosed tradeoff).
 *
 * <p>Schema comparison is structural equivalence (type/required-as-set/enum-as-set/
 * properties-recursively), not JSON-node equality — the same order-independent approach
 * TA2's Python {@code assert_schema_equivalent} already uses, since array insertion order
 * (e.g. {@code required} field order) differs incidentally between the two independently
 * hand-derived representations without being semantically different.
 */
class ThesisConfigParityTest {

    private static JsonNode loadFixtureGenerationConfig() throws IOException {
        try (InputStream in = ThesisConfigParityTest.class.getResourceAsStream(
                "/thesis/vertex-gemini-v1-fixture.json")) {
            assertThat(in).as("fixture must exist at src/test/resources/thesis/vertex-gemini-v1-fixture.json").isNotNull();
            return new ObjectMapper().readTree(in).get("generationConfig");
        }
    }

    @Test
    void decodingConstants_matchFixture() throws IOException {
        JsonNode generationConfig = loadFixtureGenerationConfig();

        assertThat(ThesisProperties.TEMPERATURE).isEqualTo(generationConfig.get("temperature").floatValue());
        assertThat(ThesisProperties.MAX_OUTPUT_TOKENS).isEqualTo(generationConfig.get("maxOutputTokens").intValue());
        // thinkingBudget lives under generationConfig.thinkingBudget in the Python config,
        // not inside responseSchema.
        assertThat(ThesisProperties.THINKING_BUDGET).isEqualTo(generationConfig.get("thinkingBudget").intValue());
        assertThat("application/json").isEqualTo(generationConfig.get("responseMimeType").asText());
    }

    @Test
    void responseSchema_isStructurallyEquivalentToFixture() throws IOException {
        JsonNode fixtureSchema = loadFixtureGenerationConfig().get("responseSchema");
        Schema built = ThesisResponseSchema.build();
        JsonNode builtSchema = com.google.genai.JsonSerializable.toJsonNode(built);

        assertEquivalent(fixtureSchema, builtSchema, "$");
    }

    private static void assertEquivalent(JsonNode expected, JsonNode actual, String path) {
        assertThat(actual.has("type")).as(path + ": type present").isTrue();
        assertThat(actual.get("type").asText()).as(path + ": type")
                .isEqualToIgnoringCase(expected.get("type").asText());

        if (expected.has("required")) {
            assertThat(setOf(actual.get("required"))).as(path + ": required").isEqualTo(setOf(expected.get("required")));
        }
        if (expected.has("enum")) {
            assertThat(setOf(actual.get("enum"))).as(path + ": enum").isEqualTo(setOf(expected.get("enum")));
        }
        if (expected.has("properties")) {
            assertThat(actual.has("properties")).as(path + ": properties present").isTrue();
            Set<String> expectedKeys = keySet(expected.get("properties"));
            Set<String> actualKeys = keySet(actual.get("properties"));
            assertThat(actualKeys).as(path + ": property names").isEqualTo(expectedKeys);
            for (String key : expectedKeys) {
                assertEquivalent(expected.get("properties").get(key), actual.get("properties").get(key), path + "." + key);
            }
        }
        if (expected.has("items")) {
            assertThat(actual.has("items")).as(path + ": items present").isTrue();
            assertEquivalent(expected.get("items"), actual.get("items"), path + "[]");
        }
    }

    private static Set<String> setOf(JsonNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false).map(JsonNode::asText)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Set<String> keySet(JsonNode objectNode) {
        Iterator<String> it = objectNode.fieldNames();
        Set<String> keys = new java.util.HashSet<>();
        it.forEachRemaining(keys::add);
        return keys;
    }
}
