#!/bin/bash
# VM-4: Setup Consumer (Scala/Spark)

# Install Java 11
sudo apt update
sudo apt install -y openjdk-11-jdk

# Install SBT
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt update
sudo apt install -y sbt

# Create project directory and logs
mkdir -p ~/taxi-stream/logs
cd ~/taxi-stream

# Copy project files (you'll need to upload these)
# gcloud compute scp --recurse src/ VM4_NAME:~/taxi-stream/
# gcloud compute scp build.sbt VM4_NAME:~/taxi-stream/
# gcloud compute scp project/ VM4_NAME:~/taxi-stream/

# Update consumer configuration to point to VM2's Kafka
# sed -i 's/localhost:29092/VM2_INTERNAL_IP:9092/g' src/main/scala/consumer/Consumer.scala

echo "VM-4 setup complete. Ready to run consumer"