#include "ui_messaging.h"

#include "display.pb.h"

void ui_show_confirmation(const char* text, bool lock) {
  fwpb_display_params_confirmation conf = {0};
  strncpy(conf.text, text, sizeof(conf.text) - 1);
  conf.lock_on_dismiss = lock;
  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_SHOW_CONFIRMATION, &conf, sizeof(conf));
}
