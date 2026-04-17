#include "mcu_flash.h"

#include "assert.h"
#include "attributes.h"
#include "mcu_opt.h"
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

/**
 * @brief Option byte unlock key 1.
 */
#define MCU_FLASH_OPT_KEY1 0x08192A3BU

/**
 * @brief Option byte unlock key 2.
 */
#define MCU_FLASH_OPT_KEY2 0x4C5D6E7FU

/**
 * @brief RDP Level 0.
 */
#define MCU_FLASH_RDP_LEVEL_0 0xAAu

/**
 * @brief RDP Level 0.5.
 */
#define MCU_FLASH_RDP_LEVEL_0_5 0x55u

/**
 * @brief RDP Level 1.
 */
#define MCU_FLASH_RDP_LEVEL_1 0xBBu

/**
 * @brief RDP Level 2.
 */
#define MCU_FLASH_RDP_LEVEL_2 0xCCu

typedef struct {
  uint8_t bank;
  uint8_t page;
} mcu_flash_page_config_t;

typedef struct {
  uint32_t optr;
  uint32_t nsbootadd0r;
  uint32_t nsbootadd1r;
  uint32_t secbootadd0r;
  uint32_t secwm1r1;
  uint32_t secwm1r2;
  uint32_t wrp1ar;
  uint32_t wrp1br;
  uint32_t secwm2r1;
  uint32_t secwm2r2;
  uint32_t wrp2ar;
  uint32_t wrp2br;
  uint32_t oem1keyr1;
  uint32_t oem1keyr2;
  uint32_t oem2keyr1;
  uint32_t oem2keyr2;
} mcu_flash_opt_expected_t;

static const mcu_flash_opt_expected_t _mcu_flash_opt_expected_dev = {
  .optr = 0x3DA978AAu,
  .nsbootadd0r = 0x0800007Fu,
  .nsbootadd1r = 0x0BF9007Fu,
  .secbootadd0r = 0x0C00007Cu,
  .secwm1r1 = 0xFFFFFF80u,
  .secwm1r2 = 0x7F807F80u,
  .wrp1ar = 0xFF80FFFFu,
  .wrp1br = 0xFF80FFFFu,
  .secwm2r1 = 0xFFFFFF80u,
  .secwm2r2 = 0x7F807F80u,
  .wrp2ar = 0xFF80FFFFu,
  .wrp2br = 0xFF80FFFFu,
  .oem1keyr1 = 0x00000000u,
  .oem1keyr2 = 0x00000000u,
  .oem2keyr1 = 0x00000000u,
  .oem2keyr2 = 0x00000000u,
};

static const mcu_flash_opt_expected_t _mcu_flash_opt_expected_prod = {
  .optr = 0x39A978CCu,
  .nsbootadd0r = 0x0800007Fu,
  .nsbootadd1r = 0x0BF9007Fu,
  .secbootadd0r = 0x0C00007Cu,
  .secwm1r1 = 0xFFFFFF80u,
  .secwm1r2 = 0x7F807F80u,
  .wrp1ar = 0x7F8FFF80u,
  .wrp1br = 0xFF80FFFFu,
  .secwm2r1 = 0xFFFFFF80u,
  .secwm2r2 = 0x7F807F80u,
  .wrp2ar = 0xFF80FFFFu,
  .wrp2br = 0xFF80FFFFu,
  .oem1keyr1 = 0x00000000u,
  .oem1keyr2 = 0x00000000u,
  .oem2keyr1 = 0x00000000u,
  .oem2keyr2 = 0x00000000u,
};

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
 * @brief Locks write access to the option bytes register.
 */
static void _mcu_flash_optr_lock(void);

/**
 * @brief Unlocks write access to the option bytes register.
 */
static void _mcu_flash_optr_unlock(void);

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

/**
 * @brief Writes the default option bytes out.
 */
static void _mcu_flash_opt_set_default(void);

/**
 * @brief Configures the write protection registers for the bootloader region.
 *
 * @param start_addr  Start address of the bootloader in flash.
 * @param end_addr    End address of the bootloader in flash.
 * @param lock        `true` if bootloader region should be write locked.
 */
static void _mcu_flash_configure_bootloader_wrp(uintptr_t start_addr, uintptr_t end_addr,
                                                bool lock);

void mcu_flash_init(void) {
  /* Enable caching for fast memory access. */
  DCACHE1->CR |= DCACHE_CR_EN;
  ICACHE->CR |= ICACHE_CR_EN;
  FLASH->ACR |= FLASH_ACR_PRFTEN;

  /* Load default option bytes. */
  _mcu_flash_opt_set_default();
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

NO_OPTIMIZE bool mcu_flash_opt_verify(mcu_flash_opt_t const* opt) {
  ASSERT(opt != NULL);

  mcu_flash_opt_expected_t const* expected = NULL;
  switch (opt->profile) {
    case MCU_FLASH_OPT_PROFILE_DEV:
      expected = &_mcu_flash_opt_expected_dev;
      break;
    case MCU_FLASH_OPT_PROFILE_PROD:
      expected = &_mcu_flash_opt_expected_prod;
      break;
    default:
      return false;
  }

  // Do not validate RDP level as part of the option byte profile since it is set through mfgtest
  const uint32_t optr_check_mask = (uint32_t)~FLASH_OPTR_RDP_Msk;

  if (((FLASH->OPTR & optr_check_mask) != (expected->optr & optr_check_mask)) ||
      (FLASH->NSBOOTADD0R != expected->nsbootadd0r) ||
      (FLASH->NSBOOTADD1R != expected->nsbootadd1r) || (FLASH->WRP1AR != expected->wrp1ar) ||
      (FLASH->WRP1BR != expected->wrp1br) || (FLASH->WRP2AR != expected->wrp2ar) ||
      (FLASH->WRP2BR != expected->wrp2br) || (FLASH->OEM1KEYR1 != expected->oem1keyr1) ||
      (FLASH->OEM1KEYR2 != expected->oem1keyr2) || (FLASH->OEM2KEYR1 != expected->oem2keyr1) ||
      (FLASH->OEM2KEYR2 != expected->oem2keyr2)) {
    return false;
  }

  /*
   * UXC runs with TrustZone disabled. In this non-secure firmware path, secure-only
   * option-byte registers are not active and read back as 0, so they are not verified.
   */
  ASSERT((FLASH->OPTR & FLASH_OPTR_TZEN) == 0u);

  return true;
}

NO_OPTIMIZE mcu_flash_status_t mcu_flash_opt_write(mcu_flash_opt_t const* opt, bool write_rdp) {
  ASSERT(opt != NULL);

  /* Map requested RDP level to the encoded value expected by OPTR (only when writing RDP). */
  uint32_t rdp_value = MCU_FLASH_RDP_LEVEL_0;
  if (write_rdp) {
    switch (opt->target_rdp) {
      case MCU_FLASH_RDP_0:
        rdp_value = MCU_FLASH_RDP_LEVEL_0;
        break;
      case MCU_FLASH_RDP_0_5:
        rdp_value = MCU_FLASH_RDP_LEVEL_0_5;
        break;
      case MCU_FLASH_RDP_1:
        rdp_value = MCU_FLASH_RDP_LEVEL_1;
        break;
      case MCU_FLASH_RDP_2:
        rdp_value = MCU_FLASH_RDP_LEVEL_2;
        break;
      default:
        return MCU_FLASH_STATUS_UNSUPPORTED;
    }
  }

  const uintptr_t flash_start = MCU_FLASH_REGION_START;
  const uintptr_t flash_end = MCU_FLASH_REGION_START + MCU_FLASH_REGION_SIZE;

  uintptr_t boot_addr = opt->bootloader_address;
  const bool have_boot_config = (boot_addr > 0) && ((opt->bootloader_size > 0u) ||
                                                    opt->bootloader_lock || opt->boot_address_lock);

  /* Validate boot address (when provided or required by other options). */
  if (have_boot_config) {
    if ((boot_addr < flash_start) || (boot_addr >= flash_end)) {
      return MCU_FLASH_STATUS_INVALID_ADDR;
    }

    const uint32_t boot_alignment_mask = (1u << FLASH_NSBOOTADD0R_NSBOOTADD0_Pos) - 1u;
    if ((boot_addr & boot_alignment_mask) != 0u) {
      return MCU_FLASH_STATUS_UNALIGNED;
    }
  }

  uintptr_t boot_end_addr = boot_addr;

  if (opt->bootloader_size > 0u) {
    if (boot_addr == 0u) {
      return MCU_FLASH_STATUS_INVALID_ADDR;
    }

    const uintptr_t size_minus_one = opt->bootloader_size - 1u;
    if ((boot_addr + size_minus_one) < boot_addr) {
      return MCU_FLASH_STATUS_INVALID_LEN;
    }

    boot_end_addr = boot_addr + size_minus_one;
    if (boot_end_addr >= flash_end) {
      return MCU_FLASH_STATUS_INVALID_ADDR;
    }
  } else if (opt->bootloader_lock) {
    /* Locking requires a non-zero size definition. */
    return MCU_FLASH_STATUS_INVALID_LEN;
  }

  /* 1. & 2. Ensure the flash is idle and unlock the main registers. */
  _mcu_flash_wait_status(FLASH_NSSR_WDW);
  _mcu_flash_wait_status(FLASH_NSSR_BSY);
  _mcu_flash_unlock();

  /* 3. Unlock option bytes. */
  _mcu_flash_optr_unlock();

  /* 4. Clear any stale error flags. */
  _mcu_flash_clear_errors();

  /* 5. Update OPTR with the requested configuration. */
  uint32_t optr = FLASH->OPTR;

  if (write_rdp) {
    optr &= ~FLASH_OPTR_RDP;
    optr |= ((rdp_value << FLASH_OPTR_RDP_Pos) & FLASH_OPTR_RDP_Msk);
  }

  /* Enable HSLV mode. */
  optr |= FLASH_OPTR_IO_VDD_HSLV;

  /* Disable IWDG in Stop and Standby modes. */
  optr &= ~(FLASH_OPTR_IWDG_STOP | FLASH_OPTR_IWDG_STDBY);

  /* SRAM erased on reset if unset. */
  optr &= ~(FLASH_OPTR_SRAM_RST | FLASH_OPTR_SRAM2_RST);

  /* If boot address is locked, then we disable BOOT0 PIN control for boot selection. */
  if (opt->boot_address_lock) {
    optr &= ~FLASH_OPTR_nSWBOOT0;
  } else {
    optr |= FLASH_OPTR_nSWBOOT0;
  }

  /* Use NSBOOTADDR0R as boot address. */
  optr |= FLASH_OPTR_nBOOT0;

  /* Ensure error correction is on for the backup RAM (1 = Disabled). */
  optr &= ~FLASH_OPTR_BKPRAM_ECC;

  FLASH->OPTR = optr;

  /* 6. Configure boot address and locking if requested. */
  if (boot_addr != 0u) {
    FLASH->NSBOOTADD0R = (uint32_t)(boot_addr & FLASH_NSBOOTADD0R_NSBOOTADD0);
  }

  _mcu_flash_configure_bootloader_wrp(boot_addr, boot_end_addr, opt->bootloader_lock);

  /* 7. Start option byte programming. */
  FLASH->NSCR |= FLASH_NSCR_OPTSTRT;

  /* 8. Wait for the operation to complete. */
  _mcu_flash_wait_status(FLASH_NSSR_WDW);
  _mcu_flash_wait_status(FLASH_NSSR_BSY);

  if ((FLASH->NSSR & FLASH_NSSR_EOP) != 0u) {
    FLASH->NSSR &= ~FLASH_NSSR_EOP;
  }

  /* 9. Check for any programming errors. */
  const mcu_flash_status_t status = _mcu_flash_check_status();

  if (status == MCU_FLASH_STATUS_OK) {
    /* 10. Trigger option byte reload. */
    FLASH->NSCR |= FLASH_NSCR_OBL_LAUNCH;
  }

  /* 11. Lock option byte access and 12. lock flash registers. */
  _mcu_flash_optr_lock();
  _mcu_flash_lock();

  return status;
}

mcu_flash_status_t mcu_flash_opt_get_rdp(mcu_flash_opt_t const* opt, mcu_flash_rdp_t* rdp) {
  if ((opt == NULL) || (rdp == NULL)) {
    return MCU_FLASH_STATUS_INVALID_ADDR;
  }

  switch (opt->target_rdp) {
    case MCU_FLASH_RDP_0:
      *rdp = MCU_FLASH_RDP_0;
      break;
    case MCU_FLASH_RDP_0_5:
      *rdp = MCU_FLASH_RDP_0_5;
      break;
    case MCU_FLASH_RDP_1:
      *rdp = MCU_FLASH_RDP_1;
      break;
    case MCU_FLASH_RDP_2:
      *rdp = MCU_FLASH_RDP_2;
      break;
    default:
      *rdp = MCU_FLASH_RDP_UNKNOWN;
      return MCU_FLASH_STATUS_UNSUPPORTED;
  }

  return MCU_FLASH_STATUS_OK;
}

mcu_flash_status_t mcu_flash_opt_read(mcu_flash_opt_info_t* opt_info) {
  if (opt_info == NULL) {
    return MCU_FLASH_STATUS_INVALID_ADDR;
  }

  const uint32_t rdp = (FLASH->OPTR & FLASH_OPTR_RDP_Msk) >> FLASH_OPTR_RDP_Pos;

  switch (rdp) {
    case MCU_FLASH_RDP_LEVEL_0:
      opt_info->current_rdp = MCU_FLASH_RDP_0;
      break;
    case MCU_FLASH_RDP_LEVEL_0_5:
      opt_info->current_rdp = MCU_FLASH_RDP_0_5;
      break;
    case MCU_FLASH_RDP_LEVEL_1:
      opt_info->current_rdp = MCU_FLASH_RDP_1;
      break;
    case MCU_FLASH_RDP_LEVEL_2:
      opt_info->current_rdp = MCU_FLASH_RDP_2;
      break;
    default:
      opt_info->current_rdp = MCU_FLASH_RDP_UNKNOWN;
      return MCU_FLASH_STATUS_UNSUPPORTED;
  }

  return MCU_FLASH_STATUS_OK;
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

static void _mcu_flash_optr_lock(void) {
  if ((FLASH->NSCR & FLASH_NSCR_OPTLOCK) == 0u) {
    FLASH->NSCR |= FLASH_NSCR_OPTLOCK;
  }
}

static void _mcu_flash_optr_unlock(void) {
  if ((FLASH->NSCR & FLASH_NSCR_OPTLOCK) != 0u) {
    FLASH->OPTKEYR = MCU_FLASH_OPT_KEY1;
    FLASH->OPTKEYR = MCU_FLASH_OPT_KEY2;
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

NO_OPTIMIZE static void _mcu_flash_opt_set_default(void) {
  bool modified = false;

  _mcu_flash_wait_status(FLASH_NSSR_BSY);
  _mcu_flash_unlock();
  _mcu_flash_optr_unlock();

  /* Enable HSLV mode if not already enabled. */
  if ((FLASH->OPTR & FLASH_OPTR_IO_VDD_HSLV) != FLASH_OPTR_IO_VDD_HSLV) {
    FLASH->OPTR |= FLASH_OPTR_IO_VDD_HSLV;
    modified = true;
  }

  /* Disable IWDG in Stop and Standby modes if not already disabled. */
  const uint32_t iwdg_msk = FLASH_OPTR_IWDG_STOP | FLASH_OPTR_IWDG_STDBY;
  if ((FLASH->OPTR & iwdg_msk) != 0u) {
    FLASH->OPTR &= ~iwdg_msk;
    modified = true;
  }

  /* Only perform option byte programming if bits were modified. */
  if (modified) {
    /* Start option byte programming. */
    FLASH->NSCR |= FLASH_NSCR_OPTSTRT;

    /* Wait for completion. */
    _mcu_flash_wait_status(FLASH_NSSR_BSY);

    /* Clear any errors (un-conditional). */
    _mcu_flash_clear_errors();

    /* Trigger option byte re-loading. */
    FLASH->NSCR |= FLASH_NSCR_OBL_LAUNCH;
  }

  /* Lock flash access to prevent parasitic writes. */
  _mcu_flash_optr_lock();
  _mcu_flash_lock();
}

static void _mcu_flash_configure_bootloader_wrp(uintptr_t start_addr, uintptr_t end_addr,
                                                bool lock) {
  /* Note: WRP1{A,B} applies to Bank 1 and WRP2{A,B} applies to Bank 2. */
  if (!lock) {
    FLASH->WRP1AR = FLASH_WRP1AR_WRP1A_PSTRT_Msk | FLASH_WRP1AR_UNLOCK;
    FLASH->WRP1BR = FLASH_WRP1BR_WRP1B_PSTRT_Msk | FLASH_WRP1BR_UNLOCK;
    FLASH->WRP2AR = FLASH_WRP2AR_WRP2A_PSTRT_Msk | FLASH_WRP2AR_UNLOCK;
    FLASH->WRP2BR = FLASH_WRP2BR_WRP2B_PSTRT_Msk | FLASH_WRP2BR_UNLOCK;
    return;
  }

  const uint32_t pages_per_bank = (uint32_t)(MCU_FLASH_BANK_SIZE / FLASH_PAGE_SIZE);
  const uint32_t start_page_idx =
    (uint32_t)((start_addr - MCU_FLASH_REGION_START) / FLASH_PAGE_SIZE);
  const uint32_t end_page_idx = (uint32_t)((end_addr - MCU_FLASH_REGION_START) / FLASH_PAGE_SIZE);

  /* Only Bank 1 is supported for the bootlaoder. */
  ASSERT(start_page_idx < pages_per_bank);
  ASSERT(end_page_idx < pages_per_bank);

  const uint32_t rel_start = start_page_idx;
  const uint32_t rel_end = end_page_idx;

  FLASH->WRP1AR = ((rel_end << FLASH_WRP1AR_WRP1A_PEND_Pos) & FLASH_WRP1AR_WRP1A_PEND_Msk) |
                  ((rel_start << FLASH_WRP1AR_WRP1A_PSTRT_Pos) & FLASH_WRP1AR_WRP1A_PSTRT_Msk);
  FLASH->WRP1BR = FLASH_WRP1BR_WRP1B_PSTRT_Msk | FLASH_WRP1BR_UNLOCK;

  FLASH->WRP2AR = FLASH_WRP2AR_WRP2A_PSTRT_Msk | FLASH_WRP2AR_UNLOCK;
  FLASH->WRP2BR = FLASH_WRP2BR_WRP2B_PSTRT_Msk | FLASH_WRP2BR_UNLOCK;
}
