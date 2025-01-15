ClickHouse database layout v20
================================

## Таблицы для входных данных

Таблицы, формируемые `logreader`.

### rawdata.range

Источник: `logreader`  
Частота дискретизации: 50 Гц  

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

### rawdata.ismredobs

Источник: `logreader`  
Частота дискретизации: 1/60 Гц  

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

### rawdata.ismdetobs

Источник: `logreader`  
Частота дискретизации: 50 Гц  

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

### rawdata.ismrawtec

Источник: `logreader`  
Частота дискретизации: 1 Гц  

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

### rawdata.satxyz2

Источник: `logreader`  
Частота дискретизации: 50 Гц  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE rawdata.satxyz2 (
  time UInt64,
  geopoint UInt64,
  geopointStr String COMMENT Подспутниковая точка в формате строкового GeoHash,
  ionpoint UInt64,
  ionpointStr String COMMENT Подионосферная точка в формате строкового GeoHash,
  elevation Float64,
  sat String,
  system String,
  prn Int32,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = MergeTree(d, (time, sat), 8192)
TTL d + INVERVAL 24 HOUR DELETE
```

## Таблицы для прореженных входных данных

### computed.range

Источник: *rawdata.range*  
Частота дискретизации: 1 Гц  

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

### computed.ismredobs

Источник: *rawdata.ismredobs*  
Частота дискретизации: 1/60 Гц  
Привязка к существующей таблице  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE VIEW computed.ismredobs
AS SELECT *, d
FROM rawdata.ismredobs
```

### computed.ismdetobs

Источник: *rawdata.ismdetobs*  
Частота дискретизации: 1 Гц  

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
    avg(power) AS power,
    sat,
    any(system) AS system,
    freq,
    any(glofreq) AS glofreq,
    any(prn) AS prn,
    any(d) AS d
FROM rawdata.ismdetobs
GROUP BY
    (intDiv(time, 1000) * 1000) AS time,
    sat,
    freq
```

### computed.ismrawtec

Источник: *rawdata.ismrawtec*  
Частота дискретизации: 1 Гц  
Привязка к существующей таблице  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE VIEW computed.ismrawtec
AS SELECT *, d
FROM rawdata.ismrawtec
```

### computed.satxyz2

Источник: *rawdata.satxyz2*  
Частота дискретизации: 1 Гц  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE MATERIALIZED VIEW computed.satxyz2
ENGINE = MergeTree
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat)
TTL d + INTERVAL 1 MONTH DELETE
POPULATE AS
SELECT *, d
FROM rawdata.satxyz2
WHERE
    time = intDiv(time, 1000) * 1000
```

## Таблицы для расчетных данных

### computed.NT

Источник: *rawdata.range*  
Частота дискретизации: 50 Гц  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.NT (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    sigcomb String COMMENT 'Комбинация сигналов',
    f1 Float64 COMMENT 'Частота 1',
    f2 Float64 COMMENT 'Частота 2',
    nt Float64 COMMENT 'ПЭС',
    adrNt Float64 COMMENT 'ПЭС псевдодальностный',
    psrNt Float64 COMMENT 'ПЭС псевдодальностный',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
TTL d + INTERVAL 1 MONTH DELETE;
```

### computed.NTDerivatives

Источник: *computed.NT*  
Частота дискретизации: 50 Гц  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.NTDerivatives (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    sigcomb String COMMENT 'Комбинация сигналов',
    f1 Float64 COMMENT 'Частота 1',
    f2 Float64 COMMENT 'Частота 2',
    avgNTcurved Float64 COMMENT 'Среднее значение наклонного ПЭС',
    delNTcurved Float64 COMMENT 'Значение флуктуаций наклонного ПЭС',
    avgNT Float64 COMMENT 'Среднее значение вертикального ПЭС',
    delNT Float64 COMMENT 'Значение флуктуаций вертикального ПЭС',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192) 
TTL d + INTERVAL 1 MONTH DELETE;
```

### computed.xz1

Источник: *computed.NTDerivatives*  
Частота дискретизации: 1 Гц  

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
    Fd Float64 COMMENT 'Значение полосы дисперсионности',
    Fk Float64 COMMENT 'Значение полосы когерентности',
    Fc Float64 COMMENT 'Значение интервала частотной корреляции',
    Pc Float64 COMMENT 'Значение интервала пространственной корреляции',
    eta_ch Float64 COMMENT 'Степени ЧСЗ',
    eta_d Float64 COMMENT 'Степени ДИ',
    eta_m Float64 COMMENT 'Степени МСИ',
    Perror Float64 COMMENT 'Значение вероятности ошибки',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192) 
TTL d + INTERVAL 1 MONTH DELETE;
```

### computed.s4

Источник: *rawdata.range*  
Частота дискретизации: 1 Гц  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.s4 (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    sigcomb String COMMENT 'Комбинация сигналов',
    s4 Float64 COMMENT 'Значение S4',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, sigcomb), 8192)
TTL d + INTERVAL 1 MONTH DELETE
```

### computed.s4cno

Источник: *rawdata.range*  
Частота дискретизации: 1 Гц  

*Примечание:* Эта S4 считается для частот, а не для их комбинаций.  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.s4cno (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    freq String COMMENT 'Частота, для которой рассчитано значение',
    s4 Float64 COMMENT 'S4 cno,
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, freq), 8192)
TTL d + INTERVAL 1 MONTH DELETE
```

### computed.s4pwr

Источник: *rawdata.ismdetobs*  
Частота дискретизации: 1 Гц  

*Примечание:* Эта S4 считается для частот, а не для их комбинаций.  

*Примечание:* для поддержки TTL необходима версия clickhouse>=19.6(1.1.54370)

```sql
CREATE TABLE computed.s4pwr (
    time UInt64 COMMENT 'Метка времени (timestamp в ms)',
    sat String COMMENT 'Спутник',
    freq String COMMENT 'Частота, для которой рассчитано значение',
    s4 Float64 COMMENT 'S4 pwr',
    d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree(d, (time, sat, freq), 8192)
TTL d + INTERVAL 1 MONTH DELETE
```

### computed.Tc

Источник: *computed.NTDerivatives*  
Частота дискретизации:   

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

## Прочие таблицы

### misc.dcb

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

### `misc.target_signal_params`

Параметры исследуемого сигнала

```sql
CREATE TABLE IF NOT EXISTS misc.target_signal_params (
    f0 Float64 COMMENT 'Частота исследуемого сигнала, Гц',
    sigPhiCoef Float64 DEFAULT 1 COMMENT 'Коэффициент повышения СКО флуктуации фазы (для отладки), Разы',
    R_T Float64 COMMENT 'Скорость передачи информации, бит/с',
    B_S Float64 COMMENT 'База сигнала'
) ENGINE = ReplacingMergeTree
ORDER BY (f0)
SETTINGS index_granularity=8192
```
