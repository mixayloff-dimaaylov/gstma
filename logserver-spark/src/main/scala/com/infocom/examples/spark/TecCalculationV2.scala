/*
 * Copyright 2023 mixayloff-dimaaylov at github dot com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.infocom.examples.spark

import scala.math
import scala.util.{Try,Success}

import com.typesafe.config.Config
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{GroupStateTimeout, OutputMode, GroupState}
import org.tupol.spark._

import java.util.Properties

import Functions._

/* Private RDDs */

private case class Raw (
  time: Long,
  sat: String,
  system: String,
  adr1: Double,
  adr2: Double,
  psr1: Double,
  psr2: Double,
  cno1: Double,
  cno2: Double,
  f1: Double,
  f2: Double,
  glofreq: Integer,
  sigcomb: String,
  k: Double)
    extends Serializable

private case class RawDNT (
  time: Long,
  sat: String,
  system: String,
  adr1: Double,
  adr2: Double,
  psr1: Double,
  psr2: Double,
  cno1: Double,
  cno2: Double,
  f1: Double,
  f2: Double,
  glofreq: Integer,
  sigcomb: String,
  dnt: Double)
    extends Serializable

private case class RangeNT (
  time: Long,
  sat: String,
  sigcomb: String,
  f1: Double,
  f2: Double,
  cno1: Double,
  cno2: Double,
  nt: Double,
  adrNt: Double,
  psrNt: Double)
    extends Serializable

private case class RangeDerNT (
  time: Long,
  sat: String,
  sigcomb: String,
  f1: Double,
  f2: Double,
  cno1: Double,
  cno2: Double,
  avgNt: Double,
  delNt: Double)
    extends Serializable

case class AppContextTecCalculationV2 (
  spark: SparkSession,
  rangeDeser: DataFrame,
  satxyz2Deser: DataFrame,
  ismdetobsDeser: DataFrame,
  sig_params: DataFrame
)

case class ResultTecCalculationV2(
  range: DataFrame,
  derivativesNT: DataFrame,
  xz: DataFrame,
  s4cno: DataFrame,
  s4pwr: DataFrame,
  s4: DataFrame
)

/**
 * Created by mixayloff-dimaaylov on 07.03.2023.
 */
object TecCalculationV2 extends Serializable with SparkRunnable[AppContextTecCalculationV2, ResultTecCalculationV2] {
  // implicit val RangeNTEncoder: Encoder[RangeNT] =
  //   Encoders.kryo[RangeNT]
  @transient implicit val dntEstimatorEncoder: Encoder[DNTEstimator] =
    Encoders.kryo[DNTEstimator]
  @transient implicit val digitalFilterEncoder: Encoder[DigitalFilter] =
    Encoders.kryo[DigitalFilter]
  @transient implicit val tuple2Encoder: Encoder[Tuple2[DigitalFilter, DigitalFilter]] =
    Encoders.kryo[Tuple2[DigitalFilter, DigitalFilter]]
  @transient implicit val tuple4Encoder: Encoder[Tuple4[DigitalFilter, DigitalFilter, Int, Long]] =
    Encoders.kryo[Tuple4[DigitalFilter, DigitalFilter, Int, Long]]

  /* Digital filter handler for flatMapGroupsWithState */
  private def digitalFilter(
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
      case RangeNT(time, sat, sigcomb, f1, f2, cno1, cno2, nt, adrNt, psrNt) =>
        RangeDerNT(time, sat, sigcomb, f1, f2, cno1, cno2, avgF(nt), delF(nt))
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

  /* DNT estimator for flatMapGroupsWithState */
  private def dntEstimator(
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
               cno1, cno2, f1, f2, glofreq, sigcomb, k) =>
        RawDNT(time, sat, system, adr1, adr2, psr1, psr2,
               cno1, cno2, f1, f2, glofreq, sigcomb, dntE(k, time))
    })

    state.update(dntE)

    res.iterator
  }

  override def run(
    implicit spark: SparkSession, context: AppContextTecCalculationV2): Try[ResultTecCalculationV2] = {

    // Data plans

    appLogic(spark, context)
  }

  def appLogic(
    spark: SparkSession,
    context: AppContextTecCalculationV2
  ): Try[ResultTecCalculationV2] = {
    import spark.implicits._

    // Calculations (computed)

    /* watermark to prevent infinite caching on joins */
    val rangeTimestamped =
      context.rangeDeser
        .withColumn("ts", expr("timestamp_millis(time)"))
        .withWatermark("ts", "10 seconds")

    val satxyz2Timestamped =
      context.satxyz2Deser
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
          $"c1.cno".as("cno1"),
          $"c2.cno".as("cno2"),
          f($"c1.system", $"c1.freq", $"c1.glofreq").as("f1"),
          f($"c2.system", $"c2.freq", $"c2.glofreq").as("f2"),
          $"c1.glofreq".as("glofreq"), //?
          concat_ws("+", $"c1.freq", $"c2.freq").as("sigcomb"))

    val rangeDNT =
      rangePrep
        .withColumn("k", k($"adr1", $"adr2", $"f1", $"f2", $"psr1", $"psr2", lit(0)))
        .as[Raw]
        .groupByKey(x => (x.sat, x.sigcomb))
        .flatMapGroupsWithState(
          OutputMode.Append, GroupStateTimeout.ProcessingTimeTimeout())(dntEstimator)

    val rangeNT =
      rangeDNT
        .withColumn("adrNt", rawNt($"adr1", $"adr2", $"f1", $"f2", lit("0")))
        .withColumn("psrNt", psrNt($"psr1", $"psr2", $"f1", $"f2", lit("0")))
        .withColumn("nt", rawNt($"adr1", $"adr2", $"f1", $"f2", $"DNT"))
        .select("time", "sat", "sigcomb", "f1", "f2", "cno1", "cno2", "nt", "adrNt", "psrNt")

    // Derivatives calculation

    val rangeGrouped =
      rangeNT
        .as[RangeNT]
        .groupByKey(x => (x.sat, x.sigcomb))

    val derivativesNT =
      rangeGrouped
        .flatMapGroupsWithState(
          OutputMode.Append, GroupStateTimeout.ProcessingTimeTimeout())(digitalFilter)
        .select("time", "sat", "sigcomb", "f1", "f2", "cno1", "cno2", "avgNT", "delNT")

    val derivativesNTuncurved =
      satxyz2Timestamped
        .withColumn("elevation", radians($"elevation"))
        .as("c4")
        // JOIN
        .join(
          derivativesNT
            .withColumn("ts", expr("timestamp_millis(time)"))
            .withWatermark("ts", "10 seconds").as("c3"), expr("""
              c3.ts = c4.ts AND
              c3.time = c4.time AND
              c3.sat = c4.sat
              """))
        .select(
          $"c3.time".as("time"),
          $"c3.sat".as("sat"),
          $"c3.sigcomb".as("sigcomb"),
          $"c3.f1".as("f1"),
          $"c3.f2".as("f2"),
          $"c3.avgNT".as("avgNTcurved"),
          $"c3.delNT".as("delNTcurved"),
          (sin($"c4.elevation") * $"c3.avgNT").as("avgNT"),
          (sin($"c4.elevation") * $"c3.delNT").as("delNT"))

    // Sigma calculation

    val xz1 =
      derivativesNT
        .withColumn("ts", expr("timestamp_millis(time)"))
        .withWatermark("ts", "20 seconds")
        .groupBy($"sat", $"sigcomb",
          window($"ts", "1 second"))
        .agg(
          first($"time").as("time"),
          $"sat",
          $"sigcomb",
          first($"f1").as("f1"),
          first($"f2").as("f2"),
          stddev_pop($"delNT").as("sigNT"),
          avg($"avgNT").as("avgNT"),
          avg($"cno1").as("cno1"))
        .join(context.sig_params)
        .withColumn("sigPhi", $"sigPhiCoef" * sigPhi($"sigNT", $"f0"))
        .withColumn("gamma", gamma($"sigPhi"))
        .withColumn("Fd", Fd($"avgNT", $"f0"))
        .withColumn("Fk", Fk($"sigPhi", $"f0"))
        .withColumn("Fc", fc($"sigPhi", $"f0"))
        .withColumn("Pc", pc($"sigPhi"))
        .withColumn("T_S", lit(1.0) / $"R_T")
        .withColumn("F_0", $"B_S" / $"T_S")
        .withColumn("eta_ch", eta_ch($"F_0", $"Fk"))
        .withColumn("eta_d", eta_d($"F_0", $"Fd"))
        .withColumn("eta_m", eta_m($"T_S", $"Fk"))
        .withColumn("Perror", Perror($"cno1", $"gamma", $"eta_ch", $"eta_d", $"eta_m"))
        .select("time", "sat", "sigcomb", "f1", "f2",
          "sigNT", "sigPhi", "gamma", "Fd", "Fk", "Fc", "Pc",
          "eta_ch", "eta_d", "eta_m", "Perror")

    // S4 C/No calculation

    val s4cno =
      rangeTimestamped
        .groupBy($"sat", $"freq",
          window($"ts", "1 second"))
        .agg(
          first($"time").as("time"),
          $"sat",
          $"freq",
          avg(pow(pow(10, $"cno"/10), 2)).as("c1"),
          avg(pow(10, $"cno"/10)).as("c2"))
        .withColumn("s4", ($"c1" - pow($"c2", 2)) / pow($"c2", 2))
        .select("time", "sat", "freq", "s4")

    // S4 Power calculation

    val ismdetobsTimestamped =
      context.ismdetobsDeser
        .withColumn("ts", expr("timestamp_millis(time)"))
        .withWatermark("ts", "10 seconds")

    val s4pwr =
      ismdetobsTimestamped
        .groupBy($"sat", $"freq",
          window($"ts", "1 second"))
        .agg(
          first($"time").as("time"),
          $"sat",
          $"freq",
          avg(pow($"power", 2)).as("c1"),
          avg($"power").as("c2"))
        .withColumn("s4", sqrt(($"c1" - pow($"c2", 2)) / pow($"c2", 2)))
        .select("time", "sat", "freq", "s4")

    // S4 calculation

    val s4 =
      xz1
        .select($"time", $"sat", $"sigcomb",
          (sqrt(lit(1) - exp(lit(-2) * pow($"sigPhi", 2)))).as("s4"))

    Success(ResultTecCalculationV2(
      (rangeNT.select("time", "sat", "sigcomb", "f1", "f2", "nt", "adrNt", "psrNt")),
      derivativesNTuncurved,
      xz1,
      s4cno,
      s4pwr,
      s4
    ))
  }
}
