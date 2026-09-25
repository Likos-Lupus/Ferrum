#ifndef FERRUM_ABI_H
#define FERRUM_ABI_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define FERRUM_ABI_VERSION 1u

enum FerrumStatus {
    FERRUM_OK = 0,
    FERRUM_ERR_INVALID_ARGUMENT = -1,
    FERRUM_ERR_BUFFER_TOO_SMALL = -2,
    FERRUM_ERR_MALFORMED_INPUT = -3,
    FERRUM_ERR_LIMIT_EXCEEDED = -4,
    FERRUM_ERR_UNSUPPORTED = -5,
    FERRUM_ERR_ABI_MISMATCH = -6,
    FERRUM_ERR_INTERNAL = -7,
    FERRUM_ERR_PANIC = -127
};

enum FerrumFeatureBits {
    FERRUM_FEATURE_NBT       = 1ull << 0,
    FERRUM_FEATURE_CODEC_LZ4 = 1ull << 1,
    FERRUM_FEATURE_PALETTE   = 1ull << 2,
    FERRUM_FEATURE_NOISE     = 1ull << 3,
    FERRUM_FEATURE_LIGHT     = 1ull << 4,
    FERRUM_FEATURE_COLLIDE   = 1ull << 5,
    FERRUM_FEATURE_PATH      = 1ull << 6
};

struct FerrumBuildInfo {
    uint32_t struct_size;
    uint32_t abi_version;
    uint64_t feature_bits;
    uint8_t  git_commit[20];
    uint8_t  reserved[28];
};

struct FerrumLimits {
    uint64_t max_total_bytes;
    uint32_t max_depth;
    uint32_t max_nodes;
    uint32_t max_array_length;
    uint32_t max_string_encoded_bytes;
};

uint32_t ferrum_abi_version(void);
uint64_t ferrum_feature_bits(void);
int32_t ferrum_build_info(struct FerrumBuildInfo* out, size_t out_size);
int32_t ferrum_selftest_checksum(uint64_t input, uint64_t* output);

/*
 * Module entry points.
 *
 * These symbols are declared for ABI stability. Until the corresponding module is implemented and
 * its feature bit is advertised by ferrum_feature_bits(), every call returns FERRUM_ERR_UNSUPPORTED.
 */

int32_t ferrum_nbt_parse(
    const uint8_t* src, size_t src_len,
    const struct FerrumLimits* limits,
    uint8_t* arena, size_t arena_cap,
    uint32_t* root_index,
    size_t* used_or_required);

int32_t ferrum_nbt_write(
    const uint8_t* arena, size_t arena_len,
    uint32_t root_index,
    uint8_t* dst, size_t dst_cap,
    size_t* written_or_required);

int32_t ferrum_nbt_parse_any(
    const uint8_t* src, size_t src_len,
    const struct FerrumLimits* limits,
    uint8_t* arena, size_t arena_cap,
    uint32_t* root_index,
    size_t* used_or_required,
    size_t* consumed);

int32_t ferrum_nbt_write_any(
    const uint8_t* arena, size_t arena_len,
    uint32_t root_index,
    uint8_t* dst, size_t dst_cap,
    size_t* written_or_required);

int32_t ferrum_lz4_block_stream_decompress(
    const uint8_t* src, size_t src_len,
    uint8_t* dst, size_t dst_cap,
    size_t* written_or_required);

int32_t ferrum_lz4_block_stream_compress(
    const uint8_t* src, size_t src_len,
    uint8_t* dst, size_t dst_cap,
    int32_t compression_level,
    size_t* written_or_required);

int32_t ferrum_palette_unpack(
    const uint64_t* data, size_t data_len,
    uint32_t bits, size_t value_count,
    uint32_t* out_values, size_t out_len);

int32_t ferrum_palette_pack(
    const uint32_t* values, size_t value_count,
    uint32_t bits,
    uint64_t* out_data, size_t out_len);

int32_t ferrum_noise_create(
    const uint8_t* descriptor, size_t descriptor_len,
    uint64_t* out_handle);

int32_t ferrum_noise_batch(
    uint64_t noise_handle,
    const double* xs, const double* ys, const double* zs,
    double* out_values, size_t sample_count,
    uint32_t flags);

int32_t ferrum_noise_destroy(uint64_t noise_handle);

int32_t ferrum_light_block_batch(
    const uint8_t* in, size_t in_len,
    uint8_t* out, size_t out_cap,
    size_t* out_written);

#ifdef __cplusplus
}
#endif
#endif
