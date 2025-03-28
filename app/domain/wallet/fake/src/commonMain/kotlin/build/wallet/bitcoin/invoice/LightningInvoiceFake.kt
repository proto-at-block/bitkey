package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.invoice.ParsedPaymentData.Lightning
import build.wallet.bitcoin.lightning.LightningInvoice

// lnbc25m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdq5vdhkven9v5sxyetpdeessp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygs9q5sqqqqqqqqqqqqqqqpqsq67gye39hfg3zd8rgc80k32tvy9xk2xunwm5lzexnvpx6fd77en8qaq424dxgt56cag2dpt359k3ssyhetktkpqh24jqnjyw6uqd08sgptq44qu
val validLightningInvoice =
  Lightning(
    lightningInvoice =
      LightningInvoice(
        payeePubKey = "03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad",
        paymentHash = "0001020304050607080900010203040506070809000102030405060708090102",
        isExpired = true,
        amountMsat = 2500000000UL
      )
  )
