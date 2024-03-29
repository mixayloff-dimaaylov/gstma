# Непрерывная интеграция

На базе Kafka можно реализовать ветвление исходных данных и реализовать две
ветки кластера: нестабильную, в которой будет вестись разработка (во всеми
вытекающими последствиями), и стабильную, которая будет прерываться только для
обновления к проверенным версиям. И, таким образом, получить что-то на подобие
CI/CD.

## Реализация с единым брокером Kafka

Например, можно реализовать 2 кластера: _prod_ и _testing_.

В кластере _prod_ будет работать основная шина Kafka, т.к. он будет реже
перезапускаться и поэтому будет более стабильным. Кластер _testing_ подключается
к Kafka из кластера _prod_:

```mermaid
flowchart LR
  N[NovAtelLogReader]

  subgraph clusters[Кластеры]
    subgraph c1["Кластер prod"]
      Z1[Zookeeper]; K1[Kafka]; S1[Spark]; C1[(ClickHouse)]

      Z1 <--> K1
      K1 --> S1
      S1 <--> C1
    end

    subgraph c2["Кластер testing"]
      S2[Spark]; C2[(ClickHouse)]

      S2<--> C2
    end
  end

  N --> K1
  K1 --> S2
```

Кластер _prod_ нужно запускать штатным образом с использованием штатных
настроек:

```sh
docker-compose --profile default up -d
```

Из кластера _testing_ нужно запустить только вычислительную часть.
Предварительно необходимо переопределить адрес шины, на которую подключается
`spark-streamer-1`, на адрес шины из другого кластера в _override_-файле Docker
Compose:

```
services:
  kafka:
    environment:
      KAFKA_HOST=<адрес_шины_в_другом_кластере>
```

Затем, после запуска Kafka в первом кластере, можно запустить кластер _testing_:

```sh
docker-compose --profile base up -d
```

## Репликация из основного брокера Kafka (не рекомендуется)

На базе compose-файла так же возможно реализовать схему репликации 1-в-2 с
подключением второго кластера к шине Kafka первого.

```mermaid
flowchart LR
  N[NovAtelLogReader]

  N --> K1
  K1 <--> K2

  subgraph clusters[Кластеры]
    subgraph c1["Кластер prod"]
      Z1[Zookeeper]; K1[Kafka]; S1[Spark]; C1[(ClickHouse)]

      Z1 <--> K1
      K1 --> S1
      S1 <--> C1
    end

    subgraph c2["Кластер testing"]
      Z2[Zookeeper]; K2[Kafka]; S2[Spark]; C2[(ClickHouse)]

      K2 --> Z1
      K2 --> S2
      S2 <--> C2
    end
  end
```

Кластер _prod_ нужно запускать штатным образом с использованием штатных
настроек:

```sh
docker-compose --profile default up -d
```

Для подключения к исходному кластеру необходимо переопределить переменные среды
в _override_-файле Docker Compose:

- Указать уникальное значение `KAFKA_BROKER_ID`
- Указать адреса Zookeeper-нод существующего кластера в
  `KAFKA_CFG_ZOOKEEPER_CONNECT`
- Если новый кластер размещен на другом хосте -- указать физические адреса
  Kafka-брокеров `KAFKA_CFG_LISTENERS` в обоих кластерах. Тогда каждый из них
  сможет найти остальных
- Указать `KAFKA_CFG_ADVERTISED_LISTENERS`

Затем, после запуска Kafka в первом кластере, можно запустить кластер _testing_:

```sh
docker-compose --profile default up -d
```

Zookeeper будет запущен, но не задействован.
