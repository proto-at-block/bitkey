# This exists to ease the burden on `core` to build this library:
# instead of relying on a prebuilt binary, or meson (and the entire build system),
# we build just the minimal code required using Make.

# Select compiler

ANDROID_TARGETS = armv7-linux-androideabi aarch64-linux-android x86_64-linux-android

ifeq ($(RUST_TARGET),x86_64-apple-ios) # iOS Simulator, x86, but not fully specified
	SYSROOT = $(shell xcrun --sdk iphonesimulator --show-sdk-path)
	CC = clang -target $(RUST_TARGET)-simulator --sysroot $(SYSROOT) -isysroot $(SYSROOT)
else ifeq ($(findstring -apple-ios-sim,$(RUST_TARGET)), -apple-ios-sim) # iOS Simulator
	SYSROOT = $(shell xcrun --sdk iphonesimulator --show-sdk-path)
	CC = clang -target $(RUST_TARGET)ulator --sysroot $(SYSROOT) -isysroot $(SYSROOT)
else ifeq ($(findstring -apple-ios,$(RUST_TARGET)), -apple-ios) # iOS
	SYSROOT = $(shell xcrun --sdk iphoneos --show-sdk-path)
	CC = clang -target $(RUST_TARGET) --sysroot $(SYSROOT) -isysroot $(SYSROOT)
else ifeq ($(RUST_TARGET),aarch64-apple-darwin) # MacOS
	CC = clang -arch arm64
else ifeq ($(RUST_TARGET),x86_64-apple-darwin) # MacOS
	CC = clang -arch x86_64
else ifeq ($(RUST_TARGET),aarch64-unknown-linux-gnu) # Github Actions Runner
	CC = clang -arch arm64
else ifeq ($(RUST_TARGET),$(filter $(RUST_TARGET),$(ANDROID_TARGETS))) # Android
	ANDROID_API_LEVEL = 21

	UNAME_S = $(shell uname -s)
	ifeq ($(UNAME_S),Linux)
		PREBUILT_PATH = linux-x86_64
	endif
	ifeq ($(UNAME_S),Darwin)
		PREBUILT_PATH = darwin-x86_64
	endif

	ifeq ($(ANDROID_NDK_ROOT),)
$(error ANDROID_NDK_ROOT is not set)
	endif

	ANDROID_COMPILER_PATH = $(ANDROID_NDK_ROOT)/toolchains/llvm/prebuilt/$(PREBUILT_PATH)/bin

	_TARGET = $(RUST_TARGET)
	ifeq ($(RUST_TARGET),armv7-linux-androideabi)  # Annoying translation
		_TARGET = armv7a-linux-androideabi
	endif

	CC = $(ANDROID_COMPILER_PATH)/$(_TARGET)$(ANDROID_API_LEVEL)-clang
else ifeq ($(RUST_TARGET),x86_64-unknown-linux-gnu) # Linux
	CC = gcc
else
$(error Unknown target $(RUST_TARGET))
endif

CFLAGS = -fPIC -Wall -Werror -Wextra
BUILD_DIR = build

FW_ROOT = ../..

INCLUDES = -I$(FW_ROOT)/lib/bitlog/inc \
	-I$(FW_ROOT)/lib/helpers/ \
	-I$(FW_ROOT)/third-party/memfault-firmware-sdk/components/include \
	-I. \
	-Iinc \
	-I$(FW_ROOT)/hal/memfault/defs

MEMFAULT_SRC_DIR = $(FW_ROOT)/third-party/memfault-firmware-sdk/components
MEMFAULT_SRCS = $(MEMFAULT_SRC_DIR)/core/src/memfault_event_storage.c \
  $(MEMFAULT_SRC_DIR)/core/src/memfault_serializer_helper.c \
  $(MEMFAULT_SRC_DIR)/core/src/memfault_trace_event.c \
  $(MEMFAULT_SRC_DIR)/util/src/memfault_minimal_cbor.c \
  $(MEMFAULT_SRC_DIR)/util/src/memfault_circular_buffer.c \
  $(MEMFAULT_SRC_DIR)/util/src/memfault_chunk_transport.c \
  $(MEMFAULT_SRC_DIR)/core/src/memfault_sdk_assert.c \
  $(MEMFAULT_SRC_DIR)/core/src/memfault_data_packetizer.c \
  $(MEMFAULT_SRC_DIR)/util/src/memfault_varint.c \
  $(MEMFAULT_SRC_DIR)/util/src/memfault_crc16_ccitt.c \

# Put the Memfault files on our search path so that the wildcard %.c will find them.
VPATH = .:$(MEMFAULT_SRC_DIR)/core/src:$(MEMFAULT_SRC_DIR)/util/src

SRCS = teltra.c teltra_memfault_port.c $(MEMFAULT_SRCS)
OBJS=$(addprefix $(BUILD_DIR)/,$(notdir $(SRCS:.c=.o)))

all: log_disk_usage clean ensure_build_dir libteltra.a

log_disk_usage:
	@echo "Disk usage before build:"
	@df -h .

$(BUILD_DIR)/%.o: %.c
	$(CC) -c $(CFLAGS) $(INCLUDES) -o $@ $<

libteltra.a: ensure_build_dir $(OBJS)
	ar rcs $(BUILD_DIR)/libteltra.a $(OBJS)

ensure_build_dir:
	mkdir -p $(BUILD_DIR)

clean:
	rm -rf $(BUILD_DIR)
