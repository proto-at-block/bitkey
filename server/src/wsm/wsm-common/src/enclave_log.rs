use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::fmt::{Display, Formatter};

pub const MAX_LOG_EVENT_SIZE_BYTES: usize = 32 * 1024; // 32 KB

// A buffer for context that gets included with return values so that wsm-api can include
// it in the logs. We do not want to use a magic "log all the context" macro like tracing::instrument
// because we want to be explicit about what context we are including in the logs (and not include things like keys)
#[derive(Deserialize, Serialize, Debug, Clone)]
pub struct LogBuffer {
    pub truncated: bool,   // whether or not the log buffer was truncated
    pub size_bytes: usize, // the size of the log buffer in bytes
    pub events: VecDeque<String>,
}

impl Default for LogBuffer {
    fn default() -> Self {
        Self::new()
    }
}

impl LogBuffer {
    pub fn new() -> Self {
        Self {
            truncated: false,
            size_bytes: 0,
            events: VecDeque::new(),
        }
    }

    pub fn to_header(self) -> [(&'static str, String); 1] {
        let json = serde_json::to_string(&self).unwrap();
        let base64 = BASE64.encode(json);
        [("X-WSM-Logs", base64)]
    }
}

impl Display for LogBuffer {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "LogBuffer: truncated: {}, size_bytes: {}, events: {:?}",
            self.truncated, self.size_bytes, self.events
        )
    }
}

// macro that adds the current file name, line number, and a supplied message to the LogBuffer
// the log buffer is passed should be a mutable reference or owned instance of LogBuffer
#[macro_export]
macro_rules! wsm_log {
    ($log_buffer:expr, $message:expr) => {
        println!("{}:{} {}", file!(), line!(), $message); // will output in the nitro console when debug-mode is turned on
        // If the log buffer will be too big after adding this message, remove the oldest messages until it is small enough
        while $log_buffer.size_bytes + $message.len() > MAX_LOG_EVENT_SIZE_BYTES {
            let oldest_event = $log_buffer.events.pop_front().unwrap();
            $log_buffer.size_bytes -= oldest_event.len();
            $log_buffer.truncated = true;
        }
        $log_buffer.size_bytes += $message.len();
        $log_buffer.events.push_back(format!("{}:{} {}", file!(), line!(), $message));
    };
}

// macro that takes an expression that can fail, and then if it does, returns the specified error with the current log buffer
// Use this when you just want to lift an error into a different error type and include the log buffer
// If you want to add custom messages, use the wsm_log! macro to add them to the log buffer before returning the error
#[macro_export]
macro_rules! try_with_log_and_error {
    ($log:expr, $error:path, $expr:expr) => {
        $expr.map_err(|e| {
            wsm_log!($log, &format!("Error: {}", e));
            $error {
                message: format!("Error: {}", e),
                log_buffer: $log.clone(),
            }
        })
    };
}
