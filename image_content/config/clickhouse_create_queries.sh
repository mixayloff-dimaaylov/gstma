#!/usr/bin/env bash

clickhouse-client <<EOL123
CREATE DATABASE IF NOT EXISTS rawdata
EOL123

clickhouse-client <<EOL123
CREATE DATABASE IF NOT EXISTS computed
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
TTL d + INTERVAL 2 WEEK DELETE
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
TTL d + INTERVAL 2 WEEK DELETE
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.tec (
  time UInt64,
  sat String,
  sigcomb String,
  tec Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.tecFiltered (
  time UInt64,
  sat String,
  sigcomb String,
  tecavg Float64,
  tecdelta Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
SETTINGS index_granularity=8192
EOL123

clickhouse-client <<EOL123
CREATE TABLE IF NOT EXISTS computed.tecsigma (
  time UInt64,
  sat String,
  sigcomb String,
  tecsigma Float64,
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
SETTINGS index_granularity=8192
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
TTL d + INTERVAL 2 WEEK DELETE
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
  d Date MATERIALIZED toDate(round(time / 1000))
) ENGINE = ReplacingMergeTree()
PARTITION BY toYYYYMM(d)
ORDER BY (time, sat, sigcomb)
TTL d + INTERVAL 2 WEEK DELETE
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
TTL d + INTERVAL 2 WEEK DELETE
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
TTL d + INTERVAL 2 WEEK DELETE
SETTINGS index_granularity=8192
EOL123

