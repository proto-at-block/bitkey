FROM alpine:3.19.1 as builder
RUN apk add alpine-sdk doas
RUN adduser -h /aports -G abuild -D -H abuild \
	&& echo "permit nopass root" >> /etc/doas.d/doas.conf
RUN mkdir -p /var/cache/distfiles \
	&& chown :abuild /var/cache/distfiles \
	&& chmod g+w /var/cache/distfiles
RUN mkdir aports \
	&& chown abuild:abuild aports \
	&& doas -u abuild git clone --depth 1 https://gitlab.alpinelinux.org/alpine/aports.git
RUN doas -u abuild abuild-keygen -a -n \
	&& cp /aports/.abuild/*.pub /etc/apk/keys

USER abuild

RUN cd /aports/testing/fulcrum && abuild -r

FROM alpine:3.19.1 as deployable

COPY --from=builder /aports/packages /aports/packages
COPY --from=builder /etc/apk/keys /etc/apk/keys
RUN --mount=type=cache,target=/var/cache/apk/ apk add \
	--repository https://dl-cdn.alpinelinux.org/alpine/v3.16/community/ \
	--repository /aports/packages/testing \
	fulcrum openssl

ARG FULCRUM_HOME=/var/lib/fulcrum

RUN adduser -h ${FULCRUM_HOME} -S fulcrum

COPY --chown=fulcrum fulcrum.conf ${FULCRUM_HOME}/fulcrum.conf
COPY entrypoint.sh entrypoint.sh
VOLUME ${FULCRUM_HOME}

# mainnet: 50001 tcp, 50002 ssl
# testnet: 51001 tcp, 51002 ssl
EXPOSE 50001 51001

USER fulcrum
ENTRYPOINT ["/entrypoint.sh"]
