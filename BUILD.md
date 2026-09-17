# Сборка EasyEUICC с русской локализацией

Исходники лежат в каталоге `openeuicc/` (распакованный архив `OpenEUICC-unpriv-v1.7.2.zip`).

Почему пересобранная версия не видит профили в слотах SIM — см. [ESIM-ACCESS.md](ESIM-ACCESS.md):
доступ к съёмной карте eSIM выдаётся по отпечатку сертификата подписи, а он у своей сборки другой.

## Версии подмодулей — важно

В архиве каталоги подмодулей пустые, а их коммиты нигде не записаны. Версии обязательно должны
совпадать с теми, на которых закреплён релиз `unpriv-v1.7.2`, иначе приложение собирается, проходит
проверку совместимости, но не видит eSIM в слотах (нативная библиотека `lpac` из `master`
несовместима с этой версией `lpac-jni`):

| Подмодуль | Путь | Коммит |
| --- | --- | --- |
| lpac | `openeuicc/libs/lpac-jni/src/main/jni/lpac` | `d214738fa0bdb23faf5833d3d798963079a00468` |
| cJSON | `openeuicc/libs/lpac-jni/src/main/jni/cjson/cjson` | `c859b25da02955fef659d658b8f324b5cde87be3` |

```bash
cd openeuicc/libs/lpac-jni/src/main/jni
git clone https://github.com/estkme-group/lpac.git lpac
git -C lpac checkout d214738fa0bdb23faf5833d3d798963079a00468
git clone https://github.com/DaveGamble/cJSON cjson/cjson
git -C cjson/cjson checkout c859b25da02955fef659d658b8f324b5cde87be3
```

Проверка после сборки: файлы `lib/*/liblpac-jni.so` в собранном APK должны быть побайтово равны
файлам из официального `app-unpriv-release.apk` версии 1.7.2.

## Окружение

- JDK 21 (в CI проекта используется 17, оба подходят);
- Android SDK: `platforms;android-37.0`, `build-tools;37.0.0`, `ndk;26.1.10909125`, `cmake;3.22.1`;
- Gradle и AGP берутся из wrapper'а (Gradle 9.5, AGP 9.3.0).

```bash
cd openeuicc
echo "sdk.dir=$ANDROID_HOME" > local.properties
cp ../signing/keystore.properties.example keystore.properties
./gradlew test :app-unpriv:assembleRelease
```

Готовый файл: `openeuicc/app-unpriv/build/outputs/apk/release/app-unpriv-release.apk`.

## Версия в APK

`versionCode` и `versionName` берутся из git (`git rev-list --count HEAD` и `git describe --always
--tags --dirty`). Чтобы в APK попало `v1.7.2-unpriv`, перед сборкой нужен тег на текущем коммите
и чистое рабочее дерево:

```bash
git tag -f unpriv-v1.7.2 HEAD
```
