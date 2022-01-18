# ionosphere_iif

## Дочерние репозитории

- [clickhouse-grafana][clickhouse-grafana]
- [satmap-panel][satmap-panel]
- [clickhouse-proxy][clickhouse-proxy]
- [grafana][grafana]
- [logserver-spark][logserver-spark]
- [NovAtelLogReader][NovAtelLogReader]

## Установка через docker-compose

Compose-файл реализует простой кластер. Чтобы его запустить необходимо:

1. Склонировать репозиторий и собрать вычислительный кластер

```sh
git clone --recurse-submodules https://github.com/mixayloff-dimaaylov/ionosphere_iif.git
cd ionosphere_iif/
docker-compose build
```

2. Указать настройки для кластера

Настройки останутся в файле `.env`.

```sh
# Физический адрес необходим NovAtelLogReader при обращении к Kafka
# если NovAtelLogReader расположен на другом хосте
echo "KAFKA_CFG_ADVERTISED_LISTENERS='PLAINTEXT://<ip_адрес>:9092'" >> .env
```

3. Запустить кластер 

```sh
docker-compose up -d
```

4. Установить NovAtelLogReader и направить трафик вычислительному кластеру по
   указанному адресу

### Репликация из основного брокера Kafka

На базе compose-файла так же возможно реализовать схему репликации 1-в-2 с
подключением второго кластера к шине Kafka первого. Для подключения к исходному
кластеру необходимо:

- Указать уникальное значение `KAFKA_BROKER_ID`
- Указать адреса Zookeeper-нод существующего кластера в
  `KAFKA_CFG_ZOOKEEPER_CONNECT`
- Если новый кластер размещен на другом хосте -- указать физические адреса
  Kafka-брокеров `KAFKA_CFG_LISTENERS` в обоих кластерах. Тогда каждый из них
  сможет найти остальных
- Указать `KAFKA_CFG_ADVERTISED_LISTENERS`

Переменные можно указать в файл `.env`, тогда `docker-compose` подхватит его.

[clickhouse-grafana]: https://github.com/mixayloff-dimaaylov/clickhouse-grafana
[satmap-panel]: https://github.com/mixayloff-dimaaylov/satmap-panel
[clickhouse-proxy]: https://github.com/mixayloff-dimaaylov/clickhouse-proxy
[grafana]: https://github.com/stig888881/grafana
[logserver-spark]: https://github.com/mixayloff-dimaaylov/ionosphere
[NovAtelLogReader]: https://github.com/mixayloff-dimaaylov/NovAtelLogReader
