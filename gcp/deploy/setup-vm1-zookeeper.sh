#!/bin/bash
# VM-1: Setup Zookeeper + File Storage

# Install Docker and Docker Compose
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
sudo curl -L "https://github.com/docker/compose/releases/download/1.29.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Create directories
mkdir -p ~/taxi-stream/data
mkdir -p ~/taxi-stream/logs
mkdir -p ~/taxi-stream/docker

# Copy parquet file (you'll need to upload this)
# gcloud compute scp yellow_tripdata_2025-02.parquet VM1_NAME:~/taxi-stream/data/

# Start Zookeeper
cd ~/taxi-stream/docker
docker-compose -f zookeeper-compose.yml up -d

echo "VM-1 setup complete. Zookeeper running on port 2181"