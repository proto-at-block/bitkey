#include "mcu_hash.h"

#include "arithmetic.h"
#include "bitops.h"
#include "mcu.h"
#ifdef IMAGE_TYPE_APPLICATION
#include "rtos_mutex.h"
#include "rtos_thread.h"
#endif
#include "stm32u5xx.h"
// Included due to IWYU error in hal_hash.h.
#include "stm32u5xx_hal_dma.h"
#include "stm32u5xx_hal_hash.h"

#include <stddef.h>
#include <stdint.h>

#define MCU_HASH_TIMEOUT_US (1000u)

typedef struct {
  /**
   * @brief Intermediate word being processed.
   */
  uint32_t buffer;

  /**
   * @brief Current buffer index.
   */
  uint8_t buffer_ix;

#ifdef IMAGE_TYPE_APPLICATION
  /**
   * @brief Mutex used to guard exclusive access to the Hash module.
   */
  rtos_mutex_t access;
#endif
} mcu_hash_state_t;

mcu_hash_state_t state;

static void _mcu_hash_reset(void);
static mcu_err_t _mcu_hash_start(mcu_hash_alg_t alg, uint32_t mode);
static void _mcu_hash_wait_digest(const uint32_t mask);

void mcu_hash_init(void) {
  /* Enable clock to the Hash module. */
  RCC->AHB2ENR1 |= RCC_AHB2ENR1_HASHEN;

#ifdef IMAGE_TYPE_APPLICATION
  /* Initialize RTOS primitives */
  rtos_mutex_create(&state.access);
#endif

  _mcu_hash_reset();
}

mcu_err_t mcu_hash(mcu_hash_alg_t alg, const uint8_t* data, size_t data_size, uint8_t* digest,
                   size_t digest_size) {
  mcu_err_t err = mcu_hash_start(alg);
  if (err != MCU_ERROR_OK) {
    return err;
  }

  err = mcu_hash_update(data, data_size);
  if (err != MCU_ERROR_OK) {
    (void)mcu_hash_finish(digest, digest_size);
    return err;
  }

  return mcu_hash_finish(digest, digest_size);
}

mcu_err_t mcu_hash_start(mcu_hash_alg_t alg) {
  return _mcu_hash_start(alg, HASH_ALGOMODE_HASH);
}

mcu_err_t mcu_hash_update(const uint8_t* data, size_t data_size) {
#ifdef IMAGE_TYPE_APPLICATION
  if (!rtos_mutex_owner(&state.access)) {
    return MCU_ERROR_OWNER;
  }
#endif

  /* Check if enough bytes to complete the current buffered word. */
  if (state.buffer_ix > 0) {
    const uint8_t remaining = sizeof(uint32_t) - state.buffer_ix;
    uint8_t avail = data_size > remaining ? remaining : data_size;

    while (avail) {
      state.buffer |= *data << (state.buffer_ix * 8u);
      state.buffer_ix++;
      data++;
      data_size--;
      avail--;
    }

    if (state.buffer_ix == sizeof(uint32_t)) {
      HASH->DIN = state.buffer;
    }
  }

  while (data_size >= sizeof(uint32_t)) {
    HASH->DIN = *((uint32_t*)data);
    data += sizeof(uint32_t);
    data_size -= sizeof(uint32_t);
  }

  /* Store the remaining bytes to be processed later. */
  while (data_size > 0) {
    state.buffer |= *data << (state.buffer_ix * 8u);
    state.buffer_ix++;
    data++;
    data_size--;
  }

  return MCU_ERROR_OK;
}

mcu_err_t mcu_hash_finish(uint8_t* digest, size_t digest_size) {
#ifdef IMAGE_TYPE_APPLICATION
  if (!rtos_mutex_owner(&state.access)) {
    return MCU_ERROR_OWNER;
  }
#endif

  /* Get the real digest size and ensure the buffer can fit it. */
  const size_t read_size = HASH_DIGEST_LENGTH();
  if (read_size > digest_size) {
#ifdef IMAGE_TYPE_APPLICATION
    rtos_mutex_unlock(&state.access);
#endif
    return MCU_ERROR_PARAMETER;
  }

  uint8_t num_bits;
  if (state.buffer_ix > 0) {
    /* Write out partial word. */
    HASH->DIN = state.buffer;
    num_bits = state.buffer_ix * 8u;
    state.buffer = 0;
    state.buffer_ix = 0;
  } else {
    num_bits = sizeof(uint32_t) * 8u;
  }

  /* Program the number of bits in the last word. */
  HASH->STR =
    (HASH->STR & ~HASH_STR_NBLW_Msk) | ((num_bits << HASH_STR_NBLW_Pos) & HASH_STR_NBLW_Msk);

  /* Start digest calculation. */
  HASH->STR |= HASH_STR_DCAL;

  /* Wait for digest to complete. */
  _mcu_hash_wait_digest(HASH_SR_DCIS);

  if ((HASH->SR & HASH_SR_DCIS) == 0) {
    /* Timed out computing the hash. */
#ifdef IMAGE_TYPE_APPLICATION
    rtos_mutex_unlock(&state.access);
#endif
    return MCU_ERROR_TIMEOUT;
  }

  for (size_t ix = 0; ix < read_size; ix += sizeof(uint32_t)) {
    uint32_t digest_word = HASH_DIGEST->HR[ix / sizeof(uint32_t)];
    *((uint32_t*)digest) = htonl(digest_word);
    digest += sizeof(uint32_t);
  }

#ifdef IMAGE_TYPE_APPLICATION
  rtos_mutex_unlock(&state.access);
#endif
  return MCU_ERROR_OK;
}

mcu_err_t mcu_hash_hmac(mcu_hash_alg_t alg, const uint8_t* message, size_t message_size,
                        const uint8_t* key, size_t key_size, uint8_t* signature,
                        size_t signature_size) {
  mcu_err_t err = _mcu_hash_start(alg, HASH_ALGOMODE_HMAC);
  if (err != MCU_ERROR_OK) {
    return err;
  }

  if (key_size > 64u) {
    /* Large key sizes not currently supported. */
    return MCU_ERROR_PARAMETER;
  }

  /* Get the real digest size and ensure the buffer can fit it. */
  const size_t read_size = HASH_DIGEST_LENGTH();
  if (read_size > signature_size) {
#ifdef IMAGE_TYPE_APPLICATION
    rtos_mutex_unlock(&state.access);
#endif
    return MCU_ERROR_PARAMETER;
  }

  const uint8_t* inputs[] = {key, message, key};
  const size_t input_sizes[] = {key_size, message_size, key_size};
  for (uint8_t i = 0; i < ARRAY_SIZE(inputs); i++) {
    err = mcu_hash_update(inputs[i], input_sizes[i]);
    if (err != MCU_ERROR_OK) {
#ifdef IMAGE_TYPE_APPLICATION
      rtos_mutex_unlock(&state.access);
#endif
      return err;
    }

    uint8_t num_bits;
    if (state.buffer_ix > 0) {
      /* Write out partial word. */
      HASH->DIN = state.buffer;
      num_bits = state.buffer_ix * 8u;
      state.buffer = 0;
      state.buffer_ix = 0;
    } else {
      num_bits = sizeof(uint32_t) * 8u;
    }

    /* Program the number of bits in the last word. */
    HASH->STR =
      (HASH->STR & ~HASH_STR_NBLW_Msk) | ((num_bits << HASH_STR_NBLW_Pos) & HASH_STR_NBLW_Msk);

    /* Start digest calculation. */
    HASH->STR |= HASH_STR_DCAL;

    /* Wait for digest to complete. */
    const uint32_t mask = (i >= ARRAY_SIZE(inputs) - 1 ? HASH_SR_DCIS : HASH_SR_DINIS);
    _mcu_hash_wait_digest(mask);

    if ((HASH->SR & mask) == 0) {
#ifdef IMAGE_TYPE_APPLICATION
      rtos_mutex_unlock(&state.access);
#endif
      return MCU_ERROR_TIMEOUT;
    }
  }

  for (size_t ix = 0; ix < read_size; ix += sizeof(uint32_t)) {
    uint32_t digest_word = HASH_DIGEST->HR[ix / sizeof(uint32_t)];
    *((uint32_t*)signature) = htonl(digest_word);
    signature += sizeof(uint32_t);
  }

#ifdef IMAGE_TYPE_APPLICATION
  rtos_mutex_unlock(&state.access);
#endif
  return MCU_ERROR_OK;
}

static void _mcu_hash_reset(void) {
  RCC->AHB2RSTR1 |= RCC_AHB2RSTR1_HASHRST;
  RCC->AHB2RSTR1 &= ~RCC_AHB2RSTR1_HASHRST;
}

static mcu_err_t _mcu_hash_start(mcu_hash_alg_t alg, uint32_t mode) {
  uint32_t sel;

  switch (alg) {
    case MCU_HASH_ALG_SHA1:
      sel = HASH_ALGOSELECTION_SHA1;
      break;

    case MCU_HASH_ALG_SHA256:
      sel = HASH_ALGOSELECTION_SHA256;
      break;

    case MCU_HASH_ALG_MD5:
      sel = HASH_ALGOSELECTION_MD5;
      break;

    default:
      return MCU_ERROR_PARAMETER;
  }

#ifdef IMAGE_TYPE_APPLICATION
  /* Lock access to the hash module to the current thread. */
  rtos_mutex_lock(&state.access);
#endif

  /* Configure hash algorithm (8-bit). */
  HASH->CR = (HASH->CR & ~HASH_CR_ALGO_Msk) | sel;
  HASH->CR = (HASH->CR & ~HASH_CR_DATATYPE_Msk) | HASH_DATATYPE_8B;
  HASH->CR = (HASH->CR & ~HASH_CR_MODE_Msk) | mode;

  /* Start hash computation. */
  HASH->CR |= HASH_CR_INIT;

  state.buffer_ix = 0;
  state.buffer = 0u;

  return MCU_ERROR_OK;
}

static void _mcu_hash_wait_digest(const uint32_t mask) {
#ifdef IMAGE_TYPE_APPLICATION
  const uint64_t start_time = rtos_thread_micros();
  while (((HASH->SR & mask) == 0) && ((rtos_thread_micros() - start_time) < MCU_HASH_TIMEOUT_US)) {
    ;
  }
#else
#ifdef IMAGE_TYPE_BOOTLOADER
  while ((HASH->SR & mask) == 0) {
    ;
  }
#else
#error "Missing application or bootloader define."
#endif
#endif
}
