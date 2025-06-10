#!/bin/bash
# VM-3: Setup Producer (Scala/Spark)

# Install Java 11
sudo apt update
sudo apt install -y openjdk-11-jdk

# Install SBT
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt update
sudo apt install -y sbt

# Create project directory
mkdir -p ~/taxi-stream
cd ~/taxi-stream

# Copy project files (you'll need to upload these)
# gcloud compute scp --recurse src/ VM3_NAME:~/taxi-stream/
# gcloud compute scp build.sbt VM3_NAME:~/taxi-stream/
# gcloud compute scp project/ VM3_NAME:~/taxi-stream/

# Update producer configuration to point to VM2's Kafka
# sed -i 's/localhost:29092/VM2_INTERNAL_IP:9092/g' src/main/scala/producer/TaxiTripStreamer.scala

# Update parquet file path to point to VM1's shared storage
# You'll need to mount VM1's storage or copy the file locally

echo "VM-3 setup complete. Ready to run producer"