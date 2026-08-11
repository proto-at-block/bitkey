/**
 * @file sim_persistence.c
 * @brief File-based persistent storage for core-sim
 */

#include "sim_persistence.h"

#include "stdio_defs.h"

#include <sys/stat.h>

#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define DEFAULT_DATA_DIR ".core-sim"
#define MAX_PATH_LEN     512

static char g_data_dir[MAX_PATH_LEN] = {0};
static bool g_initialized = false;

static bool ensure_directory(const char* path) {
  struct stat st;
  if (stat(path, &st) == 0) {
    return S_ISDIR(st.st_mode);
  }
  return mkdir(path, 0700) == 0;
}

static bool build_file_path(char* out, size_t out_size, const char* name) {
  if (!g_initialized || !name || strlen(name) == 0) {
    return false;
  }
  int len = snprintf(out, out_size, "%s/%s", g_data_dir, name);
  return len > 0 && (size_t)len < out_size;
}

static bool resolve_data_dir(void) {
  const char* env_dir = getenv("CORE_SIM_DATA_DIR");
  if (env_dir && env_dir[0] != '\0') {
    snprintf(g_data_dir, sizeof(g_data_dir), "%s", env_dir);
    return true;
  }

  const char* home = getenv("HOME");
  if (!home) {
    LOG("HOME not set, persistence disabled");
    return false;
  }
  snprintf(g_data_dir, sizeof(g_data_dir), "%s/%s", home, DEFAULT_DATA_DIR);
  return true;
}

bool sim_persistence_init(void) {
  if (g_initialized) {
    return true;
  }

  if (!resolve_data_dir()) {
    return false;
  }

  if (!ensure_directory(g_data_dir)) {
    LOG("Failed to create data directory: %s", g_data_dir);
    return false;
  }

  g_initialized = true;

  const char* reset = getenv("CORE_SIM_RESET_STORAGE");
  if (reset && strcmp(reset, "1") == 0) {
    LOG("CORE_SIM_RESET_STORAGE=1, clearing persistent data");
    sim_persistence_wipe_all();
  }

  LOG("Persistence initialized: %s", g_data_dir);
  return true;
}

bool sim_persistence_enabled(void) {
  return g_initialized;
}

const char* sim_persistence_get_dir(void) {
  return g_initialized ? g_data_dir : NULL;
}

bool sim_persistence_save(const char* name, const void* data, size_t len) {
  char path[MAX_PATH_LEN];
  if (!build_file_path(path, sizeof(path), name)) {
    return false;
  }

  FILE* f = fopen(path, "wb");
  if (!f) {
    LOG("Failed to open %s for writing: %s", path, strerror(errno));
    return false;
  }

  size_t written = fwrite(data, 1, len, f);
  fclose(f);

  if (written != len) {
    LOG("Failed to write %s: wrote %zu of %zu bytes", name, written, len);
    unlink(path);
    return false;
  }

  return true;
}

bool sim_persistence_load(const char* name, void* data, size_t len) {
  char path[MAX_PATH_LEN];
  if (!build_file_path(path, sizeof(path), name)) {
    return false;
  }

  FILE* f = fopen(path, "rb");
  if (!f) {
    return false;
  }

  fseek(f, 0, SEEK_END);
  long file_size = ftell(f);
  fseek(f, 0, SEEK_SET);

  if (file_size != (long)len) {
    LOG("File %s size mismatch: expected %zu, got %ld", name, len, file_size);
    fclose(f);
    return false;
  }

  size_t read_bytes = fread(data, 1, len, f);
  fclose(f);

  return read_bytes == len;
}

bool sim_persistence_delete(const char* name) {
  char path[MAX_PATH_LEN];
  if (!build_file_path(path, sizeof(path), name)) {
    return false;
  }

  if (unlink(path) == 0 || errno == ENOENT) {
    return true;
  }

  LOG("Failed to delete %s: %s", name, strerror(errno));
  return false;
}

bool sim_persistence_wipe_all(void) {
  if (!g_initialized) {
    return false;
  }

  DIR* dir = opendir(g_data_dir);
  if (!dir) {
    return errno == ENOENT;
  }

  char path[MAX_PATH_LEN];
  struct dirent* entry;
  bool success = true;

  while ((entry = readdir(dir)) != NULL) {
    if (entry->d_name[0] == '.') {
      continue;
    }

    snprintf(path, sizeof(path), "%s/%s", g_data_dir, entry->d_name);
    if (unlink(path) != 0 && errno != ENOENT) {
      LOG("Failed to delete %s: %s", path, strerror(errno));
      success = false;
    }
  }

  closedir(dir);
  LOG("Wiped all persistent data");
  return success;
}
