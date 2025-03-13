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
import com.github.mrpowers.spark.daria.sql.DariaWriters
import com.infocom.examples.spark.clickhouse._

import scala.reflect.io.Directory
import scala.util.{Try,Success,Failure}
import java.io.File
import java.util.Properties

object AppFromDB {
  private def writeSingleFile(spark: SparkSession, df: DataFrame, path: String): Unit = {
    val tmpDir = "/tmp/spark-daria-tmp"

    val res = DariaWriters.writeSingleFile(
      df = df,
      format = "parquet",
      sc = spark.sparkContext,
      tmpFolder = tmpDir,
      filename = path
    )

    (new Directory(new File(tmpDir))).deleteRecursively()

    res
  }

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

    val clickHouseServerAddress = args(0)
    val jdbcUri = s"jdbc:clickhouse://$clickHouseServerAddress"
    val sat = args(1)
    val sigcomb = args(2).split("\\+")
    val f1Name = sigcomb(0)
    val f2Name = sigcomb(1)
    val from = args(3).toLong
    val to = args(4).toLong

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
    jdbcProps.setProperty("customSchema", "time INTEGER")

    // Target signal parameters
    val sc = spark.sqlContext
    val sig_params = getSigParams(sc, jdbcUri, jdbcProps)

    val rangeDeser = getRange(sc, jdbcUri, jdbcProps, sat, f1Name, f2Name, from, to)
    val satxyz2Deser = getSatxyz2(sc, jdbcUri, jdbcProps, sat, from, to)
    val ismdetobsDeser = getIsmdetobs(sc, jdbcUri, jdbcProps, sat, f1Name, f2Name, from, to)

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
        writeSingleFile(spark, range, "computed.NT.parquet")
        writeSingleFile(spark, derivativesNT, "computed.NTDerivatives.parquet")
        writeSingleFile(spark, xz1, "computed.xz1.parquet")
        writeSingleFile(spark, s4cno, "computed.s4cno.parquet")
        writeSingleFile(spark, s4pwr, "computed.s4pwr.parquet")
        writeSingleFile(spark, s4, "computed.s4.parquet")
      }

      case Failure(_) => Unit
    }
  }

  private def printHelp(): Unit = {
    val usagestr = """
    Usage: <progname> <clickhouse_server> <sat> <sigcomb> <from> <to>
    <clickhouse_server> - ClickHouse server (HTTP-interface) address:port, (string)
    """
    System.out.println(usagestr)
  }
}
