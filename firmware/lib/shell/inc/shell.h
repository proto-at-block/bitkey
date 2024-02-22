#pragma once

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>

typedef int (*shell_vprintf_func_t)(const char*, va_list);
typedef void (*shell_mutex_create_fn_t)(void);
typedef bool (*shell_mutex_lock_fn_t)(void);
typedef bool (*shell_mutex_unlock_fn_t)(void);

typedef struct {
  shell_vprintf_func_t vprintf_func;
  void* cmd_list_mutex;
  shell_mutex_create_fn_t mutex_create;
  shell_mutex_lock_fn_t mutex_lock;
  shell_mutex_unlock_fn_t mutex_unlock;
} shell_config_t;

void shell_init(const shell_config_t* config);
int shell_write(const char* str, ...);
void shell_process(const uint8_t* data, uint32_t length);
