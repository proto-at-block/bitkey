use crypto::noise::{NoiseContext, NoiseRole, PrivateKey};
use std::{
    collections::{BinaryHeap, HashMap},
    time::{Duration, Instant},
};

// Session ID 8 random bytes, base64 encoded
type SessionId = String;

#[derive(Debug, Hash, Eq, PartialEq)]
pub enum KeyType {
    TestKey,
    ProductionKey,
}

impl KeyType {
    pub fn from_str(public_key: &str) -> Option<Self> {
        match public_key {
            // 046d0f2d82024c8a9defa34ac4a82f659247b38e0fdf3024d579d981f9ed7a8661f8efe8bd86dc1ba05fc986f1c9f12e450edcb1c34d072c7cde13a897767050ab
            "BG0PLYICTIqd76NKxKgvZZJHs44P3zAk1XnZgfnteoZh+O/ovYbcG6BfyYbxyfEuRQ7cscNNByx83hOol3ZwUKs=" => Some(KeyType::TestKey),
            // TODO Generate production key.
            // 046d0f2d82024c8a9defa34ac4a82f659247b38e0fdf3024d579d981f9ed7a8661f8efe8bd86dc1ba05fc986f1c9f12e450edcb1c34d072c7cde13a897767050ab
            "BG0PLYICTIqd76NKxKgvZZJHs44P3zAk1XnZgfnteoZh+O/ovYbcG6BfyYbxyfEuRQ7cscNNByx83hOol3ZwUKs=" => Some(KeyType::ProductionKey),
            _ => None,
        }
    }
}

#[derive(Debug)]
struct Session {
    context: NoiseContext,
    expiry: Instant,
}

#[derive(Debug, Eq, PartialEq, Clone)]
struct ExpiryEntry {
    expiry: Instant,
    session_id: SessionId,
}

// Reverse the ordering for BinaryHeap (to make it a min-heap by default)
//
// We have a collection of session ids with timestamps; the timestamp is a unix epoch plus a fixed expiration.
// We want to get the OLDEST, and therefore smallest timestamp, so that when we iterate over the heap,
// we can stop once we see a timestamp that isn't expired.
impl Ord for ExpiryEntry {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        other.expiry.cmp(&self.expiry) // Reverse comparison for min-heap
    }
}

impl PartialOrd for ExpiryEntry {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

/// Wrapper around a NoiseContext that caches the handshake state along with a session id.
/// This is useful for the server to avoid re-handshaking with the same client.
///
/// The cache is implemented as a priority queue of session ids with expiry times. We purge
/// the cache opportunistically. The priority queue allows us to efficiently get the oldest
/// (i.e. soonest to expire) session.
#[derive(Debug)]
pub struct NoiseCache {
    sessions: HashMap<SessionId, Session>,
    expiry_queue: BinaryHeap<ExpiryEntry>, // Priority queue based on expiry time
    key_map: HashMap<KeyType, Vec<u8>>,
    max_duration: Duration,
}

impl Default for NoiseCache {
    fn default() -> Self {
        Self::new()
    }
}

impl NoiseCache {
    pub fn new() -> Self {
        let mut key_map = HashMap::new();
        key_map.insert(
            KeyType::TestKey,
            hex::decode("22b483ea6904d7e924a5ec38ee03cd0e283fa7613cb0bcf771e84ab47aa9654e")
                .expect("Failed to decode test key"),
        );

        NoiseCache {
            sessions: HashMap::new(),
            expiry_queue: BinaryHeap::new(),
            key_map,
            max_duration: Duration::from_secs(600), // 10 minutes
        }
    }

    pub fn add_session(&mut self, session_id: SessionId, key_type: KeyType) -> &NoiseContext {
        let private_key = self.key_map.get(&key_type).expect("Key type not found");
        let noise_ctx = NoiseContext::new(
            NoiseRole::Responder,
            PrivateKey::InMemory {
                secret_bytes: private_key.clone(),
            },
            None,
            None,
        )
        .expect("Failed to create noise context");

        let expiry = Instant::now() + self.max_duration;
        self.sessions.insert(
            session_id.clone(),
            Session {
                context: noise_ctx,
                expiry,
            },
        );

        self.expiry_queue.push(ExpiryEntry {
            expiry,
            session_id: session_id.clone(),
        });

        // Safe to unwrap because we just inserted it.
        &self.sessions.get(&session_id).unwrap().context
    }

    pub fn get_session(&mut self, session_id: &SessionId) -> Option<&NoiseContext> {
        self.cleanup_expired_sessions(); // This makes get_session O(log n).
        self.sessions.get(session_id).map(|s| &s.context)
    }

    pub fn remove_session(&mut self, session_id: &SessionId) {
        self.sessions.remove(session_id);
    }

    fn cleanup_expired_sessions(&mut self) {
        let now = Instant::now();

        // Remove expired sessions from the priority queue and the map
        while let Some(entry) = self.expiry_queue.pop() {
            if entry.expiry > now {
                // If the entry hasn't expired, push it back into the queue
                self.expiry_queue.push(entry);
                break;
            }

            // Remove expired entry from the sessions map
            self.sessions.remove(&entry.session_id);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::thread::sleep;

    #[test]
    fn test_add_and_get_session() {
        let mut cache = NoiseCache::new();
        let session_id = "cookie".to_string();
        let _ = cache.add_session(session_id.clone(), KeyType::TestKey);

        assert!(cache.get_session(&session_id).is_some());
        cache.remove_session(&session_id);
        assert!(cache.get_session(&session_id).is_none());
    }

    #[test]
    fn test_session_expiry() {
        let mut cache = NoiseCache::new();
        cache.max_duration = Duration::from_secs(1); // Shorter duration for testing
        let session_id = "temp".to_string();

        cache.add_session(session_id.clone(), KeyType::TestKey);
        assert!(cache.get_session(&session_id).is_some());

        sleep(Duration::from_secs(2)); // Wait for expiry
        assert!(cache.get_session(&session_id).is_none());
    }

    #[test]
    fn test_priority_queue_ordering() {
        let mut pq = BinaryHeap::new();
        pq.push(ExpiryEntry {
            expiry: Instant::now() + Duration::from_millis(5),
            session_id: "temp".to_string(),
        });
        pq.push(ExpiryEntry {
            expiry: Instant::now() + Duration::from_millis(10),
            session_id: "temp2".to_string(),
        });

        // Should pop off the oldest entry first
        assert_eq!(pq.pop().unwrap().session_id, "temp");
        assert_eq!(pq.pop().unwrap().session_id, "temp2");
    }

    #[test]
    fn test_get_nonexistent_session() {
        let mut cache = NoiseCache::new();
        let session_id = "nonexistent".to_string();

        assert!(cache.get_session(&session_id).is_none());
    }

    #[test]
    #[should_panic]
    fn test_invalid_key_type() {
        let mut cache = NoiseCache::new();
        let session_id = "cookie".to_string();
        let invalid_key_type = KeyType::ProductionKey; // Assuming this key type is not set up

        cache.add_session(session_id, invalid_key_type);
    }
}
