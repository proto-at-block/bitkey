/* config.h for libwally - embedded-friendly build configuration */

#ifndef CONFIG_H
#define CONFIG_H

/* Determine endianness.
 * Prefer compiler-provided macros so this works when cross-compiling.
 */
#if defined(__BYTE_ORDER__) && defined(__ORDER_LITTLE_ENDIAN__) && \
  (__BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__)
#define HAVE_LITTLE_ENDIAN 1
#elif defined(__BYTE_ORDER__) && defined(__ORDER_BIG_ENDIAN__) && \
  (__BYTE_ORDER__ == __ORDER_BIG_ENDIAN__)
#define HAVE_BIG_ENDIAN 1
#elif defined(__APPLE__)
/* Fallback for Apple targets that may not expose __BYTE_ORDER__ macros */
#include <machine/endian.h>
#if BYTE_ORDER == LITTLE_ENDIAN
#define HAVE_LITTLE_ENDIAN 1
#elif BYTE_ORDER == BIG_ENDIAN
#define HAVE_BIG_ENDIAN 1
#endif
#elif defined(_WIN32) || defined(__LITTLE_ENDIAN__) || defined(__ARMEL__)
/* Reasonable defaults for known little-endian targets */
#define HAVE_LITTLE_ENDIAN 1
#endif

/* Define to 1 if you have the <inttypes.h> header file. */
#define HAVE_INTTYPES_H 1

/* Define to 1 if you have the <memory.h> header file. */
#define HAVE_MEMORY_H 1

/* Define to 1 if you have the <stdint.h> header file. */
#define HAVE_STDINT_H 1

/* Define to 1 if you have the <stdlib.h> header file. */
#define HAVE_STDLIB_H 1

/* Define to 1 if you have the <strings.h> header file. */
#define HAVE_STRINGS_H 1

/* Define to 1 if you have the <string.h> header file. */
#define HAVE_STRING_H 1

/* Define to 1 if you have the <sys/stat.h> header file. */
#define HAVE_SYS_STAT_H 1

/* Define to 1 if you have the <sys/types.h> header file. */
#define HAVE_SYS_TYPES_H 1

/* Define to 1 if you have the <unistd.h> header file. */
#define HAVE_UNISTD_H 1

/* Define to 1 if your C compiler doesn't accept -c and -o together. */
/* #undef NO_MINUS_C_MINUS_O */

/* Define to the version of this package. */
#define PACKAGE_VERSION "0.8.8"

/* Define to 1 if you have the ANSI C header files. */
#define STDC_HEADERS 1

/* Version number of package */
#define VERSION "0.8.8"

/* Disable Elements support for non-Elements builds */
/* #define BUILD_ELEMENTS 1 */

/* Use secp256k1 with reduced precomputed tables for embedded */
#define USE_ECMULT_STATIC_PRECOMPUTATION 1

/* Disable external default callbacks for embedded */
#define USE_EXTERNAL_DEFAULT_CALLBACKS 0

/* Embedded-friendly settings */
#define BUILD_STANDARD_SECP 1

/* Define platform-specific behavior */
#define WALLY_ABI_VER_1       1
#define WALLY_ABI_NO_ELEMENTS 1

/* When WALLY_ABI_NO_ELEMENTS is set, ensure BUILD_ELEMENTS is not defined */
#ifdef WALLY_ABI_NO_ELEMENTS
#undef BUILD_ELEMENTS
#endif

/* For alignment checking */
#ifndef alignment_ok
#define alignment_ok(p, n) (((size_t)(p) & ((n)-1)) == 0)
#endif

/* CCAN memory clearing macro */
#ifndef CCAN_CLEAR_MEMORY
#define CCAN_CLEAR_MEMORY(ptr, size) memset((ptr), 0, (size))
#endif

#endif /* CONFIG_H */
