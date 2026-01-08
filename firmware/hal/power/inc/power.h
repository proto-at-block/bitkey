/**
 * @file power.h
 *
 * @brief Power Driver
 *
 * @{
 */

#pragma once

#include "exti.h"
#include "mcu_gpio.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  /**
   * @brief Unused (error state).
   */
  POWER_CHARGER_MODE_INVALID = 0,

  /**
   * @brief Charger is off (device is discharging).
   */
  POWER_CHARGER_MODE_OFF,

  /**
   * @brief Pre-qualification mode.
   *
   * @details In this mode, the battery's condition is assessed before charging
   * is enabled.
   */
  POWER_CHARGER_MODE_PREQUAL,

  /**
   * @brief Constant Current mode.
   *
   * @details In this mode, the charger applies a constant current in order to
   * raise the battery's voltage until a set voltage threshold is reached.
   * After this mode completes, CV mode is entered.
   */
  POWER_CHARGER_MODE_CC,

  /**
   * @brief JEITA (Japan Electronics and Information Technology Industries
   * Association) compliant CC mode.
   */
  POWER_CHARGER_MODE_JEITA_CC,

  /**
   * @brief Constant voltage mode.
   *
   * @details In this mode, the battery has reached its target voltage and
   * the current gradually decreases as the battery gets closer to a full
   * charger.
   */
  POWER_CHARGER_MODE_CV,

  /**
   * @brief JEITA (Japan Electronics and Information Technology Industries
   * Association) compliant CV mode.
   */
  POWER_CHARGER_MODE_JEITA_CV,

  /**
   * @brief Top off mode.
   *
   * @details This is the final stage in the charging process where the current
   * decreases to a set level and charging only resumes once the voltage drops.
   */
  POWER_CHARGER_MODE_TOP_OFF,

  /**
   * @brief JEITA (Japan Electronics and Information Technology Industries
   * Association) compliant top off mode.
   */
  POWER_CHARGER_MODE_JEITA_TOP_OFF,

  /**
   * @brief Charging is complete.
   *
   * @details This is a stage in the CV stage of charging where the voltage is
   * constant and the current has decreased to a sufficiently low level to
   * indicate that charging is complete.
   */
  POWER_CHARGER_MODE_DONE,

  /**
   * @brief JEITA (Japan Electronics and Information Technology Industries
   * Association) compliant done mode.
   */
  POWER_CHARGER_MODE_JEITA_DONE,

  /**
   * @brief Prequalification failed to complete.
   *
   * @details In this state, the battery cannot be used, as its condition
   * could not be assessed.
   */
  POWER_CHARGER_MODE_PREQUAL_TIMEOUT,

  /**
   * @brief Timeout during fast charging.
   */
  POWER_CHARGER_MODE_FAST_CHARGE_TIMEOUT,

  /**
   * @brief Battery temperature fault.
   */
  POWER_CHARGER_MODE_TEMP_FAULT,
} power_charger_mode_t;

typedef enum {
  /**
   * @brief Unused (default).
   */
  POWER_CHARGER_NONE = 0,

  /**
   * @brief Analog devices MAX77734.
   */
  POWER_CHARGER_MAX77734,
} power_charger_id;

/**
 * @brief LDO configuration for the MAX77734 power management IC.
 */
typedef struct {
  /**
   * @brief LDO enable mode.
   *
   * @details Controls when the LDO is enabled:
   * - MAX77734_LDO_EN_AUTO (0b00): LDO enables when nENLDO asserts or CHGIN is valid
   * - MAX77734_LDO_EN_FORCED_ON (0b01): LDO is forced ON
   */
  uint8_t ldo_en;

  /**
   * @brief LDO power mode.
   *
   * @details Controls the LDO operating mode:
   * - MAX77734_LDO_PM_LOW_POWER (0b00): Forced low-power mode
   * - MAX77734_LDO_PM_NORMAL (0b01): Normal mode
   */
  uint8_t ldo_pm;
} power_ldo_config_t;

typedef struct {
  mcu_gpio_config_t power_retain;
  mcu_gpio_config_t five_volt_boost;
  exti_config_t cap_touch_detect;
  struct {
    mcu_gpio_config_t gpio;
    uint32_t hold_ms;
  } cap_touch_cal;
  exti_config_t charger_irq;
  exti_config_t usb_detect_irq;
  exti_config_t fuel_gauge_irq;
  power_ldo_config_t ldo;
} power_config_t;

void power_init(void);
void power_set_retain(const bool enabled);
bool power_validate_fuel_gauge(void);
void power_get_battery(uint32_t* soc_millipercent, uint32_t* vcell_mv, int32_t* avg_current_ma,
                       uint32_t* cycles);
void power_fast_charge(void);
bool power_set_battery_variant(const uint32_t variant);
void power_retain_charged_indicator(void);

/**
 * @brief Enables the charger.
 */
void power_enable_charging(void);

/**
 * @brief Disables the charger.
 */
void power_disable_charging(void);

/**
 * @brief Enables or disables USB suspend mode.
 *
 * @details When enabled, the CHGIN to SYS power path is disconnected,
 * forcing the system to run from battery even when USB is connected.
 * This is useful for discharge testing while keeping the cable plugged in.
 *
 * @param[in] enabled `true` to enable USB suspend (disconnect CHGIN from SYS),
 *                    `false` to disable (normal operation).
 */
void power_usb_suspend(bool enabled);

/**
 * @brief Returns `true` if the device is charging.
 *
 * @return `true` if charging, otherwise `false` if discharging.
 */
bool power_is_charging(void);

/**
 * @brief Returns `true` if a charger is connected (USB cable plugged in).
 *
 * @details This indicates whether a valid power input is present, regardless
 * of whether charging is currently enabled. Use this to detect cable
 * disconnect even when charging has been programmatically disabled.
 *
 * @return `true` if charger input is valid, otherwise `false`.
 */
bool power_is_plugged_in(void);

/**
 * @brief Retrieves an enum value uniquely identifying the charger hardware.
 *
 * @return Charger ID.
 */
power_charger_id power_get_charger_id(void);

/**
 * @brief Retrieves the current active mode of the charger.
 *
 * @return Charger mode.
 */
power_charger_mode_t power_get_charger_mode(void);

/**
 * @brief Returns the number of registers the charger has.
 *
 * @return Register count.
 */
uint8_t power_get_charger_register_count(void);

/**
 * @brief Reads a register from the charger.
 *
 * @param[in]  index       The index in the register list for the register to read.
 * @param[out] offset_out  The physical address of the register.
 * @param[out] value_out   The read value of the register.
 */
void power_read_charger_register(uint8_t index, uint8_t* offset_out, uint8_t* value_out);

/**
 * @brief Transitions the LDO to low-power mode.
 *
 * @details Call this as part of sleep preparation to reduce quiescent current
 * from 12µA to 1.5µA. The LDO current limit drops from 150mA to 5mA.
 *
 * On wake/boot, the LDO is automatically restored to normal mode via the
 * platform config (ldo_pm = normal).
 */
void power_set_ldo_low_power_mode(void);

/** @} */
