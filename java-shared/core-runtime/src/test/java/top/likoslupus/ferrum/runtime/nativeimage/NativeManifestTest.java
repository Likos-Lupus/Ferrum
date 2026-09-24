package top.likoslupus.ferrum.runtime.nativeimage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import top.likoslupus.ferrum.runtime.PlatformId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NativeManifestTest {

    private static final String JSON = /* language=JSON */ """
            {
              "abi": 1,
              "rustVersion": "0.1.0",
              "gitCommit": "abc123",
              "libraries": [
                {
                  "platform": "linux-glibc-x86_64",
                  "path": "linux-glibc-x86_64/libferrum.so",
                  "sha256": "00",
                  "abi": 1
                }
              ]
            }
            """;

    @Test
    void parsesManifestAndResolvesPlatform() {
        var manifest = NativeManifest.read(
                new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(1, manifest.abi());
        assertEquals("0.1.0", manifest.rustVersion());
        assertEquals("abc123", manifest.gitCommit());

        var entry = manifest.forPlatform(PlatformId.LINUX_GLIBC_X86_64);
        assertNotNull(entry);
        assertEquals("00", entry.sha256());
        assertNull(manifest.forPlatform(PlatformId.WINDOWS_X86_64));
    }

}
