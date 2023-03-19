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

import org.apache.spark.sql._
import org.apache.spark.sql.avro.functions.from_avro
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{GroupStateTimeout, OutputMode, GroupState}
import org.apache.spark.SparkConf

import com.infocom.examples.spark.{StreamFunctions => SF}
import Functions._

/* Private RDDs */

private case class RangeNT (
  time: Long,
  sat: String,
  sigcomb: String,
  f1: Double,
  f2: Double,
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
  avgNt: Double,
  delNt: Double)
    extends Serializable

/**
 * Created by mixayloff-dimaaylov on 07.03.2023.
 */
object TecCalculationV2 extends Serializable {
  private val ismrawtecSchemaPath = "/spark/avro-schemas/ismrawtec.avsc"
  private val rangeSchemaPath = "/spark/avro-schemas/range.avsc"
  private val satxyz2SchemaPath = "/spark/avro-schemas/satxyz2.avsc"

  // implicit val RangeNTEncoder: Encoder[RangeNT] =
  //   Encoders.kryo[RangeNT]
  @transient implicit val digitalFilterEncoder: Encoder[DigitalFilter] =
    Encoders.kryo[DigitalFilter]
  @transient implicit val tuple2Encoder: Encoder[Tuple2[DigitalFilter, DigitalFilter]] =
    Encoders.kryo[Tuple2[DigitalFilter, DigitalFilter]]

  /* Digital filter handler for flatMapGroupsWithState */
  private def digitalFilter(
      satcomb: Tuple2[String, String],
      input: Iterator[RangeNT],
      state: GroupState[(DigitalFilter, DigitalFilter)]):
        Iterator[RangeDerNT] = {

    val curState = state.getOption
    var (avgF, delF) = if (curState.isEmpty) {
      (DigitalFilters.avgNt, DigitalFilters.delNt)
    } else {
      state.get
    }

    // Flatten Objects
    val res = input.toSeq.sortWith(_.time < _.time).map({
      case RangeNT(time, sat, sigcomb, f1, f2, nt, adrNt, psrNt) =>
        RangeDerNT(time, sat, sigcomb, f1, f2, avgF(nt), delF(nt))
    })

    state.update((avgF, delF))

    res.iterator
  }

  private def readSchemaFile(path: String): String = {
    new String(Files.readAllBytes(Paths.get(path)))
  }

  private def satGeoPoint: UserDefinedFunction = udf {
    (X: Double, Y: Double, Z: Double) => { SF.satGeoPoint(X, Y, Z) } : Long
  }

  private def satIonPoint: UserDefinedFunction = udf {
    (X: Double, Y: Double, Z: Double) => { SF.satIonPoint(X, Y, Z) } : Long
  }

  private def satElevation: UserDefinedFunction = udf {
    (X: Double, Y: Double, Z: Double) => { SF.satElevation(X, Y, Z) } : Double
  }

  /* main */

  def main(args: Array[String]): Unit = {
    System.out.println("Run main")

    if (args.length < 2) {
      System.out.println("Wrong arguments")
      printHelp()
      System.exit(1)
    }

    if (args.length > 2) {
      System.out.println("Extra arguments")
      printHelp()
      System.exit(1)
    }

    val kafkaServerAddress = args(0)
    val clickHouseServerAddress = args(1)
    val jdbcUri = s"jdbc:clickhouse://$clickHouseServerAddress"
    val clientUID = s"${UUID.randomUUID}"

    // Read AVRO schemas
    val ismrawtecSchema = readSchemaFile(ismrawtecSchemaPath)
    val rangeSchema = readSchemaFile(rangeSchemaPath)
    val satxyz2Schema = readSchemaFile(satxyz2SchemaPath)

    val conf: SparkConf = new SparkConf().setAppName("GNSS TecCalculationV2")

    val master = conf.getOption("spark.master")

    if (master.isEmpty) {
      conf.setMaster("local[*]")
    }

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
        .foreachBatch((batchDF: DataFrame, batchId: Long) => {
          batchDF.write.mode("append")
            .jdbc(jdbcUri, tableName, jdbcProps)
          ()
        })
    }

    // Data plans

    val ismrawtecStream = createKafkaStream("datapoint-raw-ismrawtec").load()
    val rangeStream     = createKafkaStream("datapoint-raw-range").load()
    val satxyz2Stream   = createKafkaStream("datapoint-raw-satxyz2").load()

    // Calculations (rawdata)

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

    jdbcSink(rangeDeser, "rawdata.range").start()
    jdbcSink(ismrawtecDeser, "rawdata.ismrawtec").start()
    jdbcSink(satxyz2Deser, "rawdata.satxyz2").start()

    // Calculations (computed)

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

    val rangeNT =
      rangePrep
        .withColumn("nt", rawNt($"adr1", $"adr2", $"f1", $"f2", lit("0")))
        .withColumn("adrNt", rawNt($"adr1", $"adr2", $"f1", $"f2", lit("0")))
        .withColumn("psrNt", psrNt($"psr1", $"psr2", $"f1", $"f2", lit("0")))
        .select("time", "sat", "sigcomb", "f1", "f2", "nt", "adrNt", "psrNt")

    jdbcSink(rangeNT, "computed.NT").start()

    // Derivatives calculation

    val rangeGrouped =
      rangeNT
        .as[RangeNT]
        .groupByKey(x => (x.sat, x.sigcomb))

    val derivativesNT =
      rangeGrouped
        .flatMapGroupsWithState(
          OutputMode.Append, GroupStateTimeout.ProcessingTimeTimeout())(digitalFilter)
        .select("time", "sat", "sigcomb", "f1", "f2", "avgNT", "delNT")

    jdbcSink(derivativesNT, "computed.NTDerivatives").start()

    spark.streams.awaitAnyTermination()
  }

  def printHelp(): Unit = {
    val usagestr = """
    Usage: <progname> <kafka_server> <clickhouse_server>
    <kafka_server>        - Kafka server address:port, (string)
    <clickhouse_server>   - ClickHouse server (HTTP-interface) address:port, (string)
    """
    System.out.println(usagestr)
  }
}
