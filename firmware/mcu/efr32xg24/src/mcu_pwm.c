#include "mcu_pwm.h"

#include "printf.h"

#include "em_bus.h"
#include "em_cmu.h"
#include "em_gpio.h"
#include "em_timer.h"

// clang-format off
/***************************************************************************//**
 * @file
 * @brief PWM Driver
 *******************************************************************************
 * # License
 * <b>Copyright 2020 Silicon Laboratories Inc. www.silabs.com</b>
 *******************************************************************************
 *
 * The licensor of this software is Silicon Laboratories Inc. Your use of this
 * software is governed by the terms of Silicon Labs Master Software License
 * Agreement (MSLA) available at
 * www.silabs.com/about-us/legal/master-software-license-agreement. This
 * software is distributed to you in Source Code format and is governed by the
 * sections of the MSLA applicable to Source Code.
 *
 ******************************************************************************/
// clang-format on

static CMU_Clock_TypeDef get_timer_clock(TIMER_TypeDef* timer) {
#if defined(_CMU_HFCLKSEL_MASK) || defined(_CMU_CMD_HFCLKSEL_MASK)
  CMU_Clock_TypeDef timer_clock = cmuClock_HF;
#elif defined(_CMU_SYSCLKCTRL_MASK)
  CMU_Clock_TypeDef timer_clock = cmuClock_SYSCLK;
#else
#error "Unknown root of clock tree"
#endif

  switch ((uint32_t)timer) {
#if defined(TIMER0_BASE)
    case TIMER0_BASE:
      timer_clock = cmuClock_TIMER0;
      break;
#endif
#if defined(TIMER1_BASE)
    case TIMER1_BASE:
      timer_clock = cmuClock_TIMER1;
      break;
#endif
#if defined(TIMER2_BASE)
    case TIMER2_BASE:
      timer_clock = cmuClock_TIMER2;
      break;
#endif
#if defined(TIMER3_BASE)
    case TIMER3_BASE:
      timer_clock = cmuClock_TIMER3;
      break;
#endif
#if defined(TIMER4_BASE)
    case TIMER4_BASE:
      timer_clock = cmuClock_TIMER4;
      break;
#endif
#if defined(TIMER5_BASE)
    case TIMER5_BASE:
      timer_clock = cmuClock_TIMER5;
      break;
#endif
#if defined(TIMER6_BASE)
    case TIMER6_BASE:
      timer_clock = cmuClock_TIMER6;
      break;
#endif
#if defined(WTIMER0_BASE)
    case WTIMER0_BASE:
      timer_clock = cmuClock_WTIMER0;
      break;
#endif
#if defined(WTIMER1_BASE)
    case WTIMER1_BASE:
      timer_clock = cmuClock_WTIMER1;
      break;
#endif
#if defined(WTIMER2_BASE)
    case WTIMER2_BASE:
      timer_clock = cmuClock_WTIMER2;
      break;
#endif
#if defined(WTIMER3_BASE)
    case WTIMER3_BASE:
      timer_clock = cmuClock_WTIMER3;
      break;
#endif
    default:
      EFM_ASSERT(0);
      break;
  }
  return timer_clock;
}

void mcu_pwm_init(mcu_pwm_t* pwm) {
  CMU_Clock_TypeDef timer_clock = get_timer_clock(pwm->timer);
  CMU_ClockEnable(timer_clock, true);

  // Set PWM pin as output
  CMU_ClockEnable(cmuClock_GPIO, true);
  mcu_gpio_configure(&pwm->gpio, pwm->polarity);

  // Set CC channel parameters
  TIMER_InitCC_TypeDef channel_init = TIMER_INITCC_DEFAULT;
  channel_init.mode = timerCCModePWM;
  channel_init.cmoa = timerOutputActionToggle;
  channel_init.edge = timerEdgeBoth;
  channel_init.outInvert = (pwm->polarity == PWM_ACTIVE_LOW);
  TIMER_InitCC(pwm->timer, pwm->timer_channel, &channel_init);

  // Configure CC channel pinout
#if defined(_TIMER_ROUTE_MASK)
  BUS_RegMaskedWrite(&pwm->timer->ROUTE, _TIMER_ROUTE_LOCATION_MASK,
                     pwm->location << _TIMER_ROUTE_LOCATION_SHIFT);
#elif defined(_TIMER_ROUTELOC0_MASK)
  BUS_RegMaskedWrite(&pwm->timer->ROUTELOC0, _TIMER_ROUTELOC0_CC0LOC_MASK << (pwm->channel * 8U),
                     pwm->location << (pwm->timer_channel * 8U));
#elif defined(_GPIO_TIMER_ROUTEEN_MASK)
  volatile uint32_t* route_register = &GPIO->TIMERROUTE[TIMER_NUM(pwm->timer)].CC0ROUTE;
  route_register += pwm->timer_channel;
  *route_register = (pwm->gpio.port << _GPIO_TIMER_CC0ROUTE_PORT_SHIFT) |
                    (pwm->gpio.pin << _GPIO_TIMER_CC0ROUTE_PIN_SHIFT);
#else
#error "Unknown route setting"
#endif

  // Configure TIMER frequency
  uint32_t top = (CMU_ClockFreqGet(timer_clock) / (pwm->frequency)) - 1U;
  TIMER_TopSet(pwm->timer, top);

  // Set initial duty cycle to 0%
  TIMER_CompareSet(pwm->timer, pwm->timer_channel, 0U);

  // Initialize TIMER
  TIMER_Init_TypeDef timer_init = TIMER_INIT_DEFAULT;
  TIMER_Init(pwm->timer, &timer_init);
}

void mcu_pwm_deinit(mcu_pwm_t* pwm) {
  // Reset TIMER routes
  mcu_pwm_stop(pwm);

#if defined(_TIMER_ROUTE_MASK)
  BUS_RegMaskedClear(&pwm->timer->ROUTE, _TIMER_ROUTE_LOCATION_MASK);
#elif defined(_TIMER_ROUTELOC0_MASK)
  BUS_RegMaskedClear(&pwm->timer->ROUTELOC0, _TIMER_ROUTELOC0_CC0LOC_MASK << (pwm->channel * 8));
#elif defined(_GPIO_TIMER_ROUTEEN_MASK)
  volatile uint32_t* route_register = &GPIO->TIMERROUTE[TIMER_NUM(pwm->timer)].CC0ROUTE;
  route_register += pwm->timer_channel;
  *route_register = 0;
#else
#error "Unknown route setting"
#endif

  // Reset TIMER
  TIMER_Reset(pwm->timer);

  // Reset GPIO
  GPIO_PinModeSet((GPIO_Port_TypeDef)pwm->gpio.port, pwm->gpio.pin, gpioModeDisabled, 0);

  CMU_Clock_TypeDef timer_clock = get_timer_clock(pwm->timer);
  CMU_ClockEnable(timer_clock, false);
}

void mcu_pwm_start(mcu_pwm_t* pwm) {
  // Enable PWM output
#if defined(_TIMER_ROUTE_MASK)
  BUS_RegMaskedSet(&pwm->timer->ROUTE, 1 << (pwm->channel + _TIMER_ROUTE_CC0PEN_SHIFT));
#elif defined(_TIMER_ROUTELOC0_MASK)
  BUS_RegMaskedSet(&pwm->timer->ROUTEPEN, 1 << (pwm->channel + _TIMER_ROUTEPEN_CC0PEN_SHIFT));
#elif defined(_GPIO_TIMER_ROUTEEN_MASK)
  GPIO->TIMERROUTE_SET[TIMER_NUM(pwm->timer)].ROUTEEN =
    1 << (pwm->timer_channel + _GPIO_TIMER_ROUTEEN_CC0PEN_SHIFT);
#else
#error "Unknown route setting"
#endif
}

void mcu_pwm_stop(mcu_pwm_t* pwm) {
  // Disable PWM output
#if defined(_TIMER_ROUTE_MASK)
  BUS_RegMaskedClear(&pwm->timer->ROUTE, 1 << (pwm->channel + _TIMER_ROUTE_CC0PEN_SHIFT));
#elif defined(_TIMER_ROUTELOC0_MASK)
  BUS_RegMaskedClear(&pwm->timer->ROUTEPEN, 1 << (pwm->channel + _TIMER_ROUTEPEN_CC0PEN_SHIFT));
#elif defined(_GPIO_TIMER_ROUTEEN_MASK)
  int i = TIMER_NUM(pwm->timer);
  if (i == -1) {
    return;
  }

  GPIO->TIMERROUTE_CLR[TIMER_NUM(pwm->timer)].ROUTEEN =
    1 << (pwm->timer_channel + _GPIO_TIMER_ROUTEEN_CC0PEN_SHIFT);
#else
#error "Unknown route setting"
#endif

  // Keep timer running in case other channels are in use
}

void mcu_pwm_set_duty_cycle(mcu_pwm_t* pwm, uint16_t duty) {
  const uint32_t top = TIMER_TopGet(pwm->timer);
  const uint16_t compare = ((uint32_t)duty * top) / UINT16_MAX;

  // Set compare value
  TIMER_CompareBufSet(pwm->timer, pwm->timer_channel, compare);
}

uint16_t mcu_pwm_get_duty_cycle(mcu_pwm_t* pwm) {
  const uint32_t top = TIMER_TopGet(pwm->timer);
  const uint32_t compare = TIMER_CaptureGet(pwm->timer, pwm->timer_channel);
  const uint32_t duty = (compare * UINT16_MAX) / top;

  return (uint16_t)duty;
}
