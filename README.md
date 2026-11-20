Blood: Fresh Supply — порт на Android (arm64). Исходники
=========================================================

Автор порта: Telegram @VestronVulture

Здесь то, что написано для этого порта: лаунчер, раскладка тач-оверлея, сборочные скрипты и
инструменты, которыми собран пак данных. Движок (Raze 1.11) и стоковый оверлей (Zeta Touch 3.4)
взяты готовыми и НЕ патчились — их исходники лежат в upstream, нужные бинарники (.so, raze_dev.pk3,
raze.sf2, арт виджетов) включены сюда, чтобы APK собирался и сразу работал.


Структура
---------
    launcher/
        app/AndroidManifest.xml   две активности (лаунчер и SDLActivity); игра живёт в процессе :game
        app/java/                 31 java-файл: лаунчер (com.bloodtouch) + слой SDL/OpenTouch (org.libsdl.app2012)
        app/res/                  иконка, стили, строки
        assets/                   131 PNG виджетов, 7 XML раскладки, raze_dev.pk3, raze.sf2
        jniLibs/arm64-v8a/        10 библиотек: libraze_dev.so, libtouchcontrols.so, SDL2, SDL2_image,
                                  SDL2_mixer, openal, zmusic, hidapi, GL4ES, saffal
        build.sh                  сборка APK без Gradle: aapt2 + javac + d8 + zipalign + apksigner
        add_payload.py            вкладывает classes.dex и jniLibs в собранный APK

    tools/                        45 скриптов, которыми собирался пак и диагностировалось поведение
    install/                      скрипты установки на телефон через adb
    INSTALL.txt                   инструкция для игрока (та же, что в игровом архиве)

    build/ (в архив не попал)      выход сборки: base.apk, classes/, dex/, gen/, res.zip


Чем собирается
--------------
  • Android SDK: build-tools 34.0.0 и platforms/android-34
  • JDK, годный на `javac --release 8` (собиралось на 17)
  • R8 jar для d8 (`com.android.tools.r8.D8`)
  • debug keystore с псевдонимом androiddebugkey (пароль android)
  • Python 3 с Pillow — для add_payload.py и почти всех инструментов
  • adb — только для install/ и прогонов диагностики

Пути в `launcher/build.sh` берутся из расположения самого скрипта, так что дерево можно положить
куда угодно. Тулчейн по умолчанию — `G:/Android/Sdk`, `G:/blood-android/tools/r8.jar` и
`G:/blood-android/apk/debug.keystore`; переопределяются переменными:

    SDK=G:/Android/Sdk R8=/путь/r8.jar KS=/путь/debug.keystore sh launcher/build.sh

А вот `tools/*` рассчитывают на дерево в `G:\blood_port` (PACK, FS и подобные константы в первых
строках каждого скрипта) — это уже подправь под себя, если хочешь запускать их.

Сборка:

    sh launcher/build.sh
    cp -f launcher/build/bloodtouch.apk bloodtouch.apk

Скрипт сам пишет прогресс по шагам [1/7]..[7/7] и в конце показывает отпечаток подписи и размер.
Проверено на двух разных каталогах — APK собирается одинаковый (подпись не байт-в-байт: apksigner
кладёт время в сигнатуру).


Что внутри, коротко
-------------------
Лаунчер. Один экран со всеми настройками, всё применяется аргументами движку: свечение и блики
(`gl_bloom`/`gl_lens`), апскейл HQ4x, воксели, пресет управления 1997 года, потолок кадров. Отдельно
он при каждом старте копирует `assets/*.xml` в `user_files/touch_layouts` — раскладка кнопок живёт
обычными файлами и правится в игре через Options -> Customize Controls.

Звук. `+set i_soundinbackground 1 +set i_pauseinbackground 0` — обязательны: на потере фокуса Raze
вызывает `alcDevicePauseSOFT`/`ResumeSOFT`, и Oboe-бэкенд openal-soft на Android на этом вешается.

HD-ассеты. Катсцены конвертированы из Refreshed Supply в `.ivf` (VP8 720p) с озвучкой `.ogg`,
саундтрек — оцифровка SC-55 в `music/*.ogg`; `blood.ini` и ini аддонов приведены к виду, который
читает Raze (в нём нет KEX-таблицы строк, поэтому `$токены` развёрнуты в текст).

Сторож. `RenderWatchdog` пишет `user_files/logs/hang_<время>.txt`, если поток отрисовки не жрёт CPU
или главный поток не делает handler beat дольше 6 секунд: состояния всех потоков из `/proc/self/task`,
Java-стеки и хвост журнала движка.


Инструменты (tools/)
--------------------
  Пак и ассеты
    enc_ivf.sh, enc_ivf1080.sh, enc_all_movies.sh  Smacker/ogv -> .ivf (VP8), пакетно по всем роликам
    ogvprobe.py                                    чем именно перекодировать: битрейты, пиксели, звук
    detokenize.py                                  разворачивает KEX-`$токены` в аддонных ini
    inicheck.py                                    эмулирует загрузку level-def из Raze по паку на диске
    cutscenecheck.py                               копия проверок campaignInfo() из лаунчера, на диске
    musicaudit.py, redbookcheck.py                 какие треки и куда движок подхватит по mus_extendedlookup
    artdiff.py, surfacediff.py                     чем наш tiles/surface отличается от купленного пака
    patch_pk3.py                                   выключает яркость/гамму в menudef.txt внутри raze_dev.pk3
    packdiff.py                                    сверка состава пака

  Раскладка тача
    make_layouts.py                                генерирует все шесть XML групп виджетов + настройки
    preview_layout.py, check_geom.py, calibrate.py чем это выглядит на экране и где tap-зоны пересекаются
    make_enter_round.py                            круглый арт ENTER в стиле стоковых дисков
    set_slider.py                                  перебор атрибутов slide_mouse (полоса яркости)
    statdiff.py                                    какие layout-файлы движок переписал во время прогонов

  Диагностика на телефоне
    probe_tap.py, gamepid.py, sdlstat.py, alive.py тапы по реальным rect'ам, pid процесса :game,
                                                   CPU-время треда отрисовки, живой ли экран
    probe.sh, h2.sh, h3.sh, h4.sh, hang.sh         прогоны-воспроизводители зависаний
    slider_test.sh                                 три варианта сборки, чтобы убрать полосу яркости
    autoinstall.sh, wait_and_run.sh                ждут телефон и сами ставят/прогоняют
    elfinfo.py, depcheck.py, findwrites.py         что и от чего зависит в .so, кто пишет в память
    make_zip.py                                    пакование игрового архива


Чего в архиве нет
-----------------
Игровых данных. Ни Fresh Supply, ни Refreshed Supply сюда не входят — это купленные файлы, их
распространять не нужно; в `INSTALL.txt` описано, куда их положить. Также не попали логи, скриншоты
и артефакты прогонов из `work/` — это рабочий мусор конкретной сессии.


Известные недочёты этой сборки
------------------------------
  • Полоса регулировки яркости справа в меню всё ещё рисуется. `hidden="1"` движок для виджета
    `slide_mouse` игнорирует, а удалить тег из XML нельзя — пропущенный тег берётся со встроенным
    дефолтным прямоугольником. Подбор рабочего атрибута идёт через `tools/slider_test.sh`.
  • Зависание по «назад» в Options снаружи не воспроизводится (ни системным BACK/ESC, ни тапом по
    самой кнопке `<`, ни тремя циклами подряд) — за причиной теперь следит RenderWatchdog.
