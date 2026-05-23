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
        public boolean isChord = false;
        public boolean useMacro = false;
        public List<HookManager.MacroEvent> macroSequence = new ArrayList<>();
    }

    public void saveProfiles(Map<String, Map<Integer, HookManager.RemapConfig>> allProfiles) {
        try {
            Map<String, Map<String, JsonConfigEntry>> jsonProfiles = new HashMap<>();
            
            for (Map.Entry<String, Map<Integer, HookManager.RemapConfig>> profileEntry : allProfiles.entrySet()) {
                Map<String, JsonConfigEntry> jsonConfig = new HashMap<>();
                for (Map.Entry<Integer, HookManager.RemapConfig> entry : profileEntry.getValue().entrySet()) {
                    JsonConfigEntry jsonEntry = new JsonConfigEntry();
                    jsonEntry.keys = new ArrayList<>(entry.getValue().virtualKeys);
                    jsonEntry.remap = entry.getValue().isRemapped;
                    jsonEntry.repeat = entry.getValue().repeatEnabled;
                    jsonEntry.untilClick = entry.getValue().repeatUntilClick;
                    jsonEntry.repeatIntervalMs = entry.getValue().repeatIntervalMs;
                    jsonEntry.isChord = entry.getValue().isChord;
                    jsonEntry.useMacro = entry.getValue().useMacro;
                    jsonEntry.macroSequence = new ArrayList<>(entry.getValue().macroSequence);
                    jsonConfig.put(String.valueOf(entry.getKey()), jsonEntry);
                }
                jsonProfiles.put(profileEntry.getKey(), jsonConfig);
            }

            try (Writer writer = new FileWriter("profiles.json")) {
                gson.toJson(jsonProfiles, writer);
                System.out.println("Profiles saved successfully.");
            }
        } catch (Exception e) {
            System.err.println("Failed to save profiles: " + e.getMessage());
        }
    }

    public Map<String, Map<Integer, HookManager.RemapConfig>> loadProfiles() {
        Map<String, Map<Integer, HookManager.RemapConfig>> allProfiles = new HashMap<>();
        
        try {
            java.io.File file = new java.io.File("profiles.json");
            if (!file.exists()) {
                // Try migrating from old config.json
                java.io.File oldFile = new java.io.File("config.json");
                if (oldFile.exists()) {
                    try (Reader reader = new FileReader(oldFile)) {
                        Type type = new TypeToken<Map<String, JsonConfigEntry>>() {}.getType();
                        Map<String, JsonConfigEntry> oldConfig = gson.fromJson(reader, type);
                        if (oldConfig != null) {
                            Map<Integer, HookManager.RemapConfig> defaultProfile = parseProfile(oldConfig);
                            allProfiles.put("Default", defaultProfile);
                            System.out.println("Migrated old config.json to Default profile.");
                        }
                    }
                }
                return allProfiles;
            }

            try (Reader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<String, Map<String, JsonConfigEntry>>>() {}.getType();
                Map<String, Map<String, JsonConfigEntry>> jsonProfiles = gson.fromJson(reader, type);

                if (jsonProfiles != null) {
                    for (Map.Entry<String, Map<String, JsonConfigEntry>> profileEntry : jsonProfiles.entrySet()) {
                        allProfiles.put(profileEntry.getKey(), parseProfile(profileEntry.getValue()));
                    }
                    System.out.println("Profiles loaded successfully.");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load profiles: " + e.getMessage());
        }
        return allProfiles;
    }

    private Map<Integer, HookManager.RemapConfig> parseProfile(Map<String, JsonConfigEntry> jsonConfig) {
        Map<Integer, HookManager.RemapConfig> profile = new HashMap<>();
        for (int i = 1; i <= 7; i++) {
            profile.put(i, new HookManager.RemapConfig());
        }
        for (Map.Entry<String, JsonConfigEntry> entry : jsonConfig.entrySet()) {
            int button = Integer.parseInt(entry.getKey());
            JsonConfigEntry jsonEntry = entry.getValue();
            HookManager.RemapConfig cfg = new HookManager.RemapConfig();
            cfg.virtualKeys = jsonEntry.keys != null ? new ArrayList<>(jsonEntry.keys) : new ArrayList<>();
            cfg.isRemapped = jsonEntry.remap;
            cfg.repeatEnabled = jsonEntry.repeat;
            cfg.repeatUntilClick = jsonEntry.untilClick;
            cfg.repeatIntervalMs = jsonEntry.repeatIntervalMs == 0 ? 100 : jsonEntry.repeatIntervalMs;
            cfg.isChord = jsonEntry.isChord;
            cfg.useMacro = jsonEntry.useMacro;
            cfg.macroSequence = jsonEntry.macroSequence != null ? new ArrayList<>(jsonEntry.macroSequence) : new ArrayList<>();
            profile.put(button, cfg);
        }
        return profile;
    }
}
