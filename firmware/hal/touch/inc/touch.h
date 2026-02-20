/**
 * @file touch.h
 *
 * @brief Touch HAL
 *
 * @{
 */

#pragma once

#include "mcu_gpio.h"
#include "mcu_i2c.h"

#include <stdint.h>

/**
 * @brief Indefinite timeout.
 */
#define TOUCH_PEND_1000_MS 1000u

/**
 * @brief Touch controller interfaces.
 */
typedef enum {
  TOUCH_INTERFACE_NONE = 0, /**< Unused. */
  TOUCH_INTERFACE_I2C,      /**< I2C */
} touch_interface_t;

/**
 * @brief Touch I2C configuration.
 *
 * Configuration structure for I2C-based touch controllers.
 */
typedef struct {
  mcu_i2c_bus_config_t config; /**< I2C bus configuration. */
  mcu_i2c_device_t device;     /**< I2C device handle */
} touch_i2c_config_t;

/**
 * @brief Touch controller configuration.
 *
 * Configuration structure for the touch controller, including interface settings.
 */
typedef struct {
  struct {
    union {
      touch_i2c_config_t i2c; /**< I2C interface configuration. */
    };
  } interface;                      /**< Interface configuration union. */
  touch_interface_t interface_type; /**< Touch controller interface type. */
  struct {
    mcu_gpio_config_t* interrupt; /**< Interrupt GPIO (Controller -> Host). */
    mcu_gpio_config_t* reset;     /**< Reset GPIO (Host -> Controller). */
    bool reset_active_high;       /**< If true, reset is active-high (PDVT). */
    struct {
      mcu_gpio_config_t* pwr_1v8_en;  /**< 1v8 power rail. */
      mcu_gpio_config_t* pwr_avdd_en; /**< AVDD power rail. */
    } pwr;
  } gpio;
} touch_config_t;

/**
 * @brief Touch coordinate structure.
 *
 * Represents a 2D coordinate on the touch surface.
 */
typedef struct {
  uint16_t x; /**< X coordinate. */
  uint16_t y; /**< Y coordinate. */
} touch_coord_t;

/**
 * @brief Touch event types.
 *
 * Enumeration of possible touch events that can be reported.
 */
typedef enum {
  TOUCH_EVENT_NONE,       /**< No touch event. */
  TOUCH_EVENT_TOUCH_DOWN, /**< Touch down event (finger pressed). */
  TOUCH_EVENT_TOUCH_UP,   /**< Touch up event (finger released). */
  TOUCH_EVENT_CONTACT,    /**< Sustained contact event (finger held on surface). */
} touch_event_type_t;

/**
 * @brief Touch event instance.
 */
typedef struct {
  /**
   * @brief Type of touch event.
   */
  touch_event_type_t event_type;

  union {
    /**
     * @brief Touch coordinates.
     */
    touch_coord_t coord;
  };

  /**
   * @brief Timestamp when the event was recorded (in milliseconds).
   */
  uint32_t timestamp_ms;
} touch_event_t;

/**
 * @brief Initialize the touch controller.
 *
 * @details Initializes the touch hardware with the provided configuration
 * and sets up the event callback function to be invoked on touch events.
 *
 * @param[in] config          Pointer to touch configuration structure.
 */
void touch_init(const touch_config_t* config);

/**
 * @brief Enable the touch controller.
 *
 * @details Enables the touch controller to start detecting touch events.
 *
 * @return `true` if touch enabled, otherwise `false`.
 */
bool touch_enable(void);

/**
 * @brief Disable the touch controller.
 *
 * @details Disables the touch controller to stop detecting touch events.
 *
 * @return `true` if touch disabled, otherwise `false`.
 */
bool touch_disable(void);

/**
 * @brief Get current touch coordinates.
 *
 * @details Retrieves the current touch coordinates if a touch is active.
 *
 * @param[out] event_type  The touch event type.
 * @param[out] coord       Pointer to touch coordinate structure to be filled.
 *
 * @return true if touch coordinates are available, false otherwise.
 */
bool touch_get_coordinates(touch_event_t* event_type);

/**
 * @brief Blocks waiting for a touch event to occur.
 *
 * @param[out] event       Pointer to the event to populate.
 * @param[in]  timeout_ms  Duration (in milliseconds) to wait for an event.
 *
 * @return `true` if an event occurred before the timeout, otherwise `false`.
 */
bool touch_pend_event(touch_event_t* event, uint32_t timeout_ms);

/**
 * @brief Sets the latest touch event information.
 *
 * @details Stores a touch event as the most recent event. This function is
 * thread-safe and uses critical section protection. The timestamp field is
 * automatically set to the current system time when the event is stored.
 *
 * @param[in] event  Pointer to touch event structure to cache.
 */
void touch_set_latest_event(const touch_event_t* event);

/**
 * @brief Retrieves the latest touch event information.
 *
 * @details Returns the most recent touch event captured by #touch_pend_event().
 * The event data includes the event type and coordinates. This function is
 * thread-safe and uses critical section protection.
 *
 * @param[out] event  Pointer to touch event structure to be filled with the
 *                    latest cached event information.
 */
void touch_get_latest_event(touch_event_t* event);

/**
 * @brief Pushes a touch event to the circular buffer.
 *
 * @details Stores the touch event in a circular buffer for later retrieval.
 * If the buffer is full, the oldest event is overwritten to ensure the most
 * recent touch data is always available. This function is thread-safe.
 *
 * @param[in] event  Pointer to touch event structure to store.
 */
void touch_push_event(const touch_event_t* event);

/**
 * @brief Pops the oldest touch event from the circular buffer.
 *
 * @details Retrieves and removes the oldest touch event from the buffer.
 * Events are returned in FIFO order (oldest first). This function is
 * thread-safe.
 *
 * @param[out] event  Pointer to touch event structure to fill.
 * @return `true` if an event was retrieved, `false` if buffer was empty.
 */
bool touch_pop_event(touch_event_t* event);

/**
 * @brief Returns the number of events currently in the buffer.
 *
 * @return Number of buffered touch events.
 */
uint8_t touch_event_buffer_count(void);

/**
 * @brief Puts the touch controller in low power state
 *
 * @details Getting out of monitor mode is either done by hardware reset, or,
 *          if configured, a gesture can wake the touch controller
 *          (such as double tap)
 *
 * @return `true` if entered monitor mode successfully.
 */
bool touch_enter_monitor_mode(void);

/**
 * @brief Exits the touch controller from low power monitor mode
 *
 * @details Restores the touch controller to normal working mode.
 *          Should be called on boot to ensure correct state.
 *
 * @return `true` if exited monitor mode successfully.
 */
bool touch_exit_monitor_mode(void);

/**
 * @brief Process pending ESD check if needed.
 *
 * @details This function should be called periodically from your application task
 * (e.g., in your main touch handling loop). It checks if the ESD timer has set
 * a pending flag, and if so, performs the actual ESD check with I2C operations.
 *
 * This design keeps I2C operations out of the timer service task context,
 * preventing stack overflow and avoiding blocking other timers.
 *
 * @note This is safe to call frequently - it only does work when needed.
 */
void touch_process_esd_check(void);

/**
 * @brief Perform touch controller firmware upgrade if needed.
 *
 * @details Checks the firmware version in the touch controller against the
 * embedded firmware image. If the versions differ or the controller firmware
 * is invalid, performs an upgrade.
 *
 * @note This function may take several seconds to complete.
 * @note The touch controller will be reset after upgrade.
 *
 * @return `true` if upgrade succeeded or was not needed, `false` on failure.
 */
bool touch_fwup_upgrade(void);

/**
 * @brief Force touch controller firmware upgrade regardless of version.
 *
 * @details Performs a firmware upgrade regardless of the current version
 * in the touch controller. Use this when you want to ensure the firmware
 * is re-flashed.
 *
 * @note This function may take several seconds to complete.
 * @note The touch controller will be reset after upgrade.
 *
 * @return `true` if upgrade succeeded, `false` on failure.
 */
bool touch_fwup_force_upgrade(void);

/**
 * @brief Get the current firmware version from the touch controller.
 *
 * @return Firmware version byte, or 0 on error.
 */
uint8_t touch_fwup_get_version(void);

/**
 * @brief Get the firmware version embedded in the firmware image.
 *
 * @return Embedded firmware version byte.
 */
uint8_t touch_fwup_get_embedded_version(void);

/**
 * @brief Request a firmware upgrade to be performed by the touch task.
 *
 * @details This function sets a flag that will be checked by the touch task.
 * The actual upgrade is performed asynchronously in the touch task context,
 * avoiding message timeout issues when called from other tasks.
 *
 * @param[in] force  If true, force upgrade regardless of version. If false,
 *                   only upgrade if versions differ.
 */
void touch_fwup_request_upgrade(bool force);

/**
 * @brief Process any pending firmware upgrade request.
 *
 * @details This function should be called periodically from the touch task.
 * If an upgrade was requested via touch_fwup_request_upgrade(), this function
 * will perform the upgrade.
 *
 * @return true if an upgrade was performed (success or failure), false if
 *         no upgrade was pending.
 */
bool touch_fwup_process_pending(void);

/**
 * @brief Perform a hardware reset of the touch controller.
 *
 * @details Toggles the reset GPIO line to force a hardware reset
 * of the touch controller. Use this after firmware upgrade or
 * when the controller is unresponsive.
 *
 * @return `true` if reset was performed, `false` if reset GPIO not configured.
 */
bool touch_hw_reset(void);

/**
 * @brief Set the firmware upgrade in progress flag.
 *
 * @details When set to true, certain operations like ESD check will be skipped
 * to avoid interfering with the firmware upgrade process.
 *
 * @param[in] in_progress  True if firmware upgrade is in progress.
 */
void touch_set_fwup_in_progress(bool in_progress);

/**
 * @brief Get the firmware upgrade in progress flag.
 *
 * @return True if firmware upgrade is in progress.
 */
bool touch_get_fwup_in_progress(void);

/** @} */
