package com.mouseremapper;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HHOOK;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class HookManager {

    public static class RemapConfig {
        public List<Integer> virtualKeys = new ArrayList<>();
        public boolean isRemapped = false;
        public boolean repeatEnabled = false;
        public boolean repeatUntilClick = false;
    }

    // Custom MSLLHOOKSTRUCT definition to ensure full public access across all JNA versions
    public static class MyMSLLHOOKSTRUCT extends Structure {
        public POINT pt;
        public int mouseData;
        public int flags;
        public int time;
        public ULONG_PTR dwExtraInfo;

        public MyMSLLHOOKSTRUCT(Pointer p) {
            super(p);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("pt", "mouseData", "flags", "time", "dwExtraInfo");
        }
    }

    // Custom Callback interface to bypass any platform class version differences
    public interface MyHOOKPROC extends com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        LRESULT callback(int nCode, WPARAM wParam, LPARAM lParam);
    }

    // Define custom interface for user32 features not fully in JNA standard platform class
    public interface MyUser32 extends com.sun.jna.platform.win32.User32 {
        MyUser32 INSTANCE = Native.load("user32", MyUser32.class, W32APIOptions.DEFAULT_OPTIONS);
        
        HHOOK SetWindowsHookEx(int idHook, MyHOOKPROC lpfn, HMODULE hmod, int dwThreadId);
        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
    }

    private final Map<Integer, RemapConfig> config = new HashMap<>();
    private final AtomicBoolean[] repeatActive = new AtomicBoolean[5];
    private HHOOK hHook = null;
    private int hookThreadId = 0;
    private volatile boolean hookActive = false;

    // Strong reference to prevent the callback from being garbage collected by JVM
    private final MyHOOKPROC mouseHookCallback = new MyHOOKPROC() {
        @Override
        public LRESULT callback(int nCode, WPARAM wParam, LPARAM lParam) {
            return mouseProc(nCode, wParam, lParam);
        }
    };

    public HookManager() {
        for (int i = 0; i < 5; i++) {
            repeatActive[i] = new AtomicBoolean(false);
            config.put(i + 1, new RemapConfig());
        }
    }

    public synchronized void setRemap(int button, List<Integer> keys, boolean remap, boolean repeat, boolean untilClick) {
        RemapConfig cfg = config.get(button);
        if (cfg != null) {
            cfg.virtualKeys = new ArrayList<>(keys);
            cfg.isRemapped = remap;
            cfg.repeatEnabled = repeat;
            cfg.repeatUntilClick = untilClick;
        }
    }

    public synchronized Map<Integer, RemapConfig> getConfig() {
        return config;
    }

    public synchronized boolean isHookActive() {
        return hookActive;
    }

    public synchronized void startHook() {
        if (hookActive) return;
        hookActive = true;

        Thread hookThread = new Thread(() -> {
            HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
            synchronized (HookManager.this) {
                hHook = MyUser32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL, mouseHookCallback, hMod, 0);
                if (hHook == null) {
                    System.err.println("Failed to install mouse hook. Error code: " + Kernel32.INSTANCE.GetLastError());
                    hookActive = false;
                    return;
                }
                hookThreadId = Kernel32.INSTANCE.GetCurrentThreadId();
                System.out.println("Global mouse hook successfully installed. Thread ID: " + hookThreadId);
            }

            // Keep thread alive with a Windows Message Loop (required for WH_MOUSE_LL hooks)
            WinUser.MSG msg = new WinUser.MSG();
            while (hookActive) {
                int result = MyUser32.INSTANCE.GetMessage(msg, null, 0, 0);
                if (result <= 0) {
                    break;
                }
                MyUser32.INSTANCE.TranslateMessage(msg);
                MyUser32.INSTANCE.DispatchMessage(msg);
            }

            synchronized (HookManager.this) {
                if (hHook != null) {
                    MyUser32.INSTANCE.UnhookWindowsHookEx(hHook);
                    hHook = null;
                    System.out.println("Global mouse hook successfully uninstalled.");
                }
                hookThreadId = 0;
            }
        }, "MouseHookThread");

        hookThread.setDaemon(true);
        hookThread.start();
    }

    public synchronized void stopHook() {
        if (!hookActive) return;
        hookActive = false;

        // Reset repeat threads
        for (AtomicBoolean active : repeatActive) {
            active.set(false);
        }

        // Wake up the message pump by posting WM_QUIT to the hook thread
        if (hookThreadId != 0) {
            MyUser32.INSTANCE.PostThreadMessage(hookThreadId, WinUser.WM_QUIT, new WPARAM(0), new LPARAM(0));
        }
    }

    private void simulateKey(int button) {
        RemapConfig cfg;
        synchronized (this) {
            cfg = config.get(button);
        }
        if (cfg == null || cfg.virtualKeys.isEmpty()) return;

        for (int key : cfg.virtualKeys) {
            // Key Down (dwFlags = 0)
            MyUser32.INSTANCE.keybd_event((byte) key, (byte) 0, 0, 0);
            // Key Up (dwFlags = 2 for KEYEVENTF_KEYUP)
            MyUser32.INSTANCE.keybd_event((byte) key, (byte) 0, 2, 0);
        }
    }

    private LRESULT mouseProc(int nCode, WPARAM wParam, LPARAM lParam) {
        if (nCode >= 0) {
            int btn = 0;
            int w = wParam.intValue();

            // Native low-level mouse info pointer is located in lParam
            Pointer p = new Pointer(lParam.longValue());
            MyMSLLHOOKSTRUCT mouseStruct = new MyMSLLHOOKSTRUCT(p);

            // Detect button based on mouse window message
            if (w == 0x0201 || w == 0x0202) btn = 1;      // WM_LBUTTONDOWN / WM_LBUTTONUP
            else if (w == 0x0204 || w == 0x0205) btn = 2; // WM_RBUTTONDOWN / WM_RBUTTONUP
            else if (w == 0x0207 || w == 0x0208) btn = 3; // WM_MBUTTONDOWN / WM_MBUTTONUP
            else if (w == 0x020B || w == 0x020C) {        // WM_XBUTTONDOWN / WM_XBUTTONUP
                int mouseData = mouseStruct.mouseData;
                int xbtn = (mouseData >> 16) & 0xFFFF;
                btn = (xbtn == 1) ? 4 : 5;
            }

            if (btn > 0) {
                RemapConfig cfg;
                synchronized (this) {
                    cfg = config.get(btn);
                }

                if (cfg != null && cfg.isRemapped) {
                    boolean isDown = (w == 0x0201 || w == 0x0204 || w == 0x0207 || w == 0x020B);
                    boolean isUp = (w == 0x0202 || w == 0x0205 || w == 0x0208 || w == 0x020C);
                    int btnIdx = btn - 1;

                    if (isDown) {
                        if (cfg.repeatEnabled) {
                            if (cfg.repeatUntilClick) {
                                // Toggle active state
                                if (repeatActive[btnIdx].get()) {
                                    repeatActive[btnIdx].set(false);
                                } else {
                                    repeatActive[btnIdx].set(true);
                                    final int buttonToRepeat = btn;
                                    Thread repeatThread = new Thread(() -> {
                                        while (repeatActive[btnIdx].get() && hookActive) {
                                            simulateKey(buttonToRepeat);
                                            try {
                                                Thread.sleep(100);
                                            } catch (InterruptedException e) {
                                                break;
                                            }
                                        }
                                    }, "RepeatThread-" + buttonToRepeat);
                                    repeatThread.setDaemon(true);
                                    repeatThread.start();
                                }
                            } else {
                                // Start repeating while held down
                                repeatActive[btnIdx].set(true);
                                final int buttonToRepeat = btn;
                                Thread repeatThread = new Thread(() -> {
                                    while (repeatActive[btnIdx].get() && hookActive) {
                                        simulateKey(buttonToRepeat);
                                        try {
                                            Thread.sleep(100);
                                        } catch (InterruptedException e) {
                                            break;
                                        }
                                    }
                                }, "RepeatThread-" + buttonToRepeat);
                                repeatThread.setDaemon(true);
                                repeatThread.start();
                            }
                        } else {
                            simulateKey(btn);
                        }
                    }

                    if (isUp) {
                        if (cfg.repeatEnabled && !cfg.repeatUntilClick) {
                            repeatActive[btnIdx].set(false);
                        }
                        // Block default event by returning 1
                        return new LRESULT(1);
                    }

                    // Block default event by returning 1
                    return new LRESULT(1);
                }
            }
        }
        return MyUser32.INSTANCE.CallNextHookEx(hHook, nCode, wParam, lParam);
    }
}
