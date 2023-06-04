# Change Log

## _unreleased_

### Changed

- **dependencies:** Обновление Grafana с `9.1.3` до `9.5.1`
  ([#58](https://github.com/mixayloff-dimaaylov/gstma/pull/58))

- **breaking(API):** Версия таблиц ClickHouse обновлена с 16.1 до 18
  ([#60](https://github.com/mixayloff-dimaaylov/gstma/pull/60),
  [#62](https://github.com/mixayloff-dimaaylov/gstma/pull/62))

### Added

- **grafana:** Добавлена поддержку тепловых карт плагина GeoMap, официальный
  _datasource_-плагин Grafana ClickHouse и тепловая карты индекса мерцаний $S_4$
  ([#62](https://github.com/mixayloff-dimaaylov/gstma/pull/62)).

  Плагин Grafana ClickHouse поставляет дополнительные дашборды для мониторинга
  состояния ClickHouse.

- **grafana:** Добавлены правила и шаблоны уведомлений обнаружения превышений
  $S_4$ и отсутствия данных при помощи _Grafana Unified Alerting_
  ([#58](https://github.com/mixayloff-dimaaylov/gstma/pull/58))

- **grafana:** Добавлена поддержку тепловых карт плагина GeoMap и тепловая карты
  индекса мерцаний $S_4$
  ([#62](https://github.com/mixayloff-dimaaylov/gstma/pull/62))

- **docs:** Добавлена справка по подключению _Grafana Unified Alerting_ к
  Telegram ([#58](https://github.com/mixayloff-dimaaylov/gstma/pull/58))

- **docs:** Добавлены схем алгоритма расчетов и используемого технологического
  стека ([#59](https://github.com/mixayloff-dimaaylov/gstma/pull/59))

- Добавлен расчет вертикального ПЭС $N_T$
  ([#60](https://github.com/mixayloff-dimaaylov/gstma/pull/60))

- **logserver-spark:** Добавлены колонки `geopointStr`, `ionpointStr` со
  строковым GeoHash в таблице `rawdata.satxyz2` для работы плагина Grafana
  GeoMap ([#62](https://github.com/mixayloff-dimaaylov/gstma/pull/62))

### Fixed

### Removed

## [0.3.0] - 2023-05-01

### Changed

- **dependencies:** Обновление Grafana с 6 до 9.1
  ([#28](https://github.com/mixayloff-dimaaylov/gstma/pull/28))

- **dependecies:** Apache Spark обновлен с `2.2.1-hadoop2.7` до
  `3.3.0-hadoop3.3`
  ([#47](https://github.com/mixayloff-dimaaylov/gstma/pull/47))

- **dependencies:** ClickHouse обновлен с `20.5.2.7` до `20.7`
  ([#47](https://github.com/mixayloff-dimaaylov/gstma/pull/47))

- **breaking(V1, V2):** Адрес ClickHouse и координаты GPS-приёмника теперь
  являются **обязательными** аргументами коммадной строки (CLI)
  ([#8](https://github.com/mixayloff-dimaaylov/gstma/pull/8))

- **breaking(API):** Версия таблиц ClickHouse обновлена с 6 до 16.1
  ([#11](https://github.com/mixayloff-dimaaylov/gstma/pull/11))

- **dependencies:** Отказ от Geoserver в пользу OpenStreetMaps
  ([`460edc7`](https://github.com/mixayloff-dimaaylov/gstma/commit/460edc7),
  [`ec589d3`](https://github.com/mixayloff-dimaaylov/gstma/commit/ec589d3),
  [`c07f782`](https://github.com/mixayloff-dimaaylov/gstma/commit/c07f782))

- Теперь проект лицензируется под лицензией Apache 2.0
  ([#55](https://github.com/mixayloff-dimaaylov/gstma/pull/55))

### Added

- **logserver-spark:** Алгоритм расчёта ПЭС и характеристик ионосферы портирован
  на _Structured Streaming_
  ([#47](https://github.com/mixayloff-dimaaylov/gstma/pull/47),
  [#52](https://github.com/mixayloff-dimaaylov/gstma/pull/52))

- **logserver-spark:** Возвращены старые логи `ISMRAWTECB`, `ISMDETOBSB`,
  `ISMREDOBSB` для целей отладки
  ([#10](https://github.com/mixayloff-dimaaylov/gstma/pull/10),
  [#37](https://github.com/mixayloff-dimaaylov/gstma/pull/37))

- **logserver-spark:** Добавлен рассчёт $N_{T adr}$ в целях отладки
  ([#37](https://github.com/mixayloff-dimaaylov/gstma/pull/37))

- **logserver-spark:** Добавлен расчет $DNT$ с учётом $RDBC$
  ([#13](https://github.com/mixayloff-dimaaylov/gstma/pull/13),
  [#15](https://github.com/mixayloff-dimaaylov/gstma/pull/15))

- **build:** Добавлена поддержка сборки Docker-контейнера Grafana со всеми
  плагинами ([#9](https://github.com/mixayloff-dimaaylov/gstma/pull/9),
  [#12](https://github.com/mixayloff-dimaaylov/gstma/pull/12))

- **build:** Добавлена поддержка сборки Docker Compose для воспроизведения
  кластера ([#9](https://github.com/mixayloff-dimaaylov/gstma/pull/9), 12,
  [#17](https://github.com/mixayloff-dimaaylov/gstma/pull/17))

- **build:** Добавлено использование субмодулей
  ([#9](https://github.com/mixayloff-dimaaylov/gstma/pull/9),
  [#50](https://github.com/mixayloff-dimaaylov/gstma/pull/50))

- Добавлена базовая поддержка тепловых карт

- Добавлена поддержка Ammonite, Bloop, LSP Metals
  ([#14](https://github.com/mixayloff-dimaaylov/gstma/pull/14))

- **grafana:** Добавлена панель-монитор спутников для наблюдения числа видимых
  спутников ([#49](https://github.com/mixayloff-dimaaylov/gstma/pull/49))

- Добавлены автоматические усредняющие таблицы `computed.*` и дашборд Суточный
  мониторинг для экономии места в ClickHouse
  ([#16](https://github.com/mixayloff-dimaaylov/gstma/pull/16),
  [#20](https://github.com/mixayloff-dimaaylov/gstma/pull/20),
  [#36](https://github.com/mixayloff-dimaaylov/gstma/pull/36))

- Добавлен Скрипт `reporter` на базе Selenium для автоматического создания
  скриншотов ([#16](https://github.com/mixayloff-dimaaylov/gstma/pull/16),
  [#18](https://github.com/mixayloff-dimaaylov/gstma/pull/18),
  [#32](https://github.com/mixayloff-dimaaylov/gstma/pull/32))

- Добавлена утилита `plotter` для отладки/отрисовки графиков на базе Jupyter Lab
  ([#31](https://github.com/mixayloff-dimaaylov/gstma/pull/31),
  [#33](https://github.com/mixayloff-dimaaylov/gstma/pull/33),
  [#42](https://github.com/mixayloff-dimaaylov/gstma/pull/42),
  [#48](https://github.com/mixayloff-dimaaylov/gstma/pull/48),
  [#51](https://github.com/mixayloff-dimaaylov/gstma/pull/51))

- Добавлена поддержка Jupyter Lab с поддержкой Scala (Almond)
  ([#39](https://github.com/mixayloff-dimaaylov/gstma/pull/39),
  [#41](https://github.com/mixayloff-dimaaylov/gstma/pull/41),
  [#43](https://github.com/mixayloff-dimaaylov/gstma/pull/43),
  [#45](https://github.com/mixayloff-dimaaylov/gstma/pull/45))

- **docs:** Справка по реализации частичного CI/CD
  ([#40](https://github.com/mixayloff-dimaaylov/gstma/pull/40))

- **docs:** Добавлены схемы кластера
  ([#51](https://github.com/mixayloff-dimaaylov/gstma/pull/51))

- **docs:** Добавлен CHANGELOG.md
  ([#56](https://github.com/mixayloff-dimaaylov/gstma/pull/56))

### Fixed

- **logserver-spark:** Исправлена ошибка запуска контейнера
  ([#1](https://github.com/mixayloff-dimaaylov/gstma/pull/1))

- **logserver-spark:** Исправлено преобразование частот
  ([`8a0cbc5`](https://github.com/mixayloff-dimaaylov/gstma/commit/8a0cbc5))

- **logserver-spark:** Исправлена работа Kafka при подключении нескольких
  кластеров к общей шине
  ([`d05e66b`](https://github.com/mixayloff-dimaaylov/gstma/commit/d05e66b))

- **logserver-spark:** Исправлена проблема всплесков фильтров, мешающих
  обнаружению МИО, путем отсечки начальных значений фильтров
  ([#21](https://github.com/mixayloff-dimaaylov/gstma/pull/21),
  [#22](https://github.com/mixayloff-dimaaylov/gstma/pull/22))

- **logserver-spark:** Исправлены срывы ПЭС из-за неправильной синхронизации
  вычислителя и ClickHouse
  ([#23](https://github.com/mixayloff-dimaaylov/gstma/pull/23),
  [#27](https://github.com/mixayloff-dimaaylov/gstma/pull/27))

- **logserver-spark:** Исправлено неверное выражение $S_4$
  ([#29](https://github.com/mixayloff-dimaaylov/gstma/pull/29))

- **logserver-spark:** Исправлена степень в выражении $\Sigma_{\varphi}$
  ([#30](https://github.com/mixayloff-dimaaylov/gstma/pull/30))

- **grafana:** Исправлена отрисовка $\sigma_{N_T}$
  ([#19](https://github.com/mixayloff-dimaaylov/gstma/pull/19))

- **grafana:** Исправлена отрисовка в миллисекундном приближении
  ([#25](https://github.com/mixayloff-dimaaylov/gstma/pull/25),
  [#35](https://github.com/mixayloff-dimaaylov/gstma/pull/35))

### Removed

- **build:** Отказ от поддержки сборки и установки из установочного ISO-образа,
  т.к. теперь используется Docker Compose. Файлы GitLFS и git-annex,
  документация по сборке удалены
  ([#54](https://github.com/mixayloff-dimaaylov/gstma/pull/54))

- Утилита `rawdump.sh` добавлена и удалена как устаревшая
  ([#24](https://github.com/mixayloff-dimaaylov/gstma/pull/24),
  [#31](https://github.com/mixayloff-dimaaylov/gstma/pull/31),
  [#33](https://github.com/mixayloff-dimaaylov/gstma/pull/33),
  [#38](https://github.com/mixayloff-dimaaylov/gstma/pull/38),
  [#42](https://github.com/mixayloff-dimaaylov/gstma/pull/42))

## [0.2.0] - 2020-08-24

:seedling: Initial release.

[0.3.0]: https://github.com/mixayloff-dimaaylov/gstma/releases/tag/0.3.0
[0.2.0]: https://github.com/mixayloff-dimaaylov/gstma/releases/tag/0.2.0
