/**
 * @file stdio_defs.h
 * @brief Common definitions for core-sim
 *
 * Contains:
 * - LOG macros for stderr logging
 * - Byte packing utilities
 * - Message type constants for typed protocol
 * - Typed message I/O declarations
 */

#ifndef STDIO_DEFS_H
#define STDIO_DEFS_H

#include <sys/types.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

// Logging macros
#define LOG(fmt, ...)                fprintf(stderr, "[core-sim] " fmt "\n", ##__VA_ARGS__)
#define LOG_MODULE(module, fmt, ...) fprintf(stderr, "[" module "] " fmt "\n", ##__VA_ARGS__)

// Byte packing utilities (big-endian and little-endian)
static inline void pack_be32(uint8_t* buf, uint32_t value) {
  buf[0] = (uint8_t)(value >> 24);
  buf[1] = (uint8_t)(value >> 16);
  buf[2] = (uint8_t)(value >> 8);
  buf[3] = (uint8_t)value;
}

static inline void pack_le32(uint8_t* buf, uint32_t value) {
  buf[0] = (uint8_t)value;
  buf[1] = (uint8_t)(value >> 8);
  buf[2] = (uint8_t)(value >> 16);
  buf[3] = (uint8_t)(value >> 24);
}

static inline uint32_t unpack_be32(const uint8_t* buf) {
  return ((uint32_t)buf[0] << 24) | ((uint32_t)buf[1] << 16) | ((uint32_t)buf[2] << 8) |
         (uint32_t)buf[3];
}

// Message types for typed protocol
#define MSG_TYPE_WCA 0x00
#define MSG_TYPE_UI  0x01

// Typed message I/O: [1-byte type][4-byte BE len][payload]
// Implemented in posix/wca_glue.c
ssize_t read_typed_message(uint8_t* msg_type, uint8_t* buf, size_t max_len);
bool write_typed_message(uint8_t msg_type, const uint8_t* buf, size_t len);

// Initialize lib/wca with POSIX proto handler (in posix/wca_glue.c)
void posix_wca_init(void);

#endif /* STDIO_DEFS_H */
