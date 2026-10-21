package com.evilex.launcher;

import android.app.ActivityManager;
import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evilex FPS / heat presets.
 *
 * What really moves FPS and heat on a phone:
 *   1. FPS cap + render/simulation distance  (this file, via options.txt)
 *   2. Renderer and resolution scale          (launcher settings, not options.txt)
 *   3. Performance mods                       (per Minecraft version)
 * JVM flags only help a little, so jvmArgs() keeps them minimal and safe.
 *
 * options.txt format changes slightly between Minecraft versions. Keys a version
 * does not know are ignored by the game, so these presets are safe to apply.
 */
public final class EvilexPerformance {

    public enum Preset { COOL, BALANCED, MAX_FPS }

    private EvilexPerformance() {}

    /** Values written into options.txt for each preset. */
    public static Map<String, String> optionsFor(Preset preset) {
        Map<String, String> o = new LinkedHashMap<>();
        // Same for every preset: cheap visuals.
        o.put("graphicsMode", "0");          // Fast
        o.put("entityShadows", "false");
        o.put("ao", "false");                // smooth lighting off
        o.put("biomeBlendRadius", "0");
        o.put("mipmapLevels", "0");
        o.put("renderClouds", "\"false\"");

        switch (preset) {
            case COOL:                        // lowest heat, battery friendly
                o.put("maxFps", "30");
                o.put("renderDistance", "4");
                o.put("simulationDistance", "5");
                o.put("particles", "2");      // minimal
                break;
            case BALANCED:                    // smooth 60 without cooking the phone
                o.put("maxFps", "60");
                o.put("renderDistance", "6");
                o.put("simulationDistance", "5");
                o.put("particles", "1");      // decreased
                break;
            case MAX_FPS:                     // uncapped, shortest view distance
                o.put("maxFps", "260");       // 260 = unlimited in Minecraft
                o.put("renderDistance", "5");
                o.put("simulationDistance", "4");
                o.put("particles", "2");
                break;
        }
        return o;
    }

    /**
     * Rewrites only the keys above in options.txt and keeps everything else.
     * Makes a one-time backup: options.txt.evilex.bak
     */
    public static void applyOptions(File optionsTxt, Preset preset) throws IOException {
        Map<String, String> wanted = optionsFor(preset);
        File dir = optionsTxt.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }

        List<String> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (optionsTxt.isFile()) {
            File bak = new File(dir, "options.txt.evilex.bak");
            if (!bak.exists()) copy(optionsTxt, bak);

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(optionsTxt), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int i = line.indexOf(':');
                    String key = i > 0 ? line.substring(0, i) : null;
                    if (key != null && wanted.containsKey(key)) {
                        lines.add(key + ":" + wanted.get(key));
                        seen.add(key);
                    } else {
                        lines.add(line);
                    }
                }
            }
        }
        for (Map.Entry<String, String> e : wanted.entrySet()) {
            if (!seen.contains(e.getKey())) lines.add(e.getKey() + ":" + e.getValue());
        }

        File tmp = new File(dir, "options.txt.evilex.tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (String l : lines) {
                w.write(l);
                w.write('\n');
            }
        }
        if (!tmp.renameTo(optionsTxt)) {
            optionsTxt.delete();
            if (!tmp.renameTo(optionsTxt)) throw new IOException("Could not write " + optionsTxt);
        }
    }

    /** Restores options.txt from the backup made by applyOptions(). Returns false if no backup. */
    public static boolean restoreOptions(File optionsTxt) throws IOException {
        File bak = new File(optionsTxt.getAbsoluteFile().getParentFile(), "options.txt.evilex.bak");
        if (!bak.isFile()) return false;
        copy(bak, optionsTxt);
        return true;
    }

    /** Heap to give the game: about 40% of phone RAM, clamped to 1-2 GB. More heap does not add FPS. */
    public static int recommendedHeapMb(int totalRamMb) {
        return Math.max(1024, Math.min(2048, totalRamMb * 40 / 100));
    }

    /** Minimal JVM args. Keep these as the "default" and test any extra flag one by one. */
    public static String jvmArgs(int heapMb) {
        int xms = Math.min(512, heapMb);
        return "-Xms" + xms + "M -Xmx" + heapMb + "M"
                + " -XX:+UseSerialGC"          // low overhead on phones
                + " -XX:+DisableExplicitGC";   // stops mods calling System.gc() and stuttering
    }

    public static int totalRamMb(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return (int) (mi.totalMem / (1024 * 1024));
    }

    private static void copy(File from, File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
          }
