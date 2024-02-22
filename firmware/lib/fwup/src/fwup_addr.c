#include "assert.h"
#include "fwup.h"

extern char app_a_slot_page[];
extern char app_b_slot_page[];
extern char app_a_signature[];
extern char app_b_signature[];
extern char app_slot_size[];
extern uint32_t active_slot;
extern char bl_base_addr[];
extern char bl_slot_size[];

NO_OPTIMIZE void* fwup_target_slot_address(void) {
  // Slot address is the opposite of the currently running slot
  if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_A) {
    return (void*)app_b_slot_page;
  } else if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_B) {
    return (void*)app_a_slot_page;
  }
  ASSERT(false);
  return NULL;
}

NO_OPTIMIZE void* fwup_target_slot_signature_address(void) {
  /* Slot address is the opposite of the currently running slot */
  if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_A) {
    return (void*)app_b_signature;
  } else if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_B) {
    return (void*)app_a_signature;
  }
  ASSERT(false);
  return NULL;
}

NO_OPTIMIZE void* fwup_current_slot_address(void) {
  if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_A) {
    return (void*)app_a_slot_page;
  } else if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_B) {
    return (void*)app_b_slot_page;
  }
  ASSERT(false);
  return NULL;
}

NO_OPTIMIZE uint32_t fwup_slot_size(void) {
  return (uint32_t)app_slot_size;
}

NO_OPTIMIZE void* fwup_bl_address(void) {
  return (void*)bl_base_addr;
}

NO_OPTIMIZE uint32_t fwup_bl_size(void) {
  return (uint32_t)bl_slot_size;
}
