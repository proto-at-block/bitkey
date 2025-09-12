use nix::fcntl::{fcntl, FcntlArg, OFlag};
use nix::unistd::{read, write};
use std::io::{Read, Write};
use std::os::unix::io::{AsRawFd, BorrowedFd, RawFd};

use nix::sys::socket::{self, AddressFamily, SockFlag, SockType, VsockAddr};
use nix::unistd::close;

use crate::error::VsockError;

/// Simple VSock socket wrapper
#[derive(Debug)]
pub struct VsockSocket {
    fd: RawFd,
}

impl VsockSocket {
    pub fn connect(cid: u32, port: u32) -> Result<Self, VsockError> {
        let socket_fd = socket::socket(
            AddressFamily::Vsock,
            SockType::Stream,
            SockFlag::empty(),
            None,
        )
        .map_err(VsockError::SocketCreation)?;

        let sockaddr = VsockAddr::new(cid, port);
        let raw_fd = socket_fd.as_raw_fd();
        socket::connect(raw_fd, &sockaddr).map_err(VsockError::ConnectionFailed)?;

        // Convert OwnedFd to RawFd and forget about the ownership
        // The raw fd will be managed by our VsockSocket
        std::mem::forget(socket_fd);
        Ok(VsockSocket { fd: raw_fd })
    }

    pub fn set_nonblocking(&self, nonblocking: bool) -> Result<(), VsockError> {
        let flags = fcntl(self.fd, FcntlArg::F_GETFL)
            .map_err(|e| VsockError::HttpRequest(std::io::Error::from(e)))?;

        let flags = OFlag::from_bits_truncate(flags);
        let new_flags = if nonblocking {
            flags | OFlag::O_NONBLOCK
        } else {
            flags & !OFlag::O_NONBLOCK
        };

        fcntl(self.fd, FcntlArg::F_SETFL(new_flags))
            .map_err(|e| VsockError::HttpRequest(std::io::Error::from(e)))?;

        Ok(())
    }
}

impl Read for VsockSocket {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let bytes_read = read(self.fd, buf).map_err(std::io::Error::from)?;
        Ok(bytes_read)
    }
}

impl Write for VsockSocket {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        let borrowed_fd = unsafe { BorrowedFd::borrow_raw(self.fd) };
        let bytes_written = write(borrowed_fd, buf).map_err(std::io::Error::from)?;
        Ok(bytes_written)
    }

    fn flush(&mut self) -> std::io::Result<()> {
        // VSock doesn't need explicit flushing
        Ok(())
    }
}

impl AsRawFd for VsockSocket {
    fn as_raw_fd(&self) -> RawFd {
        self.fd
    }
}

impl Drop for VsockSocket {
    fn drop(&mut self) {
        let _ = close(self.fd);
    }
}
