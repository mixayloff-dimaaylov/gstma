# GSTMA - GISTM Small-scale TEC Monitor and Analyzer

GSTMA - система мониторинга мелкомасштабного ПЭС, основанная на фреймворке
Apache Spark, и прочее вспомогательное ПО.

## Дочерние репозитории

- [NovAtelLogReader][NovAtelLogReader]
- [clickhouse-grafana][clickhouse-grafana]
- [satmap-panel][satmap-panel]

## Установка через docker compose

По-умолчанию compose-файл реализует простой кластер. Чтобы его запустить
необходимо:

1. Склонировать репозиторий и собрать контейнеры кластера

```sh
git clone --recurse-submodules https://github.com/mixayloff-dimaaylov/gstma.git
cd gstma/
docker compose --profile default build
```

2. Указать настройки для кластера

Настройки указываются в `docker-compose.override.yml`-файла Docker Compose через
переменные среды:

```yaml
version: "3.4"

services:
  kafka:
    environment:
      # Физический адрес необходим NovAtelLogReader при обращении к Kafka
      # если NovAtelLogReader расположен на другом хосте
      KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://<ip_адрес>:9092

  spark-teccalculationv2:
    environment:
      # Координаты приёмника
      REC_LAT: '45.0409515'
      REC_LON: '41.9108996'
      REC_ALT: '652.1387'

  reporter:
    environment:
      # Часовой пояс для скриншотов
      REPORTER_TZ: 'Europe/Moscow'
    volumes:
      # Раздел для сохранения скриншотов
      - '/data/grafana-reporter/archives/:/usr/src/app/archives'

  reporter-webdriver:
    environment:
      # Часовой пояс для скриншотов
      WEBDRIVER_TZ: 'Europe/Moscow'
```

3. Создать БД и указать параметры исследуемого сигнала

```sh
docker compose --profile default up -d clickhouse
docker compose --profile default exec clickhouse{,-client}
```

Выполнить запись параметров в SQL-консоли:

```sql
INSERT INTO misc.target_signal_params (f0, sigPhiCoef, R_T, B_S) Values (1.6e9, 2, 64e3, 1)
```

4. Запустить кластер

```sh
docker compose --profile default up -d
```

5. Установить NovAtelLogReader и направить трафик вычислительному кластеру по
   указанному адресу

Дополнительная документация по развертыванию в папке [docs/appnotes/][docs].

[clickhouse-grafana]: https://github.com/mixayloff-dimaaylov/clickhouse-grafana
[docs]: ./docs/appnotes/
[NovAtelLogReader]: https://github.com/mixayloff-dimaaylov/NovAtelLogReader
[satmap-panel]: https://github.com/mixayloff-dimaaylov/satmap-panel
