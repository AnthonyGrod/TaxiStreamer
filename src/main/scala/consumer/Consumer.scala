package consumer

import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe

object Consumer{
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("KafkaConsumerExample")
      .master("local[*]") // for local testing
      .getOrCreate()

    import spark.implicits._

    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:29092")
      .option("subscribe", "trip-start")
      .load()

    df.selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
      .as[(String, String)]


    val query = df.writeStream
      .outputMode("append") // use "update" for aggregations
      .format("console")
      .option("truncate", false) // so it doesn't cut off long strings
      .start()

    query.awaitTermination()
  }
}

