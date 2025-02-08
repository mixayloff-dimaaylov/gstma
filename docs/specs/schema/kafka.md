Kafka topic layout v1
=====================

Формат сообщений, используемый NovAtelLogReader для отправки показаний
GISTM-приёмника NovAtel GPStation-6 через Apach Kafka.

Ключи сообщений не заполняются и игнорируются.

Содержимое сообщений упаковано в бинарный формат AVRO. Каждое сообщение содержит
группу показаний, собранных в AVRO-массив (т.н. _чанкинг_) для оптимизации
доставки.

## Топики сообщений GISTM-приёмника

Топики логов приёмника NovAtel GPStation-6.

Названия топиков этой группы имеют префикс `datapoint-raw-`.

### datapoint-raw-ismdetobs

Топик лога `ISMDETOBS`.

[AVRO-схема сообщения](./avro/datapoint-raw-ismdetobs.avsc)

### datapoint-raw-ismrawtec

Топик лога `ISMRAWTEC`.

[AVRO-схема сообщения](./avro/datapoint-raw-ismrawtec.avsc)

### datapoint-raw-ismredobs

Топик лога `ISMREDOBS`.

[AVRO-схема сообщения](./avro/datapoint-raw-ismredobs.avsc)

### datapoint-raw-psrpos

Топик лога `PSRPOS`.

[AVRO-схема сообщения](./avro/datapoint-raw-psrpos.avsc)

### datapoint-raw-range

Топик лога `RANGE`.

[AVRO-схема сообщения](./avro/datapoint-raw-range.avsc)

### datapoint-raw-satvis

Топик лога `SATVIS`.

[AVRO-схема сообщения](./avro/datapoint-raw-satvis.avsc)

### datapoint-raw-satxyz2

Топик лога `SATXYZ2`.

[AVRO-схема сообщения](./avro/datapoint-raw-satxyz2.avsc)
