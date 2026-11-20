package com.opentouchgaming.androidcore;

import android.app.Dialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;

import com.opentouchgaming.androidcore.controls.TouchSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * The helper subset the game runtime calls into, without the AndroidX/Kotlin option UI.
 */
public class Utils
{
    static final String LOG = "Utils";

    // Same placeholder the engine option strings use for a quoted argument.
    public static final String QOT = "$QOT";

    public static String[] creatArgs(String appArgs)
    {
        ArrayList<String> a = new ArrayList<String>(
                Arrays.asList(appArgs.split("\"?( |$)(?=(([^\"]*\"){2})*[^\"]*$)\"?")));
        Iterator<String> iter = a.iterator();
        while (iter.hasNext())
        {
            if (iter.next().contentEquals(""))
            {
                iter.remove();
            }
        }

        for (int index = 0; index < a.size(); index++)
        {
            a.set(index, a.get(index).replace(QOT, "\""));
        }

        return a.toArray(new String[a.size()]);
    }

    public static void copyPNGAssets(Context ctx, String dir)
    {
        copyPNGAssets(ctx, dir, "");
    }

    public static void copyPNGAssets(Context ctx, String dir, String prefix)
    {
        if (prefix == null)
            prefix = "";

        File d = new File(dir);
        if (!d.exists())
            d.mkdirs();

        AssetManager assetManager = ctx.getAssets();
        String[] files = null;
        try
        {
            files = assetManager.list("");
        }
        catch (IOException e)
        {
            Log.e(LOG, "Failed to get asset file list.", e);
        }

        if (files == null)
            return;

        for (String filename : files)
        {
            if ((filename.endsWith("png") || filename.endsWith("txt")) && filename.startsWith(prefix))
            {
                try
                {
                    InputStream in = assetManager.open(filename);
                    OutputStream out = new FileOutputStream(dir + "/" + filename.substring(prefix.length()));
                    copyFile(in, out);
                    in.close();
                    out.flush();
                    out.close();
                }
                catch (IOException e)
                {
                    Log.e(LOG, "Failed to copy asset file: " + filename, e);
                }
            }
        }
    }

    public static void copyAsset(Context ctx, String assetName, String destinationDir)
    {
        copyAsset(ctx, assetName, destinationDir, assetName);
    }

    public static void copyAsset(Context ctx, String assetName, String destinationDir, String destinationName)
    {
        File dest = new File(destinationDir, destinationName);
        if (dest.exists())
            return;

        if (!dest.getParentFile().exists())
            dest.getParentFile().mkdirs();

        try
        {
            InputStream in = ctx.getAssets().open(assetName);
            OutputStream out = new FileOutputStream(dest);
            copyFile(in, out);
            in.close();
            out.flush();
            out.close();
        }
        catch (IOException e)
        {
            Log.e(LOG, "Failed to copy asset " + assetName + " to " + dest.getAbsolutePath(), e);
        }
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException
    {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1)
        {
            out.write(buffer, 0, read);
        }
    }

    public static void mkdirs(String dir)
    {
        File f = new File(dir);
        if (!f.exists())
            f.mkdirs();
    }

    public static void setImmersionMode(final Context ctx, Window window, String key)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            return;

        if (!TouchSettings.getBoolOption(ctx, key, true))
            return;

        setImmersive(window);

        window.getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> setImmersive(window));
    }

    private static void setImmersive(Window window)
    {
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                                                    View.SYSTEM_UI_FLAG_IMMERSIVE |
                                                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    public static void expandToCutout(final Context ctx, Window window, String key)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            return;

        if (!TouchSettings.getBoolOption(ctx, key, true))
            return;

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setAttributes(attributes);
    }

    public static void setInsets(Context ctx, Dialog dialog)
    {
        View decorView = dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null;
        if (decorView != null)
            setInsets(ctx, decorView, false);
    }

    public static void setInsets(Context ctx, View view, boolean isGame)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return;

        view.setOnApplyWindowInsetsListener((v, windowInsets) ->
        {
            int type = WindowInsets.Type.systemBars();

            if (!TouchSettings.getBoolOption(ctx, isGame ? "expand_into_notch" : "launcher_expand_into_notch", false))
                type |= WindowInsets.Type.displayCutout();

            android.graphics.Insets insets = windowInsets.getInsets(type);
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);

            return windowInsets;
        });
    }
}
