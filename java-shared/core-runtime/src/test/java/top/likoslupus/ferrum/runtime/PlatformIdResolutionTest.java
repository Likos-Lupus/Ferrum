package top.likoslupus.ferrum.runtime;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PlatformIdResolutionTest {

    @Test
    void resolvesLinuxGlibcX86_64() {
        assertEquals(
                PlatformId.LINUX_GLIBC_X86_64,
                PlatformId.from("Linux", "amd64", "glibc")
        );
    }

    @Test
    void resolvesLinuxMuslAarch64() {
        assertEquals(
                PlatformId.LINUX_MUSL_AARCH64,
                PlatformId.from("Linux", "aarch64", "musl")
        );
    }

    @Test
    void resolvesWindowsAndMacos() {
        assertEquals(
                PlatformId.WINDOWS_X86_64,
                PlatformId.from("Windows 11", "x86_64", "glibc")
        );
        assertEquals(
                PlatformId.MACOS_AARCH64,
                PlatformId.from("Mac OS X", "arm64", "glibc")
        );
    }

    @Test
    void unsupportedOsOrArchIsReported() {
        assertEquals(
                PlatformId.UNSUPPORTED,
                PlatformId.from("Plan9", "x86_64", "glibc")
        );
        assertEquals(
                PlatformId.UNSUPPORTED,
                PlatformId.from("Linux", "sparc", "glibc")
        );
    }

    @Test
    void roundTripsThroughStableId() {
        Arrays.stream(PlatformId.values())
                .forEach(platform ->
                        assertEquals(platform, PlatformId.fromId(platform.id()))
                );
        assertEquals(PlatformId.UNSUPPORTED, PlatformId.fromId("nonsense"));
    }

    @Test
    void exposesLibraryFileNames() {
        assertEquals("ferrum.dll", PlatformId.WINDOWS_X86_64.libraryFileName());
        assertEquals("libferrum.dylib", PlatformId.MACOS_X86_64.libraryFileName());
        assertEquals("libferrum.so", PlatformId.LINUX_GLIBC_X86_64.libraryFileName());
    }

    @Test
    void supportedFlagMatchesUnsupportedValue() {
        assertTrue(PlatformId.LINUX_GLIBC_X86_64.isSupported());
        assertFalse(PlatformId.UNSUPPORTED.isSupported());
    }

}
