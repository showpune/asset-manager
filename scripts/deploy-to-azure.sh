#!/bin/bash
set -e

# Default values
RESOURCE_GROUP="assets-manager-rg"
LOCATION="eastus"
PREFIX="assetsapp"
CONTAINER_NAME="images"
POSTGRES_SERVER_NAME="${PREFIX}-dbserver"
POSTGRES_DB_NAME="assets_manager"
POSTGRES_ADMIN="postgresadmin"
SB_NAMESPACE="${PREFIX}-servicebus"
SB_QUEUE_NAME="image-processing"
STORAGE_ACCOUNT_NAME="${PREFIX}storage"
WEB_APP_NAME="${PREFIX}-web"
WORKER_APP_NAME="${PREFIX}-worker"
ENVIRONMENT_NAME="${PREFIX}-env"
ACR_NAME="${PREFIX}registry"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --resource-group|-g)
      RESOURCE_GROUP="$2"
      shift 2
      ;;
    --location|-l)
      LOCATION="$2"
      shift 2
      ;;
    --prefix|-p)
      PREFIX="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

echo "==========================================="
echo "Deploying Assets Manager to Azure"
echo "==========================================="
echo "Resource Group: $RESOURCE_GROUP"
echo "Location: $LOCATION"
echo "Resources prefix: $PREFIX"
echo "==========================================="

# Check prerequisites
echo "Checking prerequisites..."
if ! command -v az &> /dev/null; then
    echo "Azure CLI not found. Please install it: https://docs.microsoft.com/cli/azure/install-azure-cli"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "Maven not found. Please install it: https://maven.apache.org/install.html"
    exit 1
fi

if ! command -v docker &> /dev/null; then
    echo "Docker not found. Please install it: https://docs.docker.com/get-docker/"
    exit 1
fi

# Login to Azure
echo "Logging in to Azure..."
az account show &> /dev/null || az login

# Create resource group
echo "Creating resource group..."
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create Azure Container Registry
echo "Creating Azure Container Registry..."
az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Basic
az acr login --name $ACR_NAME

# Create Azure PostgreSQL server
echo "Creating Azure PostgreSQL server..."
az postgres flexible-server create \
  --resource-group $RESOURCE_GROUP \
  --name $POSTGRES_SERVER_NAME \
  --location $LOCATION \
  --admin-user $POSTGRES_ADMIN \
  --admin-password "P@ssw0rd$(date +%s)" \
  --sku-name Standard_B1ms \
  --tier Burstable \
  --storage-size 32 \
  --version 15 \
  --yes

echo "Creating PostgreSQL database..."
az postgres flexible-server db create \
  --resource-group $RESOURCE_GROUP \
  --server-name $POSTGRES_SERVER_NAME \
  --database-name $POSTGRES_DB_NAME

# Allow Azure services to access PostgreSQL server
echo "Configuring PostgreSQL firewall rules..."
az postgres flexible-server firewall-rule create \
  --resource-group $RESOURCE_GROUP \
  --name $POSTGRES_SERVER_NAME \
  --rule-name "AllowAzureServices" \
  --start-ip-address 0.0.0.0 \
  --end-ip-address 0.0.0.0

# Create Azure Service Bus namespace and queue
echo "Creating Azure Service Bus namespace..."
az servicebus namespace create \
  --resource-group $RESOURCE_GROUP \
  --name $SB_NAMESPACE \
  --location $LOCATION \
  --sku Standard

echo "Creating Service Bus queue..."
az servicebus queue create \
  --resource-group $RESOURCE_GROUP \
  --namespace-name $SB_NAMESPACE \
  --name $SB_QUEUE_NAME

# Create Azure Storage account and container
echo "Creating Azure Storage account..."
az storage account create \
  --resource-group $RESOURCE_GROUP \
  --name $STORAGE_ACCOUNT_NAME \
  --location $LOCATION \
  --sku Standard_LRS \
  --kind StorageV2 \
  --enable-hierarchical-namespace false

echo "Creating Blob container..."
STORAGE_ACCOUNT_KEY=$(az storage account keys list --resource-group $RESOURCE_GROUP --account-name $STORAGE_ACCOUNT_NAME --query '[0].value' -o tsv)
az storage container create \
  --name $CONTAINER_NAME \
  --account-name $STORAGE_ACCOUNT_NAME \
  --account-key $STORAGE_ACCOUNT_KEY \
  --public-access container

# Create Container Apps environment
echo "Creating Container Apps environment..."
az containerapp env create \
  --resource-group $RESOURCE_GROUP \
  --name $ENVIRONMENT_NAME \
  --location $LOCATION

# Create managed identities for web and worker apps
echo "Creating managed identities..."
az identity create \
  --resource-group $RESOURCE_GROUP \
  --name "${WEB_APP_NAME}-identity"

az identity create \
  --resource-group $RESOURCE_GROUP \
  --name "${WORKER_APP_NAME}-identity"

# Get identity details
WEB_IDENTITY_ID=$(az identity show --resource-group $RESOURCE_GROUP --name "${WEB_APP_NAME}-identity" --query id -o tsv)
WEB_IDENTITY_CLIENT_ID=$(az identity show --resource-group $RESOURCE_GROUP --name "${WEB_APP_NAME}-identity" --query clientId -o tsv)
WEB_IDENTITY_PRINCIPAL_ID=$(az identity show --resource-group $RESOURCE_GROUP --name "${WEB_APP_NAME}-identity" --query principalId -o tsv)

WORKER_IDENTITY_ID=$(az identity show --resource-group $RESOURCE_GROUP --name "${WORKER_APP_NAME}-identity" --query id -o tsv)
WORKER_IDENTITY_CLIENT_ID=$(az identity show --resource-group $RESOURCE_GROUP --name "${WORKER_APP_NAME}-identity" --query clientId -o tsv)
WORKER_IDENTITY_PRINCIPAL_ID=$(az identity show --resource-group $RESOURCE_GROUP --name "${WORKER_APP_NAME}-identity" --query principalId -o tsv)

# Assign roles to the managed identities
echo "Assigning roles to managed identities..."
# Storage Blob Data Contributor role for both web and worker
az role assignment create \
  --assignee-object-id $WEB_IDENTITY_PRINCIPAL_ID \
  --assignee-principal-type ServicePrincipal \
  --role "Storage Blob Data Contributor" \
  --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.Storage/storageAccounts/$STORAGE_ACCOUNT_NAME"

az role assignment create \
  --assignee-object-id $WORKER_IDENTITY_PRINCIPAL_ID \
  --assignee-principal-type ServicePrincipal \
  --role "Storage Blob Data Contributor" \
  --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.Storage/storageAccounts/$STORAGE_ACCOUNT_NAME"

# Service Bus Data Sender role for web
az role assignment create \
  --assignee-object-id $WEB_IDENTITY_PRINCIPAL_ID \
  --assignee-principal-type ServicePrincipal \
  --role "Azure Service Bus Data Sender" \
  --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ServiceBus/namespaces/$SB_NAMESPACE"

# Service Bus Data Receiver role for worker
az role assignment create \
  --assignee-object-id $WORKER_IDENTITY_PRINCIPAL_ID \
  --assignee-principal-type ServicePrincipal \
  --role "Azure Service Bus Data Receiver" \
  --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ServiceBus/namespaces/$SB_NAMESPACE"

# Service Bus Data Owner role for worker (needed for context.abandon() and context.complete() in ServiceBusListener)
az role assignment create \
  --assignee-object-id $WORKER_IDENTITY_PRINCIPAL_ID \
  --assignee-principal-type ServicePrincipal \
  --role "Azure Service Bus Data Owner" \
  --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ServiceBus/namespaces/$SB_NAMESPACE"

# Update PostgreSQL with AAD admin
echo "Setting up PostgreSQL AAD authentication..."
az postgres flexible-server update \
  --resource-group $RESOURCE_GROUP \
  --name $POSTGRES_SERVER_NAME \
  --set "authConfig.activeDirectoryAuth=enabled" \
  --set "authConfig.tenantId=$(az account show --query tenantId -o tsv)"

# Assign PostgreSQL AAD admin role
az postgres flexible-server ad-admin create \
  --resource-group $RESOURCE_GROUP \
  --server-name $POSTGRES_SERVER_NAME \
  --display-name "Web App Identity" \
  --object-id $WEB_IDENTITY_PRINCIPAL_ID

az postgres flexible-server ad-admin create \
  --resource-group $RESOURCE_GROUP \
  --server-name $POSTGRES_SERVER_NAME \
  --display-name "Worker App Identity" \
  --object-id $WORKER_IDENTITY_PRINCIPAL_ID

# Update application.properties for web module
echo "Updating web module application.properties..."
WEB_PROPERTIES_FILE="web/src/main/resources/application.properties"
cat > $WEB_PROPERTIES_FILE << EOF
spring.application.name=assets-manager

# Azure Blob Storage Configuration
azure.storage.account-name=$STORAGE_ACCOUNT_NAME
azure.storage.blob.container-name=$CONTAINER_NAME

# Max file size for uploads
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Service Bus Configuration
spring.cloud.azure.credential.managed-identity-enabled=true
spring.cloud.azure.credential.client-id=${WEB_IDENTITY_CLIENT_ID}
spring.cloud.azure.servicebus.namespace=$SB_NAMESPACE
spring.cloud.azure.servicebus.entity-type=queue

# Database Configuration
spring.datasource.url=jdbc:postgresql://${POSTGRES_SERVER_NAME}.postgres.database.azure.com:5432/${POSTGRES_DB_NAME}?sslmode=require
spring.datasource.username=${WEB_APP_NAME}
spring.datasource.azure.passwordless-enabled=true
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=true
EOF

# Update application.properties for worker module
echo "Updating worker module application.properties..."
WORKER_PROPERTIES_FILE="worker/src/main/resources/application.properties"
cat > $WORKER_PROPERTIES_FILE << EOF
# Azure Blob Storage Configuration
azure.storage.account-name=$STORAGE_ACCOUNT_NAME
azure.storage.blob.container-name=$CONTAINER_NAME

# Server port (different from web module)
server.port=8081

# Application name
spring.application.name=assets-manager-worker

# Service Bus Configuration
spring.cloud.azure.credential.managed-identity-enabled=true
spring.cloud.azure.credential.client-id=${WORKER_IDENTITY_CLIENT_ID}
spring.cloud.azure.servicebus.namespace=$SB_NAMESPACE
spring.cloud.azure.servicebus.entity-type=queue

# Database Configuration
spring.datasource.url=jdbc:postgresql://${POSTGRES_SERVER_NAME}.postgres.database.azure.com:5432/${POSTGRES_DB_NAME}?sslmode=require
spring.datasource.username=${WORKER_APP_NAME}
spring.datasource.azure.passwordless-enabled=true
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
EOF

# Create Dockerfiles for both modules
echo "Creating Dockerfile for web module..."
cat > web/Dockerfile << EOF
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

echo "Creating Dockerfile for worker module..."
cat > worker/Dockerfile << EOF
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# Package and build Docker images
echo "Building web module..."
cd web
mvn clean package -DskipTests
cd ..

echo "Building worker module..."
cd worker
mvn clean package -DskipTests
cd ..

# Build and push Docker images to ACR
echo "Building and pushing Docker images to ACR..."
ACR_LOGIN_SERVER=$(az acr show --name $ACR_NAME --resource-group $RESOURCE_GROUP --query loginServer -o tsv)

# Web module
cd web
docker build -t ${ACR_LOGIN_SERVER}/${WEB_APP_NAME}:latest .
docker push ${ACR_LOGIN_SERVER}/${WEB_APP_NAME}:latest
cd ..

# Worker module
cd worker
docker build -t ${ACR_LOGIN_SERVER}/${WORKER_APP_NAME}:latest .
docker push ${ACR_LOGIN_SERVER}/${WORKER_APP_NAME}:latest
cd ..

# Create Container Apps with managed identities
echo "Creating Container App for web module..."
az containerapp create \
  --resource-group $RESOURCE_GROUP \
  --name $WEB_APP_NAME \
  --environment $ENVIRONMENT_NAME \
  --image ${ACR_LOGIN_SERVER}/${WEB_APP_NAME}:latest \
  --registry-server $ACR_LOGIN_SERVER \
  --target-port 8080 \
  --ingress external \
  --user-assigned $WEB_IDENTITY_ID \
  --registry-identity system \
  --min-replicas 1 \
  --max-replicas 3

echo "Creating Container App for worker module..."
az containerapp create \
  --resource-group $RESOURCE_GROUP \
  --name $WORKER_APP_NAME \
  --environment $ENVIRONMENT_NAME \
  --image ${ACR_LOGIN_SERVER}/${WORKER_APP_NAME}:latest \
  --registry-server $ACR_LOGIN_SERVER \
  --target-port 8081 \
  --ingress internal \
  --user-assigned $WORKER_IDENTITY_ID \
  --registry-identity system \
  --min-replicas 1 \
  --max-replicas 3

# Get the web app URL
WEB_APP_URL=$(az containerapp show --resource-group $RESOURCE_GROUP --name $WEB_APP_NAME --query properties.configuration.ingress.fqdn -o tsv)

echo "==========================================="
echo "Deployment complete!"
echo "==========================================="
echo "Resource Group: $RESOURCE_GROUP"
echo "Web Application URL: https://$WEB_APP_URL"
echo "Storage Account: $STORAGE_ACCOUNT_NAME"
echo "Service Bus Namespace: $SB_NAMESPACE"
echo "PostgreSQL Server: $POSTGRES_SERVER_NAME"
echo "==========================================="