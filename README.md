# FileUpload for Codeberg

Android приложение для загрузки файлов в репозитории на [Codeberg.org](https://codeberg.org) (Forgejo API).

## Возможности

- Авторизация по Personal Access Token
- Список ваших репозиториев
- Выбор репозитория и пути для файла
- Выбор файла с устройства и загрузка (создание нового файла)
- Поддержка обновления существующего файла (с SHA)

## Как использовать

1. Создайте Personal Access Token на Codeberg:
   - Settings → Applications → Generate New Token
   - Права: `write:repository` (или полный доступ)

2. Откройте приложение, введите токен и сохраните.

3. Выберите репозиторий → укажите путь (например `docs/file.txt`) → выберите файл → Upload.

## Сборка

Откройте проект в Android Studio (Hedgehog+), синхронизируйте Gradle и запустите.

Минимальный SDK: 26  
Target SDK: 34

## API

Используется Forgejo/Gitea API v1:
- `POST /repos/{owner}/{repo}/contents/{filepath}` — создание файла
- `PUT /repos/{owner}/{repo}/contents/{filepath}` — обновление
- Контент передаётся в base64.

Base URL: `https://codeberg.org/api/v1`
