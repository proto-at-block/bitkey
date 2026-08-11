/**
 * @file uxc_socket_server.c
 * @brief TCP socket server for UXC communication
 */

#include "uxc_socket_server.h"

#include "stdio_defs.h"

#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>

#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>

#define SOCKET_LOG(fmt, ...) LOG_MODULE("uxc_socket", fmt, ##__VA_ARGS__)

static int g_server_fd = -1;
static int g_client_fd = -1;

static bool set_nonblocking(int fd) {
  int flags = fcntl(fd, F_GETFL, 0);
  if (flags == -1)
    return false;
  return fcntl(fd, F_SETFL, flags | O_NONBLOCK) != -1;
}

bool uxc_socket_init(int port) {
  g_server_fd = socket(AF_INET, SOCK_STREAM, 0);
  if (g_server_fd < 0) {
    SOCKET_LOG("Failed to create socket: %s", strerror(errno));
    return false;
  }

  // Allow reuse of address
  int opt = 1;
  if (setsockopt(g_server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
    SOCKET_LOG("Failed to set SO_REUSEADDR: %s", strerror(errno));
    close(g_server_fd);
    g_server_fd = -1;
    return false;
  }

  struct sockaddr_in addr = {
    .sin_family = AF_INET,
    .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
    .sin_port = htons(port),
  };

  if (bind(g_server_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
    SOCKET_LOG("Failed to bind to port %d: %s", port, strerror(errno));
    close(g_server_fd);
    g_server_fd = -1;
    return false;
  }

  if (listen(g_server_fd, 1) < 0) {
    SOCKET_LOG("Failed to listen: %s", strerror(errno));
    close(g_server_fd);
    g_server_fd = -1;
    return false;
  }

  // Set server socket to non-blocking for accept()
  if (!set_nonblocking(g_server_fd)) {
    SOCKET_LOG("Failed to set non-blocking: %s", strerror(errno));
    close(g_server_fd);
    g_server_fd = -1;
    return false;
  }

  SOCKET_LOG("Listening on port %d for ui-simulate connection", port);
  return true;
}

void uxc_socket_cleanup(void) {
  uxc_socket_close_client();
  if (g_server_fd >= 0) {
    close(g_server_fd);
    g_server_fd = -1;
  }
}

int uxc_socket_get_client_fd(void) {
  return g_client_fd;
}

int uxc_socket_get_server_fd(void) {
  return g_server_fd;
}

bool uxc_socket_is_connected(void) {
  return g_client_fd >= 0;
}

void uxc_socket_close_client(void) {
  if (g_client_fd >= 0) {
    close(g_client_fd);
    g_client_fd = -1;
  }
}

void uxc_socket_accept(void) {
  if (g_server_fd < 0)
    return;

  // Don't accept if already have a client
  if (g_client_fd >= 0)
    return;

  int new_fd = accept(g_server_fd, NULL, NULL);
  if (new_fd >= 0) {
    g_client_fd = new_fd;
    set_nonblocking(g_client_fd);
    SOCKET_LOG("ui-simulate connected");
  }
}

bool uxc_socket_send(const uint8_t* data, uint32_t len) {
  if (g_client_fd < 0) {
    return false;
  }

  size_t total = 0;
  while (total < len) {
    ssize_t w = write(g_client_fd, data + total, len - total);
    if (w < 0) {
      if (errno == EAGAIN || errno == EWOULDBLOCK) {
        // Buffer full, retry
        continue;
      }
      SOCKET_LOG("Write failed: %s", strerror(errno));
      uxc_socket_close_client();
      return false;
    }
    total += w;
  }
  return true;
}

int uxc_socket_recv(uint8_t* buf, uint32_t max_len) {
  if (g_client_fd < 0) {
    return -1;
  }

  ssize_t r = read(g_client_fd, buf, max_len);
  if (r < 0) {
    if (errno == EAGAIN || errno == EWOULDBLOCK) {
      return 0;  // No data available
    }
    SOCKET_LOG("Read failed: %s", strerror(errno));
    uxc_socket_close_client();
    return -1;
  }
  if (r == 0) {
    SOCKET_LOG("ui-simulate disconnected");
    uxc_socket_close_client();
    return -1;
  }
  return (int)r;
}
