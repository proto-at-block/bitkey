/**
 * @file lv_conf_test.h
 * Test configuration that includes lv_conf.h and enables lodepng for snapshot tests
 */

#ifndef LV_CONF_TEST_H
#define LV_CONF_TEST_H

/* Include the base configuration */
#include "lv_conf.h"

/* Increase memory pool for tests - lodepng needs ~868KB per decoded PNG image
 * (466x466x4 RGBA), and snapshot comparison loads golden images. With the
 * current snapshot set and potential memory fragmentation, we need
 * significantly more than 8MB. Note: Each comparison temporarily holds both
 * screen capture AND golden image. */
#undef LV_MEM_SIZE
#define LV_MEM_SIZE (64 * 1024 * 1024U) /* 64MB for tests */

/* Enable lodepng for PNG file I/O in tests */
#undef LV_USE_LODEPNG
#define LV_USE_LODEPNG 1

/* Enable snapshot support */
#undef LV_USE_SNAPSHOT
#define LV_USE_SNAPSHOT 1

/* Use C standard library for file operations in tests */
#undef LV_USE_FS_STDIO
#define LV_USE_FS_STDIO 1

#if LV_USE_FS_STDIO
#undef LV_FS_STDIO_LETTER
#define LV_FS_STDIO_LETTER '/'

#undef LV_FS_STDIO_PATH
#define LV_FS_STDIO_PATH ""

#undef LV_FS_STDIO_CACHE_SIZE
#define LV_FS_STDIO_CACHE_SIZE 0
#endif

#endif /* LV_CONF_TEST_H */
