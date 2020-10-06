#!/bin/bash

/opt/spark/bin/spark-submit \
	--deploy-mode client \
        --master local[*] \
	--class com.infocom.examples.spark.StreamReceiver \
	--driver-memory 512m \
	--num-executors 1 \
	--executor-cores 2 \
	--executor-memory 1500m \
	--conf spark.locality.wait=10 \
	--conf spark.task.maxFailures=8 \
	--conf spark.yarn.maxAppAttempts=4 \
	--conf spark.yarn.am.attemptFailuresValidityInterval=1h \
	--conf spark.yarn.max.executor.failures=8 \
	--conf spark.yarn.executor.failuresValidityInterval=1h \
	/opt/spark/jars/novatel-streaming-assembly-1.0.jar \
	st9-ape-ionosphere2c-1:9092 st9-ape-ionosphere2s-1:8123
