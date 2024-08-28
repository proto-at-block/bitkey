use once_cell::sync::Lazy;
use std::sync::{Arc, Mutex};

// Singleton log buffer for storing logs for proto exchanges.
// Higher-level app logic (Kotlin, Swift) consumes these logs and sends
// them to DataDog.

struct LogBufferInner {
    active: bool,
    logs: Vec<String>,
}

impl LogBufferInner {
    fn new() -> Self {
        LogBufferInner {
            active: false,
            logs: Vec::new(),
        }
    }

    pub fn enable(&mut self) {
        self.active = true;
    }

    pub fn disable(&mut self) {
        self.active = false;
    }
}

static INNER_BUFFER: Lazy<Arc<Mutex<LogBufferInner>>> =
    Lazy::new(|| Arc::new(Mutex::new(LogBufferInner::new())));

pub struct LogBuffer;

// UniFFI API
impl LogBuffer {
    pub fn put(log: String) {
        let mut buffer = INNER_BUFFER.lock().unwrap();
        if buffer.active {
            buffer.logs.push(log);
        }
    }

    pub fn get() -> Vec<String> {
        let mut buffer = INNER_BUFFER.lock().unwrap();
        let output = buffer.logs.clone();
        buffer.logs.clear();
        output
    }
}

pub fn get_proto_exchange_logs() -> Vec<String> {
    LogBuffer::get()
}

pub fn enable_proto_exchange_logging() {
    let mut buffer = INNER_BUFFER.lock().unwrap();
    buffer.enable();
}

pub fn disable_proto_exchange_logging() {
    let mut buffer = INNER_BUFFER.lock().unwrap();
    buffer.disable();
}

#[cfg(test)]
mod tests {
    use super::*;
    use serial_test::serial;

    fn reset_log_buffer() {
        let mut buffer = INNER_BUFFER.lock().unwrap();
        buffer.logs.clear();
        buffer.disable();
    }

    #[test]
    #[serial]
    fn test_log_buffer() {
        reset_log_buffer();

        enable_proto_exchange_logging();
        LogBuffer::put("test".to_string());
        let logs = LogBuffer::get();
        assert_eq!(logs.len(), 1);
        assert_eq!(logs[0], "test");
        let logs = LogBuffer::get();
        assert_eq!(logs.len(), 0);
    }

    #[test]
    #[serial]
    fn test_shared_log_buffer() {
        reset_log_buffer();

        enable_proto_exchange_logging();
        LogBuffer::put("log from buffer 1".to_string());
        LogBuffer::put("log from buffer 2".to_string());

        let logs = LogBuffer::get();
        assert_eq!(logs.len(), 2);
        assert!(logs.contains(&"log from buffer 1".to_string()));
        assert!(logs.contains(&"log from buffer 2".to_string()));

        let logs = LogBuffer::get();
        assert_eq!(logs.len(), 0);
    }

    #[test]
    #[serial]
    fn test_not_enabled() {
        reset_log_buffer();

        disable_proto_exchange_logging();
        LogBuffer::put("test".to_string());
        let logs = LogBuffer::get();
        println!("{:?}", logs);
        assert!(logs.is_empty());
    }

    #[test]
    #[serial]
    fn test_disable() {
        reset_log_buffer();

        enable_proto_exchange_logging();
        LogBuffer::put("test".to_string());
        let logs = LogBuffer::get();
        assert_eq!(logs.len(), 1);
        disable_proto_exchange_logging();
        LogBuffer::put("test".to_string());
        assert!(LogBuffer::get().is_empty());
    }
}
