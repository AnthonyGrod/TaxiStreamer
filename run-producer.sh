#!/bin/bash

# Taxi Trip Producer with Spark Submit
# This script launches the producer with 1 worker

echo "Starting Taxi Trip Producer with Spark Submit..."

spark-submit \
  --class producer.TaxiTripStreamer \
  --master local[1] \
  --driver-memory 2g \
  --executor-memory 1g \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.13:4.0.0 \
  --conf spark.sql.adaptive.enabled=true \
  --conf spark.sql.adaptive.coalescePartitions.enabled=true \
  --conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
  --name "TaxiTripProducer" \
  producer/target/scala-2.13/taxi-producer.jar