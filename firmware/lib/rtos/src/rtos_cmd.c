#include "attributes.h"
#include "printf.h"
#include "rtos_thread.h"
#include "shell_cmd.h"

#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#define MAX_NUM_TASKS (20U)

static struct {
  arg_lit_t* verbose;
  arg_end_t* end;
} cmd_free_args;

static void cmd_top_register(void);
static void cmd_uptime_register(void);
static void cmd_free_register(void);

static void cmd_top_run(int argc, char** argv);
static void cmd_uptime_run(int argc, char** argv);
static void cmd_free_run(int argc, char** argv);

static void cmd_top_register(void) {
  static shell_command_t cmd = {
    .command = "top",
    .help = "display thread statistics",
    .handler = cmd_top_run,
  };
  shell_command_register(&cmd);
}
SHELL_CMD_REGISTER("top", cmd_top_register);

static void cmd_uptime_register(void) {
  static shell_command_t cmd = {
    .command = "uptime",
    .help = "display system uptime",
    .handler = cmd_uptime_run,
  };
  shell_command_register(&cmd);
}
SHELL_CMD_REGISTER("uptime", cmd_uptime_register);

static void cmd_free_register(void) {
  cmd_free_args.verbose = ARG_LIT_OPT('v', "v", "verbose output");
  cmd_free_args.end = ARG_END();

  static shell_command_t cmd = {
    .command = "free",
    .help = "display heap statistics",
    .handler = cmd_free_run,
    .argtable = &cmd_free_args,
  };
  shell_command_register(&cmd);
}
SHELL_CMD_REGISTER("free", cmd_free_register);

static void cmd_top_run(int UNUSED(argc), char** UNUSED(argv)) {
  printf("Task");

  /* Minus three for the null terminator and half the number of characters in
  "Task" so the column lines up with the centre of the heading. */
  configASSERT(configMAX_TASK_NAME_LEN > 3);

  /* Add a space to align columns after the task's name. */
  const int pad = strlen("Task");
  printf("%*s", (configMAX_TASK_NAME_LEN - 3) - pad, "");

  const char* const header =
    "   State  Prio  Stack  "
    "Usage\r\n**********************************************\r\n";
  printf("%s", header);

  static TaskStatus_t task_statuses[MAX_NUM_TASKS];
  volatile uint32_t num_tasks;
  uint32_t total_run_time;

  /* Generate raw status information about each task. */
  num_tasks = uxTaskGetSystemState(task_statuses, num_tasks, &total_run_time);

/* For percentage calculations. */
#if (configGENERATE_RUN_TIME_STATS == 1) && (INCLUDE_uxTaskGetRunTime == 1)
  uint32_t usage_percentage;
  total_run_time /= 100UL;

  /* Avoid divide by zero errors. */
  if (total_run_time == 0) {
    return;
  }
#endif

  /* For each populated position in the pxTaskStatusArray array,
  format the raw data as human readable ASCII data. */
  for (uint32_t i = 0; i < num_tasks; i++) {
    TaskStatus_t* task = &task_statuses[i];

    /* Task Name */
    const uint32_t taskname_len = strlen(task->pcTaskName);
    const uint32_t taskname_pad = (configMAX_TASK_NAME_LEN - taskname_len);
    printf("%s%*s", task->pcTaskName, taskname_pad, "");

    /* Priority */
    int len = 0;
    switch (task->eCurrentState) {
      case eRunning:
        len = printf("R+");
        break;
      case eReady:
        len = printf("R");
        break;
      case eBlocked:
        len = printf("B");
        break;
      case eSuspended:
        len = printf("S");
        break;
      case eDeleted:
        len = printf("D");
        break;
      case eInvalid: /* fall-through */
      default:
        len = printf("?");
        break;
    }

    /* Priority */
    int pad = strlen("State  ");
    printf("%*s", pad - len, "");
    printf("%li", task->uxBasePriority);

    /* Stack High Water Mark */
    pad = strlen("Prio ");
    printf("%*s", pad, "");
    len = printf("%i", task->usStackHighWaterMark * sizeof(uint32_t));

    /* Percentage of total run time */
    pad = strlen("Stack  ");
    printf("%*s", pad - len, "");
#if (configGENERATE_RUN_TIME_STATS == 1) && (INCLUDE_uxTaskGetRunTime == 1)
    usage_percentage = task->ulRunTimeCounter / total_run_time;

    if (usage_percentage > 0UL) {
      printf("%lu%%", usage_percentage);
    } else {
      /* Show <1% for tasks with usage rounded to 0 */
      printf("<1%%");
    }
#else
    printf("N/A");
#endif

    printf("\n");
  }

  /* Footer */
  const char* const footer =
    "**********************************************\r\nStates: "
    "(B)locking, "
    "(R)eady, (D)eleted, (S)uspended\r\n";
  printf("%s", footer);
}

static void cmd_uptime_run(int UNUSED(argc), char** UNUSED(argv)) {
  const uint64_t uptime_millis = rtos_thread_systime();

  const uint64_t seconds = (uptime_millis / 1000) % 60;
  const uint64_t ms_remainder = uptime_millis % 1000;
  const uint64_t minutes = (uptime_millis / (1000 * 60)) % 60;
  const uint64_t hours = uptime_millis / (1000 * 60 * 60);

  printf("%02" PRIu64 ":%02" PRIu64 ":%02" PRIu64 ".%03" PRIu64 "\n", hours, minutes, seconds,
         ms_remainder);
}

static void cmd_free_run(int argc, char** argv) {
  int nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_free_args);

  if (nerrors) {
    return;
  }

  HeapStats_t stats;
  vPortGetHeapStats(&stats);

  const size_t total = configTOTAL_HEAP_SIZE / 1024;
  const size_t available = stats.xAvailableHeapSpaceInBytes / 1024;
  const size_t used = total - available;

  const int initial_pad = printf(" *      ");
  const int total_pad = printf("total   ");
  const int used_pad = printf("used     ");
  printf("available\n");

  int value_pad = 0;

  printf("Mem:%*s", initial_pad - strlen("Mem:"), "");
  value_pad = printf("%*zu Ki", strlen("total") - strlen(" Ki"), total);

  printf("%*s", total_pad - value_pad, "");
  value_pad = printf("%*zu Ki", strlen("used") - strlen(" Ki"), used);

  printf("%*s", used_pad - value_pad, "");
  value_pad = printf("%*zu Ki", strlen("available") - strlen(" Ki"), available);

  printf("\n");

  if (cmd_free_args.verbose->header.found) {
    printf(" Available Size of heap space:      %zu Bi\n", stats.xAvailableHeapSpaceInBytes);
    printf(" Size of largest free block:        %zu Bi\n", stats.xSizeOfLargestFreeBlockInBytes);
    printf(" Size of smallest free block:       %zu Bi\n", stats.xSizeOfSmallestFreeBlockInBytes);
    printf(" Number of free blocks:             %zu\n", stats.xNumberOfFreeBlocks);
    printf(" Minimum ever free bytes remaining: %zu Bi\n", stats.xMinimumEverFreeBytesRemaining);
    printf(" Number of successful allocations:  %zu\n", stats.xNumberOfSuccessfulAllocations);
    printf(" Number of successful frees:        %zu\n", stats.xNumberOfSuccessfulFrees);
  }
}
