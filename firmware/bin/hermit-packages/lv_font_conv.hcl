description = "Converts TTF/WOFF/OTF fonts to compact bitmap format for LVGL"
binaries = ["lv_font_conv"]
test = "lv_font_conv --help"
runtime-dependencies = ["node@lts"]

source = "file://${HERMIT_ENV}/third-party/lv_font_conv/lv_font_conv-${version}.tgz"
sha256sums = {
  "file://${HERMIT_ENV}/third-party/lv_font_conv/lv_font_conv-1.5.3.tgz": "73e82a745ba3dae3fe361f8552a5fe98dd1a11b139f4bb93c2a7bcbd16d5caac"
}

on "unpack" {
  # npm tarballs extract to package/ directory with all node_modules
  rename {
    from = "${root}/package"
    to = "${root}/lv_font_conv_pkg"
  }

  # Create a wrapper script that invokes the JS file via node
  run {
    cmd = "/bin/sh"
    args = [
      "-c",
      "cat > \"${root}/lv_font_conv\" << 'WRAPPER'\n#!/bin/sh\nexec /usr/bin/env node \"${root}/lv_font_conv_pkg/lv_font_conv.js\" \"$$@\"\nWRAPPER\nchmod +x \"${root}/lv_font_conv\""
    ]
  }
}

version "1.5.3" {}
