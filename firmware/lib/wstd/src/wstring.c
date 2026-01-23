#include "wstring.h"

#include <stdint.h>

// Based on
// https://github.com/jedisct1/libsodium/blob/1647f0d53ae0e370378a9195477e3df0a792408f/src/libsodium/sodium/utils.c#L102-L130
//
// NOTE: leave this function in a separate file to prevent it being optimized away (with no LTO).
void memzero(void* const pnt, const size_t len) {
  if (!pnt) {
    return;
  }

  volatile unsigned char* volatile pnt_ = (volatile unsigned char* volatile)pnt;
  size_t i = (size_t)0U;

  while (i < len) {
    pnt_[i++] = 0U;
  }
}

// The below code is taken directly from
// https://github.com/chmike/cst_time_memcmp/blob/master/consttime_memcmp.c

/*
 * How hard do we have to try to prevent unwanted compiler optimisations?
 *
 * Try compiling with "#define USE_VOLATILE_TEMPORARY 0", and examine
 * the compiler output.  If the only conditional tests in the entire
 * function are to test whether len is zero, then all is well, but try
 * again with different optimisation flags to be sure.  If the compiler
 * emitted code with conditional tests that do anything other than
 * testing whether len is zero, then that's a problem, so try again with
 * "#define USE_VOLATILE_TEMPORARY 1".  If it's still bad, then you are
 * out of luck.
 */
#define USE_VOLATILE_TEMPORARY 1

int memcmp_s(const void* b1, const void* b2, size_t len) {
  const uint8_t *c1, *c2;
  uint16_t d, r, m;

#if USE_VOLATILE_TEMPORARY
  volatile uint16_t v;
#else
  uint16_t v;
#endif

  c1 = b1;
  c2 = b2;

  r = 0;
  while (len) {
    /*
     * Take the low 8 bits of r (in the range 0x00 to 0xff,
     * or 0 to 255);
     * As explained elsewhere, the low 8 bits of r will be zero
     * if and only if all bytes compared so far were identical;
     * Zero-extend to a 16-bit type (in the range 0x0000 to
     * 0x00ff);
     * Add 255, yielding a result in the range 255 to 510;
     * Save that in a volatile variable to prevent
     * the compiler from trying any shortcuts (the
     * use of a volatile variable depends on "#ifdef
     * USE_VOLATILE_TEMPORARY", and most compilers won't
     * need it);
     * Divide by 256 yielding a result of 1 if the original
     * value of r was non-zero, or 0 if r was zero;
     * Subtract 1, yielding 0 if r was non-zero, or -1 if r
     * was zero;
     * Convert to uint16_t, yielding 0x0000 if r was
     * non-zero, or 0xffff if r was zero;
     * Save in m.
     */
    v = ((uint16_t)(uint8_t)r) + 255;
    m = v / 256 - 1;

    /*
     * Get the values from *c1 and *c2 as uint8_t (each will
     * be in the range 0 to 255, or 0x00 to 0xff);
     * Convert them to signed int values (still in the
     * range 0 to 255);
     * Subtract them using signed arithmetic, yielding a
     * result in the range -255 to +255;
     * Convert to uint16_t, yielding a result in the range
     * 0xff01 to 0xffff (for what was previously -255 to
     * -1), or 0, or in the range 0x0001 to 0x00ff (for what
     * was previously +1 to +255).
     */
    d = (uint16_t)((int)*c1 - (int)*c2);

    /*
     * If the low 8 bits of r were previously 0, then m
     * is now 0xffff, so (d & m) is the same as d, so we
     * effectively copy d to r;
     * Otherwise, if r was previously non-zero, then m is
     * now 0, so (d & m) is zero, so leave r unchanged.
     * Note that the low 8 bits of d will be zero if and
     * only if d == 0, which happens when *c1 == *c2.
     * The low 8 bits of r are thus zero if and only if the
     * entirety of r is zero, which happens if and only if
     * all bytes compared so far were equal.  As soon as a
     * non-zero value is stored in r, it remains unchanged
     * for the remainder of the loop.
     */
    r |= (d & m);

    /*
     * Increment pointers, decrement length, and loop.
     */
    ++c1;
    ++c2;
    --len;
  }

  /*
   * At this point, r is an unsigned value, which will be 0 if the
   * final result should be zero, or in the range 0x0001 to 0x00ff
   * (1 to 255) if the final result should be positive, or in the
   * range 0xff01 to 0xffff (65281 to 65535) if the final result
   * should be negative.
   *
   * We want to convert the unsigned values in the range 0xff01
   * to 0xffff to signed values in the range -255 to -1, while
   * converting the other unsigned values to equivalent signed
   * values (0, or +1 to +255).
   *
   * On a machine with two's complement arithmetic, simply copying
   * the underlying bits (with sign extension if int is wider than
   * 16 bits) would do the job, so something like this might work:
   *
   *     return (int16_t)r;
   *
   * However, that invokes implementation-defined behaviour,
   * because values larger than 32767 can't fit in a signed 16-bit
   * integer without overflow.
   *
   * To avoid any implementation-defined behaviour, we go through
   * these contortions:
   *
   * a. Calculate ((uint32_t)r + 0x8000).  The cast to uint32_t
   *    is to prevent problems on platforms where int is narrower
   *    than 32 bits.  If int is a larger than 32-bits, then the
   *    usual arithmetic conversions cause this addition to be
   *    done in unsigned int arithmetic.  If int is 32 bits
   *    or narrower, then this addition is done in uint32_t
   *    arithmetic.  In either case, no overflow or wraparound
   *    occurs, and the result from this step has a value that
   *    will be one of 0x00008000 (32768), or in the range
   *    0x00008001 to 0x000080ff (32769 to 33023), or in the range
   *    0x00017f01 to 0x00017fff (98049 to 98303).
   *
   * b. Cast the result from (a) to uint16_t.  This effectively
   *    discards the high bits of the result, in a way that is
   *    well defined by the C language.  The result from this step
   *    will be of type uint16_t, and its value will be one of
   *    0x8000 (32768), or in the range 0x8001 to 0x80ff (32769 to
   *    33023), or in the range 0x7f01 to 0x7fff (32513 to
   *    32767).
   *
   * c. Cast the result from (b) to int32_t.  We use int32_t
   *    instead of int because we need a type that's strictly
   *    larger than 16 bits, and the C standard allows
   *    implementations where int is only 16 bits.  The result
   *    from this step will be of type int32_t, and its value will
   *    be one of 0x00008000 (32768), or in the range 0x00008001
   *    to 0x000080ff (32769 to 33023), or in the range 0x00007f01
   *    to 0x00007fff (32513 to 32767).
   *
   * d. Take the result from (c) and subtract 0x8000 (32768) using
   *    signed int32_t arithmetic.  The result from this step will
   *    be of type int32_t and the value will be one of
   *    0x00000000 (0), or in the range 0x00000001 to 0x000000ff
   *    (+1 to +255), or in the range 0xffffff01 to 0xffffffff
   *    (-255 to -1).
   *
   * e. Cast the result from (d) to int.  This does nothing
   *    interesting, except to make explicit what would have been
   *    implicit in the return statement.  The final result is an
   *    int in the range -255 to +255.
   *
   * Unfortunately, compilers don't seem to be good at figuring
   * out that most of this can be optimised away by careful choice
   * of register width and sign extension.
   *
   */
  return (/*e*/ int)(/*d*/
                     (/*c*/ int32_t)(/*b*/ uint16_t)(/*a*/ (uint32_t)r + 0x8000) - 0x8000);
}
