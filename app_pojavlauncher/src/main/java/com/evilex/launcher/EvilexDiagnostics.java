package com.evilex.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Reads the end of the game log, recognises common problems and shows:
 *   popup (what went wrong)  ->  "Learn more"  ->  step-by-step fix screen.
 *
 * Usage (for example when the game closes, or from a "Check problems" button):
 *   boolean found = EvilexDiagnostics.showIfProblem(activity, new File(gameHome, "latestlog.txt"));
 * Check where your launcher writes latestlog.txt and pass that path.
 *
 * Add a new problem: add one more Rule(...) to RULES. Order matters, first match wins.
 */
public final class EvilexDiagnostics {

    private EvilexDiagnostics() {}

    public static final class Rule {
        final String[] keys;
        public final String title;
        public final String summary;
        public final String[] steps;

        Rule(String[] keys, String title, String summary, String... steps) {
            this.keys = keys;
            this.title = title;
            this.summary = summary;
            this.steps = steps;
        }
    }

    public static final class Result {
        public final Rule rule;
        public final String evidence;

        Result(Rule rule, String evidence) {
            this.rule = rule;
            this.evidence = evidence;
        }
    }

    private static final Rule[] RULES = {
        new Rule(
            new String[]{"Unrecognized VM option", "Unrecognized option:"},
            "Galat JVM argument",
            "Launcher ke JVM arguments mein koi option Java ko samajh nahi aaya.",
            "Settings mein JVM / Java arguments kholo.",
            "Custom arguments khaali karo ya Default / Reset dabao.",
            "Game dubara chalao.",
            "Chal jaye to arguments ek ek karke wapas daalo. Jo flag galat hai wahi crash dega."),

        new Rule(
            new String[]{"Could not reserve enough space", "Invalid maximum heap size",
                    "Could not create the Java Virtual Machine"},
            "RAM setting phone ke liye zyada hai",
            "Java ne itni RAM maangi jitni phone de nahi sakta.",
            "Settings mein RAM allocation kholo.",
            "Slider ko phone ki total RAM ke lagbhag 40% par rakho (zyada tar 2 GB kaafi hai).",
            "Background apps band karo.",
            "Game dubara chalao."),

        new Rule(
            new String[]{"UnsupportedClassVersionError", "more recent version of the Java Runtime"},
            "Galat Java version",
            "Is Minecraft version ko doosra Java runtime chahiye.",
            "Version / profile ki settings mein Java runtime badlo.",
            "Aam rule: 1.16.5 aur purane = Java 8, 1.17 se 1.20.4 = Java 17, 1.20.5 aur naye = Java 21.",
            "Bahut naye versions ko aur naya Java lag sakta hai. Minecraft ki official requirement check karo.",
            "Game dubara chalao."),

        new Rule(
            new String[]{"java.lang.OutOfMemoryError", "OutOfMemoryError"},
            "RAM khatam ho gayi (OutOfMemory)",
            "Game ko jitni memory mili thi wo kam padi.",
            "Render distance 4 se 6 karo.",
            "Heavy mods, shaders aur resource packs hata do.",
            "RAM allocation thodi badhao, lekin phone ki RAM ke 40-50% se zyada nahi.",
            "Background apps band karo aur dubara try karo."),

        new Rule(
            new String[]{"No space left on device"},
            "Storage full hai",
            "Phone mein game ke liye jagah nahi bachi.",
            "Kam se kam 2 se 3 GB storage khaali karo.",
            "Purane Minecraft versions aur unused worlds delete karo.",
            "Game dubara chalao."),

        new Rule(
            new String[]{"ZipException", "Invalid or corrupt jarfile", "zip END header not found"},
            "Kharab (corrupt) file",
            "Koi jar ya zip file adhoori download hui hai ya kharab hai.",
            "Log mein jis mod ya file ka naam aaye use delete karo.",
            "Naam na mile to mods folder khaali karke test karo.",
            "Phir bhi na chale to version delete karke dubara download karo."),

        new Rule(
            new String[]{"Some of your mods are incompatible", "Incompatible mods found", "Mod resolution failed",
                    "Missing or unsupported mandatory dependencies", "Duplicate mods found",
                    "Mixin apply failed", "MixinApplyError", "InvalidInjectionException"},
            "Mod ya version match nahi kar raha",
            "Koi mod game version se ya doosre mod se match nahi kar raha.",
            "Jo mod sabse aakhir mein add kiya use hata ke try karo.",
            "Check karo ki har mod isi Minecraft version aur isi loader (Fabric / Forge / NeoForge) ka hai.",
            "Zaroori dependency mods (jaise Fabric API) install hain ya nahi dekho.",
            "Mods ek ek karke wapas daalo. Jo crash kare wahi problem hai."),

        new Rule(
            new String[]{"GLFW error", "Failed to create window", "OpenGLException", "GLException",
                    "SIGSEGV", "A fatal error has been detected by the Java Runtime Environment"},
            "Graphics ya native crash",
            "Renderer ya graphics driver ke saath problem aa rahi hai.",
            "Settings mein Renderer badal ke doosra option chuno aur test karo.",
            "Resolution scale kam karo.",
            "Custom JVM arguments reset karo.",
            "Shaders aur graphics mods hata ke dekho."),

        new Rule(
            new String[]{"UnknownHostException", "Unable to resolve host", "Invalid session"},
            "Internet ya login problem",
            "Internet nahi mila ya login session expire ho gaya.",
            "Wi-Fi ya mobile data check karo.",
            "Account se logout karke Microsoft se dobara login karo.",
            "Phone ka date aur time 'Automatic' par rakho."),

        // Fallback: any crash that no rule above recognised.
        new Rule(
            new String[]{"---- Minecraft Crash Report ----", "Exception in thread"},
            "Game crash hua",
            "Crash hua, lekin exact wajah pehchani nahi gayi.",
            "Saare mods hata ke vanilla version chalao.",
            "Resource packs aur shaders band karo.",
            "Renderer badlo aur RAM thodi kam karo.",
            "Phir bhi na chale to crash-reports folder ki latest file aur latestlog.txt kisi jaankaar ko dikhao."),
    };

    /** Returns the first matching problem, or null if the log looks fine. */
    public static Result diagnose(String log) {
        if (log == null || log.isEmpty()) return null;
        for (Rule rule : RULES) {
            for (String key : rule.keys) {
                if (log.contains(key)) return new Result(rule, lineContaining(log, key));
            }
        }
        return null;
    }

    /** Scans the last ~256 KB of the log. Shows the popup and returns true if a problem was found. */
    public static boolean showIfProblem(final Activity activity, File logFile) {
        final Result result = diagnose(readTail(logFile, 256 * 1024));
        if (result == null) return false;

        new AlertDialog.Builder(activity)
                .setTitle("Problem mili: " + result.rule.title)
                .setMessage(result.rule.summary)
                .setPositiveButton("Learn more", (d, w) -> showGuide(activity, result))
                .setNegativeButton("Band karo", null)
                .show();
        return true;
    }

    private static void showGuide(Activity activity, Result result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        Context ctx = builder.getContext();   // themed context so text colours are readable
        int pad = (int) (20 * ctx.getResources().getDisplayMetrics().density);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);

        TextView why = new TextView(ctx);
        why.setText(result.rule.summary);
        why.setTextSize(16);
        box.addView(why);

        TextView head = new TextView(ctx);
        head.setText("Fix kaise karein:");
        head.setTextSize(16);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setPadding(0, pad, 0, 0);
        box.addView(head);

        for (int i = 0; i < result.rule.steps.length; i++) {
            TextView step = new TextView(ctx);
            step.setText((i + 1) + ". " + result.rule.steps[i]);
            step.setTextSize(15);
            step.setPadding(0, pad / 2, 0, 0);
            box.addView(step);
        }

        if (!result.evidence.isEmpty()) {
            TextView ev = new TextView(ctx);
            ev.setText("Log mein mila:\n" + result.evidence);
            ev.setTextSize(12);
            ev.setTypeface(Typeface.MONOSPACE);
            ev.setTextIsSelectable(true);
            ev.setPadding(0, pad, 0, 0);
            box.addView(ev);
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(box);

        builder.setTitle(result.rule.title)
                .setView(scroll)
                .setPositiveButton("Samajh gaya", null)
                .show();
    }

    static String readTail(File f, int maxBytes) {
        if (f == null || !f.isFile()) return "";
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            int n = (int) Math.min(len, maxBytes);
            byte[] buf = new byte[n];
            raf.seek(len - n);
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String lineContaining(String log, String key) {
        int i = log.indexOf(key);
        if (i < 0) return "";
        int start = log.lastIndexOf('\n', i) + 1;
        int end = log.indexOf('\n', i);
        if (end < 0) end = log.length();
        String line = log.substring(start, end).trim();
        return line.length() > 200 ? line.substring(0, 200) + "..." : line;
    }
        }
