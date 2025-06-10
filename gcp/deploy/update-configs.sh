#!/bin/bash
# Update application configs for distributed deployment

VM1_INTERNAL_IP=$1
VM2_INTERNAL_IP=$2

if [ -z "$VM1_INTERNAL_IP" ] || [ -z "$VM2_INTERNAL_IP" ]; then
    echo "Usage: $0 <VM1_INTERNAL_IP> <VM2_INTERNAL_IP>"
    exit 1
fi

echo "Updating producer configuration..."
# Update producer to connect to VM2's Kafka
sed -i "s/localhost:29092/$VM2_INTERNAL_IP:9092/g" src/main/scala/producer/TaxiTripStreamer.scala

# Update parquet file path to use network storage or local copy
sed -i "s|/home/agrodowski/Desktop/MIM/PDD/KAFKA/taxi-stream/data/|/home/\$(whoami)/taxi-stream/data/|g" src/main/scala/producer/TaxiTripStreamer.scala

echo "Updating consumer configuration..."
# Update consumer to connect to VM2's Kafka  
sed -i "s/localhost:29092/$VM2_INTERNAL_IP:9092/g" src/main/scala/consumer/Consumer.scala

# Update log paths
sed -i "s|/home/agrodowski/Desktop/MIM/PDD/KAFKA/taxi-stream/logs/|/home/\$(whoami)/taxi-stream/logs/|g" src/main/scala/consumer/Consumer.scala
sed -i "s|/home/agrodowski/Desktop/MIM/PDD/KAFKA/taxi-stream/logs/|/home/\$(whoami)/taxi-stream/logs/|g" src/main/scala/producer/TaxiTripStreamer.scala

echo "Configuration updated for distributed deployment"