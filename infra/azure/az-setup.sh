#!/usr/bin/env bash
set -euo pipefail

# Azure AKS + ACR quick setup script
# Requirements: az CLI installed and logged in; subscription selected

RG_NAME=${RG_NAME:-whanos-rg}
# Default region for the resource group and as a fallback
LOCATION=${LOCATION:-westeurope}
# Allow separate locations if your subscription limits services differently
ACR_LOCATION=${ACR_LOCATION:-$LOCATION}
AKS_LOCATION=${AKS_LOCATION:-$LOCATION}

ACR_NAME=${ACR_NAME:-whanos}
AKS_NAME=${AKS_NAME:-whanos-aks}
NODE_COUNT=${NODE_COUNT:-2}
NODE_SIZE=${NODE_SIZE:-Standard_B2s}

# Fail fast by default on the first error (disable by setting FAIL_FAST=0)
FAIL_FAST=${FAIL_FAST:-1}

# Ensure required resource providers are registered (idempotent)
ensure_provider_registered() {
  local ns="$1"
  local state
  state=$(az provider show --namespace "$ns" --query registrationState -o tsv 2>/dev/null || echo "NotRegistered")
  if [ "$state" != "Registered" ]; then
    echo "  -> registering provider: $ns (current state: $state)"
    az provider register --namespace "$ns" 1>/dev/null || true
    # Wait up to ~5 minutes, polling every 5s
    local tries=0
    while true; do
      state=$(az provider show --namespace "$ns" --query registrationState -o tsv 2>/dev/null || echo "NotRegistered")
      if [ "$state" = "Registered" ]; then
        echo "  -> $ns is Registered"
        break
      fi
      tries=$((tries+1))
      if [ $tries -ge 60 ]; then
        echo "  !! timeout waiting for $ns to register (last state: $state)" >&2
        if [ "$FAIL_FAST" = "1" ]; then
          exit 1
        else
          break
        fi
      fi
      sleep 5
    done
  else
    echo "  -> $ns already Registered"
  fi
}

echo "[1/7] Ensuring Azure resource providers are registered"
ensure_provider_registered "Microsoft.ContainerRegistry"
ensure_provider_registered "Microsoft.ContainerService"
ensure_provider_registered "Microsoft.Network"

echo "[2/7] Creating resource group: $RG_NAME"
# If the RG already exists, reuse its location to avoid conflicts
if az group exists -n "$RG_NAME" >/dev/null; then
  EXISTING_LOC=$(az group show -n "$RG_NAME" --query location -o tsv)
  echo "Resource group already exists in $EXISTING_LOC; reusing it."
  LOCATION="$EXISTING_LOC"
else
  az group create -n "$RG_NAME" -l "$LOCATION" 1>/dev/null
fi

# Ensure service-specific locations default to the (possibly updated) RG location
ACR_LOCATION=${ACR_LOCATION:-$LOCATION}
AKS_LOCATION=${AKS_LOCATION:-$LOCATION}

echo "[3/7] Creating Azure Container Registry: $ACR_NAME in $ACR_LOCATION"
create_acr() {
  local rg="$1" name="$2" location="$3"
  az acr create -g "$rg" -n "$name" -l "$location" --sku Standard --admin-enabled false 1>/dev/null
}

set +e
EXISTING_ACR_LOC=$(az acr show -n "$ACR_NAME" -g "$RG_NAME" --query location -o tsv 2>/dev/null || true)
if [ -n "$EXISTING_ACR_LOC" ]; then
  ACR_RC=0
  ACR_LOCATION="$EXISTING_ACR_LOC"
  echo "ACR '$ACR_NAME' already exists in $ACR_LOCATION; reusing it."
else
  create_acr "$RG_NAME" "$ACR_NAME" "$ACR_LOCATION"
  ACR_RC=$?
fi
set -e

if [ $ACR_RC -ne 0 ]; then
  if [ "$FAIL_FAST" = "1" ]; then
    echo "ACR creation failed in '$ACR_LOCATION'. Set ACR_LOCATION to an allowed region and retry." >&2
    exit 1
  else
    echo "Primary ACR location '$ACR_LOCATION' failed; trying fallbacks..."
    # Allow override via env, else use a sane default shortlist
    ACR_FALLBACKS=${ACR_FALLBACKS:-"francecentral eastus2 eastus northeurope uksouth germanywestcentral swedencentral polandcentral"}
    for loc in $ACR_FALLBACKS; do
      echo "  -> trying $loc"
      set +e
      create_acr "$RG_NAME" "$ACR_NAME" "$loc"
      ACR_RC=$?
      set -e
      if [ $ACR_RC -eq 0 ]; then
        ACR_LOCATION="$loc"
        echo "ACR created in $ACR_LOCATION"
        break
      fi
    done
    if [ $ACR_RC -ne 0 ]; then
      echo "Failed to create ACR in all attempted regions. Set ACR_LOCATION to an allowed region and retry." >&2
      exit 1
    fi
  fi
fi

echo "[4/7] Creating AKS cluster: $AKS_NAME in $AKS_LOCATION ($NODE_COUNT nodes)"
create_aks() {
  local rg="$1" name="$2" location="$3"
  az aks create -g "$rg" -n "$name" -l "$location" \
    --node-count "$NODE_COUNT" \
    --node-vm-size "$NODE_SIZE" \
    --enable-managed-identity \
    --generate-ssh-keys \
    --attach-acr "$ACR_NAME" 1>/dev/null
}

set +e
# If AKS already exists, reuse it instead of creating
az aks show -g "$RG_NAME" -n "$AKS_NAME" 1>/dev/null 2>&1
EXISTS_RC=$?
if [ $EXISTS_RC -eq 0 ]; then
  AKS_RC=0
  EXISTING_AKS_LOC=$(az aks show -g "$RG_NAME" -n "$AKS_NAME" --query location -o tsv)
  echo "AKS '$AKS_NAME' already exists in $EXISTING_AKS_LOC; reusing it."
  AKS_LOCATION="$EXISTING_AKS_LOC"
else
  create_aks "$RG_NAME" "$AKS_NAME" "$AKS_LOCATION"
  AKS_RC=$?
fi
set -e

if [ $AKS_RC -ne 0 ]; then
  if [ "$FAIL_FAST" = "1" ]; then
    echo "AKS creation failed in '$AKS_LOCATION'. If you see MissingSubscriptionRegistration, the script will register providers on the next run. Otherwise, set AKS_LOCATION to an allowed region and retry." >&2
    exit 1
  else
    echo "Primary AKS location '$AKS_LOCATION' failed; trying fallbacks..."
    AKS_FALLBACKS=${AKS_FALLBACKS:-"francecentral eastus2 eastus northeurope uksouth germanywestcentral swedencentral polandcentral"}
    for loc in $AKS_FALLBACKS; do
      echo "  -> trying $loc"
      set +e
      create_aks "$RG_NAME" "$AKS_NAME" "$loc"
      AKS_RC=$?
      set -e
      if [ $AKS_RC -eq 0 ]; then
        AKS_LOCATION="$loc"
        echo "AKS created in $AKS_LOCATION"
        break
      fi
    done
    if [ $AKS_RC -ne 0 ]; then
      echo "Failed to create AKS in all attempted regions. Set AKS_LOCATION to an allowed region and retry." >&2
      exit 1
    fi
  fi
fi

echo "[5/7] Getting kubeconfig"
az aks get-credentials -g "$RG_NAME" -n "$AKS_NAME" --overwrite-existing 1>/dev/null

echo "[6/7] Verifying cluster access"
kubectl get nodes -o wide

echo "[7/7] Optional: install NGINX Ingress (comment out if not needed)"
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/cloud/deploy.yaml

echo "Done. Use ACR: $ACR_NAME.azurecr.io"
