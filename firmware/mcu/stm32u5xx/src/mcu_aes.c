#include "mcu_aes.h"

#include "mcu.h"
#ifdef IMAGE_TYPE_APPLICATION
#include "rtos_mutex.h"
#include "rtos_thread.h"
#endif
#include "assert.h"
#include "stm32u5xx.h"

#include <stddef.h>
#include <stdint.h>
#include <string.h>

#define MCU_AES_TIMEOUT_US (1000000u)

/* GCM Phase definitions */
#define GCM_PHASE_INIT    (0)
#define GCM_PHASE_HEADER  (AES_CR_GCMPH_0)
#define GCM_PHASE_PAYLOAD (AES_CR_GCMPH_1)
#define GCM_PHASE_FINAL   (AES_CR_GCMPH_0 | AES_CR_GCMPH_1)

#define GCM_MODE (AES_CR_CHMOD_0 | AES_CR_CHMOD_1)

#define AES_CR_KEYSIZE_256B AES_CR_KEYSIZE
#define AES_CR_DATATYPE_32B 0
#define AES_CR_DATATYPE_8B  AES_CR_DATATYPE_1

#define AES_ICR_CLEAR_ALL (AES_ICR_CCF | AES_ICR_RWEIF | AES_ICR_KEIF)

/* AES Mode definitions */
#define AES_MODE_ENCRYPT (0)
#define AES_MODE_DECRYPT (AES_CR_MODE_1)

// Load unaligned 32-bit integer (big-endian encoding)
#define LOAD32BE(p)                                                                  \
  (((uint32_t)(((uint8_t*)(p))[0]) << 24) | ((uint32_t)(((uint8_t*)(p))[1]) << 16) | \
   ((uint32_t)(((uint8_t*)(p))[2]) << 8) | ((uint32_t)(((uint8_t*)(p))[3]) << 0))

typedef struct {
  /**
   * @brief Mutex used to guard exclusive access to the AES module.
   */
#ifdef IMAGE_TYPE_APPLICATION
  rtos_mutex_t access;
#endif
  bool initialized;
} mcu_aes_state_t;

static mcu_aes_state_t state = {
  .initialized = false,
};

// Secutils will get linked in later and provide this function
extern int memcmp_s(const void* b1, const void* b2, size_t len);

static void _mcu_aes_reset(void);
static mcu_err_t _mcu_aes_wait_completion(void);
static void _mcu_aes_write_key(const uint8_t* key);
static void _mcu_aes_write_iv(const uint8_t* iv);
static void _mcu_aes_write_data(const uint32_t* data);
static void _mcu_aes_read_data(uint32_t* data);
static mcu_err_t _mcu_aes_process_header(const uint8_t* aad, size_t aad_size);
static mcu_err_t _mcu_aes_prepare_gcm(const uint8_t* key, const uint8_t* iv, uint32_t mode);

void mcu_aes_init(void) {
  if (!state.initialized) {
    /* Enable clock to the AES module. */
    RCC->AHB2ENR1 |= RCC_AHB2ENR1_AESEN;

#ifdef IMAGE_TYPE_APPLICATION
    /* Initialize RTOS primitives */
    rtos_mutex_create(&state.access);
#endif

    _mcu_aes_reset();
  }
  state.initialized = true;
}

static mcu_err_t _mcu_aes_prepare_gcm(const uint8_t* key, const uint8_t* iv, uint32_t mode) {
  /* Ensure the peripheral is idle before configuring it. */
  AES->CR &= ~AES_CR_EN;
  while ((AES->SR & AES_SR_BUSY) != 0u) {
  }

  /* Clear any lingering status from a previous operation. */
  AES->ICR = AES_ICR_CLEAR_ALL;

  /* Configure AES for GCM as described in RM0456 section 49. */
  uint32_t cr = AES->CR;
  cr &=
    ~(AES_CR_MODE | AES_CR_DATATYPE | AES_CR_CHMOD | AES_CR_GCMPH | AES_CR_KEYSIZE | AES_CR_NPBLB);
  cr |= mode | GCM_MODE | AES_CR_DATATYPE_32B | AES_CR_KEYSIZE_256B | GCM_PHASE_INIT;
  AES->CR = cr;

  _mcu_aes_write_key(key);
  _mcu_aes_write_iv(iv);

  /* Start the GCM init phase (hash subkey generation). */
  AES->CR |= AES_CR_EN;

  mcu_err_t err = _mcu_aes_wait_completion();
  if (err != MCU_ERROR_OK) {
    AES->ICR = AES_ICR_CLEAR_ALL;
    return err;
  }

  AES->ICR = AES_ICR_CCF;
  AES->CR = (AES->CR & ~AES_CR_DATATYPE) | AES_CR_DATATYPE_8B;

  return MCU_ERROR_OK;
}

mcu_err_t mcu_aes_gcm_encrypt(const uint8_t* key, size_t key_size, const uint8_t* iv,
                              const uint8_t* aad, size_t aad_size, const uint8_t* input,
                              size_t input_size, uint8_t* output, size_t output_size, uint8_t* tag,
                              size_t tag_size) {
  ASSERT(key_size == MCU_AES_256_GCM_KEY_SIZE);
  if (!state.initialized) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  if (key == NULL || iv == NULL || tag == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  /* AAD can be NULL only if aad_size is 0 */
  if (aad_size > 0 && aad == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  /* input/output can be NULL only if input_size is 0 */
  if (input_size > 0 && (input == NULL || output == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  /* Validate buffer sizes */
  if (output_size < input_size) {
    return MCU_ERROR_PARAMETER;
  }

  if (tag_size < MCU_AES_256_GCM_TAG_SIZE) {
    return MCU_ERROR_PARAMETER;
  }

#ifdef IMAGE_TYPE_APPLICATION
  /* Lock access to the AES module to the current thread. */
  rtos_mutex_lock(&state.access);
#endif

  mcu_err_t err = _mcu_aes_prepare_gcm(key, iv, AES_MODE_ENCRYPT);
  if (err != MCU_ERROR_OK) {
    goto cleanup;
  }

  err = _mcu_aes_process_header(aad, aad_size);
  if (err != MCU_ERROR_OK) {
    goto cleanup;
  }

  size_t blocks = input_size / 16u;
  size_t remaining = input_size % 16u;

  if (input_size > 0u) {
    AES->CR = (AES->CR & ~AES_CR_GCMPH) | GCM_PHASE_PAYLOAD;
    AES->CR |= AES_CR_EN;

    for (size_t i = 0; i < blocks; i++) {
      _mcu_aes_write_data((const uint32_t*)(input + i * 16u));

      err = _mcu_aes_wait_completion();
      if (err != MCU_ERROR_OK) {
        goto cleanup;
      }

      _mcu_aes_read_data((uint32_t*)(output + i * 16u));
      AES->ICR = AES_ICR_CCF;
    }

    if (remaining > 0u) {
      uint8_t last_block[16] = {0};
      memcpy(last_block, input + blocks * 16u, remaining);

      uint32_t npblb = 16u - remaining;
      AES->CR = (AES->CR & ~AES_CR_NPBLB) | ((npblb << AES_CR_NPBLB_Pos) & AES_CR_NPBLB);

      _mcu_aes_write_data((const uint32_t*)last_block);

      err = _mcu_aes_wait_completion();
      if (err != MCU_ERROR_OK) {
        goto cleanup;
      }

      uint8_t output_block[16];
      _mcu_aes_read_data((uint32_t*)output_block);
      memcpy(output + blocks * 16u, output_block, remaining);
      AES->ICR = AES_ICR_CCF;
    }
  }

  AES->CR &= ~AES_CR_NPBLB;

  AES->CR = (AES->CR & ~AES_CR_GCMPH) | GCM_PHASE_FINAL;
  if ((AES->CR & AES_CR_EN) == 0u) {
    AES->CR |= AES_CR_EN;
  }

  AES->DINR = 0u;
  AES->DINR = (uint32_t)(aad_size * 8u);
  AES->DINR = 0u;
  AES->DINR = (uint32_t)(input_size * 8u);

  err = _mcu_aes_wait_completion();
  if (err != MCU_ERROR_OK) {
    goto cleanup;
  }

  _mcu_aes_read_data((uint32_t*)tag);
  AES->ICR = AES_ICR_CCF;

  err = MCU_ERROR_OK;

cleanup:
  AES->CR &= ~AES_CR_EN;
  AES->CR &= ~AES_CR_NPBLB;
  AES->ICR = AES_ICR_CLEAR_ALL;
  _mcu_aes_reset();

#ifdef IMAGE_TYPE_APPLICATION
  rtos_mutex_unlock(&state.access);
#endif

  return err;
}

mcu_err_t mcu_aes_gcm_decrypt(const uint8_t* key, size_t key_size, const uint8_t* iv,
                              const uint8_t* aad, size_t aad_size, const uint8_t* input,
                              size_t input_size, uint8_t* output, size_t output_size,
                              const uint8_t* tag, size_t tag_size) {
  ASSERT(key_size == MCU_AES_256_GCM_KEY_SIZE);
  if (!state.initialized) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  if (key == NULL || iv == NULL || tag == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  /* AAD can be NULL only if aad_size is 0 */
  if (aad_size > 0 && aad == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  /* input/output can be NULL only if input_size is 0 */
  if (input_size > 0 && (input == NULL || output == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  /* Validate buffer sizes */
  if (output_size < input_size) {
    return MCU_ERROR_PARAMETER;
  }

  if (tag_size < MCU_AES_256_GCM_TAG_SIZE) {
    return MCU_ERROR_PARAMETER;
  }

#ifdef IMAGE_TYPE_APPLICATION
  /* Lock access to the AES module to the current thread. */
  rtos_mutex_lock(&state.access);
#endif

  mcu_err_t err = _mcu_aes_prepare_gcm(key, iv, AES_MODE_DECRYPT);
  if (err != MCU_ERROR_OK) {
    goto cleanup;
  }

  err = _mcu_aes_process_header(aad, aad_size);
  if (err != MCU_ERROR_OK) {
    goto cleanup;
  }

  size_t blocks = input_size / 16u;
  size_t remaining = input_size % 16u;

  if (input_size > 0u) {
    AES->CR = (AES->CR & ~AES_CR_GCMPH) | GCM_PHASE_PAYLOAD;
    AES->CR |= AES_CR_EN;

    for (size_t i = 0; i < blocks; i++) {
      _mcu_aes_write_data((const uint32_t*)(input + i * 16u));

      err = _mcu_aes_wait_completion();
      if (err != MCU_ERROR_OK) {
        goto cleanup;
      }

      _mcu_aes_read_data((uint32_t*)(output + i * 16u));
      AES->ICR = AES_ICR_CCF;
    }

    if (remaining > 0u) {
      uint8_t last_block[16] = {0};
      memcpy(last_block, input + blocks * 16u, remaining);

      uint32_t npblb = 16u - remaining;
      AES->CR = (AES->CR & ~AES_CR_NPBLB) | ((npblb << AES_CR_NPBLB_Pos) & AES_CR_NPBLB);

      _mcu_aes_write_data((const uint32_t*)last_block);

      err = _mcu_aes_wait_completion();
      if (err != MCU_ERROR_OK) {
        goto cleanup;
      }

      uint8_t output_block[16];
      _mcu_aes_read_data((uint32_t*)output_block);
      memcpy(output + blocks * 16u, output_block, remaining);
      AES->ICR = AES_ICR_CCF;
    }
  }

  AES->CR &= ~AES_CR_NPBLB;

  AES->CR = (AES->CR & ~AES_CR_GCMPH) | GCM_PHASE_FINAL;
  if ((AES->CR & AES_CR_EN) == 0u) {
    AES->CR |= AES_CR_EN;
  }

  AES->DINR = 0u;
  AES->DINR = (uint32_t)(aad_size * 8u);
  AES->DINR = 0u;
  AES->DINR = (uint32_t)(input_size * 8u);

  err = _mcu_aes_wait_completion();
  if (err != MCU_ERROR_OK) {
    goto cleanup;
  }

  uint8_t computed_tag[MCU_AES_256_GCM_TAG_SIZE];
  _mcu_aes_read_data((uint32_t*)computed_tag);
  AES->ICR = AES_ICR_CCF;

  if (memcmp_s(tag, computed_tag, MCU_AES_256_GCM_TAG_SIZE) != 0) {
    err = MCU_ERROR_AUTH_FAILED;
    goto cleanup;
  }

  err = MCU_ERROR_OK;

cleanup:
  AES->CR &= ~AES_CR_EN;
  AES->CR &= ~AES_CR_NPBLB;
  AES->ICR = AES_ICR_CLEAR_ALL;
  _mcu_aes_reset();

  if (err != MCU_ERROR_OK) {
    memset(output, 0, output_size);
  }

#ifdef IMAGE_TYPE_APPLICATION
  rtos_mutex_unlock(&state.access);
#endif

  return err;
}

static void _mcu_aes_reset(void) {
  RCC->AHB2RSTR1 |= RCC_AHB2RSTR1_AESRST;
  RCC->AHB2RSTR1 &= ~RCC_AHB2RSTR1_AESRST;
}

static mcu_err_t _mcu_aes_wait_completion(void) {
#ifdef IMAGE_TYPE_APPLICATION
  const uint64_t start_time = rtos_thread_micros();
  while (((AES->SR & AES_SR_CCF) == 0) &&
         ((rtos_thread_micros() - start_time) < MCU_AES_TIMEOUT_US)) {
#else
#ifdef IMAGE_TYPE_BOOTLOADER
  while ((AES->SR & AES_SR_CCF) == 0) {
#else
#error "Missing application or bootloader define."
#endif
#endif

    if ((AES->SR & (AES_SR_WRERR | AES_SR_RDERR)) != 0u) {
      AES->ICR = AES_ICR_RWEIF;
      return MCU_ERROR_PARAMETER;
    }
  }

  if ((AES->SR & AES_SR_CCF) == 0) {
    return MCU_ERROR_TIMEOUT;
  }

  return MCU_ERROR_OK;
}

static void _mcu_aes_write_key(const uint8_t* key) {
  // Write 256-bit key (8 words) in big-endian format
  AES->KEYR7 = LOAD32BE(key);
  AES->KEYR6 = LOAD32BE(key + 4);
  AES->KEYR5 = LOAD32BE(key + 8);
  AES->KEYR4 = LOAD32BE(key + 12);
  AES->KEYR3 = LOAD32BE(key + 16);
  AES->KEYR2 = LOAD32BE(key + 20);
  AES->KEYR1 = LOAD32BE(key + 24);
  AES->KEYR0 = LOAD32BE(key + 28);
}

static void _mcu_aes_write_iv(const uint8_t* iv) {
  /* Write 96-bit IV (3 words) in big-endian format, plus counter
   * IV format for GCM: {IV[95:0], counter[31:0]}
   * IVR3 = IV[95:64]
   * IVR2 = IV[63:32]
   * IVR1 = IV[31:0]
   * IVR0 = counter (initialized to 2 for GCM per NIST SP 800-38D)
   */
  AES->IVR3 = LOAD32BE(iv);
  AES->IVR2 = LOAD32BE(iv + 4);
  AES->IVR1 = LOAD32BE(iv + 8);

  /* Set counter to 2 for GCM mode (counter starts at 2, not 1) */
  AES->IVR0 = 0x00000002u;
}

static void _mcu_aes_write_data(const uint32_t* data) {
  /* Write 128-bit block (4 words) directly */
  AES->DINR = data[0];
  AES->DINR = data[1];
  AES->DINR = data[2];
  AES->DINR = data[3];
}

static void _mcu_aes_read_data(uint32_t* data) {
  /* Read 128-bit block (4 words) directly */
  data[0] = AES->DOUTR;
  data[1] = AES->DOUTR;
  data[2] = AES->DOUTR;
  data[3] = AES->DOUTR;
}

static mcu_err_t _mcu_aes_process_header(const uint8_t* aad, size_t aad_size) {
  /* If no AAD, the header phase can be skipped entirely. */
  if (aad_size == 0 || aad == NULL) {
    return MCU_ERROR_OK;
  }

  /* Phase 2: Header phase for AAD */
  AES->CR = (AES->CR & ~AES_CR_GCMPH_Msk) | GCM_PHASE_HEADER;
  AES->CR |= AES_CR_EN;

  /* Process AAD in 16-byte blocks */
  size_t blocks = aad_size / 16u;
  size_t remaining = aad_size % 16u;

  for (size_t i = 0; i < blocks; i++) {
    /* Write AAD block */
    _mcu_aes_write_data((const uint32_t*)(aad + i * 16u));

    /* Wait for computation complete */
    mcu_err_t err = _mcu_aes_wait_completion();
    if (err != MCU_ERROR_OK) {
      return err;
    }

    /* Clear completion flag */
    AES->ICR = AES_ICR_CCF;
  }

  /* Handle remaining bytes if not block-aligned */
  if (remaining > 0) {
    uint8_t last_block[16] = {0};
    memcpy(last_block, aad + blocks * 16u, remaining);

    /* Write last block with padding */
    _mcu_aes_write_data((const uint32_t*)last_block);

    /* Wait for computation complete */
    mcu_err_t err = _mcu_aes_wait_completion();
    if (err != MCU_ERROR_OK) {
      return err;
    }

    /* Clear completion flag */
    AES->ICR = AES_ICR_CCF;
  }

  return MCU_ERROR_OK;
}
