#pragma once

#define ARRAY_SIZE(a)      (sizeof(a) / sizeof((a)[0]))
#define FIELD_SIZEOF(t, f) (sizeof(((t*)0)->f))

#define BLK_MIN(a, b)       \
  ({                        \
    __typeof__(a) _a = (a); \
    __typeof__(b) _b = (b); \
    _a < _b ? _a : _b;      \
  })

#define BLK_MAX(a, b)       \
  ({                        \
    __typeof__(a) _a = (a); \
    __typeof__(b) _b = (b); \
    _a > _b ? _a : _b;      \
  })
