#!/bin/bash
# Поднять UDP-буферы ядра до 32 MB. По умолчанию в Linux они порядка 256 KB,
# чего не хватает для QUIC при ~20+ MB/s сквозного трафика — пакеты дропаются
# в самом UDP-сокете до того, как доходят до EMQX/Netty.
#
# Применить временно (до перезагрузки):
#   sudo ./raise_udp_buffers.sh
#
# Чтобы сохранить после перезагрузки, добавьте в /etc/sysctl.d/99-quic.conf:
#   net.core.rmem_max = 33554432
#   net.core.wmem_max = 33554432
#   net.core.rmem_default = 1048576
#   net.core.wmem_default = 1048576

set -e

if [ "$EUID" -ne 0 ]; then
    echo "Run as root (sudo $0)" >&2
    exit 1
fi

sysctl -w net.core.rmem_max=33554432
sysctl -w net.core.wmem_max=33554432
sysctl -w net.core.rmem_default=1048576
sysctl -w net.core.wmem_default=1048576

echo
echo "Current values:"
sysctl net.core.rmem_max net.core.wmem_max net.core.rmem_default net.core.wmem_default
