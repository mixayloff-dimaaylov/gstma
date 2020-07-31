#!/bin/bash

/opt/spark/bin/spark-submit \
	--deploy-mode client \
        --master local[*] \
	--class com.infocom.examples.spark.TecCalculation \
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
	12000000
