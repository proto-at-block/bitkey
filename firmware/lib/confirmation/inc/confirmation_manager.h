/**
 * @file confirmation_manager.h
 * @brief Centralized confirmation manager for W3 two-tap confirmation flows
 *
 * Provides a general confirmation system for operations that require user approval
 * on W3 hardware. Supports multiple operation types (FWUP, wipe, etc.) with handle
 * generation, validation, and timeout management.
 */

#pragma once

#include "ipc.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Handle size in bytes
#define CONFIRMATION_HANDLE_SIZE 32

// Maximum size for operation-specific data
#define MAX_OPERATION_DATA_SIZE 256

/**
 * Types of operations that can require confirmation
 */
typedef enum {
  CONFIRMATION_TYPE_NONE = 0,
  CONFIRMATION_TYPE_WIPE_STATE,
  CONFIRMATION_TYPE_FWUP_START,
  CONFIRMATION_TYPE_COUNT,  // Must be last
} confirmation_type_t;

/**
 * Result codes for confirmation operations
 */
typedef enum {
  CONFIRMATION_RESULT_SUCCESS = 0,
  CONFIRMATION_RESULT_ERROR,
  CONFIRMATION_RESULT_ALREADY_PENDING,
  CONFIRMATION_RESULT_INVALID_PARAMS,
  CONFIRMATION_RESULT_TIMEOUT,
  CONFIRMATION_RESULT_NOT_APPROVED,
  CONFIRMATION_RESULT_TYPE_MISMATCH,
} confirmation_result_t;

/**
 * @brief Initialize the confirmation manager
 *
 * Creates the mutex for thread-safe access. Must be called before any other
 * confirmation_manager functions.
 */
void confirmation_manager_init(void);

/**
 * @brief Create a new pending confirmation
 *
 * Generates secure random handles and stores the operation data for later execution.
 *
 * @param type Type of operation requiring confirmation
 * @param operation_data Pointer to operation-specific data to store
 * @param data_size Size of operation data in bytes
 * @param response_handle_out Output buffer for response handle
 * @param response_handle_size Size of response handle buffer (must be CONFIRMATION_HANDLE_SIZE)
 * @param confirmation_handle_out Output buffer for confirmation handle
 * @param confirmation_handle_size Size of confirmation handle buffer (must be
 * CONFIRMATION_HANDLE_SIZE)
 * @return Result code indicating success or specific error
 */
confirmation_result_t confirmation_manager_create(confirmation_type_t type,
                                                  const void* operation_data, size_t data_size,
                                                  uint8_t* response_handle_out,
                                                  size_t response_handle_size,
                                                  uint8_t* confirmation_handle_out,
                                                  size_t confirmation_handle_size);

/**
 * @brief Validate confirmation handles
 *
 * Checks if the provided handles match the stored pending confirmation.
 *
 * @param response_handle Response handle to validate
 * @param response_handle_size Size of response handle (must be CONFIRMATION_HANDLE_SIZE)
 * @param confirmation_handle Confirmation handle to validate
 * @param confirmation_handle_size Size of confirmation handle (must be CONFIRMATION_HANDLE_SIZE)
 * @return Result code indicating success or specific error
 */
confirmation_result_t confirmation_manager_validate(const uint8_t* response_handle,
                                                    size_t response_handle_size,
                                                    const uint8_t* confirmation_handle,
                                                    size_t confirmation_handle_size);

/**
 * @brief Get stored operation data
 *
 * Retrieves the operation data for the pending confirmation after validation.
 *
 * @param expected_type Expected confirmation type (for type safety)
 * @param data_out Output buffer to copy operation data
 * @param data_size_out Output size of operation data
 * @return true if operation data retrieved successfully, false otherwise
 */
bool confirmation_manager_get_operation_data(confirmation_type_t expected_type, void* data_out,
                                             size_t* data_size_out);

/**
 * @brief Approve the pending confirmation
 *
 * Called when user approves on device screen.
 * Must be called before get_confirmation_result will succeed.
 */
void confirmation_manager_approve(void);

/**
 * @brief Check if pending confirmation has been approved by user
 *
 * @return true if user has approved on device screen, false otherwise
 */
bool confirmation_manager_is_approved(void);

/**
 * @brief Clear pending confirmation
 *
 * Clears the current pending confirmation state.
 */
void confirmation_manager_clear(void);

/**
 * @brief Check if a confirmation is pending
 *
 * @return true if a confirmation is currently pending, false otherwise
 */
bool confirmation_manager_is_pending(void);

/**
 * @brief Get the type of pending confirmation
 *
 * @return The type of pending confirmation, or CONFIRMATION_TYPE_NONE if none pending
 */
confirmation_type_t confirmation_manager_get_type(void);

/**
 * @brief Handler function type for confirmation results
 *
 * @param message IPC message containing get_confirmation_result_cmd
 * @return true if handled successfully, false otherwise
 */
typedef bool (*confirmation_result_handler_t)(ipc_ref_t* message);

/**
 * @brief Register a handler for a specific confirmation type
 *
 * Each task registers its own handler for the confirmation types it owns.
 * When a get_confirmation_result_cmd arrives, the dispatcher routes it
 * to the appropriate handler based on the pending confirmation type.
 *
 * @param type The confirmation type to handle
 * @param handler The handler function to call
 */
void confirmation_manager_register_result_handler(confirmation_type_t type,
                                                  confirmation_result_handler_t handler);

/**
 * @brief Dispatch a confirmation result to the appropriate handler
 *
 * Routes the get_confirmation_result_cmd to the handler registered for
 * the currently pending confirmation type.
 *
 * @param message IPC message containing get_confirmation_result_cmd
 * @return true if a handler was found and dispatched, false if no handler registered
 */
bool confirmation_manager_dispatch_result(ipc_ref_t* message);
