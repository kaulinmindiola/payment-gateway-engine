package com.paymentgateway.engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class OpenApiSpecSyncIT extends AbstractApplicationIntegrationTest {

    private static final Path SPEC = Path.of("docs/openapi.yaml");
    private static final String REGENERATE =
            "Regenerate with: OPENAPI_UPDATE=true ./mvnw verify -Dit.test=OpenApiSpecSyncIT";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void committedSpecMatchesGeneratedSpec() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        if ("true".equals(System.getenv("OPENAPI_UPDATE"))) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, generated);
            return;
        }

        assertThat(SPEC).as("docs/openapi.yaml is missing. " + REGENERATE).exists();
        assertThat(normalize(Files.readString(SPEC)))
                .as("docs/openapi.yaml is out of date. " + REGENERATE)
                .isEqualTo(normalize(generated));
    }

    @Test
    void generatedSpecHasNoBrokenReferences() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode spec = new ObjectMapper().readTree(json);
        List<String> broken = new ArrayList<>();
        collectBrokenRefs(spec, spec, broken);

        assertThat(broken)
                .as("Every internal $ref in the generated OpenAPI spec must resolve")
                .isEmpty();
    }

    private static void collectBrokenRefs(JsonNode node, JsonNode root, List<String> broken) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual() && ref.asText().startsWith("#/")
                    && root.at(ref.asText().substring(1)).isMissingNode()) {
                broken.add(ref.asText());
            }
        }
        node.elements().forEachRemaining(child -> collectBrokenRefs(child, root, broken));
    }

    private static String normalize(String yaml) {
        return yaml.replace("\r\n", "\n").strip();   // CRLF en Windows/WSL no cuenta como cambio
    }
}