ThisBuild / version := "1.0"
ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "ch.epfl.scala"

val sparkVersion = "4.0.0"
val kafkaVersion = "4.0.0"

val commonDependencies = Seq(
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0",
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion
)

val kafkaDependencies = Seq(
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion)

// Producer module
lazy val producer = (project in file("producer"))
  .settings(
    name := "taxi-producer",
    libraryDependencies ++= commonDependencies ++ kafkaDependencies,
    assembly / mainClass := Some("producer.TaxiTripStreamer"),
    assembly / assemblyJarName := "taxi-producer.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )
  .dependsOn(consumer)

// Consumer module  
lazy val consumer = (project in file("consumer"))
  .settings(
    name := "taxi-consumer",
    libraryDependencies ++= commonDependencies ++ kafkaDependencies,
    assembly / mainClass := Some("consumer.Consumer"),
    assembly / assemblyJarName := "taxi-consumer.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )

// Root project
lazy val root = (project in file("."))
  .aggregate(producer, consumer)
  .settings(
    name := "taxi-stream",
    publish := {},
    publishLocal := {}
  )