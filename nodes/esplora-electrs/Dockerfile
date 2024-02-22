FROM docker.io/library/rust:slim-bookworm@sha256:70c2a016184099262fd7cee46f3d35fec3568c45c62f87e37f7f665f766b1f74 as build

RUN mkdir -p /srv/electrs_bitcoin \
    && apt-get -yqq update \
    && apt-get -yqq upgrade \
    && apt-get -yqq install \
        clang \
        cmake \
        curl \
        git

RUN rustc --version

COPY electrs electrs

RUN cd electrs \
    && cargo install --root /srv/electrs_bitcoin --locked --path . --features electrum-discovery

FROM debian:bookworm-slim as deployable

ARG DIR=/srv/esplora-electrs
ARG USER=esplora-electrs

COPY --from=build /srv/electrs_bitcoin $DIR
COPY entrypoint.sh entrypoint.sh

WORKDIR $DIR
EXPOSE 9000

ENTRYPOINT ["/entrypoint.sh"]
