package com.opentouchgaming.saffal;

/**
 * Storage-access bridge the prebuilt natives link against. Only the callbacks the
 * native side resolves in JNI_OnLoad are needed; with no SAF roots registered every
 * lookup reports "not in SAF" so the engine falls back to plain POSIX file IO.
 */
public class FileJNI
{
    public static int fopen(final String filePath, final String mode)
    {
        return -1;
    }

    public static int mkdir(String path)
    {
        return -1;
    }

    public static int exists(String path)
    {
        return 0;
    }

    public static int delete(String path)
    {
        return 1;
    }

    public static int rename(String oldFilename, String newFilename)
    {
        return -1;
    }

    public static String[] opendir(String path)
    {
        return new String[]{};
    }
}
