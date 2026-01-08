/**
 * @file mcu_wdog.h
 *
 * @brief MCU Watchdog (WDOG)
 *
 * @details The watchdog is a hardware peripheral used to reset the device
 * should it get into a state where it is non-responsive. Unless the watchdog
 * is fed before the timeout interval has elapsed, the MCU will automatically
 * reset. Choice of watchdog implementation may differ based on MCU.
 *
 * @{
 */

#pragma once

/**
 * @brief Initializes the watchdog.
 *
 * @note After this method is invoked, the watchdog will need to be fed at the
 * specified interval to prevent a reset.
 */
void mcu_wdog_init(void);

/**
 * @brief Feeds the watchdog, preventing a MCU reset.
 */
void mcu_wdog_feed(void);

/** @} */
