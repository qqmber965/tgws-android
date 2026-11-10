TGWS DLC Proxy 1.0 - исправление несоответствия UI и тестов

Заменяет устаревший MainActivity.java экраном с двумя кнопками, приводит
настройки, сервис, Gradle и debug workflow к одному выпуску.
Python-ядро прокси и ключи подписания не затрагиваются.

1. Перед распаковкой выполните git pull --rebase origin main.
2. Распакуйте содержимое этого архива поверх КОРНЯ репозитория с заменой файлов.
3. Проверьте: python tests/test_android_ui_contract.py
4. Отправьте изменения: git add -A; git commit -m "Fix UI source synchronization"; git push origin main
5. В GitHub Actions запустите Build signed release APK.

Нельзя выкладывать keystore (.jks), пароли или файл с Base64 в GitHub.
