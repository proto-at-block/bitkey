#pragma once

/*
 * Generic SystemView trace shim for application-side firmware modules.
 *
 * When SystemView is enabled for the application image, pull in the real
 * SEGGER recorder declarations plus the generated peripheral event IDs.
 * Otherwise provide local no-op macros so shared sources still compile.
 */

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1) && defined(IMAGE_TYPE_APPLICATION) && \
  (IMAGE_TYPE_APPLICATION == 1)

#include "SEGGER_SYSVIEW.h"
#include "SYSVIEW_Peripherals.h"

#else

/*
 * Stub function: takes a varargs argument list and does nothing. The
 * compiler still evaluates the arguments at the call site, so values
 * captured purely for trace records do NOT trigger
 * -Werror=unused-but-set-variable when sysview is disabled. The empty
 * body is optimized away at -O2.
 */
static inline void rtos_sysview_trace_noop(int _first, ...) {
  (void)_first;
}

/*
 * Pull in the generated SYSVIEW_Peripherals.h so the SYSVIEW_PERIPHERALS_*
 * identifiers exist, then strip the runtime offset accessor out of the
 * EVENT_ID macro so the no-op build never references the (undefined)
 * SYSVIEW_GetPeripheralsEventOffset() symbol. Subsequent uses of
 * SYSVIEW_PERIPHERALS_<name> expand to a plain integer literal that
 * rtos_sysview_trace_noop consumes; the compiler elides the call at -O2.
 */
#include "SYSVIEW_Peripherals.h"
#undef SYSVIEW_PERIPHERALS_EVENT_ID
#define SYSVIEW_PERIPHERALS_EVENT_ID(EventId) (EventId)

#define SEGGER_SYSVIEW_RecordVoid(...)               rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32(...)                rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32x2(...)              rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32x3(...)              rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordU32x4(...)              rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnter(...)              rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterU32(...)           rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterU32x2(...)         rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterU32x3(...)         rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterU32x4(...)         rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterTimedU32(...)      rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterTimedU32x2(...)    rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterTimedU32x3(...)    rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEnterTimedU32x4(...)    rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEndCall(...)            rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordEndCallU32(...)         rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_MarkStart(...)                rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_MarkStop(...)                 rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_NameMarker(...)               rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_NameResource(...)             rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_SendSysDesc(...)              rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_Print(...)                    rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_Warn(...)                     rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_Error(...)                    rtos_sysview_trace_noop(0, ##__VA_ARGS__)
#define SEGGER_SYSVIEW_RecordExitISRToScheduler(...) rtos_sysview_trace_noop(0, ##__VA_ARGS__)

#ifndef SYSVIEW_PERIPHERALS_EVENT_ID
#define SYSVIEW_PERIPHERALS_EVENT_ID(EventId) (EventId)
#endif

#endif
