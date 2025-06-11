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
      .withWatermark("start_time", "1 seconds")

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
      .withWatermark("end_time", "1 seconds")

//    // Create joined table with trip_id, start_location_id, end_location_id, start_time
//    val joinedTrips = startDf.alias("start")
//      .join(
//        endDf.alias("end"),
//        expr("""
//          start.trip_id = end.trip_id AND
//          end.end_time >= start.start_time AND
//          end.end_time <= start.start_time + interval 4 hours
//        """)
//      )
//      .select(
//        col("start.trip_id"),
//        col("start.start_location_id"),
//        col("end.end_location_id"),
//        col("start.start_time")
//      )

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

    @volatile var totalTripsProcessed = 0L

    // Write hourly counts to console and logs
    val hourlyCountQuery = hourlyTripCounts.writeStream
      .outputMode("append") // Use append mode for streaming joins
      .trigger(Trigger.ProcessingTime("2 seconds"))
      .format("console")
      .option("truncate", "false")
      .foreachBatch { (batchDf: Dataset[Row], batchId: Long) =>

        println(s"=== Batch $batchId: Hourly Trip Counts ===")

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
      .start()

    hourlyCountQuery.awaitTermination()
  }
}

