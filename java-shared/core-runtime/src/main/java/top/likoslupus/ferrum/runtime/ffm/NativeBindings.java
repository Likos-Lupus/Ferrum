package top.likoslupus.ferrum.runtime.ffm;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.*;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * The single place where Ferrum native symbols are bound.
 *
 * <p>Symbols are looked up and bound exactly once, at startup, with fixed
 * {@link FunctionDescriptor}s. Business code must call the typed wrappers here and never hold a raw
 * {@link MethodHandle}; runtime symbol lookup is forbidden.
 */
public final class NativeBindings implements AutoCloseable {

    /** The ABI version this Java runtime understands. */
    public static final int EXPECTED_ABI = 1;

    private static final StructLayout BUILD_INFO_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("struct_size"),
            ValueLayout.JAVA_INT.withName("abi_version"),
            ValueLayout.JAVA_LONG.withName("feature_bits"),
            MemoryLayout.sequenceLayout(20L, ValueLayout.JAVA_BYTE).withName("git_commit"),
            MemoryLayout.sequenceLayout(28L, ValueLayout.JAVA_BYTE).withName("reserved")
    );

    /** The expected byte size of the native {@code FerrumBuildInfo} struct. */
    public static final int BUILD_INFO_SIZE = (int) BUILD_INFO_LAYOUT.byteSize();

    private static final Set<String> CORE_SYMBOLS = Set.of(
            "ferrum_abi_version",
            "ferrum_feature_bits",
            "ferrum_build_info",
            "ferrum_selftest_checksum"
    );

    private static final Map<String, FunctionDescriptor> MODULE_DESCRIPTORS = moduleDescriptors();

    private final Arena arena;
    private final MethodHandle abiVersion;
    private final MethodHandle featureBits;
    private final MethodHandle buildInfo;
    private final MethodHandle selftest;
    private final Map<String, MethodHandle> moduleSymbols;
    private final @Nullable NbtBindings nbt;
    private final @Nullable CodecBindings codec;
    private final @Nullable PaletteBindings palette;
    private final @Nullable NoiseBindings noise;
    private final @Nullable LightBindings light;
    private final @Nullable CollideBindings collide;

    private NativeBindings(
            Arena arena,
            MethodHandle abiVersion,
            MethodHandle featureBits,
            MethodHandle buildInfo,
            MethodHandle selftest,
            Map<String, MethodHandle> moduleSymbols,
            @Nullable NbtBindings nbt,
            @Nullable CodecBindings codec,
            @Nullable PaletteBindings palette,
            @Nullable NoiseBindings noise,
            @Nullable LightBindings light,
            @Nullable CollideBindings collide
    ) {
        this.arena = arena;
        this.abiVersion = abiVersion;
        this.featureBits = featureBits;
        this.buildInfo = buildInfo;
        this.selftest = selftest;
        this.moduleSymbols = moduleSymbols;
        this.nbt = nbt;
        this.codec = codec;
        this.palette = palette;
        this.noise = noise;
        this.light = light;
        this.collide = collide;
    }

    /**
     * Loads the library and binds core and optional module symbols.
     *
     * @param library the native library file
     *
     * @return the bound symbols
     *
     * @throws RuntimeException when the library cannot be loaded or a core symbol is missing
     */
    public static NativeBindings load(Path library) {
        requireNonNull(library, "library");
        var arena = Arena.ofShared();
        try {
            var lookup = SymbolLookup.libraryLookup(library, arena);
            var linker = Linker.nativeLinker();
            var abiVersion = bind(
                    linker, lookup, "ferrum_abi_version",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT)
            );
            var featureBits = bind(
                    linker, lookup, "ferrum_feature_bits",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG)
            );
            var buildInfo = bind(
                    linker, lookup, "ferrum_build_info",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG
                    )
            );
            var selftest = bind(
                    linker, lookup, "ferrum_selftest_checksum",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS
                    )
            );

            var modules = new LinkedHashMap<String, MethodHandle>();
            MODULE_DESCRIPTORS
                    .forEach((key, value) ->
                            lookup.find(key).ifPresent(memorySegment ->
                                    modules.put(
                                            key,
                                            linker.downcallHandle(memorySegment, value)
                                    )
                            )
                    );

            return new NativeBindings(
                    arena,
                    abiVersion,
                    featureBits,
                    buildInfo,
                    selftest,
                    Map.copyOf(modules),
                    nbtBindings(modules),
                    codecBindings(modules),
                    paletteBindings(modules),
                    noiseBindings(modules),
                    lightBindings(modules),
                    collideBindings(modules)
            );
        } catch (RuntimeException | Error throwable) {
            arena.close();
            throw throwable;
        }
    }

    private static MethodHandle bind(
            Linker linker,
            SymbolLookup lookup,
            String name,
            FunctionDescriptor descriptor
    ) {
        var symbol = lookup.find(name)
                .orElseThrow(() ->
                        new IllegalStateException("missing native symbol: " + name)
                );
        return linker.downcallHandle(symbol, descriptor);
    }

    private static @Nullable NbtBindings nbtBindings(Map<String, MethodHandle> modules) {
        var parse = modules.get("ferrum_nbt_parse");
        var parseAny = modules.get("ferrum_nbt_parse_any");
        var write = modules.get("ferrum_nbt_write");
        var writeAny = modules.get("ferrum_nbt_write_any");
        if (parse == null
                || parseAny == null
                || write == null
                || writeAny == null
        ) {
            return null;
        }
        return new NbtBindings(parse, parseAny, write, writeAny);
    }

    private static @Nullable CodecBindings codecBindings(Map<String, MethodHandle> modules) {
        var decompress = modules.get("ferrum_lz4_block_stream_decompress");
        var compress = modules.get("ferrum_lz4_block_stream_compress");
        return decompress == null || compress == null
                ? null
                : new CodecBindings(decompress, compress);
    }

    private static @Nullable PaletteBindings paletteBindings(Map<String, MethodHandle> modules) {
        var unpack = modules.get("ferrum_palette_unpack");
        var pack = modules.get("ferrum_palette_pack");
        return unpack == null || pack == null
                ? null
                : new PaletteBindings(unpack, pack);
    }

    private static @Nullable NoiseBindings noiseBindings(Map<String, MethodHandle> modules) {
        var create = modules.get("ferrum_noise_create");
        var batch = modules.get("ferrum_noise_batch");
        var destroy = modules.get("ferrum_noise_destroy");
        return create == null || batch == null || destroy == null
                ? null
                : new NoiseBindings(create, batch, destroy);
    }

    private static @Nullable LightBindings lightBindings(Map<String, MethodHandle> modules) {
        var blockBatch = modules.get("ferrum_light_block_batch");
        return Optional.ofNullable(blockBatch)
                .map(LightBindings::new)
                .orElse(null);
    }

    private static @Nullable CollideBindings collideBindings(Map<String, MethodHandle> modules) {
        var aabbClip = modules.get("ferrum_collide_aabb_clip");
        var sweep = modules.get("ferrum_collide_sweep");
        return aabbClip == null || sweep == null
                ? null
                : new CollideBindings(aabbClip, sweep);
    }

    private static Map<String, FunctionDescriptor> moduleDescriptors() {
        var descriptors = new LinkedHashMap<String, FunctionDescriptor>();
        var status = ValueLayout.JAVA_INT;
        var address = ValueLayout.ADDRESS;
        var longs = ValueLayout.JAVA_LONG;
        var ints = ValueLayout.JAVA_INT;

        descriptors.put(
                "ferrum_nbt_parse",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        address,
                        address,
                        longs,
                        address,
                        address
                )
        );
        descriptors.put(
                "ferrum_nbt_write",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        ints,
                        address,
                        longs,
                        address
                )
        );
        descriptors.put(
                "ferrum_nbt_parse_any",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        address,
                        address,
                        longs,
                        address,
                        address,
                        address
                )
        );
        descriptors.put(
                "ferrum_nbt_write_any",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        ints,
                        address,
                        longs,
                        address
                )
        );
        descriptors.put(
                "ferrum_lz4_block_stream_decompress",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        address,
                        longs,
                        address
                )
        );
        descriptors.put(
                "ferrum_lz4_block_stream_compress",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        address,
                        longs,
                        ints,
                        address
                )
        );
        descriptors.put(
                "ferrum_palette_unpack",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        ints,
                        longs,
                        address,
                        longs
                )
        );
        descriptors.put(
                "ferrum_palette_pack",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        ints,
                        address,
                        longs
                )
        );
        descriptors.put(
                "ferrum_noise_create",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        address
                )
        );
        descriptors.put(
                "ferrum_noise_batch",
                FunctionDescriptor.of(
                        status,
                        longs,
                        address,
                        address,
                        address,
                        address,
                        longs,
                        ints
                )
        );
        descriptors.put(
                "ferrum_noise_destroy",
                FunctionDescriptor.of(status, longs)
        );
        descriptors.put(
                "ferrum_light_block_batch",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        address,
                        longs,
                        address
                )
        );
        descriptors.put(
                "ferrum_collide_aabb_clip",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        address,
                        longs,
                        address
                )
        );
        descriptors.put(
                "ferrum_collide_sweep",
                FunctionDescriptor.of(
                        status,
                        address,
                        longs,
                        address,
                        longs,
                        address
                )
        );

        descriptors.put(
                "ferrum_selftest_panic",
                FunctionDescriptor.of(status)
        );

        return Map.copyOf(descriptors);
    }

    /**
     * Returns the ABI version reported by the library.
     *
     * @return the ABI version
     */
    public int abiVersion() {
        try {
            return (int) abiVersion.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("ferrum_abi_version failed", throwable);
        }
    }

    /**
     * Returns the feature bits reported by the library.
     *
     * @return the advertised feature bits
     */
    public long featureBits() {
        try {
            return (long) featureBits.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("ferrum_feature_bits failed", throwable);
        }
    }

    /**
     * Reads the native build information.
     *
     * @return the decoded build information
     */
    public NativeBuildInfo buildInfo() {
        var segment = arena.allocate(BUILD_INFO_LAYOUT);
        try {
            var status = (int) buildInfo.invokeExact(segment, BUILD_INFO_LAYOUT.byteSize());
            if (status != 0) {
                throw new IllegalStateException("ferrum_build_info status=" + status);
            }
            return decodeBuildInfo(segment);
        } catch (Throwable throwable) {
            throw new IllegalStateException("ferrum_build_info failed", throwable);
        }
    }

    private static NativeBuildInfo decodeBuildInfo(MemorySegment segment) {
        var structSize = segment.get(ValueLayout.JAVA_INT, 0L);
        var abiVersion = segment.get(ValueLayout.JAVA_INT, 4L);
        var featureBits = segment.get(ValueLayout.JAVA_LONG, 8L);
        var commit = new byte[20];
        MemorySegment.copy(
                segment,
                ValueLayout.JAVA_BYTE,
                16L,
                commit,
                0,
                commit.length
        );
        return new NativeBuildInfo(structSize, abiVersion, featureBits, toHex(commit));
    }

    private static String toHex(byte[] bytes) {
        var builder = new StringBuilder(bytes.length * 2);
        for (var value : bytes) {
            builder.append(Character.forDigit(((int) value >> 4) & 0xF, 16));
            builder.append(Character.forDigit((int) value & 0xF, 16));
        }
        return builder.toString();
    }

    /**
     * Runs the deterministic selftest checksum, throwing on failure.
     *
     * @param input the selftest input
     *
     * @return the checksum value
     */
    public long selftest(long input) {
        var outcome = invokeSelftest(input);
        if (!outcome.isOk()) {
            throw new IllegalStateException("ferrum_selftest_checksum status=" + outcome.status());
        }
        return requireNonNull(outcome.value(), "selftest value");
    }

    /**
     * Runs the deterministic selftest checksum, surfacing its status.
     *
     * @param input the selftest input
     *
     * @return the outcome, carrying the checksum on success
     */
    public NativeOutcome<Long> invokeSelftest(long input) {
        var output = arena.allocate(ValueLayout.JAVA_LONG);
        try {
            var status = NativeStatus.fromCode((int) selftest.invokeExact(input, output));
            return status.isOk()
                    ? NativeOutcome.ok(output.get(ValueLayout.JAVA_LONG, 0L))
                    : NativeOutcome.failure(status);
        } catch (Throwable throwable) {
            return NativeOutcome.failure(NativeStatus.INTERNAL);
        }
    }

    /**
     * Returns the typed NBT bindings.
     *
     * @return the NBT bindings, or {@code null} when the symbols are not present
     */
    public @Nullable NbtBindings nbt() {
        return nbt;
    }

    /**
     * Returns the typed codec bindings.
     *
     * @return the codec bindings, or {@code null} when the symbols are not present
     */
    public @Nullable CodecBindings codec() {
        return codec;
    }

    /**
     * Returns the typed palette bindings.
     *
     * @return the palette bindings, or {@code null} when the symbols are not present
     */
    public @Nullable PaletteBindings palette() {
        return palette;
    }

    /**
     * Returns the typed noise bindings.
     *
     * @return the noise bindings, or {@code null} when the symbols are not present
     */
    public @Nullable NoiseBindings noise() {
        return noise;
    }

    /**
     * Returns the typed light bindings.
     *
     * @return the light bindings, or {@code null} when the symbols are not present
     */
    public @Nullable LightBindings light() {
        return light;
    }

    /**
     * Returns the typed collide bindings.
     *
     * @return the collide bindings, or {@code null} when the symbols are not present
     */
    public @Nullable CollideBindings collide() {
        return collide;
    }

    /**
     * Returns whether a symbol is present in the loaded library.
     *
     * @param name the symbol name
     *
     * @return {@code true} for bound core or module symbols
     */
    public boolean hasSymbol(String name) {
        return CORE_SYMBOLS.contains(name) || moduleSymbols.containsKey(name);
    }

    /**
     * Returns the names of every bound symbol.
     *
     * @return the bound symbol names
     */
    public Set<String> symbolNames() {
        var names = new LinkedHashSet<>(CORE_SYMBOLS);
        names.addAll(moduleSymbols.keySet());
        return Set.copyOf(names);
    }

    /**
     * Returns a bound module symbol handle.
     *
     * @param name the symbol name
     *
     * @return the handle, or {@code null} when the symbol is absent
     */
    @Nullable MethodHandle symbol(String name) {
        return moduleSymbols.get(name);
    }

    @Override
    public void close() {
        arena.close();
    }

}
