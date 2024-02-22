#include "mcu_flash.h"

#include "arithmetic.h"
#include "efr32mg24_msc.h"
#include "printf.h"

#define FLASH_PAGE_MASK (~(FLASH_PAGE_SIZE - 1U))

#if defined(_MSC_STATUS_REGLOCK_MASK)
#define MSC_IS_LOCKED() ((MSC->STATUS & _MSC_STATUS_REGLOCK_MASK) != 0U)
#else
#define MSC_IS_LOCKED() ((MSC->LOCK & _MSC_LOCK_MASK) != 0U)
#endif

/**
 *    Timeout used while waiting for Flash to become ready after a write.
 *    This number indicates the number of iterations to perform before
 *    issuing a timeout.
 *    Timeout is set very large (in the order of 100x longer than
 *    necessary). This is to avoid any corner case.
 */
#define MSC_PROGRAM_TIMEOUT 10000000UL

RAMFUNC static mcu_flash_status_t write_burst(uint32_t address, const uint32_t* data, uint32_t len);
RAMFUNC static mcu_flash_status_t msc_status_wait(uint32_t mask, uint32_t value);

void mcu_flash_init(void) {
#if defined(_CMU_CLKEN1_MASK)
  CMU->CLKEN1_SET = CMU_CLKEN1_MSC;
#endif

  // Unlock MSC
  MSC->LOCK = MSC_LOCK_LOCKKEY_UNLOCK;
  // Disable flash write
  MSC->WRITECTRL_CLR = MSC_WRITECTRL_WREN;
}

RAMFUNC mcu_flash_status_t mcu_flash_write_word(uint32_t* address, void const* data, uint32_t len) {
  /* Check alignment (must be aligned to words) */
  if (((uint32_t)address & 0x3U) != 0) {
    return MCU_FLASH_STATUS_UNALIGNED;
  }

  /* Check number of bytes, must be divisible by four */
  if ((len & 0x3U) != 0) {
    return MCU_FLASH_STATUS_INVALID_LEN;
  }

#if defined(_CMU_CLKEN1_MASK)
  CMU->CLKEN1_SET = CMU_CLKEN1_MSC;
#endif
  const bool was_locked = MSC_IS_LOCKED();
  MSC->LOCK = MSC_LOCK_LOCKKEY_UNLOCK;

  // Enable flash write
  MSC->WRITECTRL_SET = MSC_WRITECTRL_WREN;

  uint32_t addr = (uint32_t)address;
  const uint8_t* pData = (uint8_t*)data;

  uint32_t burstLen;
  mcu_flash_status_t status = MCU_FLASH_STATUS_OK;
  while (len) {
    // Max burst length is up to next flash page boundary
    burstLen = BLK_MIN(len, ((addr + FLASH_PAGE_SIZE) & FLASH_PAGE_MASK) - addr);

    if ((status = write_burst(addr, (const uint32_t*)pData, burstLen)) != MCU_FLASH_STATUS_OK) {
      break;
    }

    addr += burstLen;
    pData += burstLen;
    len -= burstLen;
  }

  /* Disable flash write */
  MSC->WRITECTRL_CLR = MSC_WRITECTRL_WREN;

  if (was_locked) {
    MSC->LOCK = MSC_LOCK_LOCKKEY_LOCK;
  }

  return status;
}

RAMFUNC mcu_flash_status_t mcu_flash_erase_page(uint32_t* address) {
  /* Address must be aligned to page boundary */
  // printf("erase address = %lu, FLASH_PAGE_SIZE = %lu\n", address, FLASH_PAGE_SIZE);
  if ((((uint32_t)address) & (FLASH_PAGE_SIZE - 1U)) != 0) {
    return MCU_FLASH_STATUS_UNALIGNED;
  }

#if defined(_CMU_CLKEN1_MASK)
  CMU->CLKEN1_SET = CMU_CLKEN1_MSC;
#endif
  const bool was_locked = MSC_IS_LOCKED();
  MSC->LOCK = MSC_LOCK_LOCKKEY_UNLOCK;

  MSC->WRITECTRL_SET = MSC_WRITECTRL_WREN;
  MSC->ADDRB = (uint32_t)address;
  MSC->WRITECMD = MSC_WRITECMD_ERASEPAGE;

  mcu_flash_status_t status = msc_status_wait((MSC_STATUS_BUSY | MSC_STATUS_PENDING), 0);

  if (status == MCU_FLASH_STATUS_OK) {
    /* We need to check twice "to be sure" (or so says the silabs driver) */
    status = msc_status_wait((MSC_STATUS_BUSY | MSC_STATUS_PENDING), 0);
  }

  MSC->WRITECTRL_CLR = MSC_WRITECTRL_WREN;

  if (was_locked) {
    MSC->LOCK = MSC_LOCK_LOCKKEY_LOCK;
  }

  return status;
}

RAMFUNC static mcu_flash_status_t write_burst(uint32_t address, const uint32_t* data,
                                              uint32_t len) {
  MSC->ADDRB = address;

  if (MSC->STATUS & MSC_STATUS_INVADDR) {
    return MCU_FLASH_STATUS_INVALID_ADDR;
  }

  MSC->WDATA = *data++;
  len -= 4;

  mcu_flash_status_t status;
  while (len) {
    status = msc_status_wait(MSC_STATUS_WDATAREADY, MSC_STATUS_WDATAREADY);

    if (status != MCU_FLASH_STATUS_OK) {
      MSC->WRITECMD = MSC_WRITECMD_WRITEEND;
      return status;
    }

    MSC->WDATA = *data++;
    len -= 4;
  }

  MSC->WRITECMD = MSC_WRITECMD_WRITEEND;

  status = msc_status_wait((MSC_STATUS_BUSY | MSC_STATUS_PENDING), 0);

  if (status == MCU_FLASH_STATUS_OK) {
    // We need to check twice to be sure
    status = msc_status_wait((MSC_STATUS_BUSY | MSC_STATUS_PENDING), 0);
  }

  return status;
}

RAMFUNC static mcu_flash_status_t msc_status_wait(uint32_t mask, uint32_t value) {
  uint32_t timeout = MSC_PROGRAM_TIMEOUT;

  while (timeout) {
    const uint32_t status = MSC->STATUS;

    /* if INVADDR is asserted by MSC, BUSY will never go high, can be checked early */
    if (status & MSC_STATUS_INVADDR) {
      return MCU_FLASH_STATUS_INVALID_ADDR;
    }

    /*
     * if requested operation fails because flash is locked, BUSY will be high
     * for a few cycles and it's not safe to clear WRITECTRL.WREN during that
     * period. mscStatusWait should return only when it's safe to do so.
     *
     * So if user is checking BUSY flag, make sure it matches user's expected
     * value and only then check the lock bits. Otherwise, do check early and
     * bail out if necessary.
     */

    if ((!(mask & MSC_STATUS_BUSY)) && (status & (MSC_STATUS_LOCKED | MSC_STATUS_REGLOCK))) {
      return MCU_FLASH_STATUS_LOCKED;
    }

    if ((status & mask) == value) {
      if (status & (MSC_STATUS_LOCKED | MSC_STATUS_REGLOCK)) {
        return MCU_FLASH_STATUS_LOCKED;
      } else {
        return MCU_FLASH_STATUS_OK;
      }
    }

    timeout--;
  }

  return MCU_FLASH_STATUS_TIMEOUT;
}
