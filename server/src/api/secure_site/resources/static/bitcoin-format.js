/**
 * Formats a satoshi amount, optionally using BIP 177 Bitcoin sign.
 *
 * @param {number} amountSats - The amount in satoshis
 * @param {boolean} useBip177 - Whether to use BIP 177 Bitcoin sign (₿) instead of "sats"
 * @returns {string} The formatted amount string
 */
function formatSatoshiAmount(amountSats, useBip177) {
    if (useBip177) {
        return `₿${amountSats.toLocaleString()}`;
    } else {
        return `${amountSats.toLocaleString()} sats`;
    }
}
