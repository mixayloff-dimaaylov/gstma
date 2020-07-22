v4 clickhouse database layout
=============================

### Таблицы для входных данных

Таблицы, формируемые `logreader`.

#### rawdata.range

Источник: `logreader`  
*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE rawdata.range (
  time UInt64,
  adr Float64,
  psr Float64,
  cno Float64,
  locktime Float64,
  sat String,
  system String,
  freq String,
  glofreq Int32,
  prn Int32,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = MergeTree(d, (time, sat, freq), 8192)
TTL d + INVERVAL 2 WEEK DELETE
```

#### rawdata.satxyz2

Источник: `logreader`  
*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE rawdata.satxyz2 (
  time UInt64,
  geopoint UInt64,
  ionpoint UInt64,
  elevation Float64,
  sat String,
  system String,
  prn Int32,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = MergeTree(d, (time, sat), 8192)
TTL d + INVERVAL 2 WEEK DELETE
```

### Таблицы для расчетных данных

#### Обычные таблицы

##### computed.tec

Источник: *rawdata.range*  

```sql
CREATE TABLE computed.tec (
  time UInt64,
  sat String,
  sigcomb String,
  tec Float64
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
```

##### computed.tecFiltered

Источник: *rawdata.range*  

```sql
CREATE TABLE computed.tecFiltered (
  time UInt64,
  sat String,
  sigcomb String,
  tecavg Float64,
  tecdelta Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
```

#### Односекундные таблицы

##### computed.tecsigma

Источник: *rawdata.range*  

```sql
CREATE TABLE computed.tecsigma (
  time UInt64,
  sat String,
  sigcomb String,
  tecsigma Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
```

##### computed.s4

Источник: *rawdata.range*  
*Примечание:* s4 считается для частот, а не для их комбинаций.  

```sql
CREATE TABLE computed.s4 (
  time UInt64,
  sat String,
  freq Float64,
  s4 Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, freq), 8192)
```

