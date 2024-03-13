use std::fmt::{Display, Result};

use crate::signers::{Authentication, Spending};

use super::{Account, SignerPair};

impl Display for Account {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> Result {
        write!(f, "Account ID: {}", self.id)?;

        for (index, keyset) in self.keysets.iter().enumerate() {
            writeln!(f)?;
            writeln!(f, "Keyset ({index}): {} {:?}", keyset.id, keyset.network)?;
            writeln!(f, "\tApplication: {}", keyset.keys.application)?;
            writeln!(f, "\tHardware: {}", keyset.keys.hardware)?;
            write!(f, "\tServer: {}", keyset.keys.server)?;
        }

        Ok(())
    }
}

impl Display for SignerPair {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> Result {
        writeln!(f, "Authentication:")?;
        writeln!(
            f,
            "\tApplication: {}",
            Authentication::public_key(&self.application)
        )?;
        writeln!(
            f,
            "\tHardware: {}",
            Authentication::public_key(&self.hardware)
        )?;

        writeln!(f, "Spending:")?;
        writeln!(
            f,
            "\tApplication: {}",
            Spending::public_key(&self.application)
        )?;
        write!(f, "\tHardware: {}", Spending::public_key(&self.hardware))?;

        Ok(())
    }
}
