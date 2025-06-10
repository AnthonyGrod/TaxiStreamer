# Ansible Deployment for Taxi Stream Application

## Prerequisites

1. **Install Ansible:**
```bash
pip install ansible
```

2. **Update inventory.yml:**
- Replace `YOUR_VM*_EXTERNAL_IP` with actual external IPs
- Replace `YOUR_VM*_INTERNAL_IP` with actual internal IPs  
- Replace `YOUR_GCP_USERNAME` with your GCP username
- Replace `YOUR_GCP_SSH_KEY` with path to your SSH key

3. **Test connectivity and file paths:**
```bash
cd gcp/ansible
ansible all -m ping
ansible-playbook test-paths.yml
```

## Deployment Steps

### 1. Deploy Infrastructure

**Option A: Ultra-Simple (RECOMMENDED - individual file copying)**
```bash
cd gcp/ansible
ansible-playbook site-ultra-simple.yml
```

**Option B: Pure Docker (synchronize module)**
```bash
cd gcp/ansible
ansible-playbook site-simple.yml
```

**Option C: Docker Compose (fixed for distributed setup)**
```bash
cd gcp/ansible
ansible-playbook site.yml
```

### 2. Start Applications
```bash
# Start producer
ansible-playbook run-apps.yml -e action=start --limit producer

# Start consumer  
ansible-playbook run-apps.yml -e action=start --limit consumer
```

### 3. Stop Applications
```bash
# Stop producer
ansible-playbook run-apps.yml -e action=stop --limit producer

# Stop consumer
ansible-playbook run-apps.yml -e action=stop --limit consumer
```

## Manual Commands

### Check Status
```bash
# Check all services
ansible all -m shell -a "docker ps"

# Check Kafka topics
ansible kafka -m shell -a "docker exec kafka kafka-topics --list --bootstrap-server localhost:9092"

# Check logs
ansible producer -m shell -a "tail -f ~/taxi-stream/logs/producer.log"
ansible consumer -m shell -a "tail -f ~/taxi-stream/logs/consumer.log"
```

### Debug
```bash
# Test connectivity
ansible all -m ping

# Run comprehensive debug
ansible-playbook debug.yml

# Check disk space
ansible all -m shell -a "df -h"

# Check memory
ansible all -m shell -a "free -h"
```

## Troubleshooting

### Docker Compose Version Error (Fixed)
The error was due to Docker Compose version compatibility issues. Multiple solutions provided:

1. **Fixed version installation**: Uses compatible docker-compose==1.29.2 via pip
2. **Auto-detection**: Tries Docker Compose V2 first, falls back to V1
3. **Pure Docker**: Alternative deployment using pure docker commands (site-simple.yml)

### SBT Installation Error (Fixed)
The error occurred because SBT was trying to run `sbt version` in a directory without build.sbt. Fixed by using `sbt --version` instead.

### Java Version Error (Fixed)
Spark 4.0.0 requires Java 17+, but the deployment was using Java 11. Fixed by updating to `openjdk-17-jdk`.

**If you already deployed with Java 11, upgrade it:**
```bash
# Upgrade Java to version 17
ansible-playbook upgrade-java.yml

# Clean and recompile project
ansible-playbook clean-recompile.yml
```

### If deployment still fails:
1. Try the simple deployment: `ansible-playbook site-simple.yml`
2. Run debug playbook: `ansible-playbook debug.yml`
3. Verify file copying: `ansible-playbook verify-files.yml`
4. Check specific VM: `ansible vm1 -m ping`
5. Check Docker: `ansible zookeeper -m shell -a "docker ps"`
6. Manual cleanup: `ansible all -m shell -a "docker system prune -f"`

### File Path Error (Fixed)
The error occurred because Ansible couldn't find source files. The issue was incorrect relative paths since Ansible files are in `gcp/ansible/` subdirectory. Fixed by updating paths from `../` to `../../`.

### File Copying Issues:
```bash
# Test file paths before deployment
ansible-playbook test-paths.yml

# Verify files are copied correctly
ansible-playbook verify-files.yml

# Check project files on specific VM
ansible producer -m shell -a "ls -la ~/taxi-stream/"
ansible producer -m shell -a "cat ~/taxi-stream/build.sbt"
```

## Architecture

- **VM1**: Zookeeper + Data Storage
- **VM2**: Kafka Broker  
- **VM3**: Producer (TaxiTripStreamer)
- **VM4**: Consumer

## Ports

- Zookeeper: 2181
- Kafka Internal: 9092
- Kafka External: 29092