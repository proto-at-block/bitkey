#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
  INDEXFS_TYPE_MONOTONIC,
  INDEXFS_TYPE_MAX,
} indexfs_type_t;

typedef struct {
  indexfs_type_t type;
  char* name;
  uint32_t* address;
  uint32_t size;
} indexfs_t;

indexfs_t* indexfs_create_static(indexfs_t* fs);
#define indexfs_create(_type, _name, _address, _size)           \
  ({                                                            \
    static SHARED_TASK_DATA indexfs_t _##_name##_indexfs = {0}; \
    _##_name##_indexfs.type = _type;                            \
    _##_name##_indexfs.name = #_name;                           \
    _##_name##_indexfs.address = _address;                      \
    _##_name##_indexfs.size = _size;                            \
    indexfs_create_static(&_##_name##_indexfs);                 \
    /* returns indexfs_t* type */                               \
    &_##_name##_indexfs;                                        \
  })

uint32_t indexfs_count(indexfs_t* fs);
bool indexfs_increment(indexfs_t* fs);
bool indexfs_clear(indexfs_t* fs);
bool indexfs_get_flag(indexfs_t* fs, uint8_t* flag);
bool indexfs_set_flag(indexfs_t* fs, const uint8_t flag);
