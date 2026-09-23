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

#ifdef __cplusplus
}
#endif
#endif
