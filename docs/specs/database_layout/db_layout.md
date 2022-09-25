v14 clickhouse database layout
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
TTL d + INVERVAL 24 HOUR DELETE
```

#### rawdata.ismredobs

Источник: `logreader`  
*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE IF NOT EXISTS rawdata.ismredobs (
  time UInt64,
  totals4 Float64,
  sat String,
  system String,
  freq String,
  glofreq Int32,
  prn Int32,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 24 HOUR DELETE
SETTINGS index_granularity=8192
```

#### rawdata.ismdetobs

Источник: `logreader`  
*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE IF NOT EXISTS rawdata.ismdetobs (
  time UInt64,
  power Float64,
  sat String,
  system String,
  freq String,
  glofreq Int32,
  prn Int32,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 24 HOUR DELETE
SETTINGS index_granularity=8192
```

#### rawdata.ismrawtec

Источник: `logreader`  
*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE IF NOT EXISTS rawdata.ismrawtec (
  time UInt64,
  tec Float64,
  sat String,
  system String,
  primaryfreq String,
  secondaryfreq String,
  glofreq Int32,
  prn Int32,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, primaryfreq, secondaryfreq)
TTL d + INTERVAL 24 HOUR DELETE
SETTINGS index_granularity=8192
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
TTL d + INVERVAL 24 HOUR DELETE
```

### Таблицы для расчетных данных

#### Односекундные таблицы

##### computed.range

Источник: *rawdata.range*  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE MATERIALIZED VIEW computed.range
ENGINE = MergeTree
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 1 MONTH DELETE
POPULATE AS
SELECT
    time,
    avg(adr) AS adr,
    avg(psr) AS psr,
    avg(cno) AS cno,
    any(locktime) AS locktime,
    sat,
    any(system) AS system,
    freq,
    any(glofreq) AS glofreq,
    any(prn) AS prn,
    any(d) AS d
FROM rawdata.range
GROUP BY
    (intDiv(time, 1000) * 1000) AS time,
    sat,
    freq
```

##### computed.ismredobs

Источник: *rawdata.ismredobs*  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE MATERIALIZED VIEW computed.ismredobs
ENGINE = MergeTree
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 1 MONTH DELETE
SELECT
    time,
    totals4,
    sat,
    system,
    freq,
    glofreq,
    prn,
    d
FROM rawdata.ismredobs
```

##### computed.ismdetobs

Источник: *rawdata.ismdetobs*  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE MATERIALIZED VIEW computed.ismdetobs
ENGINE = MergeTree
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 1 MONTH DELETE
POPULATE AS
SELECT
    time,
    power,
    sat,
    system,
    freq,
    glofreq,
    prn,
    d
FROM rawdata.ismdetobs
```

##### computed.ismrawtec

Источник: *rawdata.ismrawtec*  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE MATERIALIZED VIEW computed.ismrawtec
ENGINE = MergeTree
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, primaryfreq, secondaryfreq)
TTL d + INTERVAL 1 MONTH DELETE
POPULATE AS
SELECT
    time,
    tec,
    sat,
    system,
    primaryfreq,
    secondaryfreq,
    glofreq,
    prn,
    d
FROM rawdata.ismrawtec
```

##### computed.satxyz2

Источник: *rawdata.satxyz2*  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE MATERIALIZED VIEW computed.satxyz2
ENGINE = MergeTree
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat)
TTL d + INTERVAL 1 MONTH DELETE
POPULATE AS
SELECT
    time,
    geopoint,
    ionpoint,
    elevation,
    sat,
    system,
    prn,
    d
FROM rawdata.satxyz2
```

##### computed.s4

Источник: *rawdata.range*  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.s4 (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    sigcomb String COMMENT 'Комбинация сигналов, для которой рассчитано значение',
    s4 Float64 COMMENT 'S4',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
TTL d + INTERVAL 1 MONTH DELETE
```

##### computed.s4pwr

Источник: *rawdata.range*  
*Примечание:* Эта S4 считается для частот, а не для их комбинаций.  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.s4pwr (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    freq String COMMENT 'Частота, для которой рассчитано значение',
    s4 Float64 COMMENT 'S4',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, freq), 8192)
TTL d + INTERVAL 1 MONTH DELETE
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
    psrNt Float64 COMMENT 'ПЭС псевдодальностный',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
TTL d + INTERVAL 1 MONTH DELETE;
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
TTL d + INTERVAL 1 MONTH DELETE;
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
TTL d + INTERVAL 1 MONTH DELETE;
```

#### N - секундные таблицы

Источник: *computed.NTDerivatives*
*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.Tc (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    sigcomb String COMMENT 'Комбинация сигналов',
    Tc Float64 COMMENT 'Значение интервала временной корреляции',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
TTL d + INTERVAL 1 MONTH DELETE
```

### Таблицы для прочего

#### misc.dcb

Спутниковые поправки TEC (DCB)

```sql
CREATE TABLE IF NOT EXISTS misc.dcb (
    sat String COMMENT 'Спутник',
    system String COMMENT 'Навигационная система',
    sigcomb String COMMENT 'Частота передатчика',
    dcb Float64 COMMENT 'Поправка TEC DCB'
) ENGINE = ReplacingMergeTree()
ORDER BY (system, sat, sigcomb)
SETTINGS index_granularity=8192
```
