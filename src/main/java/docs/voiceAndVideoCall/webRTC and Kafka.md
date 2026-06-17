### WebRTC and Kafka: Architectural Overview

As an architect, I’ll break this down into how these two technologies coexist in a real-time communication system.

#### 1. The Short Answer: No, Kafka is not for the Call Itself
You are correct: **Real-time audio/video media (the call data) is never passed through Kafka.**

Kafka is designed for **durability, ordering, and high-throughput asynchronous processing**, which introduces latency (buffering, disk I/O, consumer polling) that is unacceptable for voice calls. WebRTC, conversely, uses **UDP (via RTP/SRTP)** to prioritize speed over reliability—if a packet is lost in a call, we drop it rather than waiting for a retransmission.

#### 2. The Relationship: Signaling vs. Media
WebRTC cannot establish a connection alone; it needs a **Signaling Channel** to help two peers find each other. This is where your existing backend infrastructure (and Kafka) comes into play.

*   **The Media Plane (Peer-to-Peer):** This is the actual voice data. It travels directly between users (or via a TURN server if they are behind strict NATs). This **bypasses Kafka entirely**.
*   **The Signaling Plane (The Backend):** Before the call starts, peers must exchange "handshake" data (SDP offers/answers and ICE candidates).
    *   **The Role of Kafka:** In a distributed backend, User A might be connected to WebSocket Server 1, and User B to WebSocket Server 2. When User A sends a "Call Offer," Server 1 publishes this to a **Kafka topic** (e.g., `call-signals`). Server 2 consumes that message and delivers it to User B via their local WebSocket.

#### 3. Why Use Kafka for Signaling?
Even though signaling must be fast, we use Kafka in the signaling path for:
*   **Decoupling:** The server receiving the "call request" doesn't need to know which specific server instance the recipient is connected to.
*   **Scalability:** It allows the signaling service to scale horizontally across multiple regions or instances.
*   **Observability:** You can log call attempts, failures, and durations for analytics without blocking the call flow.

#### 4. Summary for Epic 3
In your implementation:
1.  **Kafka's Job:** Act as the "postman" that carries the **Signaling DTOs** (Offer, Answer, ICE Candidates) between server instances to reach the correct user.
2.  **WebRTC's Job:** Once those DTOs are exchanged, the users' browsers/apps establish a **direct UDP tunnel** for the audio.
3.  **Latency Tip:** For signaling, ensure your Kafka producers use `acks=1` or `acks=0` and low `linger.ms` to keep the call setup time under 200ms.

In short: **Kafka handles the "Dialing" and "Ringing"; WebRTC handles the "Talking."**