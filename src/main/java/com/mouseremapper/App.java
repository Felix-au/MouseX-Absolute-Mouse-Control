package com.mouseremapper;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.PopupMenu;
import java.awt.MenuItem;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.util.*;

public class App extends Application {

    private static final Map<String, Integer> KEY_MAP = new LinkedHashMap<>();
    static {
        KEY_MAP.put("Tab", 9);
        KEY_MAP.put("Enter", 13);
        KEY_MAP.put("Esc", 27);
        KEY_MAP.put("Space", 32);
        for (char c = 'A'; c <= 'Z'; c++) {
            KEY_MAP.put(String.valueOf(c), (int) c);
        }
        for (char c = '0'; c <= '9'; c++) {
            KEY_MAP.put(String.valueOf(c), (int) c);
        }
        KEY_MAP.put("Left", 37);
        KEY_MAP.put("Up", 38);
        KEY_MAP.put("Right", 39);
        KEY_MAP.put("Down", 40);
        KEY_MAP.put("Shift", 16);
        KEY_MAP.put("Ctrl", 17);
    }

    private static final String[] BUTTON_LABELS = {
            "Mouse Button 1 (Left Click)",
            "Mouse Button 2 (Right Click)",
            "Mouse Button 3 (Middle Click / Wheel Click)",
            "Mouse Button 4 (X1 / Back)",
            "Mouse Button 5 (X2 / Forward)",
            "Mouse Action 6 (Wheel Up)",
            "Mouse Action 7 (Wheel Down)"
    };

    private final HookManager hookManager = new HookManager();
    private final ConfigManager configManager = new ConfigManager(hookManager);

    private static class ButtonCardControls {
        CheckBox enableCheck;
        CheckBox repeatCheck;
        CheckBox untilClickCheck;
        CheckBox chordCheck;
        Slider repeatIntervalSlider;
        Label repeatIntervalLabel;
        CheckBox[] slotChecks = new CheckBox[3];
        @SuppressWarnings("unchecked")
        ComboBox<String>[] slotCombos = new ComboBox[3];
    }

    private final ButtonCardControls[] allCards = new ButtonCardControls[7];
    private boolean updatingUI = false;

    private Map<String, Map<Integer, HookManager.RemapConfig>> allProfiles = new HashMap<>();
    private String activeProfileName = "Default";
    private ComboBox<String> profileCombo;

    private Circle statusIndicator;
    private Label statusText;
    private Button startStopBtn;
    private CheckBox autostartCheck;

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false); // Keep running in background

        allProfiles = configManager.loadProfiles();
        if (!allProfiles.containsKey("Default")) {
            Map<Integer, HookManager.RemapConfig> def = new HashMap<>();
            for (int i = 1; i <= 7; i++) def.put(i, new HookManager.RemapConfig());
            allProfiles.put("Default", def);
        }
        applyProfileToHook(activeProfileName);

        VBox root = new VBox(20);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);

        // --- Header Section ---
        HBox headerContainer = new HBox();
        headerContainer.setAlignment(Pos.CENTER_LEFT);
        headerContainer.setSpacing(20);

        VBox titleContainer = new VBox(4);
        titleContainer.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("MOUSEX");
        titleLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 24px; -fx-font-weight: 900; -fx-letter-spacing: 1px;");
        Label subtitleLabel = new Label("ABSOLUTE MOUSE CONTROL");
        subtitleLabel.setStyle("-fx-text-fill: #A0AEC0; -fx-font-size: 11px; -fx-font-weight: bold;");
        titleContainer.getChildren().addAll(titleLabel, subtitleLabel);

        ImageView logoView = null;
        try {
            Image logoImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/MouseX.png")));
            logoView = new ImageView(logoImage);
            logoView.setFitWidth(36);
            logoView.setFitHeight(36);
            logoView.setPreserveRatio(true);
        } catch (Exception e) {
            System.err.println("Could not load header logo: " + e.getMessage());
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Profile Manager
        HBox profileBox = new HBox(8);
        profileBox.setAlignment(Pos.CENTER_LEFT);
        Label profileLbl = new Label("Profile:");
        profileLbl.setStyle("-fx-text-fill: #A0AEC0;");
        profileCombo = new ComboBox<>();
        profileCombo.getItems().addAll(allProfiles.keySet());
        profileCombo.setValue(activeProfileName);
        profileCombo.setOnAction(e -> {
            if (updatingUI) return;
            activeProfileName = profileCombo.getValue();
            applyProfileToHook(activeProfileName);
            refreshUI();
        });
        Button addProfileBtn = new Button("+");
        addProfileBtn.getStyleClass().add("secondary-button");
        addProfileBtn.setOnAction(e -> {
            Set<String> runningApps = new TreeSet<>();
            User32.INSTANCE.EnumWindows((hwnd, data) -> {
                if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                    IntByReference pid = new IntByReference();
                    User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
                    HANDLE process = Kernel32.INSTANCE.OpenProcess(0x0400 | 0x0010, false, pid.getValue());
                    if (process != null) {
                        char[] path = new char[1024];
                        int len = Psapi.INSTANCE.GetModuleFileNameExW(process, null, path, path.length);
                        Kernel32.INSTANCE.CloseHandle(process);
                        if (len > 0) {
                            String fullPath = new String(path, 0, len);
                            int lastSlash = fullPath.lastIndexOf('\\');
                            String exe = lastSlash >= 0 ? fullPath.substring(lastSlash + 1).toLowerCase() : fullPath.toLowerCase();
                            runningApps.add(exe);
                        }
                    }
                }
                return true;
            }, null);

            List<String> appsList = new ArrayList<>(runningApps);
            appsList.removeAll(allProfiles.keySet());

            if (appsList.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "No new running applications found.");
                alert.showAndWait();
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(appsList.get(0), appsList);
            dialog.setTitle("New App Profile");
            dialog.setHeaderText("Select a running application");
            dialog.setContentText("Executable:");
            dialog.showAndWait().ifPresent(name -> {
                String p = name.toLowerCase().trim();
                if (!p.isEmpty() && !allProfiles.containsKey(p)) {
                    Map<Integer, HookManager.RemapConfig> def = new HashMap<>();
                    for (int i = 1; i <= 7; i++) def.put(i, new HookManager.RemapConfig());
                    allProfiles.put(p, def);
                    updatingUI = true;
                    profileCombo.getItems().add(p);
                    profileCombo.setValue(p);
                    updatingUI = false;
                    activeProfileName = p;
                    applyProfileToHook(p);
                    refreshUI();
                    configManager.saveProfiles(allProfiles);
                }
            });
        });
        profileBox.getChildren().addAll(profileLbl, profileCombo, addProfileBtn);

        // Autostart Checkbox
        autostartCheck = new CheckBox("Autostart on Boot");
        autostartCheck.setSelected(Autostart.isAutostartEnabled());
        autostartCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                Autostart.enableAutostart();
            } else {
                Autostart.disableAutostart();
            }
        });

        if (logoView != null) {
            headerContainer.getChildren().addAll(logoView, titleContainer, spacer, profileBox, autostartCheck);
        } else {
            headerContainer.getChildren().addAll(titleContainer, spacer, profileBox, autostartCheck);
        }

        // --- Scrollable Mappings Grid ---
        VBox cardsContainer = new VBox(16);
        cardsContainer.setPadding(new Insets(4, 12, 12, 4));
        cardsContainer.setAlignment(Pos.TOP_CENTER);

        for (int i = 0; i < 7; i++) {
            final int buttonIndex = i + 1;
            VBox card = createButtonCard(buttonIndex);
            cardsContainer.getChildren().add(card);
        }

        ScrollPane scrollPane = new ScrollPane(cardsContainer);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // --- Footer Control Panel ---
        HBox footer = new HBox(20);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(12, 0, 0, 0));

        // Status Indicator
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusIndicator = new Circle(6);
        statusIndicator.getStyleClass().add("status-indicator");
        statusText = new Label("Stopped");
        statusText.setStyle("-fx-text-fill: #A0AEC0; -fx-font-weight: bold; -fx-font-size: 13px;");
        statusBox.getChildren().addAll(statusIndicator, statusText);
        updateStatusVisuals(false);

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        // Buttons
        startStopBtn = new Button("START HOOK");
        startStopBtn.getStyleClass().add("action-button");
        startStopBtn.setOnAction(e -> toggleHook());

        Button saveBtn = new Button("Save Preset");
        saveBtn.getStyleClass().add("secondary-button");
        saveBtn.setOnAction(e -> configManager.saveProfiles(allProfiles));

        Button loadBtn = new Button("Load Preset");
        loadBtn.getStyleClass().add("secondary-button");
        loadBtn.setOnAction(e -> {
            allProfiles = configManager.loadProfiles();
            if (!allProfiles.containsKey(activeProfileName)) activeProfileName = "Default";
            updatingUI = true;
            profileCombo.getItems().setAll(allProfiles.keySet());
            profileCombo.setValue(activeProfileName);
            updatingUI = false;
            applyProfileToHook(activeProfileName);
            refreshUI();
        });

        footer.getChildren().addAll(statusBox, footerSpacer, loadBtn, saveBtn, startStopBtn);

        root.getChildren().addAll(headerContainer, scrollPane, footer);

        // Start with 50% screen width + 10% increase (55% total)
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double startWidth = screenBounds.getWidth() * 0.55;

        Scene scene = new Scene(root, startWidth, 640);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/mouseremapper/styles.css")).toExternalForm());

        // Initialize UI settings from the loaded config
        refreshUI();

        startActiveWindowMonitor();

        primaryStage.setTitle("MouseX");
        try {
            primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/MouseX.png"))));
        } catch (Exception e) {
            System.err.println("Could not load application stage icon: " + e.getMessage());
        }
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(682);
        primaryStage.setMinHeight(500);
        
        setupSystemTray(primaryStage);
        
        primaryStage.setOnCloseRequest(e -> {
            e.consume(); // Prevent default exit
            primaryStage.hide(); // Minimize to tray
        });
        primaryStage.show();
    }

    private String getForegroundProcessName() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) return "Default";
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        HANDLE process = Kernel32.INSTANCE.OpenProcess(0x0400 | 0x0010, false, pid.getValue());
        if (process == null) return "Default";
        char[] path = new char[1024];
        int len = Psapi.INSTANCE.GetModuleFileNameExW(process, null, path, path.length);
        Kernel32.INSTANCE.CloseHandle(process);
        if (len == 0) return "Default";
        String fullPath = new String(path, 0, len);
        int lastSlash = fullPath.lastIndexOf('\\');
        if (lastSlash >= 0) return fullPath.substring(lastSlash + 1).toLowerCase();
        return fullPath.toLowerCase();
    }

    private void startActiveWindowMonitor() {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                    if (!hookManager.isHookActive()) continue; // Only switch if hook is active

                    String exe = getForegroundProcessName();
                    String targetProfile = allProfiles.containsKey(exe) ? exe : "Default";

                    if (!targetProfile.equals(activeProfileName)) {
                        activeProfileName = targetProfile;
                        applyProfileToHook(activeProfileName);
                        
                        Platform.runLater(() -> {
                            updatingUI = true;
                            profileCombo.setValue(activeProfileName);
                            updatingUI = false;
                            refreshUI();
                        });
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "ActiveWindowMonitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    private void applyProfileToHook(String profileName) {
        Map<Integer, HookManager.RemapConfig> profile = allProfiles.get(profileName);
        if (profile == null) return;
        for (Map.Entry<Integer, HookManager.RemapConfig> entry : profile.entrySet()) {
            HookManager.RemapConfig cfg = entry.getValue();
            hookManager.setRemap(entry.getKey(), cfg.virtualKeys, cfg.isRemapped, cfg.repeatEnabled, cfg.repeatUntilClick, cfg.repeatIntervalMs, cfg.isChord);
        }
    }

    private java.awt.Image createTrayIconImage() {
        try {
            return javax.imageio.ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/MouseX.png")));
        } catch (Exception e) {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = img.createGraphics();
            g2d.setColor(java.awt.Color.decode("#8B5CF6")); // Purple
            g2d.fillRect(0, 0, 16, 16);
            g2d.setColor(java.awt.Color.WHITE);
            g2d.drawRect(0, 0, 15, 15);
            g2d.dispose();
            return img;
        }
    }

    private void setupSystemTray(Stage primaryStage) {
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            
            PopupMenu popup = new PopupMenu();
            MenuItem openItem = new MenuItem("Open Dashboard");
            openItem.addActionListener(e -> Platform.runLater(() -> {
                primaryStage.show();
                primaryStage.toFront();
            }));
            
            MenuItem toggleItem = new MenuItem("Toggle Hook");
            toggleItem.addActionListener(e -> Platform.runLater(this::toggleHook));
            
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                hookManager.stopHook();
                Platform.exit();
                System.exit(0);
            });
            
            popup.add(openItem);
            popup.add(toggleItem);
            popup.addSeparator();
            popup.add(exitItem);
            
            TrayIcon trayIcon = new TrayIcon(createTrayIconImage(), "MouseX", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(() -> {
                primaryStage.show();
                primaryStage.toFront();
            })); // Double click to open
            
            try {
                tray.add(trayIcon);
            } catch (java.awt.AWTException e) {
                System.err.println("TrayIcon could not be added.");
            }
        }
    }

    private VBox createButtonCard(int buttonIndex) {
        VBox card = new VBox(12);
        card.getStyleClass().add("button-card");

        // Card Title
        Label title = new Label(BUTTON_LABELS[buttonIndex - 1]);
        title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        ButtonCardControls controls = new ButtonCardControls();
        allCards[buttonIndex - 1] = controls;

        // Slots for 3 key remappings
        HBox slotsContainer = new HBox(16);
        slotsContainer.setAlignment(Pos.CENTER_LEFT);

        for (int j = 0; j < 3; j++) {
            final int slotIdx = j;
            HBox slotRow = new HBox(6);
            slotRow.setAlignment(Pos.CENTER_LEFT);

            CheckBox slotCheck = new CheckBox("Slot " + (j + 1));
            slotCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updateRemap(buttonIndex));

            ComboBox<String> slotCombo = new ComboBox<>();
            slotCombo.getItems().addAll(KEY_MAP.keySet());
            slotCombo.getSelectionModel().select(0);
            slotCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateRemap(buttonIndex));
            slotCombo.setPrefWidth(100);

            // Disable combo box if slot is not checked
            slotCombo.disableProperty().bind(slotCheck.selectedProperty().not());

            slotRow.getChildren().addAll(slotCheck, slotCombo);
            slotsContainer.getChildren().add(slotRow);

            controls.slotChecks[j] = slotCheck;
            controls.slotCombos[j] = slotCombo;
        }
        card.getChildren().add(slotsContainer);

        // Control Settings Row (Enable, Repeat, Until Click)
        HBox settingsRow = new HBox(20);
        settingsRow.setAlignment(Pos.CENTER_LEFT);
        settingsRow.setPadding(new Insets(4, 0, 0, 0));

        controls.enableCheck = new CheckBox("Enable remap");
        controls.enableCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updateRemap(buttonIndex));

        controls.repeatCheck = new CheckBox("Enable repeat");
        controls.repeatCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updateRemap(buttonIndex));

        controls.untilClickCheck = new CheckBox("Repeat until click");
        controls.untilClickCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updateRemap(buttonIndex));

        controls.chordCheck = new CheckBox("As Chord");
        controls.chordCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updateRemap(buttonIndex));

        controls.repeatIntervalSlider = new Slider(10, 1000, 100);
        controls.repeatIntervalSlider.setPrefWidth(100);
        controls.repeatIntervalLabel = new Label("100ms");
        controls.repeatIntervalLabel.setStyle("-fx-text-fill: #A0AEC0; -fx-font-size: 11px;");
        
        controls.repeatIntervalSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            controls.repeatIntervalLabel.setText(newVal.intValue() + "ms");
            updateRemap(buttonIndex);
        });

        HBox sliderBox = new HBox(6, controls.repeatIntervalSlider, controls.repeatIntervalLabel);
        sliderBox.setAlignment(Pos.CENTER_LEFT);

        if (buttonIndex == 6 || buttonIndex == 7) {
            controls.repeatCheck.setVisible(false);
            controls.untilClickCheck.setVisible(false);
            sliderBox.setVisible(false);
        } else {
            // Disable until click checkbox if repeat is not enabled
            controls.untilClickCheck.disableProperty().bind(controls.repeatCheck.selectedProperty().not());
            sliderBox.visibleProperty().bind(controls.repeatCheck.selectedProperty());
        }

        settingsRow.getChildren().addAll(controls.enableCheck, controls.chordCheck, controls.repeatCheck, controls.untilClickCheck, sliderBox);
        card.getChildren().add(settingsRow);

        return card;
    }

    private void updateRemap(int buttonIndex) {
        if (updatingUI) return;

        ButtonCardControls controls = allCards[buttonIndex - 1];
        if (controls == null) return;

        List<Integer> keys = new ArrayList<>();
        for (int j = 0; j < 3; j++) {
            if (controls.slotChecks[j].isSelected()) {
                String selectedKey = controls.slotCombos[j].getValue();
                Integer vkCode = KEY_MAP.get(selectedKey);
                if (vkCode != null) {
                    keys.add(vkCode);
                }
            }
        }

        Map<Integer, HookManager.RemapConfig> currentProfile = allProfiles.get(activeProfileName);
        if (currentProfile == null) return;
        
        HookManager.RemapConfig cfg = currentProfile.get(buttonIndex);
        if (cfg == null) {
            cfg = new HookManager.RemapConfig();
            currentProfile.put(buttonIndex, cfg);
        }
        
        cfg.virtualKeys = new ArrayList<>(keys);
        cfg.isRemapped = controls.enableCheck.isSelected();
        cfg.repeatEnabled = controls.repeatCheck.isSelected();
        cfg.repeatUntilClick = controls.untilClickCheck.isSelected();
        cfg.repeatIntervalMs = (int) controls.repeatIntervalSlider.getValue();
        cfg.isChord = controls.chordCheck.isSelected();

        applyProfileToHook(activeProfileName);
    }

    private void refreshUI() {
        updatingUI = true;
        try {
            Map<Integer, HookManager.RemapConfig> config = hookManager.getConfig();
            for (int i = 0; i < 7; i++) {
                HookManager.RemapConfig cfg = config.get(i + 1);
                ButtonCardControls controls = allCards[i];
                if (cfg == null || controls == null) continue;

                controls.enableCheck.setSelected(cfg.isRemapped);
                controls.repeatCheck.setSelected(cfg.repeatEnabled);
                controls.untilClickCheck.setSelected(cfg.repeatUntilClick);
                controls.chordCheck.setSelected(cfg.isChord);
                controls.repeatIntervalSlider.setValue(cfg.repeatIntervalMs);

                // Clear slots first
                for (int j = 0; j < 3; j++) {
                    controls.slotChecks[j].setSelected(false);
                    controls.slotCombos[j].getSelectionModel().select(0);
                }

                // Fill from config
                List<Integer> keys = cfg.virtualKeys;
                for (int j = 0; j < keys.size() && j < 3; j++) {
                    int vk = keys.get(j);
                    String keyName = getKeyNameByCode(vk);
                    if (keyName != null) {
                        controls.slotChecks[j].setSelected(true);
                        controls.slotCombos[j].getSelectionModel().select(keyName);
                    }
                }
            }
        } finally {
            updatingUI = false;
        }
    }

    private String getKeyNameByCode(int code) {
        for (Map.Entry<String, Integer> entry : KEY_MAP.entrySet()) {
            if (entry.getValue() == code) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void toggleHook() {
        if (hookManager.isHookActive()) {
            hookManager.stopHook();
            startStopBtn.setText("START HOOK");
            startStopBtn.setStyle(""); // Reverts to CSS default
            updateStatusVisuals(false);
        } else {
            hookManager.startHook();
            startStopBtn.setText("STOP HOOK");
            startStopBtn.setStyle("-fx-background-color: linear-gradient(to right, #EF4444, #DC2626); -fx-effect: dropshadow(three-pass-box, rgba(239, 68, 68, 0.4), 10, 0, 0, 4);");
            updateStatusVisuals(true);
        }
    }

    private void updateStatusVisuals(boolean running) {
        if (running) {
            statusIndicator.setStyle("-fx-fill: #10B981; -fx-effect: dropshadow(three-pass-box, rgba(16, 185, 129, 0.6), 10, 0, 0, 0);");
            statusText.setText("Running");
            statusText.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");
        } else {
            statusIndicator.setStyle("-fx-fill: #EF4444; -fx-effect: dropshadow(three-pass-box, rgba(239, 68, 68, 0.6), 10, 0, 0, 0);");
            statusText.setText("Stopped");
            statusText.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
