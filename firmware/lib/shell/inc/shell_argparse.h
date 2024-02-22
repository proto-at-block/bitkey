#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define ARG_STR_MAX_LEN (64U)

typedef enum {
  ARG_TYPE_END,
  ARG_TYPE_INT,
  ARG_TYPE_BOOL,
  ARG_TYPE_STR,
  ARG_TYPE_LIT,
  ARG_TYPE_HEX,
} arg_type_t;

typedef struct {
  char shortname;
  const char* longname;
  const char* help;
  arg_type_t type;
  void* parent;
  bool required;
  bool found;
} arg_header_t;

typedef struct {
  arg_header_t header; /* Required header struct */
  int value;
} arg_int_t;

typedef struct {
  arg_header_t header; /* Required header struct */
  bool value;
} arg_bool_t;

typedef struct {
  arg_header_t header;             /* Required header struct */
  char value[ARG_STR_MAX_LEN + 1]; /* +1 for null terminator */
  size_t len;                      /* String length */
} arg_str_t;

typedef struct {
  arg_header_t header; /* Required header struct */
} arg_lit_t;

typedef struct {
  arg_header_t header; /* Required header struct */
  uint8_t* bytes;
  size_t max_len;
  size_t len;
} arg_hex_t;

typedef struct {
  arg_header_t header; /* Required header struct */
  uint32_t errors;
  bool show_help;
} arg_end_t;

void arg_int_init(arg_int_t* arg, const char shortname, const char* longname, const char* help,
                  const bool required);
void arg_bool_init(arg_bool_t* arg, const char shortname, const char* longname, const char* help,
                   const bool required);
void arg_str_init(arg_str_t* arg, const char shortname, const char* longname, const char* help,
                  const bool required);
void arg_lit_init(arg_lit_t* arg, const char shortname, const char* longname, const char* help,
                  const bool required);
void arg_hex_init(arg_hex_t* arg, const char shortname, const char* longname, const char* help,
                  const bool required, const uint8_t* buffer, const size_t buffer_len);
void arg_end_init(arg_end_t* arg);

/* Static argument creation helpers */
#define ARG_x_REQ(type, shortname, longname, help)            \
  ({                                                          \
    static arg_##type##_t arg;                                \
    arg_##type##_init(&arg, shortname, longname, help, true); \
    &arg;                                                     \
  })

#define ARG_x_OPT(type, shortname, longname, help)              \
  ({                                                            \
    static arg_##type##_t _arg;                                 \
    arg_##type##_init(&_arg, shortname, longname, help, false); \
    &_arg;                                                      \
  })

#define ARG_HEX_x(shortname, longname, help, required, buffer, buffer_len)        \
  ({                                                                              \
    static arg_hex_t _arg;                                                        \
    arg_hex_init(&_arg, shortname, longname, help, required, buffer, buffer_len); \
    &_arg;                                                                        \
  })

#define ARG_INT_REQ(shortname, longname, help)  ARG_x_REQ(int, shortname, longname, help)
#define ARG_INT_OPT(shortname, longname, help)  ARG_x_OPT(int, shortname, longname, help)
#define ARG_BOOL_REQ(shortname, longname, help) ARG_x_REQ(bool, shortname, longname, help)
#define ARG_BOOL_OPT(shortname, longname, help) ARG_x_OPT(bool, shortname, longname, help)
#define ARG_STR_REQ(shortname, longname, help)  ARG_x_REQ(str, shortname, longname, help)
#define ARG_STR_OPT(shortname, longname, help)  ARG_x_OPT(str, shortname, longname, help)
#define ARG_LIT_REQ(shortname, longname, help)  ARG_x_REQ(lit, shortname, longname, help)
#define ARG_LIT_OPT(shortname, longname, help)  ARG_x_OPT(lit, shortname, longname, help)
#define ARG_HEX_REQ(shortname, longname, help, buffer, len) \
  ARG_HEX_x(shortname, longname, help, true, buffer, len)
#define ARG_HEX_OPT(shortname, longname, help, buffer, len) \
  ARG_HEX_x(shortname, longname, help, false, buffer, len)

#define ARG_END()              \
  ({                           \
    static arg_end_t _end_arg; \
    arg_end_init(&_end_arg);   \
    &_end_arg;                 \
  })

uint32_t shell_argparse_parse(int argc, char** argv, void** argtable);
