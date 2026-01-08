description = "Converts TTF/WOFF/OTF fonts to compact bitmap format for LVGL"
binaries = ["lv_font_conv"]
test = "lv_font_conv --help"
# Note: Requires Node.js to be available in PATH (provided by parent wallet directory)

source = "https://registry.npmjs.org/lv_font_conv/-/lv_font_conv-${version}.tgz"

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
