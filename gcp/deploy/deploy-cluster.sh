#!/bin/bash
# Master deployment script for 4-VM Kafka cluster

# Get VM IPs (replace with your actual IPs)
VM1_EXTERNAL_IP="YOUR_VM1_EXTERNAL_IP"
VM2_EXTERNAL_IP="YOUR_VM2_EXTERNAL_IP" 
VM3_EXTERNAL_IP="YOUR_VM3_EXTERNAL_IP"
VM4_EXTERNAL_IP="YOUR_VM4_EXTERNAL_IP"

VM1_INTERNAL_IP="YOUR_VM1_INTERNAL_IP"
VM2_INTERNAL_IP="YOUR_VM2_INTERNAL_IP"

# Update configuration files with actual IPs
sed "s/VM1_INTERNAL_IP/$VM1_INTERNAL_IP/g" docker/kafka-compose.yml.template > docker/kafka-compose.yml
sed -i "s/VM2_INTERNAL_IP/$VM2_INTERNAL_IP/g" docker/kafka-compose.yml
sed -i "s/VM2_EXTERNAL_IP/$VM2_EXTERNAL_IP/g" docker/kafka-compose.yml

echo "=== Deploying to VM-1 (Zookeeper) ==="
gcloud compute scp docker/zookeeper-compose.yml vm1:~/
gcloud compute scp deploy/setup-vm1-zookeeper.sh vm1:~/
gcloud compute ssh vm1 --command="chmod +x setup-vm1-zookeeper.sh && ./setup-vm1-zookeeper.sh"

echo "=== Uploading parquet file to VM-1 ==="
gcloud compute scp data/yellow_tripdata_2025-02.parquet vm1:~/taxi-stream/data/

echo "=== Deploying to VM-2 (Kafka) ==="
gcloud compute scp docker/kafka-compose.yml vm2:~/
gcloud compute scp deploy/setup-vm2-kafka.sh vm2:~/
gcloud compute ssh vm2 --command="chmod +x setup-vm2-kafka.sh && ./setup-vm2-kafka.sh"

echo "=== Deploying to VM-3 (Producer) ==="
gcloud compute scp --recurse src/ vm3:~/taxi-stream/
gcloud compute scp build.sbt vm3:~/taxi-stream/
gcloud compute scp --recurse project/ vm3:~/taxi-stream/
gcloud compute scp deploy/setup-vm3-producer.sh vm3:~/
gcloud compute ssh vm3 --command="chmod +x setup-vm3-producer.sh && ./setup-vm3-producer.sh"

echo "=== Deploying to VM-4 (Consumer) ==="
gcloud compute scp --recurse src/ vm4:~/taxi-stream/
gcloud compute scp build.sbt vm4:~/taxi-stream/
gcloud compute scp --recurse project/ vm4:~/taxi-stream/
gcloud compute scp deploy/setup-vm4-consumer.sh vm4:~/
gcloud compute ssh vm4 --command="chmod +x setup-vm4-consumer.sh && ./setup-vm4-consumer.sh"

echo "=== Deployment Complete ==="
echo "VM-1: Zookeeper running on $VM1_EXTERNAL_IP:2181"
echo "VM-2: Kafka running on $VM2_EXTERNAL_IP:29092"
echo "VM-3: Producer ready - run 'sbt run' to start"
echo "VM-4: Consumer ready - run 'sbt run' to start"