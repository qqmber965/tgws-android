"""Check the Android adapter's Worker-only configuration without external network."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/python'))
import mobile_runner
from proxy.config import proxy_config


async def capture_run(event):
    assert proxy_config.pool_size == 0
    assert proxy_config.fallback_cfproxy is False
    assert proxy_config.h2_enabled is False
    assert proxy_config.cfproxy_worker_domains == ['tgws.dlcpromo.ru']


def test_worker_only():
    original = mobile_runner.tg_ws_proxy._run
    mobile_runner.tg_ws_proxy._run = capture_run
    try:
        mobile_runner.reset()
        mobile_runner.start('00112233445566778899aabbccddeeff', 1443, 0,
                            False, False, '', '149.154.167.220',
                            '149.154.167.220', True, 'tgws.dlcpromo.ru')
        assert proxy_config.cfproxy_worker_domains == ['tgws.dlcpromo.ru']
    finally:
        mobile_runner.tg_ws_proxy._run = original


def test_worker_disabled():
    original = mobile_runner.tg_ws_proxy._run
    async def capture_disabled(event):
        assert proxy_config.cfproxy_worker_domains == []
    mobile_runner.tg_ws_proxy._run = capture_disabled
    try:
        mobile_runner.reset()
        mobile_runner.start('00112233445566778899aabbccddeeff', 1443, 0,
                            False, False, '', '149.154.167.220',
                            '149.154.167.220', False, 'tgws.dlcpromo.ru')
    finally:
        mobile_runner.tg_ws_proxy._run = original


if __name__ == '__main__':
    test_worker_only()
    test_worker_disabled()
    print('PASS: Worker-only and disabled mode settings')
