# ionosphere_iif

## Дочерние репозитории

- [clickhouse-grafana][clickhouse-grafana]
- [satmap-panel][satmap-panel]
- [clickhouse-proxy][clickhouse-proxy]
- [grafana][grafana]
- [logserver-spark][logserver-spark]
- [NovAtelLogReader][NovAtelLogReader]

## Установка через docker-compose

1. Склонировать репозиторий и собрать вычислительный кластер

```sh
git clone --recurse-submodules https://github.com/mixayloff-dimaaylov/ionosphere_iif.git
cd ionosphere_iif/
docker-compose up -d
```

2. Установить NovAtelLogReader и направить трафик вычислительному кластеру

[clickhouse-grafana]: https://github.com/mixayloff-dimaaylov/clickhouse-grafana
[satmap-panel]: https://github.com/mixayloff-dimaaylov/satmap-panel
[clickhouse-proxy]: https://github.com/mixayloff-dimaaylov/clickhouse-proxy
[grafana]: https://github.com/stig888881/grafana
[logserver-spark]: https://github.com/mixayloff-dimaaylov/ionosphere
[NovAtelLogReader]: https://github.com/mixayloff-dimaaylov/NovAtelLogReader
