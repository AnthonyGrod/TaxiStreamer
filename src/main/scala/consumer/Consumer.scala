package consumer

import org.apache.kafka.clients.consumer._

import java.util.{Collections, Properties}
import scala.jdk.CollectionConverters.IterableHasAsScala

object Consumer extends App {
  val props = new Properties()
  props.put("bootstrap.servers", "localhost:9092")
  props.put("group.id", "test-group")
  props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")

  val consumer = new KafkaConsumer[String, String](props)
  consumer.subscribe(Collections.singletonList("test-topic"))

  while (true) {
    val records = consumer.poll(java.time.Duration.ofMillis(100))
    for (record <- records.asScala) {
      println(s"Consumed record with key ${record.key()} and value ${record.value()}")
    }
  }
}

