#include "mfgtest_task.h"

#include "assert.h"
#include "mcu_gpio.h"
#include "mfgtest.pb.h"
#include "mpu_auto.h"
#include "rtos.h"
#include "uc.h"
#include "uc_route.h"
#include "uxc.pb.h"

#include <stdint.h>
#include <string.h>

#define MFGTEST_TASK_PRIORITY   (RTOS_THREAD_PRIORITY_NORMAL)
#define MFGTEST_TASK_STACK_SIZE (1024u)
#define MFGTEST_TASK_QUEUE_SIZE (2u)

static bool _mfgtest_task_get_port(mcu_gpio_config_t* gpio, uint8_t port) {
  switch (port) {
    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_port_PORT_A:
      gpio->port = GPIOA;
      break;

    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_port_PORT_B:
      gpio->port = GPIOB;
      break;

    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_port_PORT_C:
      gpio->port = GPIOC;
      break;

    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_port_PORT_D:
      gpio->port = GPIOD;
      break;

    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_port_PORT_E:
      gpio->port = GPIOE;
      break;

    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_port_PORT_F:
      gpio->port = GPIOF;
      break;

    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_port_PORT_G:
      gpio->port = GPIOG;
      break;

    default:
      return false;
  }
  return true;
}

static void _mfgtest_task_handle_gpio_cmd(fwpb_uxc_msg_host* proto) {
  fwpb_mfgtest_gpio_cmd* cmd = &proto->msg.mfgtest_gpio_cmd;
  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_device_mfgtest_gpio_rsp_tag;
  fwpb_mfgtest_gpio_rsp* rsp = &msg->msg.mfgtest_gpio_rsp;

  mcu_gpio_config_t gpio = {.pin = cmd->pin};
  if (_mfgtest_task_get_port(&gpio, cmd->port)) {
    switch (cmd->action) {
      case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_action_READ:
        rsp->output = mcu_gpio_read(&gpio);
        break;

      case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_action_SET:
        mcu_gpio_set(&gpio);
        rsp->output = mcu_gpio_read(&gpio);
        break;

      case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_action_CLEAR:
        mcu_gpio_clear(&gpio);
        rsp->output = mcu_gpio_read(&gpio);
        break;

      case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_action_UNSPECIFIED:
        /* 'break' intentionally omitted. */
      default:
        rsp->output = 0;
        break;
    }
  } else {
    rsp->output = 0;
  }

  uc_free_recv_proto(proto);
  (void)uc_send(msg);
}

static void mfgtest_thread(void* args) {
  rtos_queue_t* queue = args;
  ASSERT(queue != NULL);

  uc_route_register_queue(fwpb_uxc_msg_host_mfgtest_gpio_cmd_tag, queue);

  while (true) {
    fwpb_uxc_msg_host* proto = uc_route_pend_queue(queue);
    ASSERT(proto != NULL);

    switch (proto->which_msg) {
      case fwpb_uxc_msg_host_mfgtest_gpio_cmd_tag:
        _mfgtest_task_handle_gpio_cmd(proto);
        break;

      default:
        uc_free_recv_proto(proto);
        break;
    }
  }
}

void mfgtest_task_create(void) {
  rtos_queue_t* queue =
    rtos_queue_create(mfgtest_task_queue, fwpb_uxc_msg_host*, MFGTEST_TASK_QUEUE_SIZE);
  rtos_thread_t* thread =
    rtos_thread_create(mfgtest_thread, queue, MFGTEST_TASK_PRIORITY, MFGTEST_TASK_STACK_SIZE);
  ASSERT(thread != NULL);
}
