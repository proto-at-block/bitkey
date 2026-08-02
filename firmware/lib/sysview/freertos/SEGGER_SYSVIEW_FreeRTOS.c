/*********************************************************************
*                    SEGGER Microcontroller GmbH                     *
*                        The Embedded Experts                        *
**********************************************************************
*                                                                    *
*            (c) 1995 - 2021 SEGGER Microcontroller GmbH             *
*                                                                    *
*       www.segger.com     Support: support@segger.com               *
*                                                                    *
**********************************************************************
*                                                                    *
*       SEGGER SystemView * Real-time application analysis           *
*                                                                    *
**********************************************************************
*                                                                    *
* All rights reserved.                                               *
*                                                                    *
* SEGGER strongly recommends to not make any changes                 *
* to or modify the source code of this software in order to stay     *
* compatible with the SystemView and RTT protocol, and J-Link.       *
*                                                                    *
* Redistribution and use in source and binary forms, with or         *
* without modification, are permitted provided that the following    *
* condition is met:                                                  *
*                                                                    *
* o Redistributions of source code must retain the above copyright   *
*   notice, this condition and the following disclaimer.             *
*                                                                    *
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND             *
* CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,        *
* INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF           *
* MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE           *
* DISCLAIMED. IN NO EVENT SHALL SEGGER Microcontroller BE LIABLE FOR *
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR           *
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT  *
* OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;    *
* OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF      *
* LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT          *
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE  *
* USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH   *
* DAMAGE.                                                            *
*                                                                    *
**********************************************************************
*                                                                    *
*       SystemView version: 3.30                                    *
*                                                                    *
**********************************************************************
-------------------------- END-OF-HEADER -----------------------------

File    : SEGGER_SYSVIEW_FreeRTOS.c
Purpose : Interface between FreeRTOS and SystemView.
Revision: $Rev: 7947 $
*/
#include "SEGGER_SYSVIEW_FreeRTOS.h"

#include "FreeRTOS.h"
#include "SEGGER_SYSVIEW.h"
#include "rtos_sysview.h"
#include "string.h"  // Required for memset
#include "task.h"

#include <stdio.h>

#if defined(__GNUC__) && defined(EMBEDDED_BUILD)
#define SYSVIEW_SHARED_BSS __attribute__((section(".sram4")))
#else
#define SYSVIEW_SHARED_BSS
#endif

typedef struct SYSVIEW_FREERTOS_TASK_STATUS SYSVIEW_FREERTOS_TASK_STATUS;

#define SYSVIEW_STACK_WARN_PCT   70U
#define SYSVIEW_STACK_ERROR_PCT  90U
#define SYSVIEW_STACK_WARN_SEEN  (1u << 0)
#define SYSVIEW_STACK_ERROR_SEEN (1u << 1)

struct SYSVIEW_FREERTOS_TASK_STATUS {
  U32 xHandle;
  const char* pcTaskName;
  unsigned uxCurrentPriority;
  U32 pxStack;
  U32 pxStackEnd;
  unsigned uStackSizeBytes;
  unsigned uStackUsageBytes;
  unsigned uStackAlertState;
};

static SYSVIEW_SHARED_BSS SYSVIEW_FREERTOS_TASK_STATUS _aTasks[SYSVIEW_FREERTOS_MAX_NOF_TASKS];
static SYSVIEW_SHARED_BSS unsigned _NumTasks;

/*
 * FreeRTOS guarantees pxTopOfStack is the first member of the TCB so ports can
 * restore/save context without knowing the rest of the layout. Use only that
 * first member here to avoid scanning the stack fill pattern on every switch.
 */
typedef struct {
  volatile StackType_t* pxTopOfStack;
} SYSVIEW_FREERTOS_TCB_HEAD;

static SYSVIEW_FREERTOS_TASK_STATUS* _FindTask(U32 xHandle) {
  unsigned n;

  for (n = 0; n < _NumTasks; n++) {
    if (_aTasks[n].xHandle == xHandle) {
      return &_aTasks[n];
    }
  }
  return NULL;
}

static unsigned _GetStackUsageBytesExact(const TaskStatus_t* pTaskStatus, unsigned StackSizeBytes) {
  unsigned FreeBytes;

  FreeBytes = (unsigned)pTaskStatus->usStackHighWaterMark * sizeof(StackType_t);
  if (FreeBytes > StackSizeBytes) {
    FreeBytes = StackSizeBytes;
  }

  return StackSizeBytes - FreeBytes;
}

static unsigned _GetStackSizeBytes(const TaskStatus_t* pTaskStatus, unsigned StackSizeHint) {
  (void)StackSizeHint;

/* TaskStatus_t only exposes pxEndOfStack when BOTH the stack grows upward AND
   configRECORD_STACK_HIGH_ADDRESS==1 (see xTASK_STATUS in task.h). This guard
   must match that condition exactly — on downward-growing Cortex-M stacks the
   field is absent, so we fall through to the StackSizeHint below. */
#if ((portSTACK_GROWTH > 0) && (configRECORD_STACK_HIGH_ADDRESS == 1))
  if (pTaskStatus->pxEndOfStack >= pTaskStatus->pxStackBase) {
    return (unsigned)(((pTaskStatus->pxEndOfStack - pTaskStatus->pxStackBase) + 1U) *
                      sizeof(StackType_t));
  }
#endif

  return StackSizeHint;
}

static unsigned _GetObservedStackUsageBytesFast(const SYSVIEW_FREERTOS_TASK_STATUS* pTask) {
  const SYSVIEW_FREERTOS_TCB_HEAD* pTCB;
  uintptr_t stack_low;
  uintptr_t stack_high;
  uintptr_t stack_top;

  if ((pTask == NULL) || (pTask->xHandle == 0U) || (pTask->uStackSizeBytes == 0U)) {
    return 0U;
  }

  pTCB = (const SYSVIEW_FREERTOS_TCB_HEAD*)(uintptr_t)pTask->xHandle;
  stack_low = (uintptr_t)pTask->pxStack;
  stack_high = (uintptr_t)pTask->pxStackEnd;
#if (portUSING_MPU_WRAPPERS == 1)
  /*
   * On the CM33 MPU port, the first TCB member is the saved MPU context cursor,
   * not the task's raw stack pointer. The original PSP value is stored four
   * words below that cursor by PendSV_Handler.
   */
  stack_top = (uintptr_t)pTCB->pxTopOfStack[-4];
#else
  stack_top = (uintptr_t)pTCB->pxTopOfStack;
#endif

#if (portSTACK_GROWTH < 0)
  if (stack_top < stack_low) {
    stack_top = stack_low;
  } else if (stack_top > stack_high) {
    stack_top = stack_high;
  }

  return (unsigned)(stack_high - stack_top);
#else
  if (stack_top < stack_low) {
    stack_top = stack_low;
  } else if (stack_top > stack_high) {
    stack_top = stack_high;
  }

  return (unsigned)(stack_top - stack_low);
#endif
}

static void _RefreshTaskStackMetadata(SYSVIEW_FREERTOS_TASK_STATUS* pTask, unsigned StackSizeHint) {
  TaskStatus_t TaskStatus;

  memset(&TaskStatus, 0, sizeof(TaskStatus));
  vTaskGetInfo((TaskHandle_t)pTask->xHandle, &TaskStatus, pdTRUE, eReady);

  pTask->pxStack = (U32)TaskStatus.pxStackBase;
  pTask->uStackSizeBytes = _GetStackSizeBytes(&TaskStatus, StackSizeHint);
#if ((portSTACK_GROWTH > 0) && (configRECORD_STACK_HIGH_ADDRESS == 1))
  pTask->pxStackEnd = (U32)TaskStatus.pxEndOfStack;
#else
  pTask->pxStackEnd = pTask->pxStack + pTask->uStackSizeBytes - sizeof(StackType_t);
#endif

  {
    const unsigned ExactUsageBytes = _GetStackUsageBytesExact(&TaskStatus, pTask->uStackSizeBytes);
    if (ExactUsageBytes > pTask->uStackUsageBytes) {
      pTask->uStackUsageBytes = ExactUsageBytes;
    }
  }
}

static void _EmitStackAlert(unsigned IsError, const SYSVIEW_FREERTOS_TASK_STATUS* pTask,
                            unsigned StackUsageBytes, unsigned Percent) {
  char Message[128];
  int Len;

  Len = snprintf(Message, sizeof(Message), "stack usage %s: %s %u/%u bytes (%u%%)",
                 IsError != 0U ? "high" : "elevated",
                 pTask->pcTaskName != NULL ? pTask->pcTaskName : "unknown", StackUsageBytes,
                 pTask->uStackSizeBytes, Percent);
  if ((Len < 0) || ((unsigned)Len >= sizeof(Message))) {
    if (IsError != 0U) {
      SEGGER_SYSVIEW_Error("stack usage high");
    } else {
      SEGGER_SYSVIEW_Warn("stack usage elevated");
    }
    return;
  }

  if (IsError != 0U) {
    SEGGER_SYSVIEW_Error(Message);
  } else {
    SEGGER_SYSVIEW_Warn(Message);
  }
}

static void _MaybeReportStackThresholds(SYSVIEW_FREERTOS_TASK_STATUS* pTask,
                                        unsigned StackUsageBytes) {
  unsigned Percent;

  if (pTask->uStackSizeBytes == 0U) {
    return;
  }

  Percent = (unsigned)(((unsigned long long)StackUsageBytes * 100ULL) / pTask->uStackSizeBytes);

  if ((Percent >= SYSVIEW_STACK_ERROR_PCT) &&
      ((pTask->uStackAlertState & SYSVIEW_STACK_ERROR_SEEN) == 0U)) {
    _EmitStackAlert(1U, pTask, StackUsageBytes, Percent);
    pTask->uStackAlertState |= (SYSVIEW_STACK_WARN_SEEN | SYSVIEW_STACK_ERROR_SEEN);
  } else if ((Percent >= SYSVIEW_STACK_WARN_PCT) &&
             ((pTask->uStackAlertState & SYSVIEW_STACK_WARN_SEEN) == 0U)) {
    _EmitStackAlert(0U, pTask, StackUsageBytes, Percent);
    pTask->uStackAlertState |= SYSVIEW_STACK_WARN_SEEN;
  }
}

static void _SendTaskStackInfoIfChanged(SYSVIEW_FREERTOS_TASK_STATUS* pTask) {
  const unsigned StackUsageBytes = _GetObservedStackUsageBytesFast(pTask);

  if (StackUsageBytes > pTask->uStackUsageBytes) {
    SEGGER_SYSVIEW_STACKINFO StackInfo;

    pTask->uStackUsageBytes = StackUsageBytes;
    _MaybeReportStackThresholds(pTask, StackUsageBytes);

    memset(&StackInfo, 0, sizeof(StackInfo));
    StackInfo.TaskID = pTask->xHandle;
    StackInfo.StackBase = pTask->pxStack;
    StackInfo.StackSize = pTask->uStackSizeBytes;
    StackInfo.StackUsage = pTask->uStackUsageBytes;
    SEGGER_SYSVIEW_SendStackInfo(&StackInfo);
  }
}

/*********************************************************************
 *
 *       _cbSendTaskList()
 *
 *  Function description
 *    This function is part of the link between FreeRTOS and SYSVIEW.
 *    Called from SystemView when asked by the host, it uses SYSVIEW
 *    functions to send the entire task list to the host.
 */
static void _cbSendTaskList(void) {
  unsigned n;

  for (n = 0; n < _NumTasks; n++) {
    _RefreshTaskStackMetadata(&_aTasks[n], _aTasks[n].uStackSizeBytes);
    _MaybeReportStackThresholds(&_aTasks[n], _aTasks[n].uStackUsageBytes);
    SYSVIEW_SendTaskInfo((U32)_aTasks[n].xHandle, _aTasks[n].pcTaskName,
                         (unsigned)_aTasks[n].uxCurrentPriority, (U32)_aTasks[n].pxStack,
                         (unsigned)_aTasks[n].uStackSizeBytes,
                         (unsigned)_aTasks[n].uStackUsageBytes);
  }
}

/*********************************************************************
 *
 *       _cbGetTime()
 *
 *  Function description
 *    This function is part of the link between FreeRTOS and SYSVIEW.
 *    Called from SystemView when asked by the host, returns the
 *    current system time in micro seconds.
 */
static U64 _cbGetTime(void) {
  U64 Time;

  Time = xTaskGetTickCountFromISR();
  Time *= portTICK_PERIOD_MS;
  Time *= 1000;
  return Time;
}

/*********************************************************************
 *
 *       Global functions
 *
 **********************************************************************
 */
/*********************************************************************
 *
 *       SYSVIEW_AddTask()
 *
 *  Function description
 *    Add a task to the internal list and record its information.
 */
void SYSVIEW_AddTask(U32 xHandle, const char* pcTaskName, unsigned uxCurrentPriority, U32 pxStack,
                     unsigned uStackSizeBytes) {
  if (memcmp(pcTaskName, "IDLE", 5) == 0) {
    return;
  }

  if (_NumTasks >= SYSVIEW_FREERTOS_MAX_NOF_TASKS) {
    SEGGER_SYSVIEW_Warn(
      "SYSTEMVIEW: Could not record task information. Maximum number of tasks reached.");
    return;
  }

  _aTasks[_NumTasks].xHandle = xHandle;
  _aTasks[_NumTasks].pcTaskName = pcTaskName;
  _aTasks[_NumTasks].uxCurrentPriority = uxCurrentPriority;
  _aTasks[_NumTasks].pxStack = pxStack;
  _aTasks[_NumTasks].pxStackEnd = pxStack;
  _aTasks[_NumTasks].uStackSizeBytes = uStackSizeBytes;
  _aTasks[_NumTasks].uStackUsageBytes = 0U;
  _aTasks[_NumTasks].uStackAlertState = 0U;
  _RefreshTaskStackMetadata(&_aTasks[_NumTasks], uStackSizeBytes);

  _NumTasks++;

  SYSVIEW_SendTaskInfo(xHandle, pcTaskName, uxCurrentPriority, _aTasks[_NumTasks - 1].pxStack,
                       _aTasks[_NumTasks - 1].uStackSizeBytes,
                       _aTasks[_NumTasks - 1].uStackUsageBytes);
}

/*********************************************************************
 *
 *       SYSVIEW_UpdateTask()
 *
 *  Function description
 *    Update a task in the internal list and record its information.
 */
void SYSVIEW_UpdateTask(U32 xHandle, const char* pcTaskName, unsigned uxCurrentPriority,
                        U32 pxStack, unsigned uStackSizeBytes) {
  unsigned n;

  if (memcmp(pcTaskName, "IDLE", 5) == 0) {
    return;
  }

  for (n = 0; n < _NumTasks; n++) {
    if (_aTasks[n].xHandle == xHandle) {
      break;
    }
  }
  if (n < _NumTasks) {
    _aTasks[n].pcTaskName = pcTaskName;
    _aTasks[n].uxCurrentPriority = uxCurrentPriority;
    _aTasks[n].pxStack = pxStack;
    _aTasks[n].pxStackEnd = pxStack;
    if (uStackSizeBytes != 0U) {
      _aTasks[n].uStackSizeBytes = uStackSizeBytes;
    }
    _RefreshTaskStackMetadata(&_aTasks[n], _aTasks[n].uStackSizeBytes);

    SYSVIEW_SendTaskInfo(xHandle, pcTaskName, uxCurrentPriority, _aTasks[n].pxStack,
                         _aTasks[n].uStackSizeBytes, _aTasks[n].uStackUsageBytes);
  } else {
    SYSVIEW_AddTask(xHandle, pcTaskName, uxCurrentPriority, pxStack, uStackSizeBytes);
  }
}

/*********************************************************************
 *
 *       SYSVIEW_DeleteTask()
 *
 *  Function description
 *    Delete a task from the internal list.
 */
void SYSVIEW_DeleteTask(U32 xHandle) {
  unsigned n;

  if (_NumTasks == 0) {
    return;  // Early out
  }
  for (n = 0; n < _NumTasks; n++) {
    if (_aTasks[n].xHandle == xHandle) {
      break;
    }
  }
  if (n == (_NumTasks - 1)) {
    //
    // Task is last item in list.
    // Simply zero the item and decrement number of tasks.
    //
    memset(&_aTasks[n], 0, sizeof(_aTasks[n]));
    _NumTasks--;
  } else if (n < _NumTasks) {
    //
    // Task is in the middle of the list.
    // Move last item to current position and decrement number of tasks.
    // Order of tasks does not really matter, so no need to move all following items.
    //
    _aTasks[n].xHandle = _aTasks[_NumTasks - 1].xHandle;
    _aTasks[n].pcTaskName = _aTasks[_NumTasks - 1].pcTaskName;
    _aTasks[n].uxCurrentPriority = _aTasks[_NumTasks - 1].uxCurrentPriority;
    _aTasks[n].pxStack = _aTasks[_NumTasks - 1].pxStack;
    _aTasks[n].pxStackEnd = _aTasks[_NumTasks - 1].pxStackEnd;
    _aTasks[n].uStackSizeBytes = _aTasks[_NumTasks - 1].uStackSizeBytes;
    _aTasks[n].uStackUsageBytes = _aTasks[_NumTasks - 1].uStackUsageBytes;
    _aTasks[n].uStackAlertState = _aTasks[_NumTasks - 1].uStackAlertState;
    memset(&_aTasks[_NumTasks - 1], 0, sizeof(_aTasks[_NumTasks - 1]));
    _NumTasks--;
  }
}

/*********************************************************************
 *
 *       SYSVIEW_SendTaskInfo()
 *
 *  Function description
 *    Record task information.
 */
void SYSVIEW_SendTaskInfo(U32 TaskID, const char* sName, unsigned Prio, U32 StackBase,
                          unsigned StackSize, unsigned StackUsage) {
  SEGGER_SYSVIEW_TASKINFO TaskInfo;

  memset(&TaskInfo, 0, sizeof(TaskInfo));  // Fill all elements with 0 to allow extending the
                                           // structure in future version without breaking the code
  TaskInfo.TaskID = TaskID;
  TaskInfo.sName = sName;
  TaskInfo.Prio = Prio;
  TaskInfo.StackBase = StackBase;
  TaskInfo.StackSize = StackSize;
  TaskInfo.StackUsage = StackUsage;
  SEGGER_SYSVIEW_SendTaskInfo(&TaskInfo);
}

void SYSVIEW_TaskSwitchedIn(U32 TaskID, const char* sName, unsigned IsIdle) {
  rtos_sysview_task_switched_in(TaskID, sName, IsIdle != 0u);
  if (IsIdle != 0u) {
    SEGGER_SYSVIEW_OnIdle();
  } else {
    SEGGER_SYSVIEW_OnTaskStartExec(TaskID);
  }
}

void SYSVIEW_TaskSwitchedOut(U32 TaskID, const char* sName, unsigned IsIdle) {
  SYSVIEW_FREERTOS_TASK_STATUS* pTask;

  rtos_sysview_task_switched_out(TaskID, sName, IsIdle != 0u);
  if (SEGGER_SYSVIEW_IsStarted()) {
    pTask = _FindTask(TaskID);
    if (pTask != NULL) {
      _SendTaskStackInfoIfChanged(pTask);
    }
  }
  SEGGER_SYSVIEW_OnTaskStopExec();
}

/*********************************************************************
 *
 *       Public API structures
 *
 **********************************************************************
 */
// Callbacks provided to SYSTEMVIEW by FreeRTOS
const SEGGER_SYSVIEW_OS_API SYSVIEW_X_OS_TraceAPI = {
  _cbGetTime,
  _cbSendTaskList,
};

/*************************** End of file ****************************/
