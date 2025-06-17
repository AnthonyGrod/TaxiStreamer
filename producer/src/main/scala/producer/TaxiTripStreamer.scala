package producer

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

import java.io.{FileWriter, PrintWriter}
import java.time.LocalDateTime
import java.util.Properties
import scala.jdk.CollectionConverters.IteratorHasAsScala

import consumer.Consumer

object TaxiTripStreamer {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder
      .appName("TaxiTripStreamer")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    // Read Parquet
    val df = spark.read
      .parquet("data/yellow_tripdata_2025-02.parquet")
      .select(
        unix_timestamp($"tpep_pickup_datetime").cast("long").alias("pickup_time"),
        unix_timestamp($"tpep_dropoff_datetime").cast("long").alias("dropoff_time"),
        $"PULocationID",
        $"DOLocationID"
      )
      .withColumn("trip_id", monotonically_increasing_id())
      .orderBy($"pickup_time")

    df.printSchema()
    println(df.show(100))


    val totalTrips = df.count()
    println(s"Total trips in parquet file: $totalTrips")

    val logWriter = new PrintWriter(new FileWriter("logs/trip-num-check.txt", true))
    logWriter.println(s"[${LocalDateTime.now()}] PRODUCER: Total trips in parquet file: $totalTrips")
    logWriter.flush()

    val recordIter = df.toLocalIterator().asScala.map { row =>
      val pickup = row.getLong(0)
      val dropoff = row.getLong(1)
      val pu = row.getInt(2)
      val doo = row.getInt(3)
      val tripId = row.getLong(4)
      (pickup, dropoff, pu, doo, tripId)
    }

    val firstRecord = recordIter.buffered.headOption
    if (firstRecord.isEmpty) {
      println("No records to process.")
      spark.stop()
      return
    }

    val baseRealTime = System.currentTimeMillis()
    val baseDataTime = firstRecord.get._1

    logWriter.println(s"[${LocalDateTime.now()}] PRODUCER: First trip in ms: $baseDataTime")
    logWriter.flush()

    val speed = 60.0  // 2x speed

    // Kafka setup
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:29092")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)

    val consumerThread = new Thread(new Runnable() {
      override def run(): Unit = {
        Consumer.run()
      }
    })
    consumerThread.start()

    var tripsSent = 0
    // TODO: find a faster way to produce trips (some Spark API for parallelism?). Also: send trip end separately based on actual time.
    recordIter.foreach { case (pickup, dropoff, pu, doo, tripId) =>
      val now = System.currentTimeMillis()
      val simWallTime = baseDataTime + ((now - baseRealTime) * speed).toLong

      if (pickup <= simWallTime) {
        val pickupMsg = s"""{"trip_id":$tripId, "time":$pickup, "PULocationID":$pu}"""
        val dropoffMsg = s"""{"trip_id":$tripId, "time":$dropoff, "DOLocationID":$doo}"""

        producer.send(new ProducerRecord[String, String]("trip-start", null, pickupMsg))
        producer.send(new ProducerRecord[String, String]("trip-end", null, dropoffMsg))
        tripsSent += 1

        println(s"Sent pickup & dropoff for trip $tripId: PU=$pu → DO=$doo")
      } else {
        val waitMs = ((pickup - simWallTime) / speed).toLong
        Thread.sleep(waitMs)
        // Then send
        val pickupMsg = s"""{"trip_id":$tripId, "time":$pickup, "PULocationID":$pu}"""
        val dropoffMsg = s"""{"trip_id":$tripId, "time":$dropoff, "DOLocationID":$doo}"""
        producer.send(new ProducerRecord[String, String]("trip-start", null, pickupMsg))
        producer.send(new ProducerRecord[String, String]("trip-end", null, dropoffMsg))
        tripsSent += 1
      }
    }

    logWriter.println(s"[${LocalDateTime.now()}] PRODUCER: Total trips sent: $tripsSent")
    logWriter.close()
    println(s"Total trips sent: $tripsSent")

    producer.close()

    consumerThread.join()

    spark.stop()
  }
}


