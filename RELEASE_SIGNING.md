# Иконка и постоянная подпись TGWS DLC Proxy

Обновление добавляет новую иконку во все необходимые Android-ресурсы и отдельный ручной workflow **Build signed release APK**. Обычный **Build Android APK** остается без изменений. `applicationId` приложения сохранен; версия 1.0 (versionCode 2) также сохранена.

## 1. Обновить исходники (Windows PowerShell)

```powershell
Set-Location "C:\Downloads\tg-ws-proxy-1.11.4-android-project\tg-ws-proxy-android-python-1.11.4"
git pull --rebase origin main
Expand-Archive -LiteralPath "C:\Downloads\tgws-dlcproxy-icon-signing-patch.zip" -DestinationPath (Get-Location).Path -Force
git add -A
git commit -m "Add launcher icon and signed release build"
git push origin main
```

## 2. Сгенерировать ключ ОДИН РАЗ, только на своем ПК

Для этого необходим установленный JDK с командой `keytool`. Проверить: `keytool -help`.

```powershell
keytool -genkeypair -v -keystore "$env:USERPROFILE\tgws-dlcproxy-release.jks" -alias tgws-dlcproxy -keyalg RSA -keysize 3072 -validity 10000 -storetype JKS
```

Ввести пароль хранилища, сведения о владельце (произвольные достоверные сведения) и пароль ключа (можно совпадающий с паролем хранилища). Сохранить оба пароля и alias `tgws-dlcproxy` в менеджере паролей.

**Не загружайте `.jks` в репозиторий, чат, Issue или Release.** Храните резервную копию `.jks` и паролей в защищенном месте. При утрате ключа обновления APK, подписанных им, будут невозможны без переустановки.

## 3. Создать секреты GitHub Actions

Перейти на https://github.com/qqmber965/tgws-android/settings/secrets/actions → **New repository secret**. Создать четыре секрета:

- `TGWS_SIGNING_KEYSTORE_BASE64` – Base64 от бинарного файла JKS (не путь к нему);
- `TGWS_SIGNING_STORE_PASSWORD` – пароль хранилища;
- `TGWS_SIGNING_KEY_ALIAS` – `tgws-dlcproxy`;
- `TGWS_SIGNING_KEY_PASSWORD` – пароль ключа.

Для копирования Base64 в буфер обмена PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\tgws-dlcproxy-release.jks")) | Set-Clipboard
```

Вставить содержимое буфера обмена в значение секрета `TGWS_SIGNING_KEYSTORE_BASE64`. После этого можно очистить буфер обмена: `Set-Clipboard -Value ""`.

## 4. Собрать подписанный APK

GitHub → **Actions** → **Build signed release APK** → **Run workflow** → **Run workflow**.

После успешного выполнения на странице запуска в **Artifacts** скачать `TGWS-DLC-Proxy-v1.0-signed-arm64`. Внутри находится `tgws-dlcproxy-v1.0-arm64.apk`.

В workflow автоматически проверяется цифровая подпись командой `apksigner verify`.

## 5. GitHub Releases

Перейти на https://github.com/qqmber965/tgws-android/releases/new, создать тег `v1.0`, название `TGWS DLC Proxy 1.0`, прикрепить именно **подписанный release APK** и нажать **Publish release**.

Важно: старый установленный `app-debug.apk` подписан другим (отладочным) ключом. Новую подписанную сборку поверх него Android не установит. Сохраните настройки и секрет прокси, удалите старую версию, затем установите release APK. Все последующие **release** APK должны подписываться тем же `.jks` и иметь увеличенный `versionCode` (например, 3 для версии 1.1). Тогда обновление поверх установленного релиза сохранит настройки.
