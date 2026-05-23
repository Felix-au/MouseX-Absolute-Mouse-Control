package com.mouseremapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final HookManager hookManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ConfigManager(HookManager hookManager) {
        this.hookManager = hookManager;
    }

    public static class JsonConfigEntry {
        public List<Integer> keys;
        public boolean remap;
        public boolean repeat;
        public boolean untilClick;
        public int repeatIntervalMs = 100;
    }

    public void saveConfig() {
        try {
            Map<Integer, HookManager.RemapConfig> internalConfig = hookManager.getConfig();
            Map<String, JsonConfigEntry> jsonConfig = new HashMap<>();

            for (Map.Entry<Integer, HookManager.RemapConfig> entry : internalConfig.entrySet()) {
                JsonConfigEntry jsonEntry = new JsonConfigEntry();
                jsonEntry.keys = new ArrayList<>(entry.getValue().virtualKeys);
                jsonEntry.remap = entry.getValue().isRemapped;
                jsonEntry.repeat = entry.getValue().repeatEnabled;
                jsonEntry.untilClick = entry.getValue().repeatUntilClick;
                jsonEntry.repeatIntervalMs = entry.getValue().repeatIntervalMs;
                jsonConfig.put(String.valueOf(entry.getKey()), jsonEntry);
            }

            try (Writer writer = new FileWriter("config.json")) {
                gson.toJson(jsonConfig, writer);
                System.out.println("Config saved successfully.");
            }
        } catch (Exception e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public void loadConfig() {
        try {
            java.io.File file = new java.io.File("config.json");
            if (!file.exists()) {
                System.out.println("Config file config.json does not exist. Skipping load.");
                return;
            }

            try (Reader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<String, JsonConfigEntry>>() {}.getType();
                Map<String, JsonConfigEntry> jsonConfig = gson.fromJson(reader, type);

                if (jsonConfig != null) {
                    for (Map.Entry<String, JsonConfigEntry> entry : jsonConfig.entrySet()) {
                        int button = Integer.parseInt(entry.getKey());
                        JsonConfigEntry jsonEntry = entry.getValue();
                        hookManager.setRemap(
                                button,
                                jsonEntry.keys != null ? jsonEntry.keys : new ArrayList<>(),
                                jsonEntry.remap,
                                jsonEntry.repeat,
                                jsonEntry.untilClick,
                                jsonEntry.repeatIntervalMs == 0 ? 100 : jsonEntry.repeatIntervalMs
                        );
                    }
                    System.out.println("Config loaded successfully.");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
    }
}
