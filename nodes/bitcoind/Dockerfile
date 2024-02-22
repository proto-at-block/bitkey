FROM alpine:3.19.1 as deployable

ARG BITCOIN_HOME=/var/lib/bitcoin

RUN apk add --no-cache bitcoin

COPY --chown=bitcoin bitcoin.conf ${BITCOIN_HOME}/bitcoin.conf
COPY entrypoint.sh entrypoint.sh
VOLUME ${BITCOIN_HOME}

# rpcport: (default: 8332, testnet: 18332, signet: 38332, regtest: 18443)
# port: (default: 8333, testnet: 18333, signet: 38333, regtest: 18444)

EXPOSE 8332 8333 18332 18333 38332 38333 18443 18444

USER bitcoin
ENTRYPOINT ["/entrypoint.sh"]
