Выгрузка данных из ClickHouse
=============================

На данный момент данных в ClickHouse хранятся ограниченное время, по истечению
которого удаляются. При необходимости можно выгрузить данные за прошлые сутки во
внешний файл. Для этого нужно использовать скрипт
[util/dump_yesterday.sh][dump], который можно запустить в локальном окружении,
если в нем установлен ClickHouse и `clickhouse-client`:

```sh
util/dump_yesterday.sh
```

Выгрузка будет сохранена в директории `/datadump`.

Либо, используя docker-контейнер:

```sh
docker run --rm -it \
       -v '/data/historical-datasets/Сияния на юге:/datadump' \
       -w '/datadump' \
       --network=gstma_default \
       --entrypoint util/dump_yesterday.sh \
       yandex/clickhouse-server:20.7
```

Формат выгрузки - [Parquet][parquet].

[dump]: ../../util/dump_yesterday.sh
[parquet]: https://parquet.apache.org
