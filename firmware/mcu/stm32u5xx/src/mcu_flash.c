#include "mcu_flash.h"

#include "assert.h"
#include "attributes.h"
#include "stm32u5xx.h"

#include <stddef.h>
#include <stdint.h>
#include <string.h>

/**
 * @brief Start address of flash.
 */
#define MCU_FLASH_REGION_START 0x08000000

/**
 * @brief Total flash size.
 */
#define MCU_FLASH_REGION_SIZE 0x200000

/**
 * @brief Size of each flash bank.
 */
#define MCU_FLASH_BANK_SIZE (MCU_FLASH_REGION_SIZE >> 1u)

/**
 * @brief Number of flash banks.
 */
#define MCU_FLASH_NBR_BANKS (FLASH_BANK_SIZE / FLASH_PAGE_SIZE)

/**
 * @brief Quad-word write size.
 */
#define MCU_FLASH_WRITE_SIZE 0x10u

/**
 * @brief Number of bytes in a burst write (8x quad-word).
 */
#define MCU_FLASH_BURST_WRITE_SIZE 0x80

typedef struct {
  uint8_t bank;
  uint8_t page;
} mcu_flash_page_config_t;

/**
 * @brief Retrieves the secure status of the flash module.
 *
 * @return `true` if flash is secure, otherwise `false`.
 */
static bool _mcu_flash_is_secure(void);

/**
 * @brief Locks access to the flash registers.
 */
static void _mcu_flash_lock(void);

/**
 * @brief Unlocks access to the flash registers.
 */
static void _mcu_flash_unlock(void);

/**
 * @brief Retrieves the bank and page corresponding to a given flash address.
 *
 * @param[in]  address  The flash address to look-up.
 * @param[out] config   Flash page configuration to populate.
 *
 * @return #mcu_flash_status_t indicating if the configuration was found.
 */
static mcu_flash_status_t _mcu_flash_get_page_config(uint32_t* address,
                                                     mcu_flash_page_config_t* config);

/**
 * @brief Flushes data and instruction caches.
 */
static void _mcu_cache_flush(void);

/**
 * @brief Busy loop until the flash status flags are cleared.
 *
 * @param status Flags to wait for.
 */
static void _mcu_flash_wait_status(uint32_t status);

/**
 * @brief Clears any pending status errors.
 */
static void _mcu_flash_clear_errors(void);

/**
 * @brief Checks for a flash program/erase/option set operation failure.
 *
 * @return #mcu_flash_status_t indicating the appropriate error found.
 */
static mcu_flash_status_t _mcu_flash_check_status(void);

/**
 * @brief Writes a quad word to flash.
 *
 * @param address    The address in flash to program.
 * @param quad_word  The buffer of data to program.
 *
 * @note Caller should check the status of the flash write operation by calling
 * #_mcu_flash_check_status().
 */
static void _mcu_flash_write_quad_word(uint32_t* address, const uint32_t* quad_word);

/**
 * @brief Performs a burst write (writes 8 quad-words to flash.
 *
 * @details A burst is a faster write to flash provided there is at least 8
 * quad-words to write and the address is aligned by 8 quad-words.
 *
 * @param address  The address in flash to program.
 * @param burst    The buffer of data to program.
 *
 * @note The @p address must be aligned to 8 quad-words.
 *
 * @note Caller should check the status of the flash write operation by calling
 * #_mcu_flash_check_status().
 */
static void _mcu_flash_write_burst(uint32_t* address, const uint32_t* burst);

void mcu_flash_init(void) {
  /* Enable caching for fast memory access. */
  DCACHE1->CR |= DCACHE_CR_EN;
  ICACHE->CR |= ICACHE_CR_EN;
  FLASH->ACR |= FLASH_ACR_PRFTEN;

  /* Enable HSLV mode. */
  FLASH->OPTR |= FLASH_OPTR_IO_VDD_HSLV;

  /* Disable IWDG in Stop and Standby modes. */
  FLASH->OPTR &= ~(FLASH_OPTR_IWDG_STOP | FLASH_OPTR_IWDG_STDBY);

  /* Lock flash access to prevent parasitic writes. */
  _mcu_flash_lock();
}

RAMFUNC mcu_flash_status_t mcu_flash_write_word(uint32_t* address, void const* data, uint32_t len) {
  mcu_flash_page_config_t config = {0};
  mcu_flash_status_t status = _mcu_flash_get_page_config(address, &config);
  if (status != MCU_FLASH_STATUS_OK) {
    return status;
  }

  /* Discard the return value, just used for validation. */
  (void)config;

  _mcu_flash_unlock();
  _mcu_flash_wait_status(FLASH_NSSR_BSY);
  _mcu_flash_wait_status(FLASH_NSSR_WDW);
  _mcu_flash_clear_errors();

  /* Start programming. */
  if (_mcu_flash_is_secure()) {
    FLASH->SECCR |= FLASH_SECCR_PG;
  } else {
    FLASH->NSCR |= FLASH_NSCR_PG;
  }

  /* Writes must be aligned to the minimum write size (quad-word). */
  uintptr_t addr = (uintptr_t)address;
  uint8_t offset = (addr % MCU_FLASH_WRITE_SIZE);
  addr -= offset;

  const uint8_t* src = (const uint8_t*)data;
  uint8_t wr_size;

  while (len > 0) {
    if (offset > 0u) {
      /* Calculate number of bytes within the quad word to write. */
      wr_size = MCU_FLASH_WRITE_SIZE - offset;
    } else {
      if ((len >= MCU_FLASH_BURST_WRITE_SIZE) &&
          ((addr & (MCU_FLASH_BURST_WRITE_SIZE - 1)) == 0u)) {
        /* Perform burst programming can be performed. */
        wr_size = MCU_FLASH_BURST_WRITE_SIZE;
      } else if (MCU_FLASH_WRITE_SIZE > len) {
        wr_size = len;
      } else {
        wr_size = MCU_FLASH_WRITE_SIZE;
      }
    }

    switch (wr_size) {
      case MCU_FLASH_BURST_WRITE_SIZE:
        _mcu_flash_write_burst((uint32_t*)addr, (const uint32_t*)src);
        addr += MCU_FLASH_BURST_WRITE_SIZE;
        break;

      case MCU_FLASH_WRITE_SIZE:
        _mcu_flash_write_quad_word((uint32_t*)addr, (const uint32_t*)src);
        addr += MCU_FLASH_WRITE_SIZE;
        break;

      case 0u:
        /* Should never happen. */
        abort();
        break;

      default: {
        /* Word-aligned write within a quad word. */
        uint8_t quad_word[MCU_FLASH_WRITE_SIZE];
        memset(quad_word, 0xFFu, sizeof(quad_word));
        memcpy(quad_word + offset, src, wr_size);
        _mcu_flash_write_quad_word((uint32_t*)addr, (const uint32_t*)quad_word);
        addr += MCU_FLASH_WRITE_SIZE;
        break;
      }
    }

    status = _mcu_flash_check_status();
    if (status != MCU_FLASH_STATUS_OK) {
      break;
    }

    offset = 0u;
    src += wr_size;
    len -= wr_size;
  }

  /* End programming. */
  if (_mcu_flash_is_secure()) {
    FLASH->SECCR &= ~FLASH_SECCR_PG;
  } else {
    FLASH->NSCR &= ~FLASH_NSCR_PG;
  }
  _mcu_flash_lock();

  return status;
}

RAMFUNC mcu_flash_status_t mcu_flash_erase_page(uint32_t* address) {
  mcu_flash_page_config_t config = {0};
  mcu_flash_status_t status = _mcu_flash_get_page_config(address, &config);
  if (status != MCU_FLASH_STATUS_OK) {
    return status;
  }

  _mcu_flash_wait_status(FLASH_NSSR_BSY);
  _mcu_flash_unlock();
  _mcu_flash_clear_errors();

  /* Program bank and page to erase. */
  if (_mcu_flash_is_secure()) {
    FLASH->SECCR = (FLASH->SECCR & ~FLASH_SECCR_BKER_Msk) |
                   ((config.bank << FLASH_SECCR_BKER_Pos) & FLASH_SECCR_BKER_Msk);
    FLASH->SECCR = (FLASH->SECCR & ~FLASH_SECCR_PNB_Msk) |
                   ((config.page << FLASH_SECCR_PNB_Pos) & FLASH_SECCR_PNB_Msk);

    /* Erase the page. */
    FLASH->SECCR |= FLASH_SECCR_PER;
    FLASH->SECCR |= FLASH_SECCR_STRT;

    _mcu_flash_wait_status(FLASH_SECSR_BSY);
    FLASH->SECCR &= ~FLASH_SECCR_PER;
  } else {
    FLASH->NSCR = (FLASH->NSCR & ~FLASH_NSCR_BKER_Msk) |
                  ((config.bank << FLASH_NSCR_BKER_Pos) & FLASH_NSCR_BKER_Msk);
    FLASH->NSCR = (FLASH->NSCR & ~FLASH_NSCR_PNB_Msk) |
                  ((config.page << FLASH_NSCR_PNB_Pos) & FLASH_NSCR_PNB_Msk);

    /* Erase the page. */
    FLASH->NSCR |= FLASH_NSCR_PER;
    FLASH->NSCR |= FLASH_NSCR_STRT;

    _mcu_flash_wait_status(FLASH_NSSR_BSY);
    FLASH->NSCR &= ~FLASH_NSCR_PER;
  }

  status = _mcu_flash_check_status();

  /* Cache must be flushed after an erase to purge stale instructions. */
  _mcu_cache_flush();
  _mcu_flash_lock();

  return status;
}

static bool _mcu_flash_is_secure(void) {
  return false;
}

static void _mcu_flash_lock(void) {
  if (_mcu_flash_is_secure()) {
    FLASH->SECCR |= FLASH_SECCR_LOCK;
  } else {
    FLASH->NSCR |= FLASH_NSCR_LOCK;
  }
}

static void _mcu_flash_unlock(void) {
  if (_mcu_flash_is_secure()) {
    if ((FLASH->SECCR & FLASH_SECCR_LOCK) != 0u) {
      FLASH->SECKEYR = 0x45670123;
      FLASH->SECKEYR = 0xCDEF89AB;
    }
  } else {
    if ((FLASH->NSCR & FLASH_NSCR_LOCK) != 0u) {
      FLASH->NSKEYR = 0x45670123;
      FLASH->NSKEYR = 0xCDEF89AB;
    }
  }
}

static mcu_flash_status_t _mcu_flash_get_page_config(uint32_t* address,
                                                     mcu_flash_page_config_t* config) {
  ASSERT(config != NULL);

  uintptr_t addr = (uintptr_t)address;
  if ((addr < MCU_FLASH_REGION_START) ||
      (addr >= (MCU_FLASH_REGION_START + MCU_FLASH_REGION_SIZE))) {
    return MCU_FLASH_STATUS_INVALID_ADDR;
  }

  config->bank = ((addr - MCU_FLASH_REGION_START) / MCU_FLASH_BANK_SIZE);
  config->page = ((addr - MCU_FLASH_REGION_START) % MCU_FLASH_BANK_SIZE) / FLASH_PAGE_SIZE;
  return MCU_FLASH_STATUS_OK;
}

static void _mcu_cache_flush(void) {
  if ((ICACHE->CR & ICACHE_CR_EN) != 0u) {
    /* Flush instruction cache. */
    ICACHE->CR &= ~ICACHE_CR_EN;
    ICACHE->CR |= ICACHE_CR_CACHEINV;

    while ((ICACHE->SR & ICACHE_SR_BUSYF) != 0u) {
      /* Wait for instruction to finish. */
      ;
    }

    /* Re-enable the cache. */
    ICACHE->CR |= ICACHE_CR_EN;
  }

  if ((DCACHE1->CR & DCACHE_CR_EN) != 0u) {
    /* Flush the data cache. */
    DCACHE1->CR &= ~DCACHE_CR_EN;
    DCACHE1->CR |= DCACHE_CR_CACHEINV;

    while ((DCACHE1->SR & DCACHE_SR_BUSYF) != 0u) {
      /* Wait for instruction to finish. */
      ;
    }

    DCACHE1->CR |= DCACHE_CR_EN;
  }
}

static void _mcu_flash_wait_status(uint32_t status) {
  volatile uint32_t* regs = (_mcu_flash_is_secure() ? &(FLASH->SECSR) : &(FLASH->NSSR));
  while ((*regs & status) != 0u) {
    ;
  }
}

static void _mcu_flash_clear_errors(void) {
  if (_mcu_flash_is_secure()) {
    FLASH->SECSR |= (FLASH_SECSR_OPERR | FLASH_SECSR_PROGERR | FLASH_SECSR_WRPERR |
                     FLASH_SECSR_PGAERR | FLASH_SECSR_SIZERR | FLASH_SECSR_PGSERR);
  } else {
    FLASH->NSSR |= (FLASH_NSSR_OPERR | FLASH_NSSR_PROGERR | FLASH_NSSR_WRPERR | FLASH_NSSR_PGAERR |
                    FLASH_NSSR_SIZERR | FLASH_NSSR_PGSERR | FLASH_NSSR_OPTWERR);
  }
}

static mcu_flash_status_t _mcu_flash_check_status(void) {
  if (_mcu_flash_is_secure()) {
    if ((FLASH->SECSR & FLASH_SECSR_OPERR) != 0u) {
      return MCU_FLASH_STATUS_OPT_ERROR;
    }

    if ((FLASH->SECSR & FLASH_SECSR_PROGERR) != 0u) {
      return MCU_FLASH_STATUS_PROG_ERROR;
    }

    if ((FLASH->SECSR & FLASH_SECSR_WRPERR) != 0u) {
      return MCU_FLASH_STATUS_LOCKED;
    }

    if ((FLASH->SECSR & FLASH_SECSR_PGAERR) != 0u) {
      return MCU_FLASH_STATUS_INVALID_ADDR;
    }

    if ((FLASH->SECSR & FLASH_SECSR_SIZERR) != 0u) {
      return MCU_FLASH_STATUS_INVALID_LEN;
    }

    if ((FLASH->SECSR & FLASH_SECSR_PGSERR) != 0u) {
      return MCU_FLASH_STATUS_PROG_ERROR;
    }
  } else {
    if ((FLASH->NSSR & FLASH_NSSR_OPERR) != 0u) {
      return MCU_FLASH_STATUS_OPT_ERROR;
    }

    if ((FLASH->NSSR & FLASH_NSSR_PROGERR) != 0u) {
      return MCU_FLASH_STATUS_PROG_ERROR;
    }

    if ((FLASH->NSSR & FLASH_NSSR_WRPERR) != 0u) {
      return MCU_FLASH_STATUS_LOCKED;
    }

    if ((FLASH->NSSR & FLASH_NSSR_PGAERR) != 0u) {
      return MCU_FLASH_STATUS_INVALID_ADDR;
    }

    if ((FLASH->NSSR & FLASH_NSSR_SIZERR) != 0u) {
      return MCU_FLASH_STATUS_INVALID_LEN;
    }

    if ((FLASH->NSSR & FLASH_NSSR_PGSERR) != 0u) {
      return MCU_FLASH_STATUS_PROG_ERROR;
    }

    if ((FLASH->NSSR & FLASH_NSSR_OPTWERR) != 0u) {
      return MCU_FLASH_STATUS_OPT_ERROR;
    }
  }

  return MCU_FLASH_STATUS_OK;
}

static void _mcu_flash_write_quad_word(uint32_t* address, const uint32_t* quad_word) {
  ASSERT(quad_word != NULL);
  ASSERT(address != NULL);

  *(address + 0u) = *(quad_word + 0u);
  *(address + 1u) = *(quad_word + 1u);
  *(address + 2u) = *(quad_word + 2u);
  *(address + 3u) = *(quad_word + 3u);

  if (_mcu_flash_is_secure()) {
    _mcu_flash_wait_status(FLASH_SECSR_WDW);
    _mcu_flash_wait_status(FLASH_SECSR_BSY);

    if ((FLASH->SECSR & FLASH_SECSR_EOP) != 0u) {
      FLASH->SECSR &= ~FLASH_SECSR_EOP;
    }
  } else {
    _mcu_flash_wait_status(FLASH_NSSR_WDW);
    _mcu_flash_wait_status(FLASH_NSSR_BSY);

    if ((FLASH->NSSR & FLASH_NSSR_EOP) != 0u) {
      FLASH->NSSR &= ~FLASH_NSSR_EOP;
    }
  }
}

static void _mcu_flash_write_burst(uint32_t* address, const uint32_t* burst) {
  ASSERT(burst != NULL);
  ASSERT(address != NULL);

  /* Enable burst write. */
  if (_mcu_flash_is_secure()) {
    FLASH->SECCR |= FLASH_SECCR_BWR;
  } else {
    FLASH->NSCR |= FLASH_NSCR_BWR;
  }

  const uint32_t* next = burst;
  const uint32_t* end = next + (MCU_FLASH_BURST_WRITE_SIZE / sizeof(uint32_t));

  while (next < end) {
    *address = *next;
    next++;
    address++;
  }

  if (_mcu_flash_is_secure()) {
    _mcu_flash_wait_status(FLASH_SECSR_WDW);
    _mcu_flash_wait_status(FLASH_SECSR_BSY);

    if ((FLASH->SECSR & FLASH_SECSR_EOP) != 0u) {
      FLASH->SECSR &= ~FLASH_SECSR_EOP;
    }

    /* Clear burst write. */
    FLASH->SECCR &= ~FLASH_SECCR_BWR;
  } else {
    _mcu_flash_wait_status(FLASH_NSSR_WDW);
    _mcu_flash_wait_status(FLASH_NSSR_BSY);

    if ((FLASH->NSSR & FLASH_NSSR_EOP) != 0u) {
      FLASH->NSSR &= ~FLASH_NSSR_EOP;
    }

    /* Clear burst write. */
    FLASH->NSCR &= ~FLASH_NSCR_BWR;
  }
}
