# ionosphere_iif

## Дочерние репозитории

- https://github.com/mixayloff-dimaaylov/clickhouse-grafana
- https://github.com/mixayloff-dimaaylov/satmap-panel
- https://github.com/mixayloff-dimaaylov/clickhouse-proxy
- https://github.com/stig888881/grafana
- https://github.com/mixayloff-dimaaylov/ionosphere
- https://github.com/mixayloff-dimaaylov/NovAtelLogReader

## Установка через docker-compose

1. Склонировать репозиторий и собрать вычислительный кластер

```sh
git clone --recurse-submodules https://github.com/mixayloff-dimaaylov/ionosphere_iif.git
cd ionosphere_iif/
docker-compose up -d
```

2. Установить NovAtelLogReader и направить трафик вычислительному кластеру
