#pragma once

#include "attributes.h"

#include <assert.h>
#include <stdint.h>
#include <stdlib.h>

/**
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 *    IMPORTANT: All callers of the SECURE_* macros MUST be marked as NO_OPTIMIZE.
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 */

// Significance: https://en.wikipedia.org/wiki/42_(number)
#define MAGIC_COUNTER_STEP (42U)

/**
 * secutils_detect_glitch_cb_t - Function pointer for a glitch detection handler.
 */
typedef void (*secutils_detect_glitch_cb_t)(void);

/**
 * secutils_secure_random_cb_t - Function pointer for a secure random number generator.
 *
 * Return: A securely random 16 bit unsigned integer.
 */
typedef uint16_t (*secutils_secure_random_cb_t)(void);

/**
 * secutils_cpu_freq_cb_t - Function pointer for getting the CPU frequency in hertz.
 *
 * Return: CPU clock frequency in hertz
 */
typedef uint32_t (*secutils_cpu_freq_cb_t)(void);

typedef struct {
  secutils_detect_glitch_cb_t detect_glitch;
  secutils_secure_random_cb_t secure_random;
  secutils_cpu_freq_cb_t cpu_freq;
} secutils_api_t;

/**
 * secure_bool_t - A secure boolean to use as an alternative to the default boolean type.
 *
 * The hamming distance is 19, and the values are not exact inverses of each other.
 * That is important because a single invert operation could change the meaning from one
 * to the other. Equal bits are spread over all bytes of the type.
 *
 *     SECURE_FALSE  0xacefaecf  1010 1100 1110 1111 1010 1110 1100 1111
 *     SECURE_TRUE   0xca38c3e8  1100 1010 0011 1000 1100 0011 1110 1000
 *
 * See https://h2lab.org/blogposts/dev_c_fia/
 *
 * IMPORTANT NOTE: When using `secure_bool_t` for secure comparisons, it is important that we always
 * fail to a safe default and the default/unspecified conditions always direct us to the safe
 * behavior. For instance, this is __correct__ behavior since we only perform `unsafe_op()` if
 * `condition == SECURE_TRUE`:
 *
 * -----------------------------------------------------------------------------------------------------
 * # Good
 * SECURE_DO_FAILOUT(condition == SECURE_TRUE, {
 *   unsafe_op();
 * });
 *
 * safe_op();
 * -----------------------------------------------------------------------------------------------------
 * # Good
 * SECURE_DO_FAILIN(condition != SECURE_TRUE, {
 *   safe_op();
 *   return;
 * })
 *
 * unsafe_op();
 * -----------------------------------------------------------------------------------------------------
 *
 * An incorrect usage would be if any default/unspecified conditions leads us unsafe behavior.
 *
 * -----------------------------------------------------------------------------------------------------
 * # Bad: if `condition` is any value that is not exactly `SECURE_FALSE`, we allow the unsafe
 * behavior.
 * SECURE_DO_FAILIN(condition === SECURE_FALSE, {
 *   safe_op();
 *   return;
 * })
 *
 * unsafe_op();
 * -----------------------------------------------------------------------------------------------------
 * # Bad: Any value for `condition` that is not `SECURE_FALSE` would allow unsafe behavior.
 * SECURE_DO_FAILOUT(condition !== SECURE_FALSE), {
 *   unsafe_op();
 * })
 *
 * safe_op();
 * -----------------------------------------------------------------------------------------------------
 */

typedef enum {
  SECURE_FALSE = 0xacefaecf,
  SECURE_TRUE = 0xca38c3e8,
} secure_bool_t;

// _FIXED_READ() reads from volatile memory from a fixed address. This helps to protect
// against compiler optimizations. It's also useful to audit the SECURE_IF macros with usage of
// a decompiler to ensure that the compiler is not optimizing away the checks (just look for
// secutils_fixed_true xrefs).
extern volatile bool* secutils_fixed_true;
#define _FIXED_READ() ((*((volatile bool*)secutils_fixed_true)))

/**
 * SECURE_IF_FAILIN() - If statement macro used to mitigate against fault injection.
 * This macro should be used so that, if a glitch happens, the conditional should be
 * entered, i.e. "fail in".
 *
 * @condition: The condition to check. Best for boolean checks to be performed against secure_bool_t
 *
 * Note: `condition` must use a volatile variable, possibly a pointer. Check the dissassembly to be
 * sure. Note: A lighter-weight alternative to the SECURE_DO_FAILIN macro. This macro only performs
 * the checks multiple times.
 *
 * Context:
 * https://research.nccgroup.com/2021/07/08/software-based-fault-injection-countermeasures-part-2-3/
 *          See section  CM-1-C.
 */
#define SECURE_IF_FAILIN(condition)                                           \
  if (((_FIXED_READ()) && (condition)) || ((_FIXED_READ()) && (condition)) || \
      ((_FIXED_READ()) && (condition)))

/**
 * SECURE_IF_FAILOUT() - If statement macro used to mitigate against fault injection.
 * This macro should be used so that, if a glitch happens, the conditional should NOT be
 * entered, i.e. "fail out".
 *
 * @condition: The condition to check. Best for boolean checks to be performed against secure_bool_t
 *
 * Note: `condition` must use a volatile variable, possibly a pointer. Check the dissassembly to be
 * sure. Note: A lighter-weight alternative to the SECURE_DO_FAILOUT macro. This macro only performs
 * the checks multiple times.
 *
 * Context:
 * https://research.nccgroup.com/2021/07/08/software-based-fault-injection-countermeasures-part-2-3/
 *          See section  CM-1-C.
 */
#define SECURE_IF_FAILOUT(condition)                                          \
  if (((_FIXED_READ()) && (condition)) && ((_FIXED_READ()) && (condition)) && \
      ((_FIXED_READ()) && (condition)))

/**
 * SECURE_ASSERT() - Assert macro used to mitigate against fault injection.
 * @condition: The condition to assert on. `condition` MUST use a volatile variable.
 */
#define SECURE_ASSERT(condition)                                                      \
  do {                                                                                \
    volatile unsigned int cfi_ctr = 0;                                                \
    secure_glitch_random_delay();                                                     \
    ASSERT(condition);                                                                \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                    \
    secure_glitch_random_delay();                                                     \
    ASSERT(condition);                                                                \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                    \
    secure_glitch_random_delay();                                                     \
    ASSERT(condition);                                                                \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                    \
    SECURE_IF_FAILIN(cfi_ctr != (3 * MAGIC_COUNTER_STEP)) { secure_glitch_detect(); } \
  } while (0)

/**
 * SECURE_DO_ONCE() - Performs an operation securely by inserting a random delay before the
 * operation to make it difficult to locate timing to glitch the operation. Only performs the
 * operation once.
 *
 * @...: The action to perform. Similar to lambda/anonymous function.
 */
#define SECURE_DO_ONCE(...)                                                     \
  do {                                                                          \
    volatile unsigned int cfi_ctr = 0;                                          \
    secure_glitch_random_delay();                                               \
    { __VA_ARGS__; }                                                            \
    cfi_ctr += MAGIC_COUNTER_STEP;                                              \
    SECURE_IF_FAILIN(cfi_ctr != MAGIC_COUNTER_STEP) { secure_glitch_detect(); } \
  } while (0)

/**
 * SECURE_DO() - Performs an operation securely by inserting a random delay before the
 * operation to make it difficult to locate timing to glitch the operation. This supports idempotent
 * operations and will execute the operation 3 times.
 *
 * @...: The action to perform. Similar to lambda/anonymous function.
 */
#define SECURE_DO(...)                                                                \
  do {                                                                                \
    volatile unsigned int cfi_ctr = 0;                                                \
    secure_glitch_random_delay();                                                     \
    { __VA_ARGS__; }                                                                  \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                    \
    secure_glitch_random_delay();                                                     \
    { __VA_ARGS__; }                                                                  \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                    \
    secure_glitch_random_delay();                                                     \
    { __VA_ARGS__; }                                                                  \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                    \
    SECURE_IF_FAILIN(cfi_ctr != (3 * MAGIC_COUNTER_STEP)) { secure_glitch_detect(); } \
  } while (0)

/**
 * SECURE_DO_FAILOUT() - Performs an operation if the condition evaluates to true.
 *                       May call the glitch detection handler if a glitch is detected. This is
 * considered "failing-out" as we don't perform the requested operation if any of the checks fail.
 *
 * @condition: The condition to check. Best for boolean checks to be performed against secure_bool_t
 * @...: The action to perform on successful condition. Similar to lambda/anonymous function.
 *
 * Note: A more robust alternative to the SECURE_IF_FAILOUT macro.
 *       This macro will perform control flow checking, and insertion of random delays.
 */
#define SECURE_DO_FAILOUT(condition, ...)                                            \
  do {                                                                               \
    volatile unsigned int cfi_ctr = 0;                                               \
    volatile bool c1 = (condition);                                                  \
    volatile secure_bool_t done = SECURE_FALSE;                                      \
    secure_glitch_random_delay();                                                    \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                   \
    volatile bool c2 = (condition);                                                  \
    secure_glitch_random_delay();                                                    \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                   \
    volatile bool c3 = (condition);                                                  \
    secure_glitch_random_delay();                                                    \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                   \
    if (cfi_ctr == (3 * MAGIC_COUNTER_STEP)) {                                       \
      done = SECURE_TRUE;                                                            \
    }                                                                                \
    SECURE_IF_FAILIN((done != SECURE_TRUE)) { secure_glitch_detect(); }              \
    SECURE_IF_FAILIN((c1 != c2 || c1 != c3 || c2 != c3)) { secure_glitch_detect(); } \
    SECURE_IF_FAILOUT((c1 && c2 && c3)) {                                            \
      { __VA_ARGS__; }                                                               \
    }                                                                                \
  } while (0)

/**
 * SECURE_DO_FAILIN() - Performs an operation if the condition evaluates to true.
 *                      May call the glitch detection handler if a glitch is detected. This is
 * considered "failing-in" as we will perform the requested operation if any of the checks pass.
 *
 * @condition: The condition to check. Best for boolean checks to be performed against secure_bool_t
 * @...: The action to perform on successful condition. Similar to lambda/anonymous function.
 *
 * Note: A more robust alternative to the SECURE_IF_FAILIN macro.
 *       This macro will perform control flow checking, and insertion of random delays.
 */
#define SECURE_DO_FAILIN(condition, ...)                                             \
  do {                                                                               \
    volatile unsigned int cfi_ctr = 0;                                               \
    volatile bool c1 = (condition);                                                  \
    volatile secure_bool_t done = SECURE_FALSE;                                      \
    secure_glitch_random_delay();                                                    \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                   \
    volatile bool c2 = (condition);                                                  \
    secure_glitch_random_delay();                                                    \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                   \
    volatile bool c3 = (condition);                                                  \
    secure_glitch_random_delay();                                                    \
    cfi_ctr += MAGIC_COUNTER_STEP;                                                   \
    if (cfi_ctr == (3 * MAGIC_COUNTER_STEP)) {                                       \
      done = SECURE_TRUE;                                                            \
    }                                                                                \
    SECURE_IF_FAILIN((done != SECURE_TRUE)) { secure_glitch_detect(); }              \
    SECURE_IF_FAILIN((c1 != c2 || c1 != c3 || c2 != c3)) { secure_glitch_detect(); } \
    SECURE_IF_FAILIN((c1 || c2 || c3)) {                                             \
      { __VA_ARGS__; }                                                               \
    }                                                                                \
  } while (0)

/**
 * secutils_init() - Initialize secutils state and the secutils APIs.
 *
 * @api: The API to inject for the secutils module
 */
void secutils_init(secutils_api_t api);

/**
 * secure_glitch_detect() - Handler for glitch detection, increments counters, fires interrupts,
 * etc.
 */
void secure_glitch_detect(void);

/**
 * secure_glitch_get_count() - Gets the count of glitches that have been recorded.
 *
 * Return: The number of glitches that have been recorded
 */
unsigned int secure_glitch_get_count(void);

/**
 * secure_glitch_random_delay() - Performs a blocking random delay to make locating timings more
 * difficult.
 */
NO_OPTIMIZE void secure_glitch_random_delay(void);

/**
 * memzero() - Zeroize memory
 *
 * @pnt: Pointer to the starting address of the region to zeroize
 * @len: The length in bytes to zeroize
 */
void memzero(void* const pnt, const size_t len);

/**
 * memcmp_s() - Compare two regions of memory in constant time
 *
 * @b1: Pointer to the first region of memory
 * @b2: Pointer to the second region of memory
 * @len: The length in bytes to compare
 *
 * Implementation taken directly from https://github.com/chmike/cst_time_memcmp
 */
int memcmp_s(const void* b1, const void* b2, size_t len);
