/**
 * @file mfgtest_task_impl.h
 *
 * @brief Manufacturing Task Implementation Specific Header
 *
 * @{
 */

#pragma once

#include "bio.h"
#include "ipc_messages_mfgtest_port.h"
#include "rtos.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Manufacturing task thread state.
 */
typedef struct {
  /**
   * @brief Queue used for receiving IPC messages.
   */
  rtos_queue_t* queue;

  /**
   * @brief Result from the biometrics self test.
   */
  bio_selftest_result_t bio_selftest_result;

  /**
   * @brief Pointer to a buffer of bytes comprising the fingerprint image
   * capture.
   */
  uint8_t* image;

  /**
   * @brief Size of `image` in bytes.
   */
  uint32_t image_size;

  /**
   * @brief Results from the last run of the run-in app (if any).
   */
  mfgtest_runin_complete_internal_t runin_results;

  /**
   * @brief `true` if `runin_results` is valid.
   */
  bool runin_has_results;

  /**
   * @brief Results from the last run touch test (if any).
   */
  mfgtest_touch_test_result_internal_t touch_test_result;

  /**
   * @brief `true` if `touch_test_result` is valid.
   */
  bool touch_test_has_result;
} mfgtest_priv_t;

/** @} */
