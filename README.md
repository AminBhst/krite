# Krite: Distributed Task Executor

Krite is a distributed, scalable task-processing system consisting of a Coordinator and multiple Executors.
Clients send oauth authenticated requests to the api module, upload task data, and the coordinator assigns the work
across available executors based on the executor CPU specs, load, and available memory.

It uses gRPC for internal and REST API for client level communications, MinIO object storage, PostgreSQL and MongoDB.

This system is ideal for CPU-heavy, IO-heavy, or parallelizable workloads, and can easily be extended to support more
task types.

## 🧱 Architecture Overview

Krite consists of three different modules,

### API

- Provides HTTP REST API for clients
- Provides registration and login services via oauth
- Serves as the entry point for creating tasks

### Coordinator

- Acts as the load-balancer for executors
- Dispatches tasks to executors via gRPC
- Collects task results and statuses

### Executors

- Connect to coordinator via gRPC
- Poll for tasks
- Execute assigned jobs (thumbnail generation, file compression etc.)
- Push status updates back to coordinator

## 🚀 Getting Started

### 1. Run the API

```bash
./gradlew :api:bootRun
```

### 2. Configure the Coordinator

edit the `application.properties` and set proper address and ports for gRPC, PostgreSQL and other configurations.

### 3. Run the Coordinator

```bash
./gradlew :coordinator:bootRun
```

### 4. Configure executors

edit the `application.properties` and set proper address and ports for gRPC to enable communication with the
coordinator.

Make sure to set a unique executor.id for each executor.

### 5. Run the executors

```bash
./gradlew :executor:bootRun
```

# 🔐 Authentication

Krite uses username/password authentication.
Clients must first log in to receive a JWT token and then send it in the Authorization header.

## 1. Register

### POST /auth/register

Request Body:

```json
{
  "username": "newuser",
  "password": "abcd1234"
}
```

## 2. Login

### POST /auth/login

Request Body:

```json
{
  "username": "newuser",
  "password": "abcd1234"
}

```

Response:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6..."
}
```

# 📤 Creating Tasks

To create a task, send a multipart/form-data POST request to:

### POST /task/{taskType}

Example request (thumbnail generation):

```bash
POST /task/GENERATE_THUMBNAIL
Authorization: Bearer <token>
Content-Type: multipart/form-data
```
