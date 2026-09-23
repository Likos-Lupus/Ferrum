package top.likoslupus.ferrum.runtime.data;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.toml.TomlMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The only entry point for reading and writing structured data (JSON/YAML/TOML) inside Ferrum.
 *
 * <p>Business modules must not construct their own mappers; the mappers below are built once and
 * reused.
 */
public final class DataFormats {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final TomlMapper TOML = TomlMapper.builder().build();

    private DataFormats() {
    }

    public static JsonMapper json() {
        return JSON;
    }

    public static YAMLMapper yaml() {
        return YAML;
    }

    public static TomlMapper toml() {
        return TOML;
    }

}
