package consumer

import org.apache.spark.sql.{Dataset, SparkSession}
import java.io.{FileWriter, PrintWriter}
import java.time.LocalDateTime

object Consumer{
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("KafkaConsumerExample")
      .master("local[*]") // for local testing
      .getOrCreate()

    import spark.implicits._

    val startDf = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:29092")
      .option("subscribe", "trip-start")
      .load()
      .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
      .as[(String, String)]

    val endDf = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:29092")
      .option("subscribe", "trip-end")
      .load()
      .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
      .as[(String, String)]

    @volatile var messagesReceived = 0L

    val query = startDf.writeStream
      .outputMode("append")
      .format("console")
      .option("truncate", "false")
      .foreachBatch { (batchDf: Dataset[(String, String)], batchId: Long) =>
        val batchCount = batchDf.count()
        messagesReceived += batchCount
        println(s"Batch $batchId: Received $batchCount messages. Total: $messagesReceived")
        
        val logWriter = new PrintWriter(new FileWriter("/home/agrodowski/Desktop/MIM/PDD/KAFKA/taxi-stream/logs/trip-num-check.txt", true))
        logWriter.println(s"[${LocalDateTime.now()}] CONSUMER: Batch $batchId - received $batchCount messages, total: $messagesReceived")
        logWriter.close()
        
        batchDf.show(truncate = false)
      }
      .start()

    query.awaitTermination()
  }
}

