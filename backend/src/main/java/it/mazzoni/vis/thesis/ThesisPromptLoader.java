package it.mazzoni.vis.thesis;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the system prompt text from a bundled classpath resource
 * ({@code src/main/resources/prompts/system-prompt-v3.txt}) — a checked-in copy of
 * vis-model-training/prompts/system-prompt-v3.txt, kept in sync manually (two separate
 * deployable artifacts, Python training repo vs. Java backend, same disclosed tradeoff as
 * ThesisResponseSchema/ThesisConfigParityTest). Never read from the training repo's
 * filesystem path directly — no cross-project filesystem access guarantee in every
 * deployment/CI environment.
 */
@Component
public class ThesisPromptLoader {

    private static final String RESOURCE_PATH = "classpath:prompts/system-prompt-v3.txt";

    private final String promptText;

    public ThesisPromptLoader() {
        this.promptText = read();
    }

    public String promptText() {
        return promptText;
    }

    private static String read() {
        Resource resource = new PathMatchingResourcePatternResolver().getResource(RESOURCE_PATH);
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE_PATH, e);
        }
    }
}
