provider "google" {
  credentials = file(var.deployKeyName)
  project     = var.project
  region      = var.region
  zone        = var.zone
}


# VM 0: Kafka
resource "google_compute_instance" "kafka" {
  name         = "taxi-kafka-worker"
  machine_type = var.kafka_machine_type
  zone         = var.zone

  tags = ["taxi-stream", "kafka"]

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2204-lts"
      size  = 30
      type  = "pd-standard"
    }
  }

  network_interface {
    network = "default"
    access_config {
      // Ephemeral public IP
    }
  }

  metadata = {
    ssh-keys = "${var.ssh_user}:${file(var.ssh_public_key_path)}"
    startup-script = <<-EOT
      #!/bin/bash
      # Ensure proper PATH and shell environment
      echo 'export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"' >> /etc/environment
      source /etc/environment
      # Update system packages
      apt-get update
      apt-get install -y curl wget unzip vim htop net-tools
    EOT
  }

}

# VM1: Spark
resource "google_compute_instance" "spark-instance" {
  name         = "taxi-spark-instance"
  machine_type = var.spark_machine_type
  zone         = var.zone

  tags = ["taxi-stream", "spark"]

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2204-lts"
      size  = 30
      type  = "pd-standard"
    }
  }

  network_interface {
    network = "default"
    access_config {
      // Ephemeral public IP
    }
  }

  metadata = {
    ssh-keys = "${var.ssh_user}:${file(var.ssh_public_key_path)}"
    startup-script = <<-EOT
      #!/bin/bash
      # Ensure proper PATH and shell environment
      echo 'export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"' >> /etc/environment
      source /etc/environment
      # Update system packages
      apt-get update
      apt-get install -y curl wget unzip vim htop net-tools
    EOT
  }

}

# Output the IP addresses
output "kafka_ip" {
  value = google_compute_instance.kafka.network_interface.0.access_config.0.nat_ip
  description = "External IP of Kafka VM"
}

output "spark_ip" {
  value = google_compute_instance.spark-instance.network_interface.0.access_config.0.nat_ip
  description = "External IP of Spark Master VM"
}



output "deployment_summary" {
  value = {
    kafka = "${google_compute_instance.kafka.network_interface.0.access_config.0.nat_ip}:29092"
    spark_instance = "${google_compute_instance.spark-instance.network_interface.0.access_config.0.nat_ip}:8080"
  }
}