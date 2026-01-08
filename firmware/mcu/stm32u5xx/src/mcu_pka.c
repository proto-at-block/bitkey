#include "mcu_pka.h"

#include "assert.h"
#include "mcu.h"
#include "mcu_tamper.h"
#ifdef IMAGE_TYPE_APPLICATION
#include "rtos_mutex.h"
#include "rtos_thread.h"
#endif
#include "stm32u5xx.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_pka.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

/**
 * @brief Timeout (in milliseconds) to wait for PKA operations to complete.
 *
 * @note This value is based on the ECDSA sign/verification calculation timings
 * provided by ST. RSA is significantly slower, so if RSA support is needed,
 * a differen timeout value will need to be used.
 */
#define MCU_PKA_TIMEOUT_MS (300u)

/**
 * @brief Point P (x, y) is on curve.
 */
#define MCU_PKA_POINT_ON_CURVE 0xD60Du

/**
 * @brief Sign was successful.
 */
#define MCU_PKA_SIGN_SUCCESS 0xD60Du

/**
 * @brief Post reset, it can take up to 667 cycles before PKA registers can be
 * accessed.
 */
#define MCU_PKA_RESET_TIMEOUT_CYCLES (667u)

/**
 * @brief Cycles to wait for the PKA to initialize during boot.
 */
#define MCU_PKA_INIT_TIMEOUT_CYCLES (1000000u)

/* NIST P-256 (secp256r1) curve parameters */
static const uint8_t secp256r1_modulus[] = {
  0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

static const uint8_t secp256r1_coef_a[] = {
  0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC};

static const uint8_t secp256r1_coef_b[] = {
  0x5A, 0xC6, 0x35, 0xD8, 0xAA, 0x3A, 0x93, 0xE7, 0xB3, 0xEB, 0xBD, 0x55, 0x76, 0x98, 0x86, 0xBC,
  0x65, 0x1D, 0x06, 0xB0, 0xCC, 0x53, 0xB0, 0xF6, 0x3B, 0xCE, 0x3C, 0x3E, 0x27, 0xD2, 0x60, 0x4B};

static const uint8_t secp256r1_base_point_x[] = {
  0x6B, 0x17, 0xD1, 0xF2, 0xE1, 0x2C, 0x42, 0x47, 0xF8, 0xBC, 0xE6, 0xE5, 0x63, 0xA4, 0x40, 0xF2,
  0x77, 0x03, 0x7D, 0x81, 0x2D, 0xEB, 0x33, 0xA0, 0xF4, 0xA1, 0x39, 0x45, 0xD8, 0x98, 0xC2, 0x96};

static const uint8_t secp256r1_base_point_y[] = {
  0x4F, 0xE3, 0x42, 0xE2, 0xFE, 0x1A, 0x7F, 0x9B, 0x8E, 0xE7, 0xEB, 0x4A, 0x7C, 0x0F, 0x9E, 0x16,
  0x2B, 0xCE, 0x33, 0x57, 0x6B, 0x31, 0x5E, 0xCE, 0xCB, 0xB6, 0x40, 0x68, 0x37, 0xBF, 0x51, 0xF5};

static const uint8_t secp256r1_order[] = {
  0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
  0xBC, 0xE6, 0xFA, 0xAD, 0xA7, 0x17, 0x9E, 0x84, 0xF3, 0xB9, 0xCA, 0xC2, 0xFC, 0x63, 0x25, 0x51};

/* secp256k1 curve parameters */
static const uint8_t secp256k1_modulus[] = {
  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE, 0xFF, 0xFF, 0xFC, 0x2F};

static const uint8_t secp256k1_coef_a[] = {
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

static const uint8_t secp256k1_coef_b[] = {
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07};

static const uint8_t secp256k1_base_point_x[] = {
  0x79, 0xBE, 0x66, 0x7E, 0xF9, 0xDC, 0xBB, 0xAC, 0x55, 0xA0, 0x62, 0x95, 0xCE, 0x87, 0x0B, 0x07,
  0x02, 0x9B, 0xFC, 0xDB, 0x2D, 0xCE, 0x28, 0xD9, 0x59, 0xF2, 0x81, 0x5B, 0x16, 0xF8, 0x17, 0x98};

static const uint8_t secp256k1_base_point_y[] = {
  0x48, 0x3A, 0xDA, 0x77, 0x26, 0xA3, 0xC4, 0x65, 0x5D, 0xA4, 0xFB, 0xFC, 0x0E, 0x11, 0x08, 0xA8,
  0xFD, 0x17, 0xB4, 0x48, 0xA6, 0x85, 0x54, 0x19, 0x9C, 0x47, 0xD0, 0x8F, 0xFB, 0x10, 0xD4, 0xB8};

static const uint8_t secp256k1_order[] = {
  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE,
  0xBA, 0xAE, 0xDC, 0xE6, 0xAF, 0x48, 0xA0, 0x3B, 0xBF, 0xD2, 0x5E, 0x8C, 0xD0, 0x36, 0x41, 0x41};

/* NIST P-384 (secp384r1) curve parameters */
static const uint8_t secp384r1_modulus[] = {
  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE,
  0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF};

static const uint8_t secp384r1_coef_a[] = {
  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE,
  0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFC};

static const uint8_t secp384r1_coef_b[] = {
  0xB3, 0x31, 0x2F, 0xA7, 0xE2, 0x3E, 0xE7, 0xE4, 0x98, 0x8E, 0x05, 0x6B, 0xE3, 0xF8, 0x2D, 0x19,
  0x18, 0x1D, 0x9C, 0x6E, 0xFE, 0x81, 0x41, 0x12, 0x03, 0x14, 0x08, 0x8F, 0x50, 0x13, 0x87, 0x5A,
  0xC6, 0x56, 0x39, 0x8D, 0x8A, 0x2E, 0xD1, 0x9D, 0x2A, 0x85, 0xC8, 0xED, 0xD3, 0xEC, 0x2A, 0xEF};

static const uint8_t secp384r1_base_point_x[] = {
  0xAA, 0x87, 0xCA, 0x22, 0xBE, 0x8B, 0x05, 0x37, 0x8E, 0xB1, 0xC7, 0x1E, 0xF3, 0x20, 0xAD, 0x74,
  0x6E, 0x1D, 0x3B, 0x62, 0x8B, 0xA7, 0x9B, 0x98, 0x59, 0xF7, 0x41, 0xE0, 0x82, 0x54, 0x2A, 0x38,
  0x55, 0x02, 0xF2, 0x5D, 0xBF, 0x55, 0x29, 0x6C, 0x3A, 0x54, 0x5E, 0x38, 0x72, 0x76, 0x0A, 0xB7};

static const uint8_t secp384r1_base_point_y[] = {
  0x36, 0x17, 0xDE, 0x4A, 0x96, 0x26, 0x2C, 0x6F, 0x5D, 0x9E, 0x98, 0xBF, 0x92, 0x92, 0xDC, 0x29,
  0xF8, 0xF4, 0x1D, 0xBD, 0x28, 0x9A, 0x14, 0x7C, 0xE9, 0xDA, 0x31, 0x13, 0xB5, 0xF0, 0xB8, 0xC0,
  0x0A, 0x60, 0xB1, 0xCE, 0x1D, 0x7E, 0x81, 0x9D, 0x7A, 0x43, 0x1D, 0x7C, 0x90, 0xEA, 0x0E, 0x5F};

static const uint8_t secp384r1_order[] = {
  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xC7, 0x63, 0x4D, 0x81, 0xF4, 0x37, 0x2D, 0xDF,
  0x58, 0x1A, 0x0D, 0xB2, 0x48, 0xB0, 0xA7, 0x7A, 0xEC, 0xEC, 0x19, 0x6A, 0xCC, 0xC5, 0x29, 0x73};

typedef struct {
#ifdef IMAGE_TYPE_APPLICATION
  /**
   * @brief Mutex used to guard exclusive access to the PKA module.
   */
  rtos_mutex_t access;
#endif
} mcu_pka_state_t;

#ifdef IMAGE_TYPE_APPLICATION
static mcu_pka_state_t state;
#endif

/**
 * @brief Retrieves the base address of the PKA registers.
 *
 * @return Pointer to the PKA registers.
 */
static PKA_TypeDef* _mcu_pka_get_registers(void);

/**
 * @brief Write bytes to PKA RAM at specified word offset.
 *
 * @param offset  Word offset in PKA RAM.
 * @param data    Pointer to data (big-endian bytes).
 * @param size    Number of bytes to write.
 *
 * @return #MCU_ERROR_OK on success.
 */
static mcu_err_t _mcu_pka_ram_write(uint32_t offset, const uint8_t* data, size_t size);

/**
 * @brief Write bytes to PKA RAM at specified word offset, padded to the
 * specified padding size.
 *
 * @param offset     Word offset in PKA RAM.
 * @param data       Pointer to data (big-endian bytes).
 * @param data_size  Size of @p data_size in bytes.
 * @param pad_size   Total size to pad to.
 *
 * @return #MCU_ERROR_OK on success.
 */
static mcu_err_t _mcu_pka_ram_write_padded(uint32_t offset, const uint8_t* data, size_t data_size,
                                           size_t pad_size);

/**
 * @brief Read bytes from PKA RAM at specified word offset.
 *
 * @param[in]  offset  Word offset in PKA RAM.
 * @param[out] data    Pointer to output buffer (big-endian bytes).
 * @param[in]  size    Number of bytes to read.
 *
 * @return #MCU_ERROR_OK on success.
 */
static mcu_err_t _mcu_pka_ram_read(uint32_t offset, uint8_t* data, size_t size);

/**
 * @brief Returns `true` if a public key is the identity element.
 *
 * @param public_key  Pointer to the PKA public key.
 *
 * @return `true` if public key is identity element, otherwise `false`.
 */
static bool _mcu_pka_is_identity(const mcu_pka_public_key_t* public_key);

/**
 * @brief Compares two big-endian integers.
 *
 * @param a      First integer (big-endian).
 * @param b      Second integer (big-endian).
 * @param size   Size of both integers in bytes.
 *
 * @return -1 if a < b, 0 if a == b, 1 if a > b.
 */
static int _mcu_pka_compare(const uint8_t* a, const uint8_t* b, size_t size);

/**
 * @brief Returns `true` if a big-endian integer is zero.
 *
 * @param data   Pointer to integer data (big-endian).
 * @param size   Size of integer in bytes.
 *
 * @return `true` if integer is zero, otherwise `false`.
 */
static bool _mcu_pka_is_zero(const uint8_t* data, size_t size);

/**
 * @brief Clears the PKA operation flags.
 */
static void _mcu_pka_clear_flags(void);

/**
 * @brief Waits for a PKA operation to complete.
 *
 * @return Error value:
 *  - #MCU_ERROR_OK on success.
 *  - #MCU_ERROR_TIMEOUT on timeout.
 *  - #MCU_ERROR_PKA_FAIL on PKA operation failure.
 */
static mcu_err_t _mcu_pka_wait_for_completion(void);

void mcu_pka_init(void) {
  /* Enable clock to the PKA module. */
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_PKA);

  /* Force PKA peripheral reset. */
  LL_AHB2_GRP1_ForceReset(LL_AHB2_GRP1_PERIPH_PKA);

  /* Release PKA peripheral from reset. */
  LL_AHB2_GRP1_ReleaseReset(LL_AHB2_GRP1_PERIPH_PKA);

  /* Clear control register. */
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  ASSERT(pka != NULL);
  pka->CR = 0;

  /* Post PKA reset, it can take some clock cycles before EN can be set. */
  volatile uint32_t cycles = MCU_PKA_RESET_TIMEOUT_CYCLES;
  while (cycles > 0) {
    cycles--;
  }

#ifdef IMAGE_TYPE_APPLICATION
  /* Initialize RTOS primitives. */
  rtos_mutex_create(&state.access);
#endif

  /* Enable PKA with timeout. */
  cycles = MCU_PKA_INIT_TIMEOUT_CYCLES;
  while (cycles > 0u) {
    cycles--;

    if (!LL_PKA_IsEnabled(pka)) {
      /* Attempt to enable the peripheral. */
      pka->CR = PKA_CR_EN | (LL_PKA_MODE_ARITHMETIC_ADD << PKA_CR_MODE_Pos);
    } else {
      _mcu_pka_clear_flags();

      /* Check if initialization has finished. */
      if ((pka->SR & PKA_SR_INITOK) != 0u) {
        break;
      }
    }
  }

  /* Assert if timed out. */
  ASSERT(cycles > 0u);
}

mcu_err_t mcu_pka_get_curve_params(mcu_pka_curve_t curve, mcu_pka_curve_params_t* params) {
  if (params == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  switch (curve) {
    case MCU_PKA_CURVE_SECP256R1:
      params->modulus = secp256r1_modulus;
      params->coef_a = secp256r1_coef_a;
      params->coef_b = secp256r1_coef_b;
      params->base_point_x = secp256r1_base_point_x;
      params->base_point_y = secp256r1_base_point_y;
      params->order = secp256r1_order;
      params->modulus_size = MCU_PKA_SECP256R1_SIZE;
      params->order_size = MCU_PKA_SECP256R1_SIZE;
      params->coef_a_sign = 0; /* Positive */
      break;

    case MCU_PKA_CURVE_SECP256K1:
      params->modulus = secp256k1_modulus;
      params->coef_a = secp256k1_coef_a;
      params->coef_b = secp256k1_coef_b;
      params->base_point_x = secp256k1_base_point_x;
      params->base_point_y = secp256k1_base_point_y;
      params->order = secp256k1_order;
      params->modulus_size = MCU_PKA_SECP256K1_SIZE;
      params->order_size = MCU_PKA_SECP256K1_SIZE;
      params->coef_a_sign = 0; /* Positive (a=0) */
      break;

    case MCU_PKA_CURVE_SECP384R1:
      params->modulus = secp384r1_modulus;
      params->coef_a = secp384r1_coef_a;
      params->coef_b = secp384r1_coef_b;
      params->base_point_x = secp384r1_base_point_x;
      params->base_point_y = secp384r1_base_point_y;
      params->order = secp384r1_order;
      params->modulus_size = MCU_PKA_SECP384R1_SIZE;
      params->order_size = MCU_PKA_SECP384R1_SIZE;
      params->coef_a_sign = 0; /* Positive */
      break;

    case MCU_PKA_CURVE_CUSTOM:
      /* For custom curves, the caller must fill in the parameters. */
      return MCU_ERROR_PARAMETER;

    default:
      return MCU_ERROR_PARAMETER;
  }

  return MCU_ERROR_OK;
}

mcu_err_t mcu_pka_ecdsa_sign(const mcu_pka_curve_params_t* curve_params, const uint8_t* private_key,
                             const uint8_t* hash, size_t hash_size, const uint8_t* k,
                             uint8_t* signature_r, uint8_t* signature_s) {
  if ((curve_params == NULL) || (private_key == NULL) || (hash == NULL) || (k == NULL) ||
      (signature_r == NULL) || (signature_s == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  if (hash_size > curve_params->order_size) {
    return MCU_ERROR_PARAMETER;
  }

  /* Verify that the base point G is on the curve. */
  mcu_err_t err =
    mcu_pka_point_check(curve_params, curve_params->base_point_x, curve_params->base_point_y);
  if (err != MCU_ERROR_OK) {
    return err;
  }

#ifdef IMAGE_TYPE_APPLICATION
  /* Lock access to the PKA module to the current thread. */
  rtos_mutex_lock(&state.access);
#endif

  /* Clear any previous flags. */
  _mcu_pka_clear_flags();

  /* Set PKA mode to ECDSA sign (Section 53.3.5, 53.4.16). */
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  ASSERT(pka != NULL);
  LL_PKA_SetMode(pka, LL_PKA_MODE_ECDSA_SIGNATURE);

  /* Write input parameters to PKA RAM (Section 53.4.16). */
  const uint32_t order_bits = curve_params->order_size * 8;
  const uint32_t modulus_bits = curve_params->modulus_size * 8;

  /* Curve prime order n length (bits). */
  pka->RAM[PKA_ECDSA_SIGN_IN_ORDER_NB_BITS] = order_bits;

  /* Curve modulus p length 64 bits (in bits). */
  pka->RAM[PKA_ECDSA_SIGN_IN_MOD_NB_BITS] = modulus_bits;

  /* Curve coefficient a sign. */
  pka->RAM[PKA_ECDSA_SIGN_IN_A_COEFF_SIGN] = curve_params->coef_a_sign;

  /* Curve coefficient |a| */
  _mcu_pka_ram_write(PKA_ECDSA_SIGN_IN_A_COEFF, curve_params->coef_a, curve_params->modulus_size);

  /* Curve coefficient b */
  _mcu_pka_ram_write(PKA_ECDSA_SIGN_IN_B_COEFF, curve_params->coef_b, curve_params->modulus_size);

  /* Curve modulus value p */
  _mcu_pka_ram_write(PKA_ECDSA_SIGN_IN_MOD_GF, curve_params->modulus, curve_params->modulus_size);

  /* Integer k */
  _mcu_pka_ram_write(PKA_ECDSA_SIGN_IN_K, k, curve_params->order_size);

  /* Curve base point Gx */
  _mcu_pka_ram_write(PKA_ECDSA_SIGN_IN_INITIAL_POINT_X, curve_params->base_point_x,
                     curve_params->modulus_size);

  /* Curve base point Gy */
  _mcu_pka_ram_write(PKA_ECDSA_SIGN_IN_INITIAL_POINT_Y, curve_params->base_point_y,
                     curve_params->modulus_size);

  /* Hash of message z */
  _mcu_pka_ram_write_padded(PKA_ECDSA_SIGN_IN_HASH_E, hash, hash_size, curve_params->order_size);

  /* Private key d */
  _mcu_pka_ram_write(PKA_ECDSA_SIGN_IN_PRIVATE_KEY_D, private_key, curve_params->order_size);

  /* Curve prime order n */
  _mcu_pka_ram_write(PKA_ECDSA_SIGN_IN_ORDER_N, curve_params->order, curve_params->order_size);

  /* Start the operation */
  LL_PKA_Start(pka);

  /* Wait for completion */
  err = _mcu_pka_wait_for_completion();

  if (err != MCU_ERROR_OK) {
#ifdef IMAGE_TYPE_APPLICATION
    rtos_mutex_unlock(&state.access);
#endif
    return err;
  }

  /* Read result. */
  const uint32_t result = pka->RAM[PKA_ECDSA_SIGN_OUT_ERROR];

  if (result != MCU_PKA_SIGN_SUCCESS) {
    /* Clear completion flag. */
    LL_PKA_ClearFlag_PROCEND(pka);
#ifdef IMAGE_TYPE_APPLICATION
    rtos_mutex_unlock(&state.access);
#endif
    /* If a PKA operation fails, a PKA tamper event is generated. */
    mcu_tamper_clear(MCU_TAMPER_FLAG_CRYPTO);
    return MCU_ERROR_UNKNOWN;
  }

  /* Result: signature part r */
  err = _mcu_pka_ram_read(PKA_ECDSA_SIGN_OUT_SIGNATURE_R, signature_r, curve_params->order_size);
  if (err == MCU_ERROR_OK) {
    /* Result: signature part s */
    err = _mcu_pka_ram_read(PKA_ECDSA_SIGN_OUT_SIGNATURE_S, signature_s, curve_params->order_size);
  }

  /* Clear completion flag. */
  LL_PKA_ClearFlag_PROCEND(pka);

#ifdef IMAGE_TYPE_APPLICATION
  rtos_mutex_unlock(&state.access);
#endif

  return err;
}

mcu_err_t mcu_pka_ecdsa_verify(const mcu_pka_curve_params_t* curve_params,
                               const mcu_pka_public_key_t* public_key, const uint8_t* hash,
                               size_t hash_size, const mcu_pka_signature_t* signature) {
  if ((curve_params == NULL) || (public_key == NULL) || (hash == NULL) || (signature == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  if (hash_size > curve_params->order_size) {
    return MCU_ERROR_PARAMETER;
  }

  if (public_key->size != curve_params->modulus_size) {
    return MCU_ERROR_PARAMETER;
  }

  if (signature->size != curve_params->order_size) {
    return MCU_ERROR_PARAMETER;
  }

  /* Verify that 0 < r < n */
  if (_mcu_pka_is_zero(signature->r, signature->size) ||
      (_mcu_pka_compare(signature->r, curve_params->order, signature->size) >= 0)) {
    return MCU_ERROR_PKA_INVALID_SIGNATURE;
  }

  /* Verify that 0 < s < n */
  if (_mcu_pka_is_zero(signature->s, signature->size) ||
      (_mcu_pka_compare(signature->s, curve_params->order, signature->size) >= 0)) {
    return MCU_ERROR_PKA_INVALID_SIGNATURE;
  }

  /* Check 1: Verify that the public key is not the identity element O. */
  if (_mcu_pka_is_identity(public_key)) {
    return MCU_ERROR_PKA_INVALID_KEY;
  }

  /* Check 2: Verify that the public key is on the curve. */
  mcu_err_t err = mcu_pka_point_check(curve_params, public_key->x, public_key->y);
  if (err != MCU_ERROR_OK) {
    /* Invalid public key or curve provided. */
    return err;
  }

  /* Check 3: Verify that n × QA = O (public key has correct order). */
  uint8_t result_x[MCU_PKA_MAX_KEY_SIZE];
  uint8_t result_y[MCU_PKA_MAX_KEY_SIZE];
  err = mcu_pka_ecc_mul(curve_params, curve_params->order, curve_params->order_size, public_key->x,
                        public_key->y, result_x, result_y);

  if (err != MCU_ERROR_UNKNOWN) {
    /* Expected the multiplication to fail (point at infinity). */
    return MCU_ERROR_PKA_INVALID_KEY;
  }

#ifdef IMAGE_TYPE_APPLICATION
  /* Lock access to the PKA module to the current thread. */
  rtos_mutex_lock(&state.access);
#endif

  /* Clear any previous flags */
  _mcu_pka_clear_flags();

  /* Set PKA mode to ECDSA verification (Section 53.3.5, 53.4.17). */
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  ASSERT(pka != NULL);
  LL_PKA_SetMode(pka, LL_PKA_MODE_ECDSA_VERIFICATION);

  /* Write input parameters to PKA RAM (Section 53.4.17). */
  const uint32_t order_bits = curve_params->order_size * 8;
  const uint32_t modulus_bits = curve_params->modulus_size * 8;

  /* Curve prime order n length (bits). */
  pka->RAM[PKA_ECDSA_VERIF_IN_ORDER_NB_BITS] = order_bits;

  /* Curve modulus p length 64 bits (in bits). */
  pka->RAM[PKA_ECDSA_VERIF_IN_MOD_NB_BITS] = modulus_bits;

  /* Curve coefficient a sign. */
  pka->RAM[PKA_ECDSA_VERIF_IN_A_COEFF_SIGN] = curve_params->coef_a_sign;

  /* Curve coefficient |a| */
  _mcu_pka_ram_write(PKA_ECDSA_VERIF_IN_A_COEFF, curve_params->coef_a, curve_params->modulus_size);

  /* Curve modulus value p */
  _mcu_pka_ram_write(PKA_ECDSA_VERIF_IN_MOD_GF, curve_params->modulus, curve_params->modulus_size);

  /* Curve base point G coordinate x */
  _mcu_pka_ram_write(PKA_ECDSA_VERIF_IN_INITIAL_POINT_X, curve_params->base_point_x,
                     curve_params->modulus_size);

  /* Curve base point G coordinate y */
  _mcu_pka_ram_write(PKA_ECDSA_VERIF_IN_INITIAL_POINT_Y, curve_params->base_point_y,
                     curve_params->modulus_size);

  /* Public-key curve point Q coordinate xQ */
  _mcu_pka_ram_write(PKA_ECDSA_VERIF_IN_PUBLIC_KEY_POINT_X, public_key->x, public_key->size);

  /* Public-key curve point Q coordinate yQ */
  _mcu_pka_ram_write(PKA_ECDSA_VERIF_IN_PUBLIC_KEY_POINT_Y, public_key->y, public_key->size);

  /* Signature part r */
  _mcu_pka_ram_write(PKA_ECDSA_VERIF_IN_SIGNATURE_R, signature->r, signature->size);

  /* Signature part s */
  _mcu_pka_ram_write(PKA_ECDSA_VERIF_IN_SIGNATURE_S, signature->s, signature->size);

  /* Hash of message z */
  _mcu_pka_ram_write_padded(PKA_ECDSA_VERIF_IN_HASH_E, hash, hash_size, curve_params->order_size);

  /* Curve prime order n */
  _mcu_pka_ram_write(PKA_ECDSA_VERIF_IN_ORDER_N, curve_params->order, curve_params->order_size);

  /* Start the operation */
  LL_PKA_Start(pka);

  /* Wait for completion */
  err = _mcu_pka_wait_for_completion();

  if (err != MCU_ERROR_OK) {
#ifdef IMAGE_TYPE_APPLICATION
    rtos_mutex_unlock(&state.access);
#endif
    return err;
  }

  /* Read result. */
  const uint32_t result = pka->RAM[PKA_ECDSA_VERIF_OUT_RESULT];

  /* Clear completion flag. */
  LL_PKA_ClearFlag_PROCEND(pka);

#ifdef IMAGE_TYPE_APPLICATION
  rtos_mutex_unlock(&state.access);
#endif

  if (result == MCU_PKA_POINT_ON_CURVE) {
    return MCU_ERROR_OK;
  }
  /* Signature verification failed. */
  return MCU_ERROR_PKA_FAIL;
}

mcu_err_t mcu_pka_point_check(const mcu_pka_curve_params_t* curve_params, const uint8_t* point_x,
                              const uint8_t* point_y) {
  if ((curve_params == NULL) || (point_x == NULL) || (point_y == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

#ifdef IMAGE_TYPE_APPLICATION
  /* Lock access to the PKA module to the current thread. */
  rtos_mutex_lock(&state.access);
#endif

  /* Clear any previous flags. */
  _mcu_pka_clear_flags();

  /* Set PKA mode to point check (Section 53.4.14). */
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  LL_PKA_SetMode(pka, LL_PKA_MODE_POINT_CHECK);

  /* Write input parameters to PKA RAM (Section 53.4.14). */
  const uint32_t modulus_bits = curve_params->modulus_size * 8;

  /* Modulus length. */
  pka->RAM[PKA_POINT_CHECK_IN_MOD_NB_BITS] = modulus_bits;

  /* Curve coefficient a sign. */
  pka->RAM[PKA_POINT_CHECK_IN_A_COEFF_SIGN] = curve_params->coef_a_sign;

  /* Curve coefficient |a| */
  _mcu_pka_ram_write(PKA_POINT_CHECK_IN_A_COEFF, curve_params->coef_a, curve_params->modulus_size);

  /* Curve coefficient b */
  _mcu_pka_ram_write(PKA_POINT_CHECK_IN_B_COEFF, curve_params->coef_b, curve_params->modulus_size);

  /* Curve modulus value p */
  _mcu_pka_ram_write(PKA_POINT_CHECK_IN_MOD_GF, curve_params->modulus, curve_params->modulus_size);

  /* Point P coordinate x */
  _mcu_pka_ram_write(PKA_POINT_CHECK_IN_INITIAL_POINT_X, point_x, curve_params->modulus_size);

  /* Point P coordinate y */
  _mcu_pka_ram_write(PKA_POINT_CHECK_IN_INITIAL_POINT_Y, point_y, curve_params->modulus_size);

  /* Start the operation. */
  LL_PKA_Start(pka);

  /* Wait for completion. */
  const mcu_err_t err = _mcu_pka_wait_for_completion();

  if (err != MCU_ERROR_OK) {
#ifdef IMAGE_TYPE_APPLICATION
    rtos_mutex_unlock(&state.access);
#endif
    return err;
  }

  /* Read result. */
  const uint32_t result = pka->RAM[PKA_POINT_CHECK_OUT_ERROR];

  /* Clear completion flag. */
  LL_PKA_ClearFlag_PROCEND(pka);

#ifdef IMAGE_TYPE_APPLICATION
  rtos_mutex_unlock(&state.access);
#endif

  if (result == MCU_PKA_POINT_ON_CURVE) {
    return MCU_ERROR_OK;
  }

  return MCU_ERROR_PARAMETER;
}

mcu_err_t mcu_pka_ecc_mul(const mcu_pka_curve_params_t* curve_params, const uint8_t* scalar,
                          size_t scalar_size, const uint8_t* point_x, const uint8_t* point_y,
                          uint8_t* result_x, uint8_t* result_y) {
  if ((curve_params == NULL) || (scalar == NULL) || (point_x == NULL) || (point_y == NULL) ||
      (result_x == NULL) || (result_y == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  if (scalar_size > curve_params->order_size) {
    return MCU_ERROR_PARAMETER;
  }

  mcu_err_t err = mcu_pka_point_check(curve_params, point_x, point_y);
  if (err != MCU_ERROR_OK) {
    /* PKA tampers if P(x,y) does not exist on curve and a scalar */
    /* multiplication is attempted, so we have to sanity check it first. */
    return err;
  }

#ifdef IMAGE_TYPE_APPLICATION
  /* Lock access to the PKA module to the current thread. */
  rtos_mutex_lock(&state.access);
#endif

  /* Clear any previous flags. */
  _mcu_pka_clear_flags();

  /* Set PKA mode to ECC scalar multiplication (Section 53.4.15). */
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  ASSERT(pka != NULL);
  LL_PKA_SetMode(pka, LL_PKA_MODE_ECC_MUL);

  /* Write input parameters to PKA RAM (Section 53.4.15). */
  const uint32_t scalar_bits = scalar_size * 8;
  const uint32_t modulus_bits = curve_params->modulus_size * 8;

  /* Curve prime order n length (bits). */
  pka->RAM[PKA_ECC_SCALAR_MUL_IN_EXP_NB_BITS] = scalar_bits;

  /* Curve modulus p length (bits). */
  pka->RAM[PKA_ECC_SCALAR_MUL_IN_OP_NB_BITS] = modulus_bits;

  /* Curve coefficient a sign. */
  pka->RAM[PKA_ECC_SCALAR_MUL_IN_A_COEFF_SIGN] = curve_params->coef_a_sign;

  /* Curve coefficient |a| */
  _mcu_pka_ram_write(PKA_ECC_SCALAR_MUL_IN_A_COEFF, curve_params->coef_a,
                     curve_params->modulus_size);

  /* Curve coefficient b */
  _mcu_pka_ram_write(PKA_ECC_SCALAR_MUL_IN_B_COEFF, curve_params->coef_b,
                     curve_params->modulus_size);

  /* Curve modulus value p */
  _mcu_pka_ram_write(PKA_ECC_SCALAR_MUL_IN_MOD_GF, curve_params->modulus,
                     curve_params->modulus_size);

  /* Scalar multiplier k */
  _mcu_pka_ram_write(PKA_ECC_SCALAR_MUL_IN_K, scalar, scalar_size);

  /* Point P coordinate xP */
  _mcu_pka_ram_write(PKA_ECC_SCALAR_MUL_IN_INITIAL_POINT_X, point_x, curve_params->modulus_size);

  /* Point P coordinate yP */
  _mcu_pka_ram_write(PKA_ECC_SCALAR_MUL_IN_INITIAL_POINT_Y, point_y, curve_params->modulus_size);

  /* Curve prime order n */
  _mcu_pka_ram_write(PKA_ECC_SCALAR_MUL_IN_N_PRIME_ORDER, curve_params->order,
                     curve_params->order_size);

  /* Start the operation. */
  LL_PKA_Start(pka);

  /* Wait for completion. */
  err = _mcu_pka_wait_for_completion();

  if (err != MCU_ERROR_OK) {
#ifdef IMAGE_TYPE_APPLICATION
    rtos_mutex_unlock(&state.access);
#endif
    /* If a PKA operation fails, a PKA tamper event is generated. */
    mcu_tamper_clear(MCU_TAMPER_FLAG_CRYPTO);
    return err;
  }

  /* Check for errors. */
  const uint32_t pka_error = pka->RAM[PKA_ECC_SCALAR_MUL_OUT_ERROR];
  if (pka_error != MCU_PKA_POINT_ON_CURVE) {
    /* Clear completion flag. */
    LL_PKA_ClearFlag_PROCEND(pka);
#ifdef IMAGE_TYPE_APPLICATION
    rtos_mutex_unlock(&state.access);
#endif
    /* If a PKA operation fails, a PKA tamper event is generated. */
    mcu_tamper_clear(MCU_TAMPER_FLAG_CRYPTO);
    return MCU_ERROR_UNKNOWN;
  }

  /* Result: k x P coordinate x' */
  err = _mcu_pka_ram_read(PKA_ECC_SCALAR_MUL_OUT_RESULT_X, result_x, curve_params->modulus_size);
  if (err == MCU_ERROR_OK) {
    /* Result: k x P coordinate y' */
    err = _mcu_pka_ram_read(PKA_ECC_SCALAR_MUL_OUT_RESULT_Y, result_y, curve_params->modulus_size);
  }

  /* Clear completion flag. */
  LL_PKA_ClearFlag_PROCEND(pka);

#ifdef IMAGE_TYPE_APPLICATION
  rtos_mutex_unlock(&state.access);
#endif

  return err;
}

static PKA_TypeDef* _mcu_pka_get_registers(void) {
  return PKA_NS;
}

static mcu_err_t _mcu_pka_ram_write(uint32_t offset, const uint8_t* data, size_t size) {
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  ASSERT(pka != NULL);

  if ((data == NULL) || (size == 0) || ((size & 0x03) != 0)) {
    return MCU_ERROR_PARAMETER;
  }

  /* PKA hardware expects little-endian word order: read buffer backwards. */
  size_t i;
  for (i = 0; i < (size / sizeof(uint32_t)); i++) {
    /* Read from end of buffer, pack bytes within each word as little-endian. */
    const size_t byte_offset = size - (i * 4u) - 4u;
    const uint32_t word =
      ((uint32_t)data[byte_offset + 3u] << 0) | ((uint32_t)data[byte_offset + 2u] << 8) |
      ((uint32_t)data[byte_offset + 1u] << 16) | ((uint32_t)data[byte_offset + 0u] << 24);
    pka->RAM[offset + i] = word;
  }

  /* An additional double-word of all 0s must follow each PKA operand. */
  pka->RAM[offset + i] = 0u;
  pka->RAM[offset + i + 1] = 0u;

  return MCU_ERROR_OK;
}

static mcu_err_t _mcu_pka_ram_write_padded(uint32_t offset, const uint8_t* data, size_t data_size,
                                           size_t pad_size) {
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  ASSERT(pka != NULL);

  if ((data == NULL) || (pad_size == 0) || ((pad_size & 0x03) != 0) || (pad_size < data_size)) {
    return MCU_ERROR_PARAMETER;
  }

  /* PKA hardware expects little-endian word order: read buffer backwards. */
  /* Data is right-aligned in the padded buffer (zero-padded on the left). */
  const size_t padding_bytes = pad_size - data_size;
  size_t i;
  for (i = 0; i < (pad_size / sizeof(uint32_t)); i++) {
    /* Read from end of buffer, pack bytes within each word as little-endian. */
    const size_t byte_offset = pad_size - (i * 4u) - 4u;
    uint32_t word = 0;

    /* Check each byte position in the padded buffer */
    if ((byte_offset + 0u) >= padding_bytes) {
      word |= (uint32_t)data[byte_offset + 0u - padding_bytes] << 24;
    }
    if ((byte_offset + 1u) >= padding_bytes) {
      word |= (uint32_t)data[byte_offset + 1u - padding_bytes] << 16;
    }
    if ((byte_offset + 2u) >= padding_bytes) {
      word |= (uint32_t)data[byte_offset + 2u - padding_bytes] << 8;
    }
    if ((byte_offset + 3u) >= padding_bytes) {
      word |= (uint32_t)data[byte_offset + 3u - padding_bytes] << 0;
    }

    pka->RAM[offset + i] = word;
  }

  /* An additional double-word of all 0s must follow each PKA operand. */
  pka->RAM[offset + i] = 0u;
  pka->RAM[offset + i + 1] = 0u;

  return MCU_ERROR_OK;
}

static mcu_err_t _mcu_pka_ram_read(uint32_t offset, uint8_t* data, size_t size) {
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  ASSERT(pka != NULL);

  if ((data == NULL) || (size == 0) || ((size & 0x03) != 0)) {
    return MCU_ERROR_PARAMETER;
  }

  const size_t num_words = size / sizeof(uint32_t);
  for (size_t i = 0; i < num_words; i++) {
    /* PKA stores in little-endian word order: LSW at lowest address. */
    const uint32_t word = pka->RAM[offset + i];

    /* Write to end of buffer backwards to convert to big-endian. */
    const size_t byte_offset = size - (i * 4u) - 4u;
    data[byte_offset + 3] = ((word >> 0) & 0xFF);
    data[byte_offset + 2] = ((word >> 8) & 0xFF);
    data[byte_offset + 1] = ((word >> 16) & 0xFF);
    data[byte_offset + 0] = ((word >> 24) & 0xFF);
  }

  return MCU_ERROR_OK;
}

static bool _mcu_pka_is_identity(const mcu_pka_public_key_t* public_key) {
  for (size_t i = 0; i < public_key->size; i++) {
    if (public_key->x[i] != 0 || public_key->y[i] != 0) {
      return false;
      break;
    }
  }
  return true;
}

static int _mcu_pka_compare(const uint8_t* a, const uint8_t* b, size_t size) {
  for (size_t i = 0; i < size; i++) {
    if (a[i] < b[i]) {
      return -1;
    }
    if (a[i] > b[i]) {
      return 1;
    }
  }
  return 0;
}

static bool _mcu_pka_is_zero(const uint8_t* data, size_t size) {
  for (size_t i = 0; i < size; i++) {
    if (data[i] != 0) {
      return false;
    }
  }
  return true;
}

static void _mcu_pka_clear_flags(void) {
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  ASSERT(pka != NULL);
  LL_PKA_ClearFlag_PROCEND(pka);
  LL_PKA_ClearFlag_ADDERR(pka);
  LL_PKA_ClearFlag_RAMERR(pka);
  LL_PKA_ClearFlag_OPERR(pka);
}

mcu_err_t mcu_pka_validate_scalar(const mcu_pka_curve_params_t* curve_params, const uint8_t* scalar,
                                  size_t scalar_size) {
  if ((curve_params == NULL) || (scalar == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  if (scalar_size != curve_params->order_size) {
    return MCU_ERROR_PARAMETER;
  }

  /* Check that scalar is not zero */
  if (_mcu_pka_is_zero(scalar, scalar_size)) {
    return MCU_ERROR_PARAMETER;
  }

  /* Check that scalar < order (must be in range [1, n-1]) */
  if (_mcu_pka_compare(scalar, curve_params->order, scalar_size) >= 0) {
    return MCU_ERROR_PARAMETER;
  }

  return MCU_ERROR_OK;
}

static mcu_err_t _mcu_pka_wait_for_completion(void) {
  PKA_TypeDef* pka = _mcu_pka_get_registers();
  ASSERT(pka != NULL);

#ifdef IMAGE_TYPE_APPLICATION
  const uint64_t start_time = rtos_thread_micros();
  const uint64_t timeout_us = MCU_PKA_TIMEOUT_MS * 1000ULL;

  while (!LL_PKA_IsActiveFlag_PROCEND(pka)) {
    if ((rtos_thread_micros() - start_time) >= timeout_us) {
      return MCU_ERROR_TIMEOUT;
    }

    /* Check for errors */
    if (LL_PKA_IsActiveFlag_ADDRERR(pka) || LL_PKA_IsActiveFlag_RAMERR(pka) ||
        LL_PKA_IsActiveFlag_OPERR(pka)) {
      return MCU_ERROR_PKA_FAIL;
    }

    rtos_thread_sleep(10);
  }
#else
#ifdef IMAGE_TYPE_BOOTLOADER
  /* In bootloader mode, just poll without timeout */
  while (!LL_PKA_IsActiveFlag_PROCEND(pka)) {
    /* Check for errors */
    if (LL_PKA_IsActiveFlag_ADDRERR(pka) || LL_PKA_IsActiveFlag_RAMERR(pka) ||
        LL_PKA_IsActiveFlag_OPERR(pka)) {
      return MCU_ERROR_PKA_FAIL;
    }
  }
#else
#error "Either bootloader or application must be defined."
#endif
#endif

  return MCU_ERROR_OK;
}
