variable "project" {
  description = "Project ID"
  type        = string
  default     = "bigdata-ag438477"
}

variable "region" {
  description = "GCP Region"
  type        = string
  default     = "europe-central2"
}

variable "zone" {
  description = "GCP Zone"
  type        = string
  default     = "europe-central2-c"
}

variable "deployKeyName" {
  description = "Path to the GCP service account key file"
  type        = string
  default     = "deployment-key.json"
}

variable "ssh_user" {
  description = "SSH username for accessing VMs"
  type        = string
  default     = "a_grodowski_student_uw_edu_pl"
}

variable "ssh_public_key_path" {
  description = "Path to SSH public key for VM access"
  type        = string
  default     = "~/.ssh/id_rsa.pub"
}

# Machine type configurations for different VMs
variable "kafka_machine_type" {
  description = "Machine type for Kafka VM"
  type        = string
  default     = "e2-standard-4"  # 2 vCPUs, 8GB RAM
}

variable "spark_machine_type" {
  description = "Machine type for Spark VM"
  type        = string
  default     = "n2-standard-8"  # 8 vCPUs, 32GB RAM
}

