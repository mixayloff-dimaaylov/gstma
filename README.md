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
docker-compose --profile default build
```

2. Указать настройки для кластера

Настройки останутся в файле `.env`.

```sh
# Физический адрес необходим NovAtelLogReader при обращении к Kafka
# если NovAtelLogReader расположен на другом хосте
echo "KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://<ip_адрес>:9092" >> .env
```

3. Запустить кластер 

```sh
docker-compose --profile default up -d
```

4. Установить NovAtelLogReader и направить трафик вычислительному кластеру по
   указанному адресу

Дополнительная документация по развертыванию в папке [docs/deployment/][docs].

[clickhouse-grafana]: https://github.com/mixayloff-dimaaylov/clickhouse-grafana
[satmap-panel]: https://github.com/mixayloff-dimaaylov/satmap-panel
[clickhouse-proxy]: https://github.com/mixayloff-dimaaylov/clickhouse-proxy
[grafana]: https://github.com/stig888881/grafana
[logserver-spark]: https://github.com/mixayloff-dimaaylov/ionosphere
[NovAtelLogReader]: https://github.com/mixayloff-dimaaylov/NovAtelLogReader
[docs]: https://github.com/mixayloff-dimaaylov/ionosphere_iif/tree/master/docs/deploynment/cicd-branches.md
