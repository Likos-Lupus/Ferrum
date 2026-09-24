package top.likoslupus.ferrum.codec;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodecOptionsTest {

    @Test
    void absentOptionsUseDefaults() {
        var options = CodecOptions.from(new ModuleSettings(true));

        assertTrue(options.lz4());
        assertTrue(options.accelerateExistingLz4());
        assertFalse(options.preferLz4ForNewWrites());
    }

    @Test
    void scalarOptionsAreRead() {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        values.put("lz4", JsonNodeFactory.instance.booleanNode(false));
        values.put("accelerateExistingLz4", JsonNodeFactory.instance.booleanNode(false));
        values.put("preferLz4ForNewWrites", JsonNodeFactory.instance.booleanNode(true));

        var options = CodecOptions.from(new ModuleSettings(true, 0, values));

        assertFalse(options.lz4());
        assertFalse(options.accelerateExistingLz4());
        assertTrue(options.preferLz4ForNewWrites());
    }

    @Test
    void nonBooleanOptionFallsBackToDefault() {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        values.put("lz4", JsonNodeFactory.instance.numberNode(1));

        var options = CodecOptions.from(new ModuleSettings(true, 0, values));

        assertTrue(options.lz4());
    }

}
