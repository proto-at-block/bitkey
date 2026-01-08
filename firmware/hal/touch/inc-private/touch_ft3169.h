/**
 * @file touch_ft3169.h
 *
 * @brief Touch Implement Header
 *
 * @{
 */

#pragma once

#include <stddef.h>
#include <stdint.h>

// FT3169 Register Definitions
#define FT3169_REG_MODE_SWITCH    0x00  //<! Touch Mode Control
#define FT3169_REG_GESTURE        0x01  //<! Touch Gestures / feature byte
#define FT3169_REG_TD_STATUS      0x02  //<! Touch Data Status - number of touch points
#define FT3169_REG_TOUCH1_XH      0x03  //<! 1st touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH1_XL      0x04  //<! 1st touch: X position [7:0]
#define FT3169_REG_TOUCH1_YH      0x05  //<! 1st touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH1_YL      0x06  //<! 1st touch: Y position [7:0]
#define FT3169_REG_TOUCH1_WEIGHT  0x07  //<! 1st touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH1_MISC    0x08  //<! 1st touch: Touch Misc [3:0]
#define FT3169_REG_TOUCH2_XH      0x09  //<! 2nd touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH2_XL      0x0A  //<! 2nd touch: X position [7:0]
#define FT3169_REG_TOUCH2_YH      0x0B  //<! 2nd touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH2_YL      0x0C  //<! 2nd touch: Y position [7:0]
#define FT3169_REG_TOUCH2_WEIGHT  0x0D  //<! 2nd touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH2_MISC    0x0E  //<! 2nd touch: Touch Misc [3:0]
#define FT3169_REG_TOUCH3_XH      0x0F  //<! 3rd touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH3_XL      0x10  //<! 3rd touch: X position [7:0]
#define FT3169_REG_TOUCH3_YH      0x11  //<! 3rd touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH3_YL      0x12  //<! 3rd touch: Y position [7:0]
#define FT3169_REG_TOUCH3_WEIGHT  0x13  //<! 3rd touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH3_MISC    0x14  //<! 3rd touch: Touch Misc [3:0]
#define FT3169_REG_TOUCH4_XH      0x15  //<! 4th touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH4_XL      0x16  //<! 4th touch: X position [7:0]
#define FT3169_REG_TOUCH4_YH      0x17  //<! 4th touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH4_YL      0x18  //<! 4th touch: Y position [7:0]
#define FT3169_REG_TOUCH4_WEIGHT  0x19  //<! 4th touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH4_MISC    0x1A  //<! 4th touch: Touch Misc [3:0]
#define FT3169_REG_TOUCH5_XH      0x1B  //<! 5th touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH5_XL      0x1C  //<! 5th touch: X position [7:0]
#define FT3169_REG_TOUCH5_YH      0x1D  //<! 5th touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH5_YL      0x1E  //<! 5th touch: Y position [7:0]
#define FT3169_REG_TOUCH5_WEIGHT  0x1F  //<! 5th touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH5_MISC    0x20  //<! 5th touch: Touch Misc [3:0]
#define FT3169_REG_TOUCH6_XH      0x21  //<! 6th touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH6_XL      0x22  //<! 6th touch: X position [7:0]
#define FT3169_REG_TOUCH6_YH      0x23  //<! 6th touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH6_YL      0x24  //<! 6th touch: Y position [7:0]
#define FT3169_REG_TOUCH6_WEIGHT  0x25  //<! 6th touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH6_MISC    0x26  //<! 6th touch: Touch Misc [3:0]
#define FT3169_REG_TOUCH7_XH      0x27  //<! 7th touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH7_XL      0x28  //<! 7th touch: X position [7:0]
#define FT3169_REG_TOUCH7_YH      0x29  //<! 7th touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH7_YL      0x2A  //<! 7th touch: Y position [7:0]
#define FT3169_REG_TOUCH7_WEIGHT  0x2B  //<! 7th touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH7_MISC    0x2C  //<! 7th touch: Touch Misc [3:0]
#define FT3169_REG_TOUCH8_XH      0x2D  //<! 8th touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH8_XL      0x2E  //<! 8th touch: X position [7:0]
#define FT3169_REG_TOUCH8_YH      0x2F  //<! 8th touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH8_YL      0x30  //<! 8th touch: Y position [7:0]
#define FT3169_REG_TOUCH8_WEIGHT  0x31  //<! 8th touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH8_MISC    0x32  //<! 8th touch: Touch Misc [3:0]
#define FT3169_REG_TOUCH9_XH      0x33  //<! 9th touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH9_XL      0x34  //<! 9th touch: X position [7:0]
#define FT3169_REG_TOUCH9_YH      0x35  //<! 9th touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH9_YL      0x36  //<! 9th touch: Y position [7:0]
#define FT3169_REG_TOUCH9_WEIGHT  0x37  //<! 9th touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH9_MISC    0x38  //<! 9th touch: Touch Misc [3:0]
#define FT3169_REG_TOUCH10_XH     0x39  //<! 10th touch: Event flag [7:6], X position [11:8]
#define FT3169_REG_TOUCH10_XL     0x3A  //<! 10th touch: X position [7:0]
#define FT3169_REG_TOUCH10_YH     0x3B  //<! 10th touch: Touch ID [7:4], Y position [11:8]
#define FT3169_REG_TOUCH10_YL     0x3C  //<! 10th touch: Y position [7:0]
#define FT3169_REG_TOUCH10_WEIGHT 0x3D  //<! 10th touch: Touch Weight [7:0]
#define FT3169_REG_TOUCH10_MISC   0x3E  //<! 10th touch: Touch Misc [3:0]
#define FT3169_REG_FLOW_WORK_CNT  0x91  //<! Flow work counter for ESD monitoring
#define FT3169_REG_POWER_MODE     0xA5  //<! Power mode register
#define FT3169_REG_CHIP_ID        0xA3  //<! Chip ID high byte
#define FT3169_REG_CHIP_ID2       0x9F  //<! Chip ID low byte
#define FT3169_REG_FW_VER         0xA6  //<! Firmware version
#define FT3169_REG_GESTURE_EN     0xD0  //<! Gesture enable register
#define FT3169_REG_GESTURE_OUTPUT 0xD3  //<! Gesture information block
#define FT3169_REG_BOOT_START     0x55  //<! Boot command register

// FT3169 command/data values
#define FT3169_CMD_READ_ID        0x90
#define FT3169_BOOT_TRIGGER_VALUE 0xAA
#define FT3169_GESTURE_ENABLE     0x01
#define FT3169_POWER_MODE_SLEEP   0x03

// Chip IDs
#define FT3169_CHIP_IDH 0x52
#define FT3169_CHIP_IDL 0x60

// FT3169 Touch Modes
#define FT3169_MODE_WORKING     0x00  //<! Working Mode (bits [6:4] = 000b)
#define FT3169_MODE_TEST        0x04  //<! Test Mode (bits [6:4] = 100b)
#define FT3169_MAX_TOUCH_POINTS 2     //<! Controller supports up to 10 concurrent touches

// FT3169 Gestures
#define FT3169_GESTURE_UP       0x10  //<! Move Up
#define FT3169_GESTURE_RIGHT    0x14  //<! Move Right
#define FT3169_GESTURE_DOWN     0x18  //<! Move Down
#define FT3169_GESTURE_LEFT     0x1C  //<! Move Left
#define FT3169_GESTURE_ZOOM_IN  0x48  //<! Pinch Zoom-In
#define FT3169_GESTURE_ZOOM_OUT 0x49  //<! Pinch Zoom-Out
#define FT3169_GESTURE_NONE     0x00  //<! No Gesture

// Event flags (bits [7:6] of TOUCHn_XH)
#define FT3169_EVENT_PRESS_DOWN 0x00  //<! Finger press event.
#define FT3169_EVENT_LIFT_UP    0x01  //<! Finger lift event.
#define FT3169_EVENT_CONTACT    0x02  //<! Single touch event.
#define FT3169_EVENT_NO_EVENT   0x03  //<! No event.
#define FT3169_EVENT_INVALID    0x0F  //<! Invalid touch event.

// Timing constants from FT3169 datasheet
#define FT3169_RESET_PULSE_MS   10   //<! Reset pulse width (datasheet Fig 3-7)
#define FT3169_INIT_TIME_MS     200  //<! Time to start reporting after reset
#define FT3169_BOOT_ID_DELAY_MS 12   //<! Delay after boot start command

// Workmode register values
#define FT3169_REG_WORKMODE_FACTORY_VALUE 0x40  //<! Magic Value from Sample Code focaltech_core.c
#define FT3169_REG_WORKMODE_SCAN_VALUE    0xC0  //<! Magic Value from Sample Code focaltech_core.c

// I2C Configuration
#define FT3169_I2C_TIMEOUT_MS \
  100U  //<! Number of millisoconds to wait before an I2C timeout with the touch controller
#define FT3169_I2C_MAX_RETRIES \
  3U  //<! Number of times an I2C read opertion will be retried upon failure

// ESD Check Configuration
#define FT3169_ESD_CHECK_PERIOD_MS \
  1000U  //<! Period in milliseconds that the ESD check is performed
#define FT3169_ESD_HOLD_THRESHOLD \
  5U  //<! Number of times the ESD check has to fail in a row before the touch controller is reset

/**
 * @brief FT3169 touch point structure (packed to match register layout).
 *
 * @details This structure represents the touch data read from registers of a
 * single touch event.
 */
typedef struct __attribute__((packed)) {
  struct __attribute__((packed)) {
    uint8_t x_msb : 4;      /**< X position MSB [3:0] */
    uint8_t reserved : 2;   /**< Reserved [5:4] */
    uint8_t event_flag : 2; /**< Event flag [7:6] */
  } touch_xh;               /**< TOUCHn_XH (0x03): Event flag and X MSB */
  uint8_t touch_xl;         /**< TOUCHn_XL (0x04): X LSB [7:0] */
  struct __attribute__((packed)) {
    uint8_t y_msb : 4;    /**< Y position MSB [3:0] */
    uint8_t touch_id : 4; /**< Touch ID [7:4] */
  } touch_yh;             /**< TOUCHn_YH (0x05): Touch ID and Y MSB */
  uint8_t touch_yl;       /**< TOUCHn_YL (0x06): Y LSB [7:0] */
  uint8_t touch_weight;   /**< TOUCHn_WEIGHT (0x07): Touch Weight [7:0] */
  uint8_t touch_area : 3; /**< TOUCHn_AREA (0x08): Touch Area [3:0] */
  uint8_t reserved : 5;   /**< RFU */
} ft3169_touch_point_t;

_Static_assert((sizeof(ft3169_touch_point_t) == 6), "ft3169_touch_point_t size must be 6 bytes");

/**
 * @brief Touch data with variable read touch points.
 */
typedef struct __attribute__((packed)) {
  uint8_t gesture;              /**< GESTURE (0x01): Gesture ID */
  uint8_t num_points;           /**< TD_STATUS (0x02): Number of touch points */
  ft3169_touch_point_t touch[]; /**< TOUCH1...TOUCH10 */
} ft3169_touch_data_t;

/**
 * @brief FT3169 chip ID structure.
 *
 * @details This structure represents the two-byte chip ID.
 */
typedef struct __attribute__((packed)) {
  uint8_t id_high; /**< Chip ID high byte (0xA3): Expected 0x52 */
  uint8_t id_low;  /**< Chip ID low byte (0x9F): Expected 0x60 */
} ft3169_chip_id_t;

_Static_assert((sizeof(ft3169_chip_id_t) == 2), "ft3169_chip_id_t size must be 2 bytes");

/**
 * @brief Extracts an X coordinate from a touch point.
 *
 * @details `TOUCHn.XH.x_msb (MSB) | TOUCHn.XL (LSB)`.
 *
 * @param point  Pointer to a touch point (#ft316_touch_point_t).
 *
 * @return X coordinate of the touch point.
 */
#define FT3169_TOUCH_COORD_X(point) (((point)->touch_xh.x_msb << 8) | ((point)->touch_xl))

/**
 * @brief Extracts a Y coordinate from a touch point.
 *
 * @details `TOUCHn.YH.y_msb (MSB) | TOUCHn.YL (LSB)`.
 *
 * @param point  Pointer to a touch point (#ft316_touch_point_t).
 *
 * @return Y coordinate of the touch point.
 */
#define FT3169_TOUCH_COORD_Y(point) (((point)->touch_yh.y_msb << 8) | ((point)->touch_yl))

/**
 * @brief Returns the size of memory required to record touch data for the
 * specified number of touch points @p points.
 *
 * @param points Number of touch points.
 *
 * @return Number of bytes required for the touch data.
 */
#define FT3169_TOUCH_DATA_SIZE(points) \
  (sizeof(ft3169_touch_data_t) + ((size_t)(points)) * sizeof(ft3169_touch_point_t))

/**
 * @brief Allocates memory on the stack for recording touch data for the
 * specified number of touch points @p points.
 *
 * @details This method defines a buffer on the stack to hold the touch data
 * for the specified number of points @p points as `_<name>_buf` and provides
 * two additional variables: `<name>_size` indicating the byte size of the
 * buffer and `<name>` which is a `ft3169_touch_data` pointer using the
 * underlying buffer as memory where `<name>` is @p name.
 *
 * @param name    The name for the touch data pointer.
 * @param points  Number of touch points.
 */
#define FT3169_TOUCH_DATA(name, points)                      \
  const size_t name##_size = FT3169_TOUCH_DATA_SIZE(points); \
  uint8_t _##name##_buf[name##_size];                        \
  ft3169_touch_data_t* name = (ft3169_touch_data_t*)_##name##_buf;

/** @} */
