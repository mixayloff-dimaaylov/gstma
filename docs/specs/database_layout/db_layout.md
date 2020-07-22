v2 clickhouse database layout

### Таблицы для входных данных

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
```

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
```

### Таблицы для расчетных данных

#### Обычные таблицы

```sql
CREATE TABLE computed.tec (
  time UInt64,
  system String,
  sat String,
  prn Int32,
  sigcomb String,
  tec Float64
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat), 8192)
```

```sql
CREATE TABLE computed.tecFiltered (
  time UInt64,
  system String,
  sat String,
  prn Int32,
  tecavg Float64,
  tecdelta Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat), 8192)
```

#### Односекундные таблицы

```sql
CREATE TABLE computed.tecsigma (
  time UInt64,
  system String,
  sat String,
  prn Int32,
  sigcomb String,
  tecsigma Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat), 8192)
```

```sql
CREATE TABLE computed.s4 (
  time UInt64,
  system String,
  sat String,
  prn Int32,
  sigcomb String,
  freq Float64,
  s4 Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat), 8192)
```

