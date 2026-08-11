#pragma once

// Host (POSIX) stub for MCU-specific definitions.
#ifndef MCU_FLASH_WRITE_ALIGNMENT
#define MCU_FLASH_WRITE_ALIGNMENT 16u
#endif

// Flash page size (matches STM32U5 8KB pages)
#ifndef FLASH_PAGE_SIZE
#define FLASH_PAGE_SIZE 0x2000U
#endif
