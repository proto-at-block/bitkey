enum class HttpLogLevel {
  /** Logs everything, headers, body and info */
  ALL,

  /** Logs headers and info */
  HEADERS,

  /** Logs body and info */
  BODY,

  /** Logs only info */
  INFO,

  /** Doesn't log anything */
  NONE,
}
