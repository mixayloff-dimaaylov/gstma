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

import java.nio.file.{Files, Paths}
import java.util.{Properties, UUID}

import scala.math

import org.apache.spark.sql._
import org.apache.spark.sql.avro.functions.from_avro
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{GroupStateTimeout, OutputMode, GroupState}
import org.apache.spark.SparkConf

import com.infocom.examples.spark.StreamFunctions
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

/**
 * Created by mixayloff-dimaaylov on 07.03.2023.
 */
object TecCalculationV2 extends Serializable {
  private val ismdetobsSchemaPath = "/spark/avro-schemas/ismdetobs.avsc"
  private val ismrawtecSchemaPath = "/spark/avro-schemas/ismrawtec.avsc"
  private val ismredobsSchemaPath = "/spark/avro-schemas/ismredobs.avsc"
  private val rangeSchemaPath = "/spark/avro-schemas/range.avsc"
  private val satxyz2SchemaPath = "/spark/avro-schemas/satxyz2.avsc"

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

  private def readSchemaFile(path: String): String = {
    new String(Files.readAllBytes(Paths.get(path)))
  }

  /* main */

  def main(args: Array[String]): Unit = {
    System.out.println("Run main")

    if (args.length < 5) {
      System.out.println("Wrong arguments")
      printHelp()
      System.exit(1)
    }

    if (args.length > 5) {
      System.out.println("Extra arguments")
      printHelp()
      System.exit(1)
    }

    val recLat = args(0).toDouble
    val recLon = args(1).toDouble
    val recAlt = args(2).toDouble

    /* Definitions */
    val sf = new StreamFunctions(recLat, recLon, recAlt)

    def satGeoPoint: UserDefinedFunction
      = udf[Long, Double, Double, Double](sf.satGeoPoint _)

    def satGeoPointStr: UserDefinedFunction
      = udf[String, Double, Double, Double](sf.satGeoPointStr _)

    def satIonPoint: UserDefinedFunction
      = udf[Long, Double, Double, Double](sf.satIonPoint _)

    def satIonPointStr: UserDefinedFunction
      = udf[String, Double, Double, Double](sf.satIonPointStr _)

    def satElevation: UserDefinedFunction
      = udf[Double, Double, Double, Double](sf.satElevation _)

    val kafkaServerAddress = args(3)
    val clickHouseServerAddress = args(4)
    val jdbcUri = s"jdbc:clickhouse://$clickHouseServerAddress"
    val clientUID = s"${UUID.randomUUID}"

    // Read AVRO schemas
    val ismdetobsSchema = readSchemaFile(ismdetobsSchemaPath)
    val ismrawtecSchema = readSchemaFile(ismrawtecSchemaPath)
    val ismredobsSchema = readSchemaFile(ismredobsSchemaPath)
    val rangeSchema = readSchemaFile(rangeSchemaPath)
    val satxyz2Schema = readSchemaFile(satxyz2SchemaPath)

    val conf: SparkConf = new SparkConf().setAppName("GNSS TecCalculationV2")

    val master = conf.getOption("spark.master")

    if (master.isEmpty) {
      conf.setMaster("local[*]")
    }

    conf.set("spark.sql.streaming.statefulOperator.checkCorrectness.enabled", "false")
    System.out.println("Init conf")

    val spark = SparkSession.builder.config(conf).getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    import spark.implicits._

    // Sinks and Sources

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

    // Ref: https://github.com/ClickHouse/clickhouse-java/issues/975
    // Ref: https://github.com/ClickHouse/clickhouse-java/pull/1008#issuecomment-1303964814
    val jdbcProps = new Properties()
    jdbcProps.setProperty("isolationLevel", "NONE")
    jdbcProps.setProperty("numPartitions", "1")
    jdbcProps.setProperty("user", "default")
    jdbcProps.setProperty("password", "")

    def jdbcSink(stream: DataFrame, tableName: String) = {
      stream
        .writeStream
        .queryName(tableName)
        .foreachBatch((batchDF: DataFrame, batchId: Long) => {
          batchDF.write.mode("append")
            .jdbc(jdbcUri, tableName, jdbcProps)
          ()
        })
    }

    // Target signal parameters
    val sc = spark.sqlContext;
    val sig_params = sc.read.jdbc(
      jdbcUri,
      s"""
         |(SELECT * FROM misc.target_signal_params
         |FINAL)
        """.stripMargin,
      jdbcProps
    )

    // Data plans

    val ismdetobsStream = createKafkaStream("datapoint-raw-ismdetobs").load()
    val ismrawtecStream = createKafkaStream("datapoint-raw-ismrawtec").load()
    val ismredobsStream = createKafkaStream("datapoint-raw-ismredobs").load()
    val rangeStream     = createKafkaStream("datapoint-raw-range").load()
    val satxyz2Stream   = createKafkaStream("datapoint-raw-satxyz2").load()

    // Calculations (rawdata)

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
          satGeoPointStr($"point.X", $"point.Y", $"point.Z").as("geopointStr"),
          satIonPoint($"point.X", $"point.Y", $"point.Z").as("ionpoint"),
          satIonPointStr($"point.X", $"point.Y", $"point.Z").as("ionpointStr"),
          satElevation($"point.X", $"point.Y", $"point.Z").as("elevation"),
          $"point.Satellite".as("sat"),
          $"point.NavigationSystem".as("system"),
          $"point.Prn".as("prn"))

    jdbcSink(ismdetobsDeser, "rawdata.ismdetobs").start()
    jdbcSink(ismrawtecDeser, "rawdata.ismrawtec").start()
    jdbcSink(ismredobsDeser, "rawdata.ismredobs").start()
    jdbcSink(rangeDeser, "rawdata.range").start()
    jdbcSink(satxyz2Deser, "rawdata.satxyz2").start()

    // Calculations (computed)

    /* watermark to prevent infinite caching on joins */
    val rangeTimestamped =
      rangeDeser
        .withColumn("ts", expr("timestamp_millis(time)"))
        .withWatermark("ts", "10 seconds")

    val satxyz2Timestamped =
      satxyz2Deser
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

    jdbcSink(
      rangeNT
        .select("time", "sat", "sigcomb", "f1", "f2", "nt", "adrNt", "psrNt"), "computed.NT").start()

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

    jdbcSink(derivativesNTuncurved, "computed.NTDerivatives").start()

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
        .join(sig_params)
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

    jdbcSink(xz1, "computed.xz1").start()

    // S4 C/No calculation

    val S4cno =
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

    jdbcSink(S4cno, "computed.s4cno").start()

    // S4 Power calculation

    val ismdetobsTimestamped =
      ismdetobsDeser
        .withColumn("ts", expr("timestamp_millis(time)"))
        .withWatermark("ts", "10 seconds")

    val S4pwr =
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

    jdbcSink(S4pwr, "computed.s4pwr").start()

    // S4 calculation

    val S4 =
      xz1
        .select($"time", $"sat", $"sigcomb",
          (sqrt(lit(1) - exp(lit(-2) * pow($"sigPhi", 2)))).as("s4"))

    jdbcSink(S4, "computed.s4").start()

    spark.streams.awaitAnyTermination()
  }

  def printHelp(): Unit = {
    val usagestr = """
    Usage: <progname> <lat> <lon> <alt> <kafka_server> <clickhouse_server>
    <lat>                 - receiver latitude
    <lon>                 - receiver longitude
    <alt>                 - receiver altitude
    <kafka_server>        - Kafka server address:port, (string)
    <clickhouse_server>   - ClickHouse server (HTTP-interface) address:port, (string)
    """
    System.out.println(usagestr)
  }
}
