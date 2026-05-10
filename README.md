# YPP-Gizmo

A Java desktop application for extracting and analysing crew performance data from the Yohoho! Puzzle Pirates (YPP) game client.

Previously maintained as a private repository; published here as a portfolio reference.

## Overview

YPP-Gizmo is a sophisticated data extraction and analysis tool built with **advanced Java runtime introspection, agent-based instrumentation, and complex GUI automation**. It demonstrates practical mastery of:

- Advanced Java reflection and instrumentation techniques
- Real-time third-party application integration via component introspection
- Multi-threaded event-driven architecture in desktop applications
- Complex recursive algorithms for hierarchical data structure traversal

This project exemplifies production-grade software design applied to a non-trivial technical challenge: seamlessly extracting structured data from a live, dynamically-updating GUI without API access or source code.

---

## Gallery / In Action

What's on Screen:
<img width="800" height="600" alt="Puzzle Pirates Ozpin 3" src="https://github.com/user-attachments/assets/1d6a791c-6bd5-42a5-b55f-1b6df9acb451" />

Read from Client and stored in Application:

<img width="295" height="687" alt="gizmo-1" src="https://github.com/user-attachments/assets/f0ca7ee8-2a3f-4bb6-93f5-009cd9a752ac" />
<img width="300" height="690" alt="gizmo-1 1" src="https://github.com/user-attachments/assets/11356410-af8d-4971-bc10-6787546c6ba1" />

Example of a different activity:
<img width="800" height="600" alt="Puzzle Pirates Ozpin 5" src="https://github.com/user-attachments/assets/7b69a4ff-3127-4294-bdc6-7e5a700dd9c7" />

Completed Run Data, including optional buttons used:
<img width="391" height="478" alt="gizmo rundata" src="https://github.com/user-attachments/assets/3c8b5513-331d-4cff-98ca-7c240b79dde1" />

---

## Key Capabilities

- **Real-Time Data Extraction** — Processes live performance metrics directly from the game client UI via reflection-based component introspection
- **Multi-Station Analysis** — Handles data from multiple job types, each with distinct scoring algorithms and performance characteristics
- **Persistent Storage** — Manages historical data and user preferences across sessions with type-safe serialization
- **Cross-Platform Compatibility** — Supports multiple game client implementations through abstraction layers
- **Interactive GUI** — Full-featured Swing interface for data visualization, filtering, advanced search, and one-click export
- **Event-Driven Architecture** — Responsive, non-blocking UI with background task processing and thread-safe data updates

---

## Data Integration

This tool is specifically designed to empower the following analysis workflows:

- [Cursed Island YPP Forage](https://docs.google.com/spreadsheets/d/1e4hu0NvzBdEVhf2MgD0-7MYhLEIvAEKG8tBv3c_Cd5o/edit?gid=14#gid=14)
- [Puzzle Pirates Vampire Lair](https://docs.google.com/spreadsheets/d/1_5SVAsvFmNTCMXJXLasZSPK3RgJ97dQmVSiYy1_uKKE/edit?gid=721918292#gid=721918292)

**Workflow:** YPP-Gizmo reads performance metrics from the game client in real-time. At the end of each run, users export the data with a single-click copy-to-clipboard function, then paste it into the relevant analysis sheet.

---

## Technology Stack

| Component | Technology | Notes |
|-----------|-----------|-------|
| **Language** | Java | Full codebase with modern Java patterns |
| **UI Framework** | Java Swing | Custom components with advanced event handling |
| **Build System** | Maven | Standard project structure with dependency management |
| **Architecture** | Event-driven, Component-based | Reflection and agent-based instrumentation |

---

## Technical Deep Dive

### Advanced Implementation Techniques

#### ⭐ **Agent-Based Instrumentation**
Dynamic runtime bytecode manipulation using Java agents. This enables non-invasive monitoring and inspection of third-party libraries without modifying source code. The agent attaches to the JVM at startup and provides hooks for advanced introspection patterns.

#### **Java Reflection & Runtime Introspection**
Comprehensive use of `java.lang.reflect` API to dynamically discover and traverse component hierarchies at runtime. Handles complex inheritance chains, generic types, and field access across package boundaries.

#### **Recursive Hierarchical Traversal**
Sophisticated algorithms for depth-first and breadth-first traversal of Swing component trees, efficiently extracting nested UI state while maintaining type safety and handling circular references.

#### **Multi-threaded Event Dispatching**
Thread-safe event handling in Swing with careful synchronization, leveraging `SwingUtilities.invokeLater()` and custom thread pools to prevent UI freezing during intensive data extraction operations.

#### **Component State Analysis**
Real-time analysis of live UI components, including visibility detection, bounds calculation, state introspection, and dynamic property extraction from obfuscated third-party applications.

### Employer-Relevant Skills

| Skill | Evidence | Relevance |
|-------|----------|-----------|
| **Advanced Java** | Reflection, agents, introspection, bytecode manipulation | Core infrastructure development, JVM internals |
| **Concurrency** | Multi-threaded event handling, thread-safe collections, synchronization | Backend systems, high-throughput services |
| **GUI Development** | Complex Swing application, custom components, event-driven architecture | Desktop tools, rich client applications |
| **Software Architecture** | Component-based design, separation of concerns, abstraction layers | System design, maintainability |
| **Problem-Solving** | Non-API integration, reverse engineering UI state, constraint handling | Legacy system integration, DevOps tooling |

### Real-World Applications

This architecture is directly applicable to:
- **Financial systems monitoring** — Real-time extraction of data from legacy trading systems
- **Test automation frameworks** — Intelligent GUI testing without source code access
- **Performance monitoring tools** — Dynamic instrumentation and profiling of third-party applications
- **Data migration pipelines** — Reverse-engineering structured data from unstructured UI sources
- **System integration layers** — Bridging modern systems with legacy applications

---

## Getting Started

For security reasons, I've intentionally kept setup documentation minimal. This tool was designed for a specific use case and includes integration points with the Yohoho! Puzzle Pirates game client—detailed setup instructions would create unnecessary vectors for misuse or unintended interaction with third-party software.

If you're interested in understanding the architecture, technical implementation, or how the reflection-based introspection works, I'm happy to discuss specific components or techniques. The codebase is well-structured and documented for code review purposes.

---

## License

MIT License — See LICENSE file for details

---

## Portfolio Note

This project is published as a demonstration of advanced Java development capabilities. While originally maintained privately, it serves as a reference for technical depth, architectural decision-making, and practical problem-solving with complex runtime environments.
