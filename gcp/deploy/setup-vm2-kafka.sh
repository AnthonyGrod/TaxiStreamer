#!/bin/bash
# VM-2: Setup Kafka Broker

# Install Docker and Docker Compose
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
sudo curl -L "https://github.com/docker/compose/releases/download/1.29.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Create directories
mkdir -p ~/taxi-stream/docker

# Update kafka-compose.yml with actual IP addresses
# Replace VM1_INTERNAL_IP, VM2_INTERNAL_IP, VM2_EXTERNAL_IP in kafka-compose.yml

# Start Kafka
cd ~/taxi-stream/docker
docker-compose -f kafka-compose.yml up -d

# Create topics
sleep 30
docker exec $(docker ps -q -f "ancestor=confluentinc/cp-kafka") kafka-topics --create --topic trip-start --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
docker exec $(docker ps -q -f "ancestor=confluentinc/cp-kafka") kafka-topics --create --topic trip-end --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

echo "VM-2 setup complete. Kafka running on port 9092"