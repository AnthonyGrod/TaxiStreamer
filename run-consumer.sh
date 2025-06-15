#!/bin/bash

# Taxi Trip Consumer with Spark Submit
# This script launches the consumer with 4 workers

echo "Starting Taxi Trip Consumer with Spark Submit..."

spark-submit \
  --class consumer.Consumer \
  --master local[4] \
  --driver-memory 4g \
  --executor-memory 2g \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.13:4.0.0 \
  --conf spark.sql.adaptive.enabled=true \
  --conf spark.sql.adaptive.coalescePartitions.enabled=true \
  --conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
  --conf spark.sql.streaming.checkpointLocation.cleanup=true \
  --name "TaxiTripConsumer" \
  consumer/target/scala-2.13/taxi-consumer.jar