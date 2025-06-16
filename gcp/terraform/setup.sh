#!/bin/bash

# User ID on GCP
export GCP_userID="a_grodowski_student_uw_edu_pl"

# Private key to use to connect to GCP
export GCP_privateKeyFile="$HOME/.ssh/id_rsa"

# Name of your GCP project
export TF_VAR_project="bigdata-ag438477"

# Name of your selected GCP region
export TF_VAR_region="europe-central2"

# Name of your selected GCP zone
export TF_VAR_zone="europe-central2-c"


# Number of VMs created
export TF_VAR_machineCount=4

# VM type
export TF_VAR_machineType="e2-medium"

# Prefix for you VM instances
export TF_VAR_instanceName="tf-instance"

# Prefix of your GCP deployment key
export TF_VAR_deployKeyName="deployment-key.json"

export SERVICE_NAME="deployment-key"