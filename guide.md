# MouseX: Absolute Mouse Control — Quick Guide

A local-first, low-level mouse-to-keyboard remapping utility for Windows. Configure buttons 1-5 and mouse scrolls 6-7 to simulate keyboard hotkeys, custom chords, or repeating keystroke loops, with automated foreground-window profiling.

> [!IMPORTANT]
> **Unlike generic mouse macro applications**, MouseX operates at the Win32 kernel level using a low-level mouse hook (`WH_MOUSE_LL`). When a mapping is active, MouseX completely intercepts and blocks the native mouse click or scroll message from reaching other applications, generating pure keyboard events instead.

## 🚀 How to Run

### Option A — From Source (Development)

**Prerequisites:** Windows 10/11, JDK 21+, Maven 3.8+

```powershell
# 1. Package the shaded JAR
mvn clean package

# 2. Run the JavaFX app
mvn javafx:run
```

The application window will open showing the current remapping cards for buttons 1 through 7, a profile manager, and a global **START HOOK** trigger in the footer.

### Option B — Standalone EXE

If you have built or downloaded `MouseX.exe`:

```
Just double-click MouseX.exe
```

> [!NOTE]
> The EXE is a native wrapper generated using Launch4j. It loads settings from the local `profiles.json` and runs silently in the background, utilizing the system Java path mapped in `%JAVA_HOME%`.

---

## 🎯 How to Use

1. **Launch MouseX** — The Dashboard opens displaying the remapping cards. The global status in the footer shows `Stopped` (Red circle).
2. **Select Mapped Keys** — Choose a mouse button card (e.g., *Mouse Button 4 (X1 / Back)*).
3. **Configure Slots** — Check `Slot 1` and select a key (e.g., `Ctrl` or `Space`). You can enable up to 3 slots for combinations.
4. **Choose Remap Settings:**
   - **Enable remap:** Toggle this to activate remapping for the button.
   - **As Chord:** If checked, slots fire simultaneously (like a chord combo). If unchecked, they fire in rapid sequence.
   - **Enable repeat:** Repeat the keystroke loop (only available for buttons 1-5).
   - **Repeat until click:** Toggle the loop on first click, turn it off on the second click. If unchecked, the key repeats only while the mouse button is held down.
   - **Repeat Interval:** Use the slider to set the delay between repeated strokes (10ms to 1000ms).
5. **Click START HOOK** — The status indicator turns green (`Running`). The mappings are active system-wide!
6. **Autostart on Boot** — Check the box in the header to register MouseX in the Windows Registry to launch at login.

---

## 🗣️ Example Mappings

Here are some common remapping configurations:

### 1. Shift-Click remap
- **Button Card**: Mouse Button 4 (X1 / Back)
- **Slots**: Enable `Slot 1` -> Select `Shift`
- **Settings**: Check `Enable remap`
- **Result**: Clicking the Back button on your mouse acts exactly like holding or clicking the Shift key.

### 2. Tab Cycler (Scroll Remap)
- **Button Card**: Mouse Action 6 (Wheel Up)
  - **Slots**: `Slot 1` -> Select `Tab`
  - **Settings**: Check `Enable remap`
- **Button Card**: Mouse Action 7 (Wheel Down)
  - **Slots**: `Slot 1` -> Select `Shift`, `Slot 2` -> Select `Tab`
  - **Settings**: Check `Enable remap`, Check `As Chord`
- **Result**: Scrolling up cycles tabs forward (`Tab`), scrolling down cycles tabs backward (`Shift+Tab`).

### 3. Rapid Fire Space (Game Macro)
- **Button Card**: Mouse Button 3 (Middle Click)
- **Slots**: `Slot 1` -> Select `Space`
- **Settings**: Check `Enable remap`, Check `Enable repeat`, Slider -> Set to `50ms`
- **Result**: Holding down Middle Click continuously fires the Space key every 50 milliseconds.

---

## 🗂️ Profile Management

MouseX switches configurations based on the focused foreground window.

- **Default Profile**: Used for the desktop and unmapped applications.
- **Application Profiles**: Click `+` in the header. MouseX will scan all visible open windows. Select an executable (e.g. `chrome.exe` or `valheim.exe`).
- **Auto Switcher**: The background monitor checks active window focus every 500ms. If you focus Chrome, the profile automatically changes to `chrome.exe` and updates the UI and hooks instantly.

---

## ⚙️ Configuration

Settings are saved in `profiles.json` located in the application's root directory:

| Key | Default | Description |
|---|---|---|
| `keys` | `[]` | Array of virtual key codes mapped to the button |
| `remap` | `false` | Enable or disable remapping for this channel |
| `repeat` | `false` | Enable repeating keystroke loop |
| `untilClick` | `false` | Toggle mode for repeating loop (click-on, click-off) |
| `repeatIntervalMs`| `100` | Delay between key repetitions |
| `isChord` | `false` | Run keys simultaneously (`true`) or sequentially (`false`) |

---

## ⚠️ Important Notes

- **Windows-Only:** MouseX relies heavily on native Windows APIs (`WH_MOUSE_LL` hooks, registry writes, and keybd_event simulation). It will not run on macOS or Linux.
- **JVM Execution:** If autostart is enabled, it registers the path to `javaw.exe` to run the shaded JAR headless (without showing the standard console window).
- **Blocked Native Click:** When a mouse button is remapped, its native function is blocked. If you remap *Mouse Button 1 (Left Click)*, you will not be able to left-click elements normally unless you toggle the hook off!
- **Failsafe:** If you accidentally lock yourself out (e.g., by mapping left click to a useless key), you can use the system tray menu to quit the app or toggle the hook off. Double-click the tray icon to open the GUI.

---

## 📁 Important Files

| File | Purpose |
|---|---|
| `src/main/java/com/mouseremapper/App.java` | Main JavaFX GUI, profile scanner, active window thread, system tray layout |
| `src/main/java/com/mouseremapper/HookManager.java` | Native mouse hook processor and keyboard simulator |
| `src/main/java/com/mouseremapper/ConfigManager.java` | Config file loader and GSON mapper |
| `src/main/java/com/mouseremapper/Autostart.java` | Registry manipulation for autostart |
| `src/main/resources/com/mouseremapper/styles.css` | Custom dark-theme QSS stylesheets |
| `profiles.json` | Profile presets and layout rules |
| `MouseX.xml` | Launch4j wrapper settings |
