package com.mouseremapper;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import java.io.File;

public class Autostart {
    private static final String REG_PATH = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String KEY_NAME = "MouseRemapper";

    public static void enableAutostart() {
        try {
            // Get the absolute path to the current running application (likely a JAR or build folder)
            String jarPath = new File(Autostart.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .getAbsolutePath();

            String command;
            if (jarPath.endsWith(".jar")) {
                // If running as a packaged JAR, run it with javaw to avoid showing a console window
                command = "javaw -jar \"" + jarPath + "\"";
            } else {
                // Otherwise run the current classpath target
                command = "java -cp \"" + jarPath + "\" com.mouseremapper.App";
            }

            Advapi32Util.registrySetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    REG_PATH,
                    KEY_NAME,
                    command
            );
            System.out.println("Autostart successfully registered in Windows Registry: " + command);
        } catch (Exception e) {
            System.err.println("Failed to register autostart: " + e.getMessage());
        }
    }

    public static void disableAutostart() {
        try {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REG_PATH, KEY_NAME)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, REG_PATH, KEY_NAME);
                System.out.println("Autostart unregistered from Windows Registry.");
            }
        } catch (Exception e) {
            System.err.println("Failed to unregister autostart: " + e.getMessage());
        }
    }

    public static boolean isAutostartEnabled() {
        try {
            return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REG_PATH, KEY_NAME);
        } catch (Exception e) {
            return false;
        }
    }
}
