#pragma once

/*
 * SystemView trace shim for the STM32U5 MCU sources.
 *
 * Behaviour summary:
 *
 *   USE_SYSVIEW=1 + IMAGE_TYPE_APPLICATION=1
 *     Pulls in the real SEGGER SystemView and SYSVIEW_Peripherals headers,
 *     so the SEGGER_SYSVIEW_Record*() and SYSVIEW_PERIPHERALS_* identifiers
 *     resolve to the actual recording macros + IDs.
 *
 *   USE_SYSVIEW=0 (or undefined)
 *     SystemView is not built into the firmware at all. The MCU sources
 *     still call SEGGER_SYSVIEW_Record*() and reference SYSVIEW_PERIPHERALS_*
 *     IDs, so we provide local no-op macros so the code compiles cleanly
 *     without dragging in any sysview headers or symbols.
 *
 *   USE_SYSVIEW=1 + bootloader image (IMAGE_TYPE_APPLICATION undefined or 0)
 *     The MCU sources are shared with the bootloader, but SystemView is only
 *     linked into the application image. Use the same no-op fallbacks as the
 *     USE_SYSVIEW=0 case so the bootloader builds without SystemView symbols.
 */

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1) && defined(IMAGE_TYPE_APPLICATION) && \
  (IMAGE_TYPE_APPLICATION == 1)

#include "FreeRTOS.h"
#include "SEGGER_SYSVIEW.h"
#include "SYSVIEW_Peripherals.h"

/*
 * IRQ enter/exit trace helpers.
 *
 * On Cortex-M there is no shared IRQ dispatcher: the vector table jumps
 * directly to each *_IRQHandler symbol. To get every peripheral ISR into
 * the SystemView timeline we have to bracket each handler body with one
 * line at the top and one at the bottom. SEGGER_SYSVIEW_RecordEnterISR()
 * reads the active vector number from IPSR itself, so the same two-line
 * pair works for every IRQ without ID arguments. If an ISR wakes a higher
 * priority task we must also request the FreeRTOS yield here; tracing the
 * scheduler return alone is not enough.
 */
#define MCU_IRQ_TRACE_BEGIN() SEGGER_SYSVIEW_RecordEnterISR()
#define MCU_IRQ_TRACE_END()   SEGGER_SYSVIEW_RecordExitISR()
#define MCU_IRQ_TRACE_END_SWITCH(ShouldSwitch)    \
  do {                                            \
    const int _switch = ((ShouldSwitch) ? 1 : 0); \
    if (_switch) {                                \
      SEGGER_SYSVIEW_RecordExitISRToScheduler();  \
    } else {                                      \
      SEGGER_SYSVIEW_RecordExitISR();             \
    }                                             \
    portYIELD_FROM_ISR(_switch);                  \
  } while (0)

#else /* sysview disabled or building the bootloader image */

/*
 * Stub function: takes a varargs argument list, evaluates each argument
 * (so the compiler treats them as "used" and does NOT fire
 * -Werror=unused-but-set-variable on values captured purely for trace
 * records), and does nothing with them. The compiler optimizes the call
 * away at -O2 because the function body is empty.
 */
static inline void mcu_sysview_trace_noop(int _first, ...) {
  (void)_first;
}

/*
 * Pull in the generated SYSVIEW_Peripherals.h so the SYSVIEW_PERIPHERALS_*
 * identifiers exist, then immediately strip the runtime offset accessor
 * out of the EVENT_ID macro so the no-op build never references the (un-
 * defined) SYSVIEW_GetPeripheralsEventOffset() symbol.
 *
 * Order matters: this overrides the macro that the generated header
 * defines, so subsequent uses of SYSVIEW_PERIPHERALS_<name> in this TU
 * expand to a plain integer literal that mcu_sysview_trace_noop happily
 * consumes and the compiler optimizes out.
 */
#include "SYSVIEW_Peripherals.h"
#undef SYSVIEW_PERIPHERALS_EVENT_ID
#define SYSVIEW_PERIPHERALS_EVENT_ID(EventId)        (EventId)

/*
 * Stub macros: arguments ARE evaluated by passing them to
 * mcu_sysview_trace_noop so values captured purely for trace recording do
 * not trigger compiler "set but not used" warnings when sysview is
 * disabled. The trailing `, ##__VA_ARGS__` is the standard GCC variadic-
 * with-zero-args trick.
 */
#define SEGGER_SYSVIEW_RecordVoid(...)               mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32(...)                mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32x2(...)              mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32x3(...)              mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32x4(...)              mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32x5(...)              mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32x6(...)              mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnter(...)              mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterU32(...)           mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterU32x2(...)         mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterU32x3(...)         mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterU32x4(...)         mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterTimedU32(...)      mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterTimedU32x2(...)    mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterTimedU32x3(...)    mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterTimedU32x4(...)    mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEndCall(...)            mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEndCallU32(...)         mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_MarkStart(...)                mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_MarkStop(...)                 mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_NameMarker(...)               mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_NameResource(...)             mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_SendSysDesc(...)              mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_Print(...)                    mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_Warn(...)                     mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_Error(...)                    mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordExitISRToScheduler(...) mcu_sysview_trace_noop(0, ##__VA_ARGS__)
#define rtos_sysview_register_marker(...)            (mcu_sysview_trace_noop(0, ##__VA_ARGS__), 0u)

/* IRQ trace helpers collapse to nothing when sysview is disabled,
   but MCU_IRQ_TRACE_END_SWITCH must still yield so ISR semantics
   are identical regardless of the sysview build flag. */
#define MCU_IRQ_TRACE_BEGIN()                        ((void)0)
#define MCU_IRQ_TRACE_END()                          ((void)0)
#define MCU_IRQ_TRACE_END_SWITCH(ShouldSwitch)       portYIELD_FROM_ISR(ShouldSwitch)

/* Stub ID expansion so files referencing SYSVIEW_PERIPHERALS_* compile
 * when the generated header is not included. */
#ifndef SYSVIEW_PERIPHERALS_EVENT_ID
#define SYSVIEW_PERIPHERALS_EVENT_ID(EventId) (EventId)
#endif

#endif /* sysview enabled */
