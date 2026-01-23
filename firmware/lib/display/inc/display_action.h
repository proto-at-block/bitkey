/**
 * @file display_action.h
 *
 * @brief API for sending user actions from UXC to Core.
 *
 * This module provides a simple interface for screens to communicate flow-level
 * decisions (approve, cancel, back, menu navigation, etc.) from the UXC display
 * microcontroller to the Core processor.
 *
 * @{
 */

#pragma once

#include "display.pb.h"

#include <stdint.h>

/**
 * @brief Send a user action from UXC to Core.
 *
 * This function queues an action message to be sent to the Core processor.
 * Actions represent flow-level decisions like approving a transaction, cancelling
 * an operation, navigating back, etc.
 *
 * @param action The action type to send
 * @param data Optional action-specific data (e.g., fingerprint index 0-2, menu item, brightness
 * value)
 */
void display_send_action(fwpb_display_action_display_action_type action, uint32_t data);

/** @} */
