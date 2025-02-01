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

// Пример Spark Legacy Streaming
// =============================

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

// Пример с использованием Spark Streaming. Это устаревшая технология.

// ## Импорты

import $ivy.`org.apache.spark::spark-sql:2.4.0`
import $ivy.`org.apache.spark::spark-streaming:2.4.0`
import $ivy.`org.apache.spark::spark-streaming-kafka-0-10:2.4.0`

// +
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe

import org.apache.spark._
import org.apache.spark.streaming._
import org.apache.spark.streaming.StreamingContext._ // not necessary since Spark 1.3
// -

// ## Streaming

// +
// Create a local StreamingContext with two working thread and batch interval of 1 second.
// The master requires 2 cores to prevent a starvation scenario.

val conf = new SparkConf()
  .setMaster("local[*]")
  .setAppName("NetworkWordCount")
val ssc = new StreamingContext(conf, Seconds(1))

val kafkaParams = Map[String, Object](
  // Использование Docker DNS позволяет обращаться к контейнерам по именам внутри одной
  // Docker-сети. Поэтому в `bootstrap.servers` прописано имя контейнера:
  "bootstrap.servers" -> "kafka:9092",
  "key.deserializer" -> classOf[StringDeserializer],
  "value.deserializer" -> classOf[StringDeserializer],
  "group.id" -> "use_a_separate_group_id_for_each_stream",
  "auto.offset.reset" -> "latest",
  "enable.auto.commit" -> (false: java.lang.Boolean)
)

val topics = Array("rawdata.range")
val stream = KafkaUtils.createDirectStream[String, String](
  ssc,
  PreferConsistent,
  Subscribe[String, String](topics, kafkaParams)
)

val query = stream.map(record => (record.key, record.value))
//  .writeStream
//  .outputMode("complete")
//  .format("console")
//  .start()
// -

query.print()

ssc.start()             // Start the computation
ssc.awaitTermination()  // Wait for the computation to terminate
