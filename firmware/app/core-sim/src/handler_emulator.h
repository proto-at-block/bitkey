/**
 * @file handler_emulator.h
 * @brief Emulator introspection and control for core-sim
 */

#ifndef HANDLER_EMULATOR_H
#define HANDLER_EMULATOR_H

#include <stdint.h>

// UI command codes (must match StdioNfcSession.kt)
#define UI_CMD_GET_CURRENT_SCREEN    0x03
#define UI_CMD_GET_CURRENT_FLOW      0x04
#define UI_CMD_GET_BATTERY_STATE     0x05
#define UI_CMD_SET_AUTHENTICATED     0x06
#define UI_CMD_RESET_EMULATOR        0x07
#define UI_CMD_ACTION_APPROVE        0x10
#define UI_CMD_ACTION_CANCEL         0x11
#define UI_CMD_ACTION_BACK           0x12
#define UI_CMD_START_ENROLLMENT      0x13
#define UI_CMD_TICK                  0x14
#define UI_CMD_ACTION_EXIT           0x15
#define UI_CMD_SET_AUTH_MODE         0x1E
#define UI_CMD_GET_AUTH_MODE         0x1F
#define UI_CMD_SIMULATE_FINGER_TOUCH 0x20
#define UI_CMD_GET_UNLOCK_STATE      0x21
#define UI_CMD_SET_UNLOCK_SECRET     0x22
#define UI_CMD_ADVANCE_TIME          0x23

void stdio_emulator_init(void);

void stdio_handle_emulator_command(uint8_t cmd, const uint8_t* payload, uint32_t payload_len,
                                   uint8_t* rsp, uint32_t* rsp_len);

#endif /* HANDLER_EMULATOR_H */
