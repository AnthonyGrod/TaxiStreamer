import org.apache.spark.sql.{Dataset, Row, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types._

import java.io.{FileWriter, PrintWriter}
import java.time.LocalDateTime

object Consumer{
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("KafkaConsumerExample")
      .master("local[*]")
      .config("spark.sql.adaptive.enabled", "true")
      .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
      .getOrCreate()

    import spark.implicits._

    // Define schema for trip data based on producer JSON structure
    val tripStartSchema = StructType(Array(
      StructField("trip_id", LongType, true),
      StructField("time", LongType, true),
      StructField("PULocationID", IntegerType, true)
    ))

    val tripEndSchema = StructType(Array(
      StructField("trip_id", LongType, true),
      StructField("time", LongType, true),
      StructField("DOLocationID", IntegerType, true)
    ))

    val hourlyCountSchema = StructType(Array(
      StructField("hour_start", TimestampType, true),
      StructField("trip_count", LongType, true),
      StructField("first_trip_time", TimestampType, true),
      StructField("last_trip_time", TimestampType, true)
    ))

    // Read trip-start stream
    val startDf = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:29092")
      .option("subscribe", "trip-start")
      .option("startingOffsets", "latest")
      .load()
      .select(
        col("key").cast("string").as("trip_id_key"),
        from_json(col("value").cast("string"), tripStartSchema).as("data")
      )
      .select(
        col("data.trip_id"),
        col("data.PULocationID").as("start_location_id"),
        from_unixtime(col("data.time")).cast("timestamp").as("start_time")
      )
      .withWatermark("start_time", "10 seconds")

    // Read trip-end stream
    val endDf = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:29092")
      .option("subscribe", "trip-end")
      .option("startingOffsets", "latest")
      .load()
      .select(
        col("key").cast("string").as("trip_id_key"),
        from_json(col("value").cast("string"), tripEndSchema).as("data")
      )
      .select(
        col("data.trip_id"),
        col("data.DOLocationID").as("end_location_id"),
        from_unixtime(col("data.time")).cast("timestamp").as("end_time")
      )
      .withWatermark("end_time", "10 seconds")


    // Count trips every hour based on start_time
    val hourlyTripCounts = startDf
      .withColumn("hour_window", date_trunc("hour", col("start_time")))
      .groupBy(
        window(col("start_time"), "1 hour").as("time_window")
      )
      .agg(
        count("*").as("trip_count"),
        min("start_time").as("first_trip_time"),
        max("start_time").as("last_trip_time")
      )
      .select(
        col("time_window.start").as("hour_start"),
        col("trip_count"),
        col("first_trip_time"),
        col("last_trip_time")
      )

    // Write hourly counts to Kafka for downstream processing
    val hourlyCountQuery = hourlyTripCounts
      .select(
        col("hour_start").cast("string").as("key"),
        to_json(struct(
          col("hour_start"),
          col("trip_count"),
          col("first_trip_time"),
          col("last_trip_time")
        )).as("value")
      )
      .writeStream
      .outputMode("append")
      .trigger(Trigger.ProcessingTime("1 second"))
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:29092")
      .option("topic", "hourly-counts")
      .option("checkpointLocation", "/tmp/spark-checkpoint/hourly-counts-kafka")
      .start()

    @volatile var totalTripsProcessed = 0L

    // Write hourly counts to console and logs
    val hourlyCountQuery2 = hourlyTripCounts.writeStream
      .outputMode("append")
      .trigger(Trigger.ProcessingTime("1 second"))
      .foreachBatch { (batchDf: Dataset[Row], batchId: Long) =>

        println(s"=== Batch $batchId: Hourly Trip Counts ===")

        if (!batchDf.isEmpty) {
          // Sort the batch by hour_start for consistent ordering
          val sortedRows = batchDf.orderBy("hour_start").collect()

          sortedRows.foreach { row =>
            val hourStart = row.getTimestamp(0)
            val tripCount = row.getLong(1)
            val firstTrip = row.getTimestamp(2)
            val lastTrip = row.getTimestamp(3)

            // Calculate hour end (add 1 hour to start)
            val hourEnd = new java.sql.Timestamp(hourStart.getTime + 3600000L)

            val logMessage = s"[${LocalDateTime.now()}] HOURLY_COUNT: Hour ${hourStart} to ${hourEnd} - ${tripCount} trips started"

            // Write to log file
            val logWriter = new PrintWriter(new FileWriter("/home/agrodowski/Desktop/MIM/PDD/TAXI-SECOND/taxi-stream/logs/hourly-trip-counts.txt", true))
            logWriter.println(logMessage)
            logWriter.flush()
            logWriter.close()

            // Also print to console
            println(logMessage)

            totalTripsProcessed += tripCount
          }

          // Show the detailed batch data
          batchDf.show(truncate = false)

          // Write summary to main log file
          val summaryWriter = new PrintWriter(new FileWriter("/home/agrodowski/Desktop/MIM/PDD/TAXI-SECOND/taxi-stream/logs/trip-num-check.txt", true))
          summaryWriter.println(s"[${LocalDateTime.now()}] CONSUMER: Batch $batchId - processed hourly counts, total trips processed: $totalTripsProcessed")
          summaryWriter.close()
        }
      }
      .option("checkpointLocation", "/tmp/spark-checkpoint/hourly-counts-logs")
      .start()

    // Read hourly counts from Kafka and calculate daily aggregates
    val hourlyCountsStream = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:29092")
      .option("subscribe", "hourly-counts")
      .option("startingOffsets", "earliest") // Changed from "latest" to process all data
      .load()
      .select(
        from_json(col("value").cast("string"), hourlyCountSchema).as("hourly_data")
      )
      .select(
        col("hourly_data.hour_start"),
        col("hourly_data.trip_count"),
        col("hourly_data.first_trip_time"),
        col("hourly_data.last_trip_time")
      )
      .withWatermark("hour_start", "1 hour")

    // Calculate daily trip counts
    val dailyTripCounts = hourlyCountsStream
      .withColumn("day", date_trunc("day", col("hour_start")))
      .groupBy(
        window(col("hour_start"), "1 day").as("day_window")
      )
      .agg(
        sum("trip_count").as("daily_trip_count"),
        count("*").as("hours_with_data"),
        min("first_trip_time").as("first_trip_of_day"),
        max("last_trip_time").as("last_trip_of_day")
      )
      .select(
        col("day_window.start").as("day_start"),
        col("daily_trip_count"),
        col("hours_with_data"),
        col("first_trip_of_day"),
        col("last_trip_of_day")
      )

    @volatile var totalDailyTripsProcessed = 0L

    // Write daily counts to log file
    val dailyCountQuery = dailyTripCounts
      .writeStream
      .outputMode("append")
      .trigger(Trigger.ProcessingTime("5 seconds"))
      .foreachBatch { (batchDf: Dataset[Row], batchId: Long) =>

        println(s"=== Batch $batchId: Daily Trip Counts ===")

        if (!batchDf.isEmpty) {
          val sortedRows = batchDf.orderBy("day_start").collect()

          sortedRows.foreach { row =>
            val dayStart = row.getTimestamp(0)
            val dailyTripCount = row.getLong(1)
            val hoursWithData = row.getLong(2)

            // Calculate day end (add 1 day to start)
            val dayEnd = new java.sql.Timestamp(dayStart.getTime + 86400000L) // 24 hours in milliseconds

            val logMessage = s"[${LocalDateTime.now()}] DAILY_COUNT: Day ${dayStart} to ${dayEnd} - ${dailyTripCount} trips started (${hoursWithData} hours with data)"

            // Write to log file
            val logWriter = new PrintWriter(new FileWriter("/home/agrodowski/Desktop/MIM/PDD/TAXI-SECOND/taxi-stream/logs/daily-trip-counts.txt", true))
            logWriter.println(logMessage)
            logWriter.flush()
            logWriter.close()

            // Also print to console
            println(logMessage)

            totalDailyTripsProcessed += dailyTripCount
          }

          // Show the detailed batch data
          batchDf.show(truncate = false)

          // Write summary to main log file
          val summaryWriter = new PrintWriter(new FileWriter("/home/agrodowski/Desktop/MIM/PDD/TAXI-SECOND/taxi-stream/logs/trip-num-check.txt", true))
          summaryWriter.println(s"[${LocalDateTime.now()}] CONSUMER: Batch $batchId - processed daily counts, total daily trips processed: $totalDailyTripsProcessed")
          summaryWriter.close()
        }
      }
      .option("checkpointLocation", "/tmp/spark-checkpoint/daily-counts")
      .start()

    // Wait for all streams to terminate
    spark.streams.awaitAnyTermination()
  }
}