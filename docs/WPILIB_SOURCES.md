# Первичные источники WPILib

Версия backend зафиксирована: 2026.2.1. Просмотрено 23 сентября 2026.

- Physics Simulation: https://docs.wpilib.org/en/stable/docs/software/wpilib-tools/robot-simulation/physics-sim.html
- Drivetrain model: https://docs.wpilib.org/en/stable/docs/software/wpilib-tools/robot-simulation/drivesim-tutorial/drivetrain-model.html
- Simulated hardware: https://docs.wpilib.org/en/stable/docs/software/wpilib-tools/robot-simulation/drivesim-tutorial/simulation-instance.html
- DCMotorSim API: https://github.wpilib.org/allwpilib/docs/release/java/edu/wpi/first/wpilibj/simulation/DCMotorSim.html
- BatterySim API: https://github.wpilib.org/allwpilib/docs/release/java/edu/wpi/first/wpilibj/simulation/BatterySim.html
- EncoderSim API: https://github.wpilib.org/allwpilib/docs/release/java/edu/wpi/first/wpilibj/simulation/EncoderSim.html
- AnalogGyro API: https://github.wpilib.org/allwpilib/docs/release/java/edu/wpi/first/wpilibj/AnalogGyro.html
- Official release tag: https://github.com/wpilibsuite/allwpilib/releases/tag/v2026.2.1
- Java dependencies: https://github.com/wpilibsuite/allwpilib/blob/v2026.2.1/wpimath/build.gradle
- Native publications: https://github.com/wpilibsuite/allwpilib/blob/v2026.2.1/shared/jni/publish.gradle
- HAL native basename: https://github.com/wpilibsuite/allwpilib/blob/v2026.2.1/hal/build.gradle
- Runtime loading: https://github.com/wpilibsuite/allwpilib/blob/v2026.2.1/wpiutil/src/main/java/edu/wpi/first/util/RuntimeLoader.java

Публичная справка release может показывать версию 2026.2.2; tag для зависимости намеренно не плавающий. Наличие описания API не заменяет успешную сборку.

## Сторонние компоненты
WPILib — отдельная библиотека с лицензией BSD-3-Clause; EJML, Jackson и Quickbuf — сторонние зависимости со своими лицензиями. Бинарные зависимости в этом исходном архиве не распространяются. При создании готового дистрибутива необходимо сохранить уведомления об их лицензиях. Внутри WPILib используются штатные нативные библиотеки JNI; собственный код адаптера — Java.
