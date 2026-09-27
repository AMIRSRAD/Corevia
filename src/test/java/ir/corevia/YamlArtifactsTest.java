package ir.corevia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class YamlArtifactsTest {

    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    @Test
    void openApiAndConfigurationPacksAreValidYaml() throws IOException {
        Map<String, Object> openApi = load("contracts/openapi/corevia-v1.yaml");
        Map<String, Object> iran = load("jurisdiction-packs/iran/v1.yml");
        Map<String, Object> bank = load("bank-packs/reference-bank/v1.yml");
        Map<String, Object> compose = load("compose.yaml");

        assertThat(openApi.get("openapi")).isEqualTo("3.1.0");
        assertThat(asMap(openApi.get("paths"))).containsKeys(
                "/api/v1/accounts", "/api/v1/journals", "/api/v1/journals/{id}/reversals");
        assertThat(asMap(iran.get("pack"))).containsEntry("id", "iran");
        assertThat(asMap(bank.get("pack"))).containsEntry("id", "reference-bank");
        assertThat(asMap(compose.get("services"))).containsKeys("postgres", "corevia");

        assertLocalReferencesResolve(openApi, openApi);
    }

    @SuppressWarnings("unchecked")
    private void assertLocalReferencesResolve(Object node, Map<String, Object> root) {
        if (node instanceof Map<?, ?> map) {
            Object reference = map.get("$ref");
            if (reference instanceof String ref && ref.startsWith("#/")) {
                Object resolved = root;
                for (String segment : ref.substring(2).split("/")) {
                    resolved = asMap(resolved).get(segment.replace("~1", "/").replace("~0", "~"));
                    assertThat(resolved).as("OpenAPI reference %s", ref).isNotNull();
                }
            }
            map.values().forEach(value -> assertLocalReferencesResolve(value, root));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> assertLocalReferencesResolve(value, root));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> load(String relativePath) throws IOException {
        try (InputStream input = Files.newInputStream(Path.of(relativePath))) {
            return asMap(yaml.load(input));
        }
    }
}
