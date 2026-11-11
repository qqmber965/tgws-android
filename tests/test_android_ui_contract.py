"""Fast checks that the redesigned shell retains key runtime contracts."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/io/github/tgwsproxy/python114'


def test_brand_and_version():
    manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
    app = manifest.find('application')
    assert app is not None
    android = '{http://schemas.android.com/apk/res/android}'
    assert app.get(android + 'label') == 'TGWS DLC Proxy'
    assert any(x.get(android + 'name') == '.SettingsActivity' for x in app.findall('activity'))
    gradle = (ROOT / 'app/build.gradle').read_text()
    assert "versionName '1.1'" in gradle
    assert 'versionCode 3' in gradle
    assert "applicationId 'io.github.tgwsproxy.python114'" in gradle
    settings_gradle = (ROOT / 'settings.gradle').read_text()
    assert "rootProject.name = 'tgws-dlcproxy'" in settings_gradle


def test_home_has_only_two_primary_buttons():
    src = (JAVA / 'MainActivity.java').read_text()
    assert src.count('AppUi.button(') == 2
    assert 'Включить прокси' in src and 'Применить в Telegram' in src
    assert 'new Intent(this, SettingsActivity.class)' in src
    assert 'secret = ' not in src and 'pool = ' not in src
    assert 'new Intent(this, ProxyService.class)' in src
    assert 'Settings.link(this)' in src


def test_settings_keep_engine_arguments():
    src = (JAVA / 'SettingsActivity.java').read_text()
    for key in ('secret', 'port', 'pool', 'cf', 'h2', 'domains', 'dc2', 'dc4'):
        assert f'"{key}"' in src or f'Settings.{key}(this)' in src
    assert 'Settings.RUNNING.equals(state)' in src
    assert 'Settings.httpsLink(this)' in src
    assert '"worker_enabled"' in src and '"worker_domain"' in src
    assert 'workerEnabled.isChecked()' in src
    runner = (ROOT / 'app/src/main/python/mobile_runner.py').read_text()
    service = (JAVA / 'ProxyService.java').read_text()
    assert 'proxy_config.cfproxy_worker_domains = worker_domains' in runner
    assert 'Settings.workerEnabled(this), Settings.workerDomain(this)' in service


def test_build_workflow_updated():
    workflow = (ROOT / '.github/workflows/android-apk.yml').read_text()
    assert 'android-actions/setup-android@v4' in workflow
    assert 'name: tgws-dlcproxy-android' in workflow
    assert 'gradle --no-daemon :app:assembleDebug' in workflow


if __name__ == '__main__':
    test_brand_and_version()
    test_home_has_only_two_primary_buttons()
    test_settings_keep_engine_arguments()
    test_build_workflow_updated()
    print('PASS: UI/Gradle/workflow structural checks')
