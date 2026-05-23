# 🖱️ Mouse Remapper (Java Edition)

**Mouse Remapper** is a powerful, lightweight native system utility for Windows migrated from C++ to **Java 21 + JavaFX**. It lets you intercept system-wide mouse events and remap up to **five mouse buttons** to virtually any common keyboard input combination. 

Designed with an ultra-premium, dark glassmorphic interface, Mouse Remapper supports advanced input remapping patterns such as **continuous repeat**, **toggle-repeat until click**, and **custom configuration presets**, all powered by a low-level native Windows hooking system (`SetWindowsHookEx`) via **JNA**.

---

## 🎯 Key Features

- ✅ **Remap 5 Mouse Buttons** (Left, Right, Middle, Back, Forward) to up to 3 custom keyboard keys:
  - Letters: `A–Z`
  - Digits: `0–9`
  - Special keys: `Tab`, `Enter`, `Esc`, `Space`
  - Navigation: `Left`, `Right`, `Up`, `Down`
  - Modifiers: `Ctrl`, `Shift`
- 🔁 **Repeat Mode**: Simulates continuous keypresses at a 100ms interval while the mouse button is held down.
- 🔄 **Repeat Until Click Mode**: Starts repeating on the first press and stops only when the same button is clicked again.
- 💾 **Preset Configuration**: Save and load custom remapping structures dynamically to/from `config.json`.
- 🚀 **Autostart on Boot**: Registers itself in the Windows Registry (`HKEY_CURRENT_USER\...\Run`) to launch silently in the background on system boot.
- 🎨 **Glassmorphic UI**: High-end modern styling featuring deep gradients, glow effects, smooth transitions, and responsive cards.
- 🛡️ **Thread-Isolated Hooking**: Runs the low-level Win32 mouse hook thread with a dedicated message pump to guarantee zero JavaFX thread stutters.

---

## 🧰 Tech Stack

| Component         | Technology / Dependency |
|-------------------|-------------------------|
| Runtime Environment | Java 21 (LTS)           |
| Build System      | Maven 3.9+              |
| GUI Framework     | JavaFX 21               |
| Styling           | Custom CSS Stylesheet   |
| Native Win32 API  | Java Native Access (JNA) 5.14.0 |
| Config Storage    | Gson 2.10.1 (JSON)      |

---

## 🏁 Getting Started

### 📋 Prerequisites

- **Windows OS** (required for native hooking)
- **Java Development Kit (JDK) 21** or higher
- **Maven** build tool

### 🔨 Build & Run

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Felix-au/Mouse-Remapper.git
   cd Mouse-Remapper
   ```

2. **Compile the application:**
   ```bash
   mvn clean compile
   ```

3. **Launch the application:**
   ```bash
   mvn javafx:run
   ```

4. **Package into a standalone JAR:**
   ```bash
   mvn package
   ```
   The compiled artifact will be located in the `target/` directory.

---

## 📦 File Structure

```plaintext
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── mouseremapper/
│   │   │           ├── App.java          # Main JavaFX Application UI & Binding
│   │   │           ├── HookManager.java  # Win32 Low-Level Hook & keybd_event Simulation
│   │   │           ├── ConfigManager.java# Preset load/save utilizing Gson
│   │   │           └── Autostart.java    # Registry Boot Autostart Integration
│   │   └── resources/
│   │       └── com/
│   │           └── mouseremapper/
│   │               └── styles.css        # Premium Glassmorphic Theme CSS
├── pom.xml                               # Maven Project Descriptor
└── config.json                           # Saved User Presets (auto-generated)
```

---

## ⚙️ How It Works (Native Under the Hood)

1. **Win32 Hooking**: The application installs a native low-level mouse hook (`WH_MOUSE_LL`) using `SetWindowsHookEx`.
2. **Dedicated Loop**: Because Windows hooks require a message pump, the hook is managed in a separate, isolated background thread running a Win32 message loop (`GetMessage` / `TranslateMessage` / `DispatchMessage`).
3. **Key Interception**: When a mapped button click event is captured, the handler returns `1` to discard the native click and fires `keybd_event` calls on native Windows thread pools to simulate keyboard inputs.
4. **JVM Safety**: The JNA callback object is held in a strong static reference within Java to prevent JVM garbage collection which would cause system-level crashes during OS notifications.