#!/usr/bin/env bash

# Copyright 2023 mixayloff-dimaaylov at github dot com
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# v19

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
TTL d + INTERVAL 24 HOUR DELETE
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
TTL d + INTERVAL 24 HOUR DELETE
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
TTL d + INTERVAL 24 HOUR DELETE
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
TTL d + INTERVAL 24 HOUR DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS rawdata.satxyz2 (
  time UInt64,
  geopoint UInt64,
  geopointStr String,
  ionpoint UInt64,
  ionpointStr String,
  elevation Float64,
  sat String,
  system String,
  prn Int32,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat)
TTL d + INTERVAL 24 HOUR DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE MATERIALIZED VIEW IF NOT EXISTS computed.range
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
EOL123

clickhouse-client <<EOL123
CREATE VIEW IF NOT EXISTS computed.ismredobs
AS SELECT *, d
FROM rawdata.ismredobs
EOL123

clickhouse-client <<EOL123
CREATE MATERIALIZED VIEW IF NOT EXISTS computed.ismdetobs
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
EOL123

clickhouse-client <<EOL123
CREATE VIEW IF NOT EXISTS computed.ismrawtec
AS SELECT *, d
FROM rawdata.ismrawtec
EOL123

clickhouse-client <<EOL123
CREATE MATERIALIZED VIEW IF NOT EXISTS computed.satxyz2
ENGINE = MergeTree
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat)
TTL d + INTERVAL 1 MONTH DELETE
POPULATE AS
SELECT *, d
FROM rawdata.satxyz2
WHERE
    time = intDiv(time, 1000) * 1000
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.s4 (
  time UInt64,
  sat String,
  sigcomb String,
  s4 Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
TTL d + INTERVAL 1 MONTH DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.s4cno (
  time UInt64,
  sat String,
  freq String,
  s4 Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 1 MONTH DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.s4pwr (
  time UInt64,
  sat String,
  freq String,
  s4 Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, freq)
TTL d + INTERVAL 1 MONTH DELETE
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
  adrNt Float64,
  psrNt Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
TTL d + INTERVAL 1 MONTH DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.NTDerivatives (
  time UInt64,
  sat String,
  sigcomb String,
  f1 Float64,
  f2 Float64,
  avgNTcurved Float64,
  delNTcurved Float64,
  avgNT Float64,
  delNT Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
TTL d + INTERVAL 1 MONTH DELETE
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
TTL d + INTERVAL 1 MONTH DELETE
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
TTL d + INTERVAL 1 MONTH DELETE
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

clickhouse-client <<EOL123
ALTER TABLE system.query_log MODIFY TTL event_date + INTERVAL 14 DAY;
EOL123
