# Проверка UBOR R02 / WPILib

Дата выпуска: 23 сентября 2026. Среда: Linux x86-64, OpenJDK 21.0.11.

| Объект | Результат | Доказательство |
|---|---|---|
| Компиляция robot-core + robot-app | PASS | offline-build.log; software/dist/ubor-sim.jar |
| Прежние проверки ядра и интеграции | PASS, 50 assertions | r02-core/ |
| Граница сменной физики | PASS, 20 assertions, тестовая модель | r02-boundary/ |
| Загрузка/валидация параметров | PASS, 9 assertions, без WPILib | r02-parameters/ |
| Реальный TCP/HMAC loopback | PASS, 40 обменов, останов после отключения | network-legacy-r02.txt |
| Синтаксис Java | PASS, 37 файлов, только parse | java-parse.txt |
| Компиляция модуля robot-wpilib | NOT RUN | wpilib-build-attempt.txt; Maven отсутствует |
| Загрузка WPILib | BLOCKED | dependency-download-attempt.txt; DNS/соединение недоступны |
| Native HAL / WPILib runtime | NOT RUN | wpilib-launch-attempt.txt |
| 25 проверок WpiVerification | Подготовлены; НЕ выполнены | software/robot-wpilib/src/main/java/com/ubor/wpilib/WpiVerification.java |
| Windows / macOS | НЕ выполнено | предусмотрены скрипт и профили, без подтверждённого запуска |
| Raspberry Pi / Pi4J / DL4J / аппаратная часть | НЕ выполнено в R02 | исходные ограничения R01 сохраняются |

**Нельзя считать 50+20+9 проверок доказательством работающей WPILib.** В тестах границы используется класс Probe с именем `TEST_DOUBLE_NOT_WPILIB`. Он не подменяет классы edu.wpi.first.* и не входит в WPILib backend.

Выполненная проверка синтаксиса использует JavacTask.parse(): импорты и сигнатуры внешних библиотек ею НЕ проверяются. Никакие суррогатные классы WPILib для имитации успешной компиляции не создавались.

Для полной проверки: из `software` выполнить `mvn -Pwpilib clean verify`. Отчёт нового запуска должен появиться в `robot-wpilib/target/wpilib-report/result.json`. Только статус PASS этого нового запуска подтверждает выполнение подготовленных WPILib-сценариев. В начале проверки отчёт заменяется на RUNNING_NOT_YET_PASSED, чтобы не оставлять ложный старый PASS при аварийном завершении.

Механика, электронные схемы и ML не стали автоматически верифицированными после добавления симулятора. Модель движения требует калибровки по физическому роботу; моменты инерции и параметры двигателей пока задают гипотезу.
