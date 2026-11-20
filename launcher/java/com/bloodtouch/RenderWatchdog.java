package com.bloodtouch;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Watches the engine's render thread from inside the game process and writes a report the moment it
 * stops burning CPU, so a hang that nobody can reproduce still leaves evidence on disk.
 *
 * <p>The menu is a static picture, so pixel and gfxinfo checks prove nothing here; the SDL loop runs
 * at ~100 % CPU even when nothing moves, which makes a frozen CPU-time counter the only honest
 * liveness signal. Reading /proc/self is allowed without root, so this works in the shipped build.
 */
public final class RenderWatchdog
{
    private static Thread worker;
    private static int reports;
    private static volatile long lastBeat;

    private RenderWatchdog()
    {
    }

    public static synchronized void start(String userFiles)
    {
        if (worker != null || userFiles == null || reports >= 12)
            return;

        final File dir = new File(userFiles + "/logs");
        lastBeat = SystemClock.elapsedRealtime();
        beat();

        worker = new Thread(() -> watch(dir), "BloodWatchdog");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Re-posts itself on the main looper. A render thread that keeps spinning while this stops
     * running is the signature of an ANR - a blocked UI thread, which the CPU check alone cannot see.
     */
    private static void beat()
    {
        new Handler(Looper.getMainLooper()).postDelayed(() ->
        {
            lastBeat = SystemClock.elapsedRealtime();
            beat();
        }, 500);
    }

    public static synchronized void stop()
    {
        if (worker != null)
        {
            worker.interrupt();
            worker = null;
        }
    }

    private static void watch(File dir)
    {
        long previous = -1;
        int stalled = 0;
        boolean reportedUi = false;

        while (!Thread.currentThread().isInterrupted())
        {
            if (reports >= 12)
                return;

            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                return;
            }

            long blocked = SystemClock.elapsedRealtime() - lastBeat;
            if (blocked > 6000 && !reportedUi)
            {
                reportedUi = true;
                writeReport(dir, "main thread has not run a handler beat for " + blocked + " ms");
            }
            else if (blocked < 2000)
            {
                reportedUi = false;
            }

            long now = renderThreadTime();
            if (now < 0)
            {
                previous = -1;
                stalled = 0;
                continue;
            }

            if (previous >= 0 && now == previous)
            {
                if (++stalled == 6)
                {
                    writeReport(dir, "render thread burned no CPU for " + stalled + " s");
                    stalled = 0;
                    previous = -1;
                }
            }
            else
            {
                stalled = 0;
            }
            previous = now;
        }
    }

    /** utime+stime of whichever SDL thread has burned the most CPU - that is the main loop. */
    private static long renderThreadTime()
    {
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null)
            return -1;

        long best = -1;
        for (File task : tasks)
        {
            String comm = readLine(new File(task, "comm"));
            if (comm == null || !comm.startsWith("SDL"))
                continue;

            String stat = readLine(new File(task, "stat"));
            if (stat == null)
                continue;

            // The comm field is parenthesised and may itself contain spaces, so count from its end.
            int tail = stat.lastIndexOf(')');
            if (tail < 0 || tail + 15 >= stat.length())
                continue;

            String[] fields = stat.substring(tail + 2).split(" ");
            try
            {
                long time = Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
                if (time > best)
                    best = time;
            }
            catch (NumberFormatException e)
            {
                // A thread that disappears mid-sample is not worth chasing.
            }
        }
        return best;
    }

    private static void writeReport(File dir, String why)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(why).append(" at ")
          .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
          .append("\n");

        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks != null)
        {
            for (File task : tasks)
            {
                String comm = readLine(new File(task, "comm"));
                sb.append("tid=").append(task.getName())
                  .append(" name=").append(comm)
                  .append(" state=").append(stateLetter(task))
                  .append(" wchan=").append(readLine(new File(task, "wchan")))
                  .append(" syscall=").append(firstWord(readLine(new File(task, "syscall"))))
                  .append('\n');
            }
        }

        sb.append("--- java stacks ---\n");
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet())
        {
            sb.append(entry.getKey().getName()).append('\n');
            for (StackTraceElement frame : entry.getValue())
                sb.append("    ").append(frame).append('\n');
        }

        sb.append("--- engine log tail ---\n").append(tail(new File(dir, "blood_raze.txt"), 60));

        reports++;
        try
        {
            dir.mkdirs();
            File out = new File(dir, "hang_" + new SimpleDateFormat("HHmmss", Locale.US)
                    .format(new Date()) + ".txt");
            try (FileOutputStream fos = new FileOutputStream(out))
            {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (IOException e)
        {
            // Nowhere to put the evidence; the watchdog must not become a second problem.
        }
    }

    private static String stateLetter(File task)
    {
        String stat = readLine(new File(task, "stat"));
        if (stat == null)
            return "?";
        int tail = stat.lastIndexOf(')');
        if (tail < 0 || tail + 3 >= stat.length())
            return "?";
        return stat.substring(tail + 2, tail + 3);
    }

    private static String firstWord(String line)
    {
        if (line == null)
            return "?";
        int space = line.indexOf(' ');
        return space < 0 ? line : line.substring(0, space);
    }

    private static String readLine(File file)
    {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"))
        {
            byte[] buf = new byte[(int) Math.min(raf.length(), 4096)];
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8).trim();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private static String tail(File file, int lines)
    {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"))
        {
            long size = raf.length();
            int keep = (int) Math.min(size, 24000);
            byte[] buf = new byte[keep];
            raf.seek(size - keep);
            raf.readFully(buf);
            String[] all = new String(buf, StandardCharsets.UTF_8).split("\\r?\\n");
            int from = Math.max(0, all.length - lines);
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < all.length; i++)
                sb.append(all[i]).append('\n');
            return sb.toString();
        }
        catch (IOException e)
        {
            return "(no engine log)";
        }
    }
}
