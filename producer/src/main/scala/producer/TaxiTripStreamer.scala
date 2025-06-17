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

    // Read Parquet and create trip events
    val tripsDF = spark.read
      .parquet("data/yellow_tripdata_2025-02.parquet")
      .select(
        unix_timestamp($"tpep_pickup_datetime").cast("long").alias("pickup_time"),
        unix_timestamp($"tpep_dropoff_datetime").cast("long").alias("dropoff_time"),
        $"PULocationID",
        $"DOLocationID"
      )
      .withColumn("trip_id", monotonically_increasing_id())

    // Create pickup events
    val pickupEvents = tripsDF
      .select(
        $"trip_id",
        $"pickup_time".alias("event_time"),
        lit("pickup").alias("event_type"),
        $"PULocationID".alias("location_id")
      )

    // Create dropoff events
    val dropoffEvents = tripsDF
      .select(
        $"trip_id",
        $"dropoff_time".alias("event_time"),
        lit("dropoff").alias("event_type"),
        $"DOLocationID".alias("location_id")
      )

    // Union pickup and dropoff events, then sort by time
    val allEventsDF = pickupEvents.union(dropoffEvents)
      .orderBy($"event_time")

    allEventsDF.printSchema()
    println(allEventsDF.show(100))

    val totalEvents = allEventsDF.count()
    println(s"Total events in dataset: $totalEvents")

    val logWriter = new PrintWriter(new FileWriter("logs/trip-num-check.txt", true))
    logWriter.println(s"[${LocalDateTime.now()}] PRODUCER: Total events in dataset: $totalEvents")
    logWriter.flush()

    val recordIter = allEventsDF.toLocalIterator().asScala.map { row =>
      val tripId = row.getLong(0)
      val eventTime = row.getLong(1)
      val eventType = row.getString(2)
      val locationId = row.getInt(3)
      (tripId, eventTime, eventType, locationId)
    }

    val firstRecord = recordIter.buffered.headOption
    if (firstRecord.isEmpty) {
      println("No records to process.")
      spark.stop()
      return
    }

    val baseRealTime = System.currentTimeMillis()
    val baseDataTime = firstRecord.get._2

    logWriter.println(s"[${LocalDateTime.now()}] PRODUCER: First event in ms: $baseDataTime")
    logWriter.flush()

    val speed = 3600.0

    // Kafka setup
    val props = new Properties()
    props.put("bootstrap.servers", "10.186.0.39:9092")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)

    def sendEvent(producer: KafkaProducer[String, String], tripId: Long, eventTime: Long, eventType: String, locationId: Int): Unit = {
      val (topic, locationField) = eventType match {
        case "pickup" => ("trip-start", "PULocationID")
        case "dropoff" => ("trip-end", "DOLocationID")
      }
      
      val message = s"""{"trip_id":$tripId, "time":$eventTime, "$locationField":$locationId}"""
      producer.send(new ProducerRecord[String, String](topic, null, message))
      
      println(s"Sent $eventType for trip $tripId at location $locationId")
    }

    val consumerThread = new Thread(new Runnable() {
      override def run(): Unit = {
        Consumer.run()
      }
    })
    consumerThread.start()

    var eventsSent = 0
    recordIter.foreach { case (tripId, eventTime, eventType, locationId) =>
      val now = System.currentTimeMillis()
      val simWallTime = baseDataTime + ((now - baseRealTime) * speed).toLong

      if (eventTime <= simWallTime) {
        // Send event immediately
        sendEvent(producer, tripId, eventTime, eventType, locationId)
        eventsSent += 1
      } else {
        // Wait before sending
        val waitMs = ((eventTime - simWallTime) / speed).toLong
        if (waitMs > 0) Thread.sleep(waitMs)
        sendEvent(producer, tripId, eventTime, eventType, locationId)
        eventsSent += 1
      }
    }

    logWriter.println(s"[${LocalDateTime.now()}] PRODUCER: Total events sent: $eventsSent")
    logWriter.close()
    println(s"Total events sent: $eventsSent")

    producer.close()

    consumerThread.join()

    spark.stop()
  }
}


