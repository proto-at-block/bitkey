// IPs that the VPN will egress through when connecting to public addresses.
output "public_egress_cidrs" {
  value = [
    # iad02
    "104.30.132.218/32",
    # nrt01
    "104.30.133.100/32",
    # mel01
    "104.30.133.101/32",
    # syd06
    "104.30.133.102/32",
    # dub01
    "104.30.133.103/32",
    # osl01
    "104.30.133.104/32",
    # lhr14
    "104.30.133.105/32",
    # ork01
    "104.30.133.113/32",
    # lhr01
    "104.30.133.114/32",
    # nrt08
    "104.30.133.115/32",
    # syd04
    "104.30.133.116/32",
    # iad08
    "104.30.133.117/32",
    # sjc06
    "104.30.133.118/32",
    # sjc01
    "104.30.134.51/32",
  ]
}

// IPs that the VPN uses for internal addresses, for both ingress and egress.
output "internal_cidrs" {
  value = ["100.127.228.0/24", "100.127.229.0/24"]
}