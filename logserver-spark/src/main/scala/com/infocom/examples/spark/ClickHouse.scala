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

import org.apache.spark.sql.{DataFrame, SQLContext}

import java.util.Properties

object clickhouse {
  def getRange(
    sc: SQLContext,
    jdbcUri: String,
    jdbcProps: Properties,
    sat: String,
    f1Name: String,
    f2Name: String,
    from: Long,
    to: Long
  ): DataFrame = {

    sc.read.jdbc(
      jdbcUri,
      s"""
         |(
         |SELECT
         |  *
         |FROM
         |  rawdata.range
         |WHERE
         |  sat='$sat' AND d BETWEEN toDate($from/1000) AND toDate($to/1000) AND time BETWEEN $from AND $to
         |  AND freq in ('$f1Name', '$f2Name')
         |ORDER BY
         |  sat
         |)
       """.stripMargin,
      jdbcProps
    )
  }

  def getSatxyz2(
    sc: SQLContext,
    jdbcUri: String,
    jdbcProps: Properties,
    sat: String,
    from: Long,
    to: Long
  ): DataFrame = {

    sc.read.jdbc(
      jdbcUri,
      s"""
         |(
         |SELECT
         |  *
         |FROM
         |  rawdata.satxyz2
         |WHERE
         |  sat='$sat' AND d BETWEEN toDate($from/1000) AND toDate($to/1000) AND time BETWEEN $from AND $to
         |ORDER BY
         |  sat
         |)
       """.stripMargin,
      jdbcProps
    )
  }

  def getIsmdetobs(
    sc: SQLContext,
    jdbcUri: String,
    jdbcProps: Properties,
    sat: String,
    f1Name: String,
    f2Name: String,
    from: Long,
    to: Long
  ): DataFrame = {

    sc.read.jdbc(
      jdbcUri,
      s"""
         |(
         |SELECT
         |  *
         |FROM
         |  rawdata.ismdetobs
         |WHERE
         |  sat='$sat' AND d BETWEEN toDate($from/1000) AND toDate($to/1000) AND time BETWEEN $from AND $to
         |  AND freq in ('$f1Name', '$f2Name')
         |ORDER BY
         |  sat
         |)
       """.stripMargin,
      jdbcProps
    )
  }

  def getSigParams(
    sc: SQLContext,
    jdbcUri: String,
    jdbcProps: Properties
  ): DataFrame = {
    sc.read.jdbc(
      jdbcUri,
      s"""
         |(SELECT * FROM misc.target_signal_params
         |FINAL)
        """.stripMargin,
      jdbcProps
    )
  }
}
