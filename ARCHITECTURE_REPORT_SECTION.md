# Section 8: System Architecture (Report Text)

Copy this section into your Word/PDF report. Insert the architecture diagram screenshot from `architecture-diagram.html`.

---

## 8. SYSTEM ARCHITECTURE

The QueueLy system follows a client-server architecture built around Firebase as the central backend platform. The architecture is designed to support real-time synchronization, allowing multiple students and advisors to interact with the same queue simultaneously without delays or data inconsistencies.

### 8.1 Architectural Overview

QueueLy consists of four major layers:

**Presentation Layer**
This layer contains the user-facing applications. The Mobile Application (Android, Java) serves as the primary interface for USIU students. Through this app, students log in, join queues, monitor their position, and receive notifications. The Advisor Dashboard provides Course Advisors with tools to view active queues, call the next student, mark consultations as completed, and review daily statistics.

**Application / Backend Layer**
Firebase serves as the cloud-based backend platform. It provides three core services: Firebase Realtime Database for live queue synchronization, Firebase Authentication for Student ID verification, and Firebase Cloud Messaging (FCM) for push notifications when a student's turn is approaching.

**Data Layer**
All persistent and real-time data is stored in Firebase Realtime Database. This includes active queue entries (queue numbers, positions, request types, and timestamps), student records (Student IDs and school selections), and analytics data (daily service counts, average wait times, and peak hours).

**External Actors**
Two primary users interact with the system: USIU students who join and monitor virtual queues, and Course Advisors who manage and serve students through the dashboard.

### 8.2 Component Description

| Component | Role | Technology |
|-----------|------|------------|
| Mobile Application | Student-facing queue interface | Android (Java), XML layouts |
| Advisor Dashboard | Queue management for advisors | Web application |
| Authentication | Verifies Student ID before access | Firebase Authentication |
| Realtime Database | Stores and syncs all queue data | Firebase Realtime Database |
| Push Notifications | Alerts students when turn is near | Firebase Cloud Messaging |

### 8.3 Data Flow

The following steps describe how data moves through the system:

1. **Student Login:** The student opens the QueueLy mobile app and enters their Student ID. The Authentication component verifies the ID before granting access.

2. **Join Queue:** The student selects their school and the purpose of their visit (Add Course, Drop Course, or Academic Consultation). The app writes a new queue entry to Firebase Realtime Database with a timestamp.

3. **Real-Time Sync:** Firebase immediately broadcasts the updated queue to all connected clients — both other student devices and the Advisor Dashboard — ensuring everyone sees the current queue state.

4. **Position Updates:** As the advisor serves students and marks entries as completed, queue positions are recalculated automatically. All students receive updated position information in real time.

5. **Notification:** When a student is next or near the front of the queue, Firebase Cloud Messaging sends a push notification to their device.

6. **Service Completion:** The advisor calls the student, conducts the consultation, and marks the request as completed in the dashboard. The entry is moved to queue history and contributes to analytics data.

### 8.4 Why This Architecture?

Firebase Realtime Database was chosen because queue management requires instant updates across many devices. When one student joins or one advisor serves a student, every connected user must see the change immediately. A traditional local database would not support this multi-user, real-time requirement.

The separation of the Mobile Application and Advisor Dashboard allows each user group to have an interface tailored to their needs, while Firebase ensures both remain synchronized through a single source of truth.

### 8.5 Architecture Diagram

[INSERT SCREENSHOT OF architecture-diagram.html HERE]

*Figure 1: QueueLy System Architecture Diagram showing the presentation layer (Mobile App and Advisor Dashboard), Firebase backend layer, data layer, and external actors.*

---

## How to get the diagram screenshot

1. Double-click `architecture-diagram.html` in `C:\Users\Admin\queuely`
2. It opens in your web browser (Chrome/Edge)
3. Press **Win + Shift + S** to screenshot the diagram
4. Paste into your Word report under Section 8.5
