# Media Attachment Storage Implementation

This document explains how the media attachment feature is implemented using **MinIO** as the object storage solution.

## 1. Create the Media Entity

First, we create an entity class that represents a media object in our application.

The entity contains the information we need to identify and manage an attachment, such as its ID, type, state, and other metadata.

The actual file content is stored in MinIO rather than directly in the database.

---

## 2. Create the Storage Interface

Next, we create an interface that defines the operations our media storage feature needs.

For example, the interface can contain methods such as:

* `add()` or `upload()` an attachment
* `get()` an attachment
* `delete()` an attachment

We use an interface so that our application is not tightly coupled to a specific storage implementation.

For example, if we are currently using MinIO but later decide to switch to another storage solution, we can create another implementation of the same interface without changing the business logic.

The structure is essentially:

```text
MediaStorage
     │
     ├── MinIO implementation
     │
     └── Another storage implementation
```

This makes the storage layer easier to replace and maintain.

---

## 3. Choose MinIO

For this feature, we use **MinIO**, an open-source object storage system.

MinIO can be used to store different types of files, including:

* Images
* Documents
* PDFs
* Videos
* Other attachments

Instead of storing the actual file bytes inside our application database, we store the files in MinIO and keep the necessary metadata in our database.

---

## 4. Configure MinIO

We configure the MinIO connection in our application configuration.

For example:

```yaml
minio:
  endpoint: http://minio-service:9000/
  access-key: minioadmin
  secret-key: minioadmin
  bucket: chat-attachments
```

The configuration defines:

* **endpoint**: The address where the MinIO server is running.
* **access-key**: The credentials used to authenticate with MinIO.
* **secret-key**: The secret used together with the access key.
* **bucket**: The MinIO bucket where our attachments will be stored.

> In a production environment, credentials should not be hardcoded in the configuration file. They should be provided through environment variables or another secure secret-management solution.

---

## 5. Add MinIO to Docker Compose

We create a `docker-compose.yml` file to run MinIO as part of our application infrastructure.

The Docker Compose configuration pulls the MinIO Docker image and starts the MinIO service with the required configuration.

This allows the application and MinIO to run together in the same Docker environment.

For example, the application can connect to MinIO using:

```text
http://minio-service:9000
```

The hostname `minio-service` is the Docker Compose service name.

---

## 6. Add the MinIO Dependency

Next, we add the MinIO Java SDK to our `pom.xml`.

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.6.0</version>
</dependency>
```

This dependency provides the Java client that allows our Spring Boot application to communicate with MinIO.

---

## 7. Create the MinIO Configuration Class

We create a configuration class responsible for initializing the MinIO client.

The MinIO client uses the configured endpoint and credentials to establish a connection with the MinIO server.

The configuration class is also responsible for ensuring that the required bucket exists.

For our application, the bucket is:

```text
chat-attachments
```

If the bucket does not exist, the application can create it during startup.

This gives us a consistent storage location for all chat attachments.

---

## 8. Implement the Storage Interface

After defining the storage interface, we create a class that implements it using MinIO.

This class contains the actual storage implementation.

For example:

```text
MediaStorage
    ↑
    │ implements
    │
MinioMediaStorage
```

The implementation is responsible for operations such as:

* Uploading files to MinIO
* Retrieving files from MinIO
* Deleting files from MinIO

The rest of the application does not need to know how MinIO works internally. It only communicates with the `MediaStorage` interface.

---

## 9. Create the Media Service

Next, we create the `MediaService`.

The service contains the **business logic** related to media attachments.

For example, the service can be responsible for:

* Validating uploaded files
* Checking file size and type
* Creating the media entity
* Uploading the file through `MediaStorage`
* Updating the media state
* Handling errors
* Deleting attachments
* Retrieving attachment information

The service sits between the controller and the storage layer:

```text
Controller
     │
     ▼
MediaService
     │
     ▼
MediaStorage
     │
     ▼
MinIO
```

This separation keeps the business logic independent from the storage implementation.

---

## 10. Create the Controllers

Finally, we create the controllers that expose our media API endpoints.

The controllers allow the client application to interact with the media functionality through HTTP requests.

For example, we can expose endpoints for:

```text
POST   /api/media
GET    /api/media/{id}
DELETE /api/media/{id}
```

The controller receives the request and passes it to the `MediaService`.

The controller should primarily handle HTTP-related concerns, while the business logic remains inside the service.

---

# Overall Architecture

The complete flow looks like this:

```text
Client
  │
  │ HTTP request
  ▼
MediaController
  │
  ▼
MediaService
  │
  ▼
MediaStorage interface
  │
  ▼
MinioMediaStorage
  │
  │ MinIO SDK
  ▼
MinIO
  │
  ▼
chat-attachments bucket
```

The database stores the **media metadata**, while MinIO stores the **actual file**.

This architecture gives us a clean separation between:

* **Controller** → Handles API requests
* **Service** → Handles business logic
* **Storage interface** → Defines storage operations
* **MinIO implementation** → Handles communication with MinIO
* **MinIO** → Stores the actual attachments
* **Database** → Stores media metadata
