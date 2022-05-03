#!/usr/bin/env bash

# v12

clickhouse-client <<EOL123
CREATE DATABASE IF NOT EXISTS rawdata
EOL123

clickhouse-client <<EOL123
CREATE DATABASE IF NOT EXISTS computed
EOL123

clickhouse-client <<EOL123
CREATE DATABASE IF NOT EXISTS misc
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS rawdata.range (
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
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 1 DAY DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
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
TTL d + INTERVAL 1 DAY DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
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
TTL d + INTERVAL 1 DAY DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
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
TTL d + INTERVAL 1 DAY DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS rawdata.satxyz2 (
  time UInt64,
  geopoint UInt64,
  ionpoint UInt64,
  elevation Float64,
  sat String,
  system String,
  prn Int32,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat)
TTL d + INTERVAL 1 DAY DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE MATERIALIZED VIEW IF NOT EXISTS computed.range
ENGINE = MergeTree
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 2 MONTH DELETE
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
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.s4 (
  time UInt64,
  sat String,
  freq String,
  s4 Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 2 MONTH DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.NT (
  time UInt64,
  sat String,
  sigcomb String,
  f1 Float64,
  f2 Float64,
  nt Float64,
  psrNt Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
TTL d + INTERVAL 2 MONTH DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.NTDerivatives (
  time UInt64,
  sat String,
  sigcomb String,
  f1 Float64,
  f2 Float64,
  avgNT Float64,
  delNT Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
TTL d + INTERVAL 2 MONTH DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.xz1 (
  time UInt64,
  sat String,
  sigcomb String,
  f1 Float64,
  f2 Float64,
  sigNT Float64,
  sigPhi Float64,
  gamma Float64,
  Fc Float64,
  Pc Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
TTL d + INTERVAL 2 MONTH DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.Tc (
  time UInt64,
  sat String,
  sigcomb String,
  Tc Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
TTL d + INTERVAL 2 MONTH DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS misc.sdcb (
    sat String COMMENT 'Спутник',
    system String COMMENT 'Навигационная система',
    sigcomb String COMMENT 'Частота передатчика',
    sdcb Float64 COMMENT 'Поправка TEC SDCB'
) ENGINE = ReplacingMergeTree()
ORDER BY (system, sat, sigcomb)
SETTINGS index_granularity=8192
EOL123
