#pragma once

#include <stdbool.h>
#include <stdint.h>

typedef bool (*wca_sim_proto_handler_t)(uint32_t tag, uint8_t* cmd, uint32_t cmd_size, uint8_t* rsp,
                                        uint32_t* rsp_size);

void wca_sim_set_proto_handler(wca_sim_proto_handler_t handler);
