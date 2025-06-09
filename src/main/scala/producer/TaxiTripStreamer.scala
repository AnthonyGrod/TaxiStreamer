package producer

import org.apache.spark.sql.{SparkSession, Row}
import org.apache.spark.sql.functions._
import java.util.Properties
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

object TaxiTripStreamer {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder
      .appName("TaxiTripStreamer")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    // Read Parquet
    val df = spark.read
      .parquet("/home/agrodowski/Desktop/MIM/PDD/KAFKA/taxi-stream/data/yellow_tripdata_2025-02.parquet")
      .select(
        unix_timestamp($"tpep_pickup_datetime").cast("long").alias("pickup_time"),
        unix_timestamp($"tpep_dropoff_datetime").cast("long").alias("dropoff_time"),
        $"PULocationID",
        $"DOLocationID"
      )

    df.printSchema()
    println(df.take(2).mkString("Array(", ", ", ")"))

    val records = df.map(row => {
      val pickup = row.getLong(0)
      val dropoff = row.getLong(1)

      val pu = row.getInt(2)
      val doo = row.getInt(3)

      (pickup, dropoff, pu, doo)
    }).collect().sortBy(_._1)

    // Simulate wall clock
    val baseRealTime = System.currentTimeMillis()
    val baseDataTime = records.head._1
    val speed = 3600.0  // 2x faster

    println(s"Starting stream at 2x speed...")

    // Kafka setup
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:29092")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)

    // Stream loop. TODO: create RDD stream with DStream
    records.foreach { case (pickup, dropoff, pu, doo) =>
      val now = System.currentTimeMillis()
      val simWallTime = baseDataTime + ((now - baseRealTime) * speed).toLong

      if (pickup <= simWallTime) {
        val pickupMsg = s"""{"time":$pickup, "PULocationID":$pu}"""
        val dropoffMsg = s"""{"time":$dropoff, "DOLocationID":$doo}"""

        producer.send(new ProducerRecord[String, String]("trip-start", null, pickupMsg))
        producer.send(new ProducerRecord[String, String]("trip-end", null, dropoffMsg))

        println(s"Sent pickup & dropoff for PU=$pu → DO=$doo")
      } else {
        val waitMs = ((pickup - simWallTime) / speed).toLong
        Thread.sleep(waitMs)
        // Then send
        val pickupMsg = s"""{"time":$pickup, "PULocationID":$pu}"""
        val dropoffMsg = s"""{"time":$dropoff, "DOLocationID":$doo}"""
        producer.send(new ProducerRecord[String, String]("trip-start", null, pickupMsg))
        producer.send(new ProducerRecord[String, String]("trip-end", null, dropoffMsg))
      }
    }

    producer.close()
    spark.stop()
  }
}


