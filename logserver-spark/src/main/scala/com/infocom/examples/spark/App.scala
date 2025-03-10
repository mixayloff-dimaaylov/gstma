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

import org.apache.spark.sql._
import org.apache.spark.SparkConf
import org.apache.spark.sql.avro.functions.from_avro
import org.apache.spark.sql.functions.{explode,udf}
import org.apache.spark.sql.expressions.UserDefinedFunction

import scala.util.{Try,Success,Failure}
import java.nio.file.{Files, Paths}
import java.util.{Properties, UUID}

import com.infocom.examples.spark.StreamFunctions

private case class AppContext (
  spark: SparkSession,
  ismdetobsSchema: String,
  ismrawtecSchema: String,
  ismredobsSchema: String,
  rangeSchema: String,
  satxyz2Schema: String
)

object App {
  private val ismdetobsSchemaPath = "/spark/avro-schemas/ismdetobs.avsc"
  private val ismrawtecSchemaPath = "/spark/avro-schemas/ismrawtec.avsc"
  private val ismredobsSchemaPath = "/spark/avro-schemas/ismredobs.avsc"
  private val rangeSchemaPath = "/spark/avro-schemas/range.avsc"
  private val satxyz2SchemaPath = "/spark/avro-schemas/satxyz2.avsc"

  private def readSchemaFile(path: String): String = {
    new String(Files.readAllBytes(Paths.get(path)))
  }

  /* kafka */

  // Sinks and Sources
  private def IsmDetobsDeser(
    context: AppContext, ismdetobsStream: DataFrame): DataFrame = {
    import context.spark.implicits._

    ismdetobsStream
      .select(from_avro($"value", context.ismdetobsSchema).as("array"))
      .withColumn("point", explode($"array"))
      .select(
        $"point.Timestamp".as("time"),
        $"point.NavigationSystem".as("system"),
        $"point.SignalType".as("freq"),
        $"point.Satellite".as("sat"),
        $"point.Prn".as("prn"),
        $"point.GloFreq".as("glofreq"),
        $"point.Power".as("power"))
  }

  private def IsmrawtecDeser(
    context: AppContext, ismrawtecStream: DataFrame): DataFrame = {
    import context.spark.implicits._

    ismrawtecStream
      .select(from_avro($"value", context.ismrawtecSchema).as("array"))
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
  }

  private def IsmredobsDeser(
    context: AppContext, ismredobsStream: DataFrame): DataFrame = {
    import context.spark.implicits._

    ismredobsStream
      .select(from_avro($"value", context.ismredobsSchema).as("array"))
      .withColumn("point", explode($"array"))
      .select(
        $"point.Timestamp".as("time"),
        $"point.NavigationSystem".as("system"),
        $"point.SignalType".as("freq"),
        $"point.Satellite".as("sat"),
        $"point.Prn".as("prn"),
        $"point.GloFreq".as("glofreq"),
        $"point.TotalS4".as("totals4"))
  }

  private def RangeDeser(
    context: AppContext, rangeStream: DataFrame): DataFrame = {
    import context.spark.implicits._

    rangeStream
      .select(from_avro($"value", context.rangeSchema).as("array"))
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
  }

  private def Satxyz2Deser(
    context: AppContext, satxyz2Stream: DataFrame, sf: StreamFunctions): DataFrame = {
    import context.spark.implicits._

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

    satxyz2Stream
      .select(from_avro($"value", context.satxyz2Schema).as("array"))
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
  }

  /* main */

  @SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
  def main(implicit args: Array[String]): Unit = {
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

    val kafkaServerAddress = args(3)
    val clickHouseServerAddress = args(4)
    val jdbcUri = s"jdbc:clickhouse://$clickHouseServerAddress"
    val clientUID = s"${UUID.randomUUID}"

    /* Definitions */
    val sf = new StreamFunctions(recLat, recLon, recAlt)

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

    // Ref: https://github.com/ClickHouse/clickhouse-java/issues/975
    // Ref: https://github.com/ClickHouse/clickhouse-java/pull/1008#issuecomment-1303964814
    val jdbcProps = new Properties()
    jdbcProps.setProperty("isolationLevel", "NONE")
    jdbcProps.setProperty("numPartitions", "1")
    jdbcProps.setProperty("user", "default")
    jdbcProps.setProperty("password", "")

    val context = AppContext(
      spark,
      ismdetobsSchema,
      ismrawtecSchema,
      ismredobsSchema,
      rangeSchema,
      satxyz2Schema
    )

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

    val ismdetobsStream = createKafkaStream("datapoint-raw-ismdetobs").load()
    val ismrawtecStream = createKafkaStream("datapoint-raw-ismrawtec").load()
    val ismredobsStream = createKafkaStream("datapoint-raw-ismredobs").load()
    val rangeStream     = createKafkaStream("datapoint-raw-range").load()
    val satxyz2Stream   = createKafkaStream("datapoint-raw-satxyz2").load()

    // Calculations (rawdata)

    val ismdetobsDeser = IsmDetobsDeser(context, ismdetobsStream)
    val ismrawtecDeser = IsmrawtecDeser(context, ismrawtecStream)
    val ismredobsDeser = IsmredobsDeser(context, ismredobsStream)
    val rangeDeser = RangeDeser(context, rangeStream)
    val satxyz2Deser = Satxyz2Deser(context, satxyz2Stream, sf)

    jdbcSink(jdbcUri, jdbcProps, ismdetobsDeser, "rawdata.ismdetobs").start()
    jdbcSink(jdbcUri, jdbcProps, ismrawtecDeser, "rawdata.ismrawtec").start()
    jdbcSink(jdbcUri, jdbcProps, ismredobsDeser, "rawdata.ismredobs").start()
    jdbcSink(jdbcUri, jdbcProps, rangeDeser, "rawdata.range").start()
    jdbcSink(jdbcUri, jdbcProps, satxyz2Deser, "rawdata.satxyz2").start()

    val tecContext = AppContextTecCalculationV2(
      spark,
      rangeDeser,
      satxyz2Deser,
      ismdetobsDeser,
      sig_params
    )

    val outcome = for {
      result    <- TecCalculationV2.run(spark, tecContext)
    } yield result

    outcome match {
      case Success(ResultTecCalculationV2(range, derivativesNT, xz1, s4cno, s4pwr, s4)) => {
        jdbcSink(jdbcUri, jdbcProps, range, "computed.NT").start()
        jdbcSink(jdbcUri, jdbcProps, derivativesNT, "computed.NTDerivatives").start()
        jdbcSink(jdbcUri, jdbcProps, xz1, "computed.xz1").start()
        jdbcSink(jdbcUri, jdbcProps, s4cno, "computed.s4cno").start()
        jdbcSink(jdbcUri, jdbcProps, s4pwr, "computed.s4pwr").start()
        jdbcSink(jdbcUri, jdbcProps, s4, "computed.s4").start()

        spark.streams.awaitAnyTermination()
      }

      case Failure(_) => Unit
    }
  }

  private def printHelp(): Unit = {
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
