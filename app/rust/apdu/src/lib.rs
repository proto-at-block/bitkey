// T4T 1.1 5.1.2 Format of Command-APDU
// Lc is encoded when serialized.
// WCA doesn't use Le, and it's optional, so it is unimplemented.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Command {
    pub cla: u8,
    pub ins: u8,
    pub p1: u8,
    pub p2: u8,
    pub data: Option<Vec<u8>>,
}

// T4T 1.1 5.1.3 Format of Response-APDU
// Per the spec, the response data is optional, but we require it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Response {
    pub data: Vec<u8>,
    pub sw1: u8,
    pub sw2: u8,
}

impl Command {
    pub fn new(cla: u8, ins: u8, p1: u8, p2: u8, data: Vec<u8>) -> Self {
        Self {
            cla,
            ins,
            p1,
            p2,
            data: Some(data),
        }
    }

    pub fn new_header(cla: u8, ins: u8, p1: u8, p2: u8) -> Self {
        Self {
            cla,
            ins,
            p1,
            p2,
            data: None,
        }
    }

    pub fn serialize(self) -> Vec<u8> {
        let mut out = vec![self.cla, self.ins, self.p1, self.p2];

        // Data length (Lc) and data, if present
        if let Some(d) = self.data {
            // T4T 1.1 Table 19: Coding of Lc field
            let lc = d.len() as u16;
            match lc {
                1..=255 => {
                    // Short coding
                    out.push(lc as u8);
                }
                _ => {
                    // Extended; 0 is prefixed by big-endian Lc
                    out.push(0);
                    out.extend(lc.to_be_bytes());
                }
            }
            out.extend(d);
        }

        out
    }
}

impl From<Command> for Vec<u8> {
    fn from(command: Command) -> Self {
        command.serialize()
    }
}

impl Response {
    pub fn is_ok(&self) -> bool {
        (self.sw1 == 0x90 || self.sw1 == 0x91) && (self.sw2 == 0x00)
    }
}

impl From<Vec<u8>> for Response {
    fn from(buffer: Vec<u8>) -> Self {
        let len = buffer.len();
        if len < 2 {
            return Self {
                data: buffer,
                sw1: 0,
                sw2: 0,
            };
        }

        let sw_off = len - 2;

        Self {
            data: buffer[..sw_off].to_vec(),
            sw1: buffer[sw_off],
            sw2: buffer[sw_off + 1],
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn header_only() {
        assert_eq!(
            vec![1, 2, 3, 4],
            Command::new_header(1, 2, 3, 4).serialize(),
        );
    }

    #[test]
    fn header_and_data() {
        assert_eq!(
            vec![0xaa, 0xbb, 0xcc, 0xdd, 2, 0xff, 0xff],
            Command::new(0xaa, 0xbb, 0xcc, 0xdd, vec![0xff, 0xff]).serialize()
        );
    }

    #[test]
    fn extended_lc() {
        let len = 512;
        let cmd = Command::new(1, 2, 3, 4, vec![0xaf; len]);
        let ser = cmd.serialize();
        // Header
        assert_eq!(vec![1, 2, 3, 4], ser[0..4]);
        // Lc
        assert_eq!(vec![0, (len >> 8) as u8, (len & 0xff) as u8], ser[4..7]);
        // Data
        assert_eq!(vec![0xaf; len], ser[7..]);
    }

    #[test]
    fn response_ok() {
        let buf = vec![0xaa, 0xbb, 0xcc, 0xdd, 0x90, 0x00];
        let rsp = Response::from(buf);
        assert!(rsp.is_ok());
        assert_eq!(vec![0xaa, 0xbb, 0xcc, 0xdd], rsp.data);
        assert_eq!(0x90, rsp.sw1);
        assert_eq!(0x00, rsp.sw2);
    }

    #[test]
    fn response_fail() {
        let buf = vec![0xaa, 0xbb, 0xcc, 0xdd, 0x92, 0x00];
        let rsp = Response::from(buf);
        assert!(!rsp.is_ok());
        assert_eq!(vec![0xaa, 0xbb, 0xcc, 0xdd], rsp.data);
        assert_eq!(0x92, rsp.sw1);
        assert_eq!(0x00, rsp.sw2);
    }

    #[test]
    fn response_short_buffer() {
        let buf = vec![0xaa];
        let rsp = Response::from(buf);
        assert!(!rsp.is_ok());
        assert_eq!(vec![0xaa], rsp.data);
    }

    #[test]
    fn response_sw_only() {
        let buf = vec![0x91, 0x00];
        let rsp = Response::from(buf);
        assert!(rsp.is_ok());
        assert!(rsp.data.is_empty());
        assert_eq!(0x91, rsp.sw1);
        assert_eq!(0x00, rsp.sw2);
    }
}
