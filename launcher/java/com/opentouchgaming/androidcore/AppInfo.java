package com.opentouchgaming.androidcore;

import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * Path model of this launcher, reduced to what the Blood runtime needs.
 * Storage is always the legacy flash root (no SAF/scoped variant).
 */
public class AppInfo
{
    public enum Apps
    {
        MOD_ENGINE, DELTA_TOUCH, ALPHA_TOUCH, QUAD_TOUCH, RAZE_TOUCH
    }

    public static Apps app = Apps.RAZE_TOUCH;
    public static boolean isAndroidTv = false;

    private static Context context;

    static public void setContext(Context ctx)
    {
        context = ctx;
    }

    static public Context getContext()
    {
        return context;
    }

    static public void setApp(Apps app)
    {
        AppInfo.app = app;
    }

    static public String getFlashRoot()
    {
        return Environment.getExternalStorageDirectory().toString();
    }

    static public String getAppDirectory()
    {
        String appDir = AppSettings.getStringOption(context, "app_dir", null);
        if (appDir == null || appDir.startsWith(getFlashRoot() + "/OpenTouch/"))
        {
            // First run, or a preference left over from the Zeta Touch layout: this app owns
            // /sdcard/BloodTouch and never shares files with it.
            appDir = getFlashRoot() + "/BloodTouch";
            AppSettings.setStringOption(context, "app_dir", appDir);
        }

        new File(appDir).mkdirs();

        return appDir;
    }

    static public String getAppSecDirectory()
    {
        return null;
    }

    static public String getUserFiles()
    {
        String userFiles = getAppDirectory() + "/user_files";

        mkdir(userFiles);
        mkdir(userFiles + "/touch_layouts");

        return userFiles;
    }

    static public String getResFiles()
    {
        String files = getAppDirectory() + "/res/";

        mkdir(files);

        return files;
    }

    static public String getGamepadDirectory()
    {
        String path = getUserFiles() + "/gamepad";

        mkdir(path);

        return path;
    }

    static public String getLogDirectory()
    {
        String path = getAppDirectory() + "/log";

        mkdir(path);

        return path;
    }

    private static void mkdir(String path)
    {
        File f = new File(path);
        if (!f.exists())
            f.mkdirs();
    }
}
