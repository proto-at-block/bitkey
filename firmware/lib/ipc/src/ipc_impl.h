#pragma once

#include "ipc.h"

#define IPC_QUEUE_MAX_NAME_SIZE (16)

typedef struct {
  char name[IPC_QUEUE_MAX_NAME_SIZE];
  rtos_queue_t* queue;
} ipc_port_obj_t;

ipc_port_obj_t* get_port_obj(ipc_port_t port);
