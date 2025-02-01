// -*- coding: utf-8 -*-
// ---
// jupyter:
//   jupytext:
//     text_representation:
//       extension: .scala
//       format_name: light
//       format_version: '1.5'
//       jupytext_version: 1.16.6
//   kernelspec:
//     display_name: Scala 2.12
//     language: scala
//     name: scala2.12
// ---

// Пример Spark Structured Streaming + Kafka
// =========================================

// + [markdown] jp-MarkdownHeadingCollapsed=true
// ## LICENSE
// -

// Copyright 2023 mixayloff-dimaaylov at github dot com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// ## Введение

// На основе [Structured Streaming + Kafka Integration Guide][kafka-streaming].
//
// - [Structured Streaming Programming Guide][guide]
// - [Spark Structured Streaming | Databricks][databricks-2016]
// - [Spark Structured Streaming – Overview | Kaizen][kaizen]
//
// [kafka-streaming]: https://spark.apache.org/docs/2.4.0/structured-streaming-kafka-integration.html
// [guide]: https://spark.apache.org/docs/2.4.0/structured-streaming-programming-guide.html
// [databricks-2016]: https://www.databricks.com/blog/2016/07/28/structured-streaming-in-apache-spark.html
// [kaizen]: https://kaizen.itversity.com/courses/hdp-certified-spark-developer-hdpcsd-scala/lessons/apache-spark-2-streaming/topic/hdpcsd-spark-structured-streaming-overview-scala/

// + [markdown] jp-MarkdownHeadingCollapsed=true
// ## Avro-схемы данных

// +
val ismdetobsSchema = """
    |{
    |  "type": "array",
    |  "items": {
    |    "name": "NovAtelLogReader.DataPoints.DataPointIsmdetobs",
    |    "type": "record",
    |    "fields": [
    |      {
    |        "name": "Timestamp",
    |        "type": "long"
    |      },
    |      {
    |        "name": "NavigationSystem",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.NavigationSystem",
    |          "type": "enum",
    |          "symbols": [
    |            "GPS",
    |            "GLONASS",
    |            "SBAS",
    |            "Galileo",
    |            "BeiDou",
    |            "QZSS",
    |            "Reserved",
    |            "Other"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "SignalType",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.SignalType",
    |          "type": "enum",
    |          "symbols": [
    |            "Unknown",
    |            "L1CA",
    |            "L2C",
    |            "L2CA",
    |            "L2P",
    |            "L2P_codeless",
    |            "L2Y",
    |            "L5Q"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "Satellite",
    |        "type": "string"
    |      },
    |      {
    |        "name": "Prn",
    |        "type": "int"
    |      },
    |      {
    |        "name": "GloFreq",
    |        "type": "int"
    |      },
    |      {
    |        "name": "Power",
    |        "type": "double"
    |      }
    |    ]
    |  }
    |}""".stripMargin

val ismrawtecSchema = """
    |{
    |  "type": "array",
    |  "items": {
    |    "name": "NovAtelLogReader.DataPoints.DataPointIsmrawtec",
    |    "type": "record",
    |    "fields": [
    |      {
    |        "name": "Timestamp",
    |        "type": "long"
    |      },
    |      {
    |        "name": "NavigationSystem",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.NavigationSystem",
    |          "type": "enum",
    |          "symbols": [
    |            "GPS",
    |            "GLONASS",
    |            "SBAS",
    |            "Galileo",
    |            "BeiDou",
    |            "QZSS",
    |            "Reserved",
    |            "Other"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "Satellite",
    |        "type": "string"
    |      },
    |      {
    |        "name": "Prn",
    |        "type": "int"
    |      },
    |      {
    |        "name": "GloFreq",
    |        "type": "int"
    |      },
    |      {
    |        "name": "PrimarySignal",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.SignalType",
    |          "type": "enum",
    |          "symbols": [
    |            "Unknown",
    |            "L1CA",
    |            "L2C",
    |            "L2CA",
    |            "L2P",
    |            "L2P_codeless",
    |            "L2Y",
    |            "L5Q"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "SecondarySignal",
    |        "type": "NovAtelLogReader.LogData.SignalType"
    |      },
    |      {
    |        "name": "Tec",
    |        "type": "double"
    |      }
    |    ]
    |  }
    |}""".stripMargin

val ismredobsSchema = """
    |{
    |  "type": "array",
    |  "items": {
    |    "name": "NovAtelLogReader.DataPoints.DataPointIsmredobs",
    |    "type": "record",
    |    "fields": [
    |      {
    |        "name": "Timestamp",
    |        "type": "long"
    |      },
    |      {
    |        "name": "NavigationSystem",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.NavigationSystem",
    |          "type": "enum",
    |          "symbols": [
    |            "GPS",
    |            "GLONASS",
    |            "SBAS",
    |            "Galileo",
    |            "BeiDou",
    |            "QZSS",
    |            "Reserved",
    |            "Other"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "SignalType",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.SignalType",
    |          "type": "enum",
    |          "symbols": [
    |            "Unknown",
    |            "L1CA",
    |            "L2C",
    |            "L2CA",
    |            "L2P",
    |            "L2P_codeless",
    |            "L2Y",
    |            "L5Q"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "Satellite",
    |        "type": "string"
    |      },
    |      {
    |        "name": "Prn",
    |        "type": "int"
    |      },
    |      {
    |        "name": "GloFreq",
    |        "type": "int"
    |      },
    |      {
    |        "name": "AverageCmc",
    |        "type": "double"
    |      },
    |      {
    |        "name": "CmcStdDev",
    |        "type": "double"
    |      },
    |      {
    |        "name": "TotalS4",
    |        "type": "double"
    |      },
    |      {
    |        "name": "CorrS4",
    |        "type": "double"
    |      },
    |      {
    |        "name": "PhaseSigma1Second",
    |        "type": "double"
    |      },
    |      {
    |        "name": "PhaseSigma30Second",
    |        "type": "double"
    |      },
    |      {
    |        "name": "PhaseSigma60Second",
    |        "type": "double"
    |      }
    |    ]
    |  }
    |}""".stripMargin

val psrposSchema = """
    |{
    |  "type": "array",
    |  "items": {
    |    "name": "NovAtelLogReader.DataPoints.DataPointPsrpos",
    |    "type": "record",
    |    "fields": [
    |      {
    |        "name": "Timestamp",
    |        "type": "long"
    |      },
    |      {
    |        "name": "Lat",
    |        "type": "double"
    |      },
    |      {
    |        "name": "Lon",
    |        "type": "double"
    |      },
    |      {
    |        "name": "Hgt",
    |        "type": "double"
    |      },
    |      {
    |        "name": "LatStdDev",
    |        "type": "double"
    |      },
    |      {
    |        "name": "LonStdDev",
    |        "type": "double"
    |      },
    |      {
    |        "name": "HgtStdDev",
    |        "type": "double"
    |      }
    |    ]
    |  }
    |}""".stripMargin

val rangeSchema = """
    |{
    |  "type": "array",
    |  "items": {
    |    "name": "NovAtelLogReader.DataPoints.DataPointRange",
    |    "type": "record",
    |    "fields": [
    |      {
    |        "name": "Timestamp",
    |        "type": "long"
    |      },
    |      {
    |        "name": "NavigationSystem",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.NavigationSystem",
    |          "type": "enum",
    |          "symbols": [
    |            "GPS",
    |            "GLONASS",
    |            "SBAS",
    |            "Galileo",
    |            "BeiDou",
    |            "QZSS",
    |            "Reserved",
    |            "Other"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "SignalType",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.SignalType",
    |          "type": "enum",
    |          "symbols": [
    |            "Unknown",
    |            "L1CA",
    |            "L2C",
    |            "L2CA",
    |            "L2P",
    |            "L2P_codeless",
    |            "L2Y",
    |            "L5Q"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "Satellite",
    |        "type": "string"
    |      },
    |      {
    |        "name": "Prn",
    |        "type": "int"
    |      },
    |      {
    |        "name": "GloFreq",
    |        "type": "int"
    |      },
    |      {
    |        "name": "Psr",
    |        "type": "double"
    |      },
    |      {
    |        "name": "Adr",
    |        "type": "double"
    |      },
    |      {
    |        "name": "CNo",
    |        "type": "double"
    |      },
    |      {
    |        "name": "LockTime",
    |        "type": "double"
    |      },
    |      {
    |        "name": "Power",
    |        "type": "double"
    |      }
    |    ]
    |  }
    |}""".stripMargin

val satvisSchema = """
    |{
    |  "type": "array",
    |  "items": {
    |    "name": "NovAtelLogReader.DataPoints.DataPointSatvis",
    |    "type": "record",
    |    "fields": [
    |      {
    |        "name": "Timestamp",
    |        "type": "long"
    |      },
    |      {
    |        "name": "NavigationSystem",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.NavigationSystem",
    |          "type": "enum",
    |          "symbols": [
    |            "GPS",
    |            "GLONASS",
    |            "SBAS",
    |            "Galileo",
    |            "BeiDou",
    |            "QZSS",
    |            "Reserved",
    |            "Other"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "Satellite",
    |        "type": "string"
    |      },
    |      {
    |        "name": "Prn",
    |        "type": "int"
    |      },
    |      {
    |        "name": "GloFreq",
    |        "type": "int"
    |      },
    |      {
    |        "name": "SatVis",
    |        "type": "boolean"
    |      },
    |      {
    |        "name": "Health",
    |        "type": "long"
    |      },
    |      {
    |        "name": "Elev",
    |        "type": "double"
    |      },
    |      {
    |        "name": "Az",
    |        "type": "double"
    |      }
    |    ]
    |  }
    |}""".stripMargin

val satxyz2Schema = """
    |{
    |  "type": "array",
    |  "items": {
    |    "name": "NovAtelLogReader.DataPoints.DataPointSatxyz2",
    |    "type": "record",
    |    "fields": [
    |      {
    |        "name": "Timestamp",
    |        "type": "long"
    |      },
    |      {
    |        "name": "NavigationSystem",
    |        "type": {
    |          "name": "NovAtelLogReader.LogData.NavigationSystem",
    |          "type": "enum",
    |          "symbols": [
    |            "GPS",
    |            "GLONASS",
    |            "SBAS",
    |            "Galileo",
    |            "BeiDou",
    |            "QZSS",
    |            "Reserved",
    |            "Other"
    |          ]
    |        }
    |      },
    |      {
    |        "name": "Satellite",
    |        "type": "string"
    |      },
    |      {
    |        "name": "Prn",
    |        "type": "int"
    |      },
    |      {
    |        "name": "X",
    |        "type": "double"
    |      },
    |      {
    |        "name": "Y",
    |        "type": "double"
    |      },
    |      {
    |        "name": "Z",
    |        "type": "double"
    |      }
    |    ]
    |  }
    |}""".stripMargin
// -

// ## Запрос к Kafka использование Structured Streaming

// ### Импорты

// +
import $ivy.`org.apache.spark::spark-sql:3.3.0`
import $ivy.`org.apache.spark::spark-sql-kafka-0-10:3.3.0`
import $ivy.`org.apache.spark::spark-avro:3.3.0`
import $ivy.`ru.yandex.clickhouse:clickhouse-jdbc:0.3.2`

import org.apache.spark.SparkConf
import org.apache.spark.sql._
import org.apache.spark.sql.avro._
import ru.yandex.clickhouse._
// -

// ### Первоначальная настройка

val conf = new SparkConf().setAppName("GNSS TecCalculationV2")
conf.setMaster("local[*]")
conf.set("spark.sql.streaming.statefulOperator.checkCorrectness.enabled", "false")

val spark = SparkSession.builder.config(conf).getOrCreate()
import spark.implicits._
import org.apache.spark.sql.functions._

// ### Модуль расчета GeoHash

// +
import $ivy.`ch.hsr:geohash:1.3.0`

import ch.hsr.geohash.GeoHash

import java.lang.Math

import org.apache.spark.sql.expressions.UserDefinedFunction

/**
 * Created by savartsov on 02.05.2017.
 */
object StreamFunctions {
  // WGS84 ellipsoid constants
  private val observationLat: Double = 45.0409515
  private val observationLon: Double = 41.9108996
  private val observationAlt: Double = 652.1387
  private val a: Double = 6378137 // radius
  private val e: Double = 8.1819190842622e-2 // eccentricity
  private val asq: Double = Math.pow(a, 2)
  private val esq: Double = Math.pow(e, 2)

  private val axleA: Double = 6728137
  private val axleB: Double = 6706752.3142

  // Декартовы координаты приемника
  private val receiver: Array[Double] = lla2ecef(Math.toRadians(observationLat), Math.toRadians(observationLon), observationAlt)

  implicit class GeoHashExt(geoHash: GeoHash) {
    def longValueLeft: Long = {
      val shift = java.lang.Long.SIZE - geoHash.significantBits
      val mask = (1L << geoHash.significantBits) - 1

      (geoHash.longValue >> shift) & mask
    }
  }

  def get_vector(start: Array[Double], end: Array[Double]): Array[Double] = {
    Array(end(0) - start(0), end(1) - start(1), end(2) - start(2))
  }

  def get_vector_length(vector: Array[Double]): Double = {
    Math.sqrt(vector(0) * vector(0) + vector(1) * vector(1) + vector(2) * vector(2))
  }

  def get_unit_vector(vector: Array[Double]): Array[Double] = {
    val length = get_vector_length(vector)

    if (Math.abs(length - 1e-8) > 0.0) {
      Array(vector(0) / length, vector(1) / length, vector(2) / length)
    } else {
      vector
    }
  }

  /**
   * координаты точки пересечения луча и эллипсоида
   * координаты спутника, направляющий вектор, полуось a, полуось b
   */
  def intersection(S: Array[Double], n: Array[Double]): Array[Double] = {
    val x1: Double = S(0)
    val y1: Double = S(1)
    val z1: Double = S(2)
    val nx: Double = n(0)
    val ny: Double = n(1)
    val nz: Double = n(2)
    val a2: Double = axleA * axleA
    val b2: Double = axleB * axleB
    val A: Double = b2 * (nx * nx + ny * ny) + a2 * nz * nz
    val B: Double = 2 * nx * x1 * b2 + 2 * ny * y1 * b2 + 2 * nz * z1 * a2
    val C: Double = b2 * (x1 * x1 + y1 * y1 - a2) + a2 * z1 * z1
    val D: Double = B * B - 4 * A * C

    if (D < 0) {
      Array(0, 0, 0)
    } else {
      val t = (-B - Math.sqrt(D)) / (2 * A)
      Array(x1 + t * nx, y1 + t * ny, z1 + t * nz)
    }
  }

  /**
   * вектор нормали к поверхности эллипсоида в точке
   * R - точка на поверхности эллипсоида, а и b - полуоси
   */
  def normal(R: Array[Double]): Array[Double] = {
    Array(2 * R(0) / (axleA * axleA), 2 * R(1) / (axleA * axleA), 2 * R(2) / (axleB * axleB))
  }

  def cos_norm_RS(normal: Array[Double], RS: Array[Double]): Double = {
    val normal_length = get_vector_length(normal)
    val RS_length = get_vector_length(RS)

    if (Math.abs(normal_length - 1e-8) > 0.0 && Math.abs(RS_length - 1e-8) > 0.0) {
      (normal(0) * RS(0) + normal(1) * RS(1) + normal(2) * RS(2)) / normal_length / RS_length
    } else {
      0.0
    }
  }

  def getElevation(normalVector: Array[Double], recSatVector: Array[Double]): Double = {
    Math.toDegrees(Math.PI / 2 - Math.acos(cos_norm_RS(normalVector, recSatVector)))
  }

  def lla2ecef(lat: Double, lon: Double, alt: Double): Array[Double] = {
    val N: Double = a / Math.sqrt(1 - esq * Math.pow(Math.sin(lat), 2))

    val x: Double = (N + alt) * Math.cos(lat) * Math.cos(lon)
    val y: Double = (N + alt) * Math.cos(lat) * Math.sin(lon)
    val z: Double = ((1 - esq) * N + alt) * Math.sin(lat)

    Array(x, y, z)
  }

  def ecef2lla(ecef: Array[Double]): Array[Double] = {
    val x: Double = ecef(0)
    val y: Double = ecef(1)
    val z: Double = ecef(2)

    val b: Double = Math.sqrt(asq * (1 - esq))
    val bsq: Double = Math.pow(b, 2)
    val ep: Double = Math.sqrt((asq - bsq) / bsq)
    val p: Double = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2))
    val th: Double = Math.atan2(a * z, b * p)

    val lon: Double = Math.atan2(y, x)
    val lat: Double = Math.atan2(z + Math.pow(ep, 2) * b * Math.pow(Math.sin(th), 3), p - esq * a * Math.pow(Math.cos(th), 3))
    val N: Double = a / Math.sqrt(1 - esq * Math.pow(Math.sin(lat), 2))
    val alt: Double = p / Math.cos(lat) - N

    // correction for altitude near poles left out
    // mod lat to 0-2pi
    Array(Math.toDegrees(lat), Math.toDegrees(lon % (2 * Math.PI)), alt)
  }

  def satGeoPointRaw(X: Double, Y: Double, Z: Double): Long = {
    val lla = ecef2lla(Array(X, Y, Z))

    GeoHash.withBitPrecision(lla(0), lla(1), 52).longValueLeft
  }

  def satIonPointRaw(X: Double, Y: Double, Z: Double): Long = {
    val point = Array(X, Y, Z)
    val lla = ecef2lla(intersection(point, get_unit_vector(get_vector(point, receiver))))

    GeoHash.withBitPrecision(lla(0), lla(1), 52).longValueLeft
  }

  def satElevationRaw(X: Double, Y: Double, Z: Double): Double = {
    val point = Array(X, Y, Z)

    getElevation(normal(receiver), get_vector(receiver, point))
  }
}

def satGeoPoint: UserDefinedFunction = udf {
  (X: Double, Y: Double, Z: Double) => {
    StreamFunctions.satGeoPointRaw(X, Y, Z)
  }
}

def satIonPoint: UserDefinedFunction = udf {
  (X: Double, Y: Double, Z: Double) => {
    StreamFunctions.satIonPointRaw(X, Y, Z)
  }
}

def satElevation: UserDefinedFunction = udf {
  (X: Double, Y: Double, Z: Double) => {
    StreamFunctions.satElevationRaw(X, Y, Z)
  }
}
// -

// ### Модуль фабрики фильтров

// +
import scala.collection.mutable;

class DigitalFilter(
    val order: Int,
    val b: Seq[Double],
    val a: Seq[Double]) extends Serializable {

  if (order < 1) throw
    new IllegalArgumentException(s"The filter order must be positive number")

  if (b.length != (order + 1) || a.length != order) throw
    new IllegalArgumentException(s"The a's and b's lengths must be satisfy to filter order")

  private val zero: Double = 0
  private val zeroSeq = mutable.Buffer.fill[Double](order)(zero)

  var bInputSeq = zeroSeq
  var aInputSeq = zeroSeq

  private def filt(bInputSeq: Seq[Double], aInputSeq: Seq[Double]): Double = {
    if (b.length !== bInputSeq.length) throw
      new IllegalArgumentException(s"The length of b must be equal to bInputSeq length")

    if (a.length !== aInputSeq.length) throw
      new IllegalArgumentException(s"The length of a must be equal to aInputSeq length")

    ((b, bInputSeq).zipped.map((x, y) => x * y).sum
     - (a, aInputSeq).zipped.map((x, y) => x * y).sum)
  }

  def apply(input: Double): Double = {
    val iSeq = bInputSeq.padTo(order, zero) :+ input
    val oSeq = aInputSeq.padTo(order + 1, zero)

    for (i <- order until iSeq.length) {
      oSeq(i) =
        filt(
          iSeq.slice(i - order, i - 1 + 2).reverse,
          oSeq.slice(i - order, i - 1 + 1).reverse)
    }

    bInputSeq = iSeq.takeRight(order)
    aInputSeq = oSeq.takeRight(order)
    oSeq(0)
  }

  def apply(input: Seq[Double]): Seq[Double] = {
    val iSeq = bInputSeq.padTo(order, zero) ++ input
    val oSeq = aInputSeq.padTo(order + input.length, zero)

    for (i <- order until iSeq.length) {
      oSeq(i) =
        filt(
          iSeq.slice(i - order, i - 1 + 2).reverse,
          oSeq.slice(i - order, i - 1 + 1).reverse)
    }

    bInputSeq = iSeq.takeRight(order)
    aInputSeq = oSeq.takeRight(order)
    oSeq.slice(0, oSeq.length - order).toSeq
  }

  implicit final class AnyOps[A](self: A) {
    def !==(other: A): Boolean = self != other
  }
}
// -

// Теперь определим конкретный фильтры с коэффициентами: фильтр среднего ПЭС и фильтр флуктуаций ПЭС:

object DigitalFilters extends Serializable {
  def avgNt(): DigitalFilter = {
    val b = Seq(
      0.00000004863987500780838,
      0.00000029183925004685027,
      0.00000072959812511712565,
      0.00000097279750015616753,
      0.00000072959812511712565,
      0.00000029183925004685027,
      0.00000004863987500780838
    )

    val a = Seq(
      -5.5145351211661655,
      12.689113056515138,
      -15.593635210704097,
      10.793296670485379,
      -3.9893594042308829,
      0.6151231220526282
    )

    new DigitalFilter(6, b, a)
  }

  def delNt(): DigitalFilter = {
    val b = Seq(
      0.076745906902313671,
      0,
      -0.23023772070694101,
      0,
      0.23023772070694101,
      0,
      -0.076745906902313671
    )

    val a = Seq(
      -3.4767608600037727,
      5.0801848641096203,
      -4.2310052826910152,
      2.2392861745041328,
      -0.69437337677433475,
      0.084273573849621822
    )

    new DigitalFilter(6, b, a)
  }
}

// ### Модуль расчета DNT 

class DNTEstimator(
  /*
   * Timeout to reset current DNT.
   */
    val timeOut: Long) extends Serializable {

  private var cnt: Int = 0
  private var acc: Double = 0
  private var lastSeen: Long = 0

  def reset() = {
    acc = 0
    cnt = 0
  }

  def apply(input: Double, time: Long): Double = {
    if((time - lastSeen) > timeOut) {
      reset()
    }

    if(cnt < 3000){
      acc += input
      cnt += 1
    }

    lastSeen = time
    acc / cnt
  }
}

// ### Общие функции

// Функция для создания потока из Kafka:

// +
import java.util.UUID

val clientUID = s"${UUID.randomUUID}"

// Использование Docker DNS позволяет обращаться к контейнерам по именам внутри одной
// Docker-сети. Поэтому в `bootstrap.servers` прописано имя контейнера:
val kafkaServerAddress = "10.208.143.231:9092"

def createKafkaStream(topic: String) = {
  spark
    .readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", kafkaServerAddress)
    .option("enable.auto.commit", (false: java.lang.Boolean))
    .option("auto.offset.reset", "latest")
    .option("failOnDataLoss", (false: java.lang.Boolean))
    .option("group.id", s"gnss-stream-receiver-${clientUID}-${topic}")
    .option("subscribe", topic)
}
// -

// Функция для отладки при помощи специальной таблицы движка в Catalyst, хранимой в памяти.  
// Такой подход используется только при отладке, потому что содержимое таких временный таблиц не ощичается и в итоге приводит к исчерпанию всей RAM!

def memTableSink(stream: DataFrame, tableName: String) = {
  stream
    .writeStream
    .outputMode("append")
    .format("memory")
    .queryName(tableName)
    .option("maxRows", 100)
}

// Функция для отправки результатов в базу данных:

// +
import java.util.Properties

val clickHouseServerAddress = "10.208.143.232:8123"

val jdbcUri = s"jdbc:clickhouse://$clickHouseServerAddress"

val jdbcProps = new Properties()
jdbcProps.setProperty("isolationLevel", "NONE")
jdbcProps.setProperty("numPartitions", "1")
jdbcProps.setProperty("user", "default")
jdbcProps.setProperty("password", "")

def jdbcSink(stream: DataFrame, tableName: String) = {
  stream
    .writeStream
    .foreachBatch((batchDF: DataFrame, batchId: Long) => {
       batchDF.write.mode("append")
         .jdbc(jdbcUri, tableName, jdbcProps)
         ()
    })
}
// -

// ### Получение сообщений из Kafka

// Теперь создадим потоки:

val ismdetobsStream = createKafkaStream("datapoint-raw-ismdetobs").load()
val ismrawtecStream = createKafkaStream("datapoint-raw-ismrawtec").load()
val ismredobsStream = createKafkaStream("datapoint-raw-ismredobs").load()
val psrposStream    = createKafkaStream("datapoint-raw-psrpos").load()
val rangeStream     = createKafkaStream("datapoint-raw-range").load()
val satvisStream    = createKafkaStream("datapoint-raw-satvis").load()
val satxyz2Stream   = createKafkaStream("datapoint-raw-satxyz2").load()

// ### Десериализация сообщений
//
// Сообщения NovAtelLogReader сериализованы при помощи формата Avro. Для их обработки нужно провести десериализацию.
//
// - [Документация на библиотеку][docs].
// - [Read and write streaming Avro data | Databricks][databricks].
//
// [docs]: https://spark.apache.org/docs/2.4.0/sql-data-sources-avro.html
// [databricks]: https://docs.databricks.com/structured-streaming/avro-dataframe.html

// Пример разложения десериализованного сообщения на составляющие поля:

// +
val ismdetobsDeser =
  ismdetobsStream
    .select(from_avro($"value", ismdetobsSchema).as("array"))
    .withColumn("point", explode($"array"))
    .select(
      $"point.Timestamp".as("time"),
      $"point.NavigationSystem".as("system"),
      $"point.SignalType".as("freq"),
      $"point.Satellite".as("sat"),
      $"point.Prn".as("prn"),
      $"point.GloFreq".as("glofreq"),
      $"point.Power".as("power"))

val ismrawtecDeser =
  ismrawtecStream
    .select(from_avro($"value", ismrawtecSchema).as("array"))
    .withColumn("point", explode($"array"))
    .select(
      $"point.Timestamp".as("time"),
      $"point.NavigationSystem".as("system"),
      $"point.Satellite".as("sat"),
      $"point.Prn".as("prn"),
      $"point.GloFreq".as("glofreq"),
      $"point.PrimarySignal".as("primaryfreq"),
      $"point.SecondarySignal".as("secondaryfreq"),
      $"point.Tec".as("tec"))

val ismredobsDeser =
  ismredobsStream
    .select(from_avro($"value", ismredobsSchema).as("array"))
    .withColumn("point", explode($"array"))
    .select(
      $"point.Timestamp".as("time"),
      $"point.NavigationSystem".as("system"),
      $"point.SignalType".as("freq"),
      $"point.Satellite".as("sat"),
      $"point.Prn".as("prn"),
      $"point.GloFreq".as("glofreq"),
      $"point.TotalS4".as("totals4"))

val rangeDeser =
  rangeStream
    .select(from_avro($"value", rangeSchema).as("array"))
    .withColumn("point", explode($"array"))
    .select(
      $"point.Timestamp".as("time"),
      $"point.NavigationSystem".as("system"),
      $"point.SignalType".as("freq"),
      $"point.Satellite".as("sat"),
      $"point.Prn".as("prn"),
      $"point.GloFreq".as("glofreq"),
      $"point.Psr".as("psr"),
      $"point.Adr".as("adr"),
      $"point.CNo".as("cno"),
      $"point.LockTime".as("locktime"))

val satxyz2Deser =
  satxyz2Stream
    .select(from_avro($"value", satxyz2Schema).as("array"))
    .withColumn("point", explode($"array"))
    .select(
      $"point.Timestamp".as("time"),
      satGeoPoint($"point.X", $"point.Y", $"point.Z").as("geopoint"),
      satIonPoint($"point.X", $"point.Y", $"point.Z").as("ionpoint"),
      satElevation($"point.X", $"point.Y", $"point.Z").as("elevation"),
      $"point.Satellite".as("sat"),
      $"point.NavigationSystem".as("system"),
      $"point.Prn".as("prn"))

jdbcSink(ismdetobsDeser, "rawdata.ismdetobs").start()
jdbcSink(ismrawtecDeser, "rawdata.ismrawtec").start()
jdbcSink(ismredobsDeser, "rawdata.ismredobs").start()
jdbcSink(rangeDeser, "rawdata.range").start()
jdbcSink(satxyz2Deser, "rawdata.satxyz2").start()
// -

// ### Подготовка данных

// +
import org.apache.spark.sql.expressions.UserDefinedFunction

val C = 299792458.0

def waveLength(f: Double): Double = C / f

def f: UserDefinedFunction = udf {
  (system: String, freq: String, glofreq: Int) =>
    system match {
      case "GLONASS" =>
        freq match {
          case "L1CA"       => 1602.0e6 + glofreq * 0.5625e6
          case "L2CA"       => 1246.0e6 + glofreq * 0.4375e6
          case "L2P"        => 1246.0e6 + glofreq * 0.4375e6
          case _            => 0
        }

      case "GPS" =>
        freq match {
          case "L1CA"       => 1575.42e6
          case "L2C"        => 1227.60e6
          case "L2P"        => 1227.60e6
          case "L5Q"        => 1176.45e6
          case _            => 0
        }

      case _ => 0
    }
}

// +
/* watermark to prevent infinite caching on joins */
val rangeTimestamped =
  rangeDeser
    .withColumn("ts", expr("timestamp_millis(time)"))
    .withWatermark("ts", "10 seconds")

val rangePrep =
  rangeTimestamped.as("c1")
    .join(rangeTimestamped.as("c2")).where(
      ($"c1.ts"   === $"c2.ts") &&
      ($"c1.time" === $"c2.time") &&
      ($"c1.sat"  === $"c2.sat") &&
      ($"c1.freq" === "L1CA") && ($"c2.freq" =!= "L1CA"))
    .select(
      $"c1.time".as("time"),
      $"c1.sat".as("sat"),
      $"c1.system".as("system"),
      $"c1.adr".as("adr1"),
      $"c2.adr".as("adr2"),
      $"c1.psr".as("psr1"),
      $"c2.psr".as("psr2"),
      f($"c1.system", $"c1.freq", $"c1.glofreq").as("f1"),
      f($"c2.system", $"c2.freq", $"c2.glofreq").as("f2"),
      $"c1.glofreq".as("glofreq"), //?
      concat_ws("+", $"c1.freq", $"c2.freq").as("sigcomb"))
// -

// ### Расчет простого ПЭС (без DNT)

// #### Расчет DNT

// +
case class Raw (
  time: Long,
  sat: String,
  system: String,
  adr1: Double,
  adr2: Double,
  psr1: Double,
  psr2: Double,
  f1: Double,
  f2: Double,
  glofreq: Integer,
  sigcomb: String,
  k: Double)
    extends Serializable

case class RawDNT (
  time: Long,
  sat: String,
  system: String,
  adr1: Double,
  adr2: Double,
  psr1: Double,
  psr2: Double,
  f1: Double,
  f2: Double,
  glofreq: Integer,
  sigcomb: String,
  dnt: Double)
    extends Serializable

implicit val dntEstimatorEncoder: Encoder[DNTEstimator] = Encoders.kryo[DNTEstimator]
implicit val tuple2Encoder: Encoder[Tuple2[DigitalFilter, DigitalFilter]] = Encoders.kryo[Tuple2[DigitalFilter, DigitalFilter]]

import org.apache.spark.sql.streaming.{GroupStateTimeout, OutputMode, GroupState}

/* DNT estimator for flatMapGroupsWithState */
def dntEstimator(
    satcomb: Tuple2[String, String],
    input: Iterator[Raw],
    state: GroupState[DNTEstimator]):
      Iterator[RawDNT] = {

  val curState = state.getOption
  val dntE = if (curState.isEmpty) {
    DNTEstimators.regular
  } else {
    state.get
  }

  // Flatten Objects
  val res = input.toSeq.sortWith(_.time < _.time).map({
    case Raw(time, sat, system, adr1, adr2, psr1, psr2,
             f1, f2, glofreq, sigcomb, k) =>
      RawDNT(time, sat, system, adr1, adr2, psr1, psr2,
             f1, f2, glofreq, sigcomb, dntE(k, time))
  })

  state.update(dntE)

  res.iterator
}

object DNTEstimators extends Serializable {
  def regular(): DNTEstimator = {
    /* Timeout -- 1 minute, 3000 points if frequency of points = 50 Hz */
    new DNTEstimator(timeOut = 60000)
  }
}

// +
def k: UserDefinedFunction = udf {
  (adr1: Double, adr2: Double, f1: Double, f2: Double, psr1: Double, psr2: Double, sdcb: Double)
    => (psr2 - psr1 + sdcb * C) - (adr2 * waveLength(f2) - adr1 * waveLength(f1))
}

val rangeDNT =
  rangePrep
    .withColumn("k", k($"adr1", $"adr2", $"f1", $"f2", $"psr1", $"psr2", lit(0)))
    .as[Raw]
    .groupByKey(x => (x.sat, x.sigcomb))
    .flatMapGroupsWithState(
      OutputMode.Append, GroupStateTimeout.ProcessingTimeTimeout())(dntEstimator)
// -

// #### Расчет ПЭС

// +
/**
 * ПЭС без поправок
 * @param dnt смещение, м
 */
def rawNt: UserDefinedFunction = udf {
  (adr1: Double, adr2: Double, f1: Double, f2: Double, dnt: Double) => {
    val f1_2 = f1 * f1
    val f2_2 = f2 * f2

    ((1e-16 * f1_2 * f2_2) / (40.308 * (f1_2 - f2_2))) * (adr2 * waveLength(f2) - adr1 * waveLength(f1) + dnt)
  }
}

/**
 * ПЭС без поправок
 * @param dnt смещение, м
 */
def psrNt: UserDefinedFunction = udf {
  (psr1: Double, psr2: Double, f1: Double, f2: Double, sdcb: Double) =>
    {
      val f1_2 = f1 * f1
      val f2_2 = f2 * f2

      ((1e-16 * f1_2 * f2_2) / (40.308 * (f1_2 - f2_2))) * (psr2 - psr1 + sdcb)
    }
}

// +
val rangeNT =
  rangeDNT
    .withColumn("adrNt", rawNt($"adr1", $"adr2", $"f1", $"f2", lit("0")))
    .withColumn("psrNt", psrNt($"psr1", $"psr2", $"f1", $"f2", lit("0")))
    .withColumn("nt", rawNt($"adr1", $"adr2", $"f1", $"f2", $"DNT"))
    .select("time", "sat", "sigcomb", "f1", "f2", "nt", "adrNt", "psrNt")

jdbcSink(rangeNT, "computed.NT").start()
// -

// ### Расчет Среднего ПЭС и Флуктуаций ПЭС
//
// Здесь нам пригодятся цифровые фильтры Баттерворта и Spark Stateful Structured Streaming:

// +
case class RangeNT (
  time: Long,
  sat: String,
  sigcomb: String,
  f1: Double,
  f2: Double,
  nt: Double,
  adrNt: Double,
  psrNt: Double)
    extends Serializable

case class RangeDerNT (
  time: Long,
  sat: String,
  sigcomb: String,
  f1: Double,
  f2: Double,
  avgNt: Double,
  delNt: Double)
    extends Serializable

implicit val digitalFilterEncoder: Encoder[DigitalFilter] = Encoders.kryo[DigitalFilter]
implicit val tuple4Encoder: Encoder[Tuple4[DigitalFilter, DigitalFilter, Int, Long]] =
    Encoders.kryo[Tuple4[DigitalFilter, DigitalFilter, Int, Long]]

/* Digital filter handler for flatMapGroupsWithState */
def digitalFilter(
    satcomb: Tuple2[String, String],
    input: Iterator[RangeNT],
    state: GroupState[(DigitalFilter, DigitalFilter, Int, Long)]):
      Iterator[RangeDerNT] = {

  val curState = state.getOption
  var (avgF, delF, skipped, lastSeen) = if (curState.isEmpty) {
    (DigitalFilters.avgNt, DigitalFilters.delNt, 0, (0: Long))
  } else {
    state.get
  }

  // Flatten Objects
  var res = input.toSeq.sortWith(_.time < _.time).map({
    case RangeNT(time, sat, sigcomb, f1, f2, nt, adrNt, psrNt) =>
      RangeDerNT(time, sat, sigcomb, f1, f2, avgF(nt), delF(nt))
  })

  // Forget last timespan and disruptions
  skipped = res.lastOption match {
    case Some(l) =>
      if ((l.time - lastSeen) > 60000) 0
      else skipped
    case None    => 0
  }

  // Update last seen time for satellite
  lastSeen = res.lastOption match {
    case Some(l) => l.time
    case None    => 0
  }

  // Cut off filter spikes/splashes (500 points / 50 Hz = 10 seconds)
  if (skipped < 500) {
    val skip = math.min(500 - skipped, res.length)
    res = res.drop(skip)
    skipped += skip
  }

  state.update((avgF, delF, skipped, lastSeen))

  res.iterator
}

// +
val rangeGrouped =
  rangeNT
    .as[RangeNT]
    .groupByKey(x => (x.sat, x.sigcomb))

val derivativesNT =
  rangeGrouped
    .flatMapGroupsWithState(OutputMode.Append, GroupStateTimeout.ProcessingTimeTimeout())(digitalFilter)
    .select("time", "sat", "sigcomb", "f1", "f2", "avgNT", "delNT")

jdbcSink(derivativesNT, "computed.NTDerivatives").start()
