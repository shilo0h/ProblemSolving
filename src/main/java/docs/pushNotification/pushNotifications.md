# Firebase Push Notifications with Spring Boot

## Overview

To send push notifications to users, we can use **Firebase Cloud Messaging (FCM)** through the **Firebase Admin SDK**.

---

# 1. Configure Firebase

Create a Firebase project and download the **Service Account** JSON file.

Place the JSON file inside your Spring Boot project and configure Firebase so the application can authenticate with your Firebase account.

Example:

```java
FirebaseOptions options = FirebaseOptions.builder()
    .setCredentials(GoogleCredentials.fromStream(new FileInputStream("firebase-service-account.json")))
    .build();

FirebaseApp.initializeApp(options);
```

---

# 2. Save Device Tokens

When the mobile application starts, it requests permission from the user to receive push notifications.

If the user accepts, Firebase generates a **device token**.

The client sends this token to the backend.

Create a database table to store the token.

Example:

| Column | Description |
|---------|-------------|
| id | Primary key |
| user_id | User who owns the device |
| token | Firebase device token |

---

# 3. Create an Endpoint to Register Tokens

The backend should expose an endpoint that accepts the device token.

Example flow:

1. User logs in.
2. Client obtains Firebase token.
3. Client sends token to backend.
4. Backend saves or updates the token.

---

# 4. One Token Per User

Store **one active token per user**.

If multiple users shared the same stored token, notifications intended for one account could be delivered to another user on the same device.

Whenever a new token is received, update the existing one.

---

# 5. Remove Token on Logout

Create another endpoint to remove the token.

This prevents users from continuing to receive notifications after logging out.

Flow:

1. User logs out.
2. Client calls the delete endpoint.
3. Backend removes the stored token.

---

# 6. Sending Notifications

Once the token is stored, sending a notification is straightforward.

```java
Notification notification = Notification.builder()
    .setTitle(title)
    .setBody(body)
    .build();

Message message = Message.builder()
    .setToken(userDeviceToken.getToken())
    .setNotification(notification)
    .build();

FirebaseMessaging.getInstance().send(message);
```

---

# 7. Improving the Architecture with Kafka

A cleaner architecture is to separate notification handling from the rest of the application.

Instead of directly sending notifications from your business logic:

1. Publish a notification event to a Kafka topic.
2. A dedicated Kafka listener consumes the event.
3. The listener sends the push notification through Firebase.

Example flow:

```
Message Created
       │
       ▼
Publish Kafka Event
       │
       ▼
Notification Listener
       │
       ▼
Firebase Cloud Messaging
       │
       ▼
User Device
```

### Benefits

- Keeps business logic clean.
- Notification failures do not affect the main application flow.
- Easy to add retries.
- Easy to scale notification processing independently.
- Other services can also publish notification events.

---

# Maven Dependency

```xml
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.4.3</version>
</dependency>
```

---

# Complete Flow

```
User Opens App
       │
       ▼
Requests Notification Permission
       │
       ▼
Firebase Generates Device Token
       │
       ▼
Client Sends Token to Backend
       │
       ▼
Backend Saves Token
       │
       ▼
Message/Event Occurs
       │
       ▼
Kafka Event (Optional)
       │
       ▼
Notification Service
       │
       ▼
Firebase Cloud Messaging
       │
       ▼
Push Notification Sent to User
```

---

# Summary

- Configure Firebase Admin SDK using a Service Account JSON file.
- Store one device token per user.
- Create an endpoint to register/update the token.
- Create an endpoint to remove the token on logout.
- Use the stored token to send push notifications with Firebase.
- For better scalability and separation of concerns, publish notification events to Kafka and let a dedicated notification service handle sending them through Firebase Cloud Messaging.