# ASTA_Adaptive-STimuli-Algorithm

## 📖 Overview

This project implements a simulation of **adaptive collective behavior in distributed systems**, where simple agents respond to **dynamic local stimuli** using a token-based communication mechanism.

The system models how agents transition between _Unaware_ and _Aware_ states based on local interactions, mimicking self-organizing behaviors observed in natural and distributed systems. The implementation is inspired by research on adaptive stimuli algorithms and programmable matter systems.

---

## 🎯 Applications / Use Cases

This type of adaptive collective system has applications in:

- **Swarm Robotics** – coordination of multiple robots using only local communication
- **Distributed Systems** – decentralized decision-making without global control
- **Search & Rescue Systems** – agents locating and aggregating around targets
- **Sensor Networks** – detecting and propagating environmental signals
- **Programmable Matter & Self-Organizing Systems**

---

## 🧠 Key Concepts Implemented

- Local stimulus detection
- Token-based communication (alert & all-clear propagation)
- State transitions: _Unaware ↔ Aware_
- Distributed coordination without central control
- Grid and Hexagonal lattice-based agent environments

---

## 📁 Project Structure

```
adaptive-stimuli-sim/
│
├── docs/              # Report and presentation materials
│   ├── report.pdf
│   └── presentation.pptx
│
├── src/               # Source code
│   ├── grid/          # Grid lattice simulation
│   └── hex/           # Hexagonal lattice simulation
│
├── demo/              # Screenshots / outputs
│
├── config/            # Configuration files (if any)
│
├── README.md
└── .gitignore
```

---

## ⚙️ Requirements

- **Java JDK 8 or higher**
- Any IDE (recommended: VS Code / IntelliJ) OR terminal

---

## ▶️ How to Run

### 🔹 Step 1: Compile the Project

Open terminal in the root directory and run:

```
javac src/*/*.java
```

---

### 🔹 Step 2: Run the Simulation

Run the main class (adjust if needed):

```
java src.grid.Main
```

or

```
java src.hex.Main
```

---

## 🧪 Simulation Modes

### 🟩 Grid Lattice Simulation

- The grid environment represents agents arranged in a square lattice
- Each agent can be manually controlled

**Steps to use:**

1. Run the grid simulation
2. Click on any agent to mark it as **Aware (stimulus detected)**
3. Click **Run** to start simulation
4. You can:
   - Select multiple agents as aware
   - Click on aware agents again to revert them to **Unaware** during runtime

📌 This mode allows interactive experimentation with stimulus propagation.

---

### 🔷 Hexagonal Lattice Simulation

- Agents are arranged in a hexagonal structure
- Supports dynamic reconfiguration

**Steps to use:**

1. Click **Regenerate** to reconfigure agent positions
2. Click **Connect Blues** to complete agent connectivity
3. Click on any agent to make it **Aware**
4. Run the simulation

📌 Behavior is similar to grid mode after initialization, but includes dynamic topology changes.

---

## 🎨 Visualization & Legends

- Legends are included in both simulations for clarity
- Different colors represent:
  - Unaware agents
  - Aware agents
  - Tokens / states

---

## 📊 Output

The simulation visually demonstrates:

- Spread of awareness through the network
- Adaptive response to changing stimuli
- Transition between global system states

---

## 📄 Reference

This implementation is inspired by research on adaptive collective systems and dynamic stimuli response in distributed networks.

---

## 👨‍💻 Author

Srijan Saha

---

## 🚀 Future Improvements

- Automated stimulus generation
- Performance analysis & metrics
- Extended agent behaviors (foraging, clustering)
- Web-based visualization

---
