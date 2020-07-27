v6 clickhouse database layout
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

#### Односекундные таблицы

##### computed.s4

Источник: *rawdata.range*  
*Примечание:* s4 считается для частот, а не для их комбинаций.  

```sql
CREATE TABLE computed.s4 (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    freq String COMMENT 'Частота, для которой рассчитано значение',
    s4 Float64 COMMENT 'S4',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, freq), 8192)
TTL d + INTERVAL 2 Week DELETE
```

#### Обычные таблицы

Источник: *rawdata.range*
*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.NT (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    sigcomb String COMMENT 'Комбинация сигналов',
    f1 Float64 COMMENT 'Частота 1',
    f2 Float64 COMMENT 'Частота 2',
    nt Float64 COMMENT 'ПЭС',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
TTL d + INTERVAL 2 Week DELETE;
```

Источник: *computed.NT*
*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.NTDerivatives (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    sigcomb String COMMENT 'Комбинация сигналов',
    f1 Float64 COMMENT 'Частота 1',
    f2 Float64 COMMENT 'Частота 2',
    avgNT Float64 COMMENT 'Среднее значение ПЭС',
    delNT Float64 COMMENT 'Значение флуктуаций ПЭС',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192) 
TTL d + INTERVAL 2 Week DELETE;
```

#### Односекундные таблицы

Источник: *computed.NTDerivatives*
*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.xz1 (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    sigcomb String COMMENT 'Комбинация сигналов',
    f1 Float64 COMMENT 'Частота 1',
    f2 Float64 COMMENT 'Частота 2',
    sigNT Float64 COMMENT 'Значение СКО флуктуаций ПЭС',
    sigPhi Float64 COMMENT 'Значение СКО флуктуаций фазы на фазовом экране',
    gamma Float64 COMMENT 'Значение параметра Райса (глубины общих замираний)',
    Fc Float64 COMMENT 'Значение интервала частотной корреляции',
    Pc Float64 COMMENT 'Значение интервала пространственной корреляции',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192) 
TTL d + INTERVAL 2 Week DELETE;
```

#### N - секундные таблицы

Источник: *computed.NTDerivatives*

```sql
CREATE TABLE computed.Tc (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    sigcomb String COMMENT 'Комбинация сигналов',
    Tc Float64 COMMENT 'Значение интервала временной корреляции',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
TTL d + INTERVAL 2 Week DELETE
```
