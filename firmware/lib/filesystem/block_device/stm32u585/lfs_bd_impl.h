/**
 * @file
 *
 * @{
 */

#pragma once

#include <stdint.h>

/**
 * @brief Wear leveling cycles.
 *
 * @details Number of erase cycles before LFS evicts metadata logs and moves
 * the metadata to another block.
 *
 * @note Suggested values are in the range 100-1000, with large values having
 * better performance at the cost of less consistent wear distribution.
 *
 * @note The STM32U585 is performant up to 100K flash page erase cycles, and
 * 10K mass erase cycles.
 */
#define LFS_BD_BLOCK_CYCLES (500u)

/**
 * @brief Empty block lookahead buffer size.
 *
 * @details Each byte in LFS is used to represent 8 blocks (8-bits ->
 * 8 blocks). When looking for a block to allocate, LFS uses the lookahead
 * size to optimize performance by storing a buffer indicating which
 * blocks have not been allocated yet. This value can be at most
 * `block_count / 8`.
 *
 * @note Must be a multiple of 8.
 */
#define LFS_BD_LOOKAHEAD_SIZE (128u)

_Static_assert((LFS_BD_LOOKAHEAD_SIZE % 8u) == 0u, "Lookahead size must be a multiple of 8");

/**
 * @brief Flash write is quad-word aligned.
 */
#define LFS_BD_MIN_WRITE_SIZE (sizeof(uint64_t) * 2u)

/** @} */
