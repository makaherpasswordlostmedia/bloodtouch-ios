package com.bloodtouch;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.opentouchgaming.androidcore.AppInfo;
import com.opentouchgaming.androidcore.AppSettings;
import com.opentouchgaming.androidcore.Utils;

import org.libsdl.app2012.SDLActivity;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

/**
 * Blood only launcher: picks the campaign, bakes the re-master console variables and hands the
 * intent to the SDL/Raze runtime running in the :game process.
 */
public class MainActivity extends Activity
{
    static final int GAME_TYPE_BLOOD = 101;
    static final String[] LOAD_LIBS = {"touchcontrols", "openal", "zmusic", "raze_dev"};

    static final String SUB_DIR = "BLOOD";

    /** Bump whenever an asset copied into res_files changes - startGame re-copies on mismatch. */
    static final int RES_REV = 2;

    static final int BG_TOP = 0xFF0A0607, BG_BOTTOM = 0xFF2B0808;
    static final int EMBER = 0xFFFF4A1E, EMBER_DIM = 0xFFFFB3A0;
    static final int TEXT = 0xFFEADCD8, TEXT_DIM = 0xFFB9A6A2;
    static final int PANEL = 0xFF4A0D0D, PANEL_DARK = 0xFF160404;

    LinearLayout root;
    TextView statusText;
    TextView campaignInfoText;
    Spinner campaignSpinner;
    CheckBox checkModern, checkV100, checkMedPrec, checkUpscale, checkBloom, checkVoxels, checkMusic;
    CheckBox checkOwnControls, checkInvertLook;
    Spinner fpsSpinner;
    Spinner versionSpinner;
    Spinner controlModeSpinner;
    Spinner lookSenseSpinner;
    Button grantButton;
    Button logButton;
    TextView logLabel;
    TextView logText;

    int appliedVersion;

    String[] lookSensValues = {"0.3", "0.6", "1.0", "1.6", "2.5"};

    String[] campaigns = new String[]{"Blood - Fresh Supply"};
    String[] campaignArgs = new String[]{""};
    File[] campaignDirs = new File[0];
    String[] campaignInis = new String[0];

    long launchRequestedAt;
    String lastArgs;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        AppInfo.setContext(this);
        AppInfo.setApp(AppInfo.Apps.RAZE_TOUCH);
        AppSettings.reloadSettings(this);

        buildUi();
        refreshStatus();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        refreshStatus();
        if (launchRequestedAt != 0)
        {
            logLabel.setVisibility(View.VISIBLE);
            logText.setVisibility(View.VISIBLE);
        }
        showEngineLog();
    }

    // ---------------------------------------------------------------- UI

    private void buildUi()
    {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM}));
        scroll.addView(root);
        setContentView(scroll);

        Title("BLOOD");
        statusText = new TextView(this);
        statusText.setTextSize(13);
        statusText.setTextColor(TEXT_DIM);
        root.addView(statusText);

        Space();
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(ActionButton(getString(R.string.start), 20, v -> startGame()));
        logButton = ActionButton("Журнал движка", 14, v -> toggleLog());
        actions.addView(logButton);
        root.addView(actions);

        grantButton = new Button(this);
        grantButton.setText("Дать доступ к файлам");
        style(grantButton, 14);
        grantButton.setVisibility(View.GONE);
        grantButton.setOnClickListener(v -> openStorageSettings());
        root.addView(grantButton);

        Space();
        Label("Версия");
        versionSpinner = new Spinner(this);
        String[] versions = {"Ремастер (вид Fresh Supply)", "1997 (вид DOS)"};
        versionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, versions));
        appliedVersion = AppSettings.getIntOption(this, "bt_version", 0);
        versionSpinner.setSelection(appliedVersion);
        versionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                AppSettings.setIntOption(MainActivity.this, "bt_version", position);
                appliedVersion = position;
            }

            public void onNothingSelected(AdapterView<?> parent)
            {
            }
        });
        root.addView(versionSpinner);

        Space();
        Label("Управление");
        checkOwnControls = Option("Свои кнопки (стик + свайп, без оверлея Zeta)", "bt_own_controls", false);

        controlModeSpinner = Spin(new String[]{"Аналоговый стик + аналоговый обзор", "Только клавиши (стрелки, без аналоги)"}, "bt_control_mode", 0);
        lookSenseSpinner = Spin(new String[]{"Обзор очень медленный", "Обзор медленный", "Обзор обычный", "Обзор быстрый", "Обзор очень быстрый"}, "bt_look_sense", 2);
        checkInvertLook = Option("Инвертировать вверх/вниз при обзоре", "bt_invert_look", false);

        Space();
        Label("Графика");
        checkModern = Option("Рендер GLES 3.2 (снимите для запаса GLES 2.0+)", "bt_modern", true);
        checkV100 = Option("Принудительно шейдеры GLSL 1.00 — если новая игра падает при старте", "bt_glsl_v100", false);
        checkMedPrec = Option("Средняя точность GLSL — быстрее, но геометрия может дрожать", "bt_glsl_medium", false);
        checkUpscale = Option("Апскейл текстур (HQ4x)", "bt_upscale", true);
        checkBloom = Option("Свечение (bloom) и блики", "bt_bloom", true);
        checkVoxels = Option("Воксели для склонов и предметов", "bt_voxels", true);
        checkMusic = Option("Оригинальный CD-rip саундтрек (выключено = ремастерный SC-55)", "bt_redbook", false);

        Label("Частота кадров");
        fpsSpinner = new Spinner(this);
        String[] fps = {"Без ограничений (частота экрана)", "60", "75", "90", "120"};
        fpsSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fps));
        fpsSpinner.setSelection(AppSettings.getIntOption(this, "bt_fps_index", 0));
        fpsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                AppSettings.setIntOption(MainActivity.this, "bt_fps_index", position);
            }

            public void onNothingSelected(AdapterView<?> parent)
            {
            }
        });
        root.addView(fpsSpinner);

        Space();
        Label("Кампания");
        campaignSpinner = new Spinner(this);
        campaignSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                AppSettings.setIntOption(MainActivity.this, "bt_campaign", position);
                showCampaignInfo(position);
            }

            public void onNothingSelected(AdapterView<?> parent)
            {
            }
        });
        root.addView(campaignSpinner);

        campaignInfoText = new TextView(this);
        campaignInfoText.setTypeface(android.graphics.Typeface.MONOSPACE);
        campaignInfoText.setTextSize(11);
        campaignInfoText.setTextColor(TEXT_DIM);
        campaignInfoText.setTextIsSelectable(true);
        root.addView(campaignInfoText);

        Space();
        logLabel = new TextView(this);
        logLabel.setText("Журнал движка");
        logLabel.setTextSize(15);
        logLabel.setTextColor(EMBER_DIM);
        logLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        logLabel.setPadding(0, dp(10), 0, dp(2));
        logLabel.setVisibility(View.GONE);
        root.addView(logLabel);
        logText = new TextView(this);
        logText.setTypeface(android.graphics.Typeface.MONOSPACE);
        logText.setTextSize(11);
        logText.setTextColor(TEXT_DIM);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable logBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE0000000, 0xE0140404});
        logBg.setCornerRadius(dp(10));
        logBg.setStroke(dp(1), 0x66FF4A1E);
        logText.setBackground(logBg);
        logText.setText("Ещё не запускалось.");
        logText.setVisibility(View.GONE);
        root.addView(logText);
    }

    private Button ActionButton(String text, float size, View.OnClickListener onClick)
    {
        Button b = new Button(this);
        b.setText(text);
        style(b, size);
        b.setOnClickListener(onClick);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    private void style(Button b, float size)
    {
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        b.setShadowLayer(dp(7), 0f, 0f, EMBER);

        GradientDrawable idle = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{PANEL, PANEL_DARK});
        idle.setCornerRadius(dp(12));
        idle.setStroke(dp(2), EMBER);

        GradientDrawable hot = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{PANEL_DARK, PANEL});
        hot.setCornerRadius(dp(12));
        hot.setStroke(dp(2), 0xFFFF8A4A);

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, hot);
        bg.addState(new int[]{android.R.attr.state_focused}, hot);
        bg.addState(new int[]{}, idle);
        b.setBackground(bg);
    }

    private void toggleLog()
    {
        boolean show = logText.getVisibility() != View.VISIBLE;
        int v = show ? View.VISIBLE : View.GONE;
        logText.setVisibility(v);
        logLabel.setVisibility(v);
        if (show)
            showEngineLog();
    }

    private CheckBox Option(String text, final String key, boolean def)
    {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextColor(TEXT);
        cb.setButtonTintList(ColorStateList.valueOf(EMBER));
        cb.setChecked(AppSettings.getBoolOption(this, key, def));
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> AppSettings.setBoolOption(this, key, isChecked));
        root.addView(cb);
        return cb;
    }

    private Spinner Spin(String[] items, final String key, int def)
    {
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        sp.setSelection(AppSettings.getIntOption(this, key, def));
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                AppSettings.setIntOption(MainActivity.this, key, position);
            }

            public void onNothingSelected(AdapterView<?> parent)
            {
            }
        });
        root.addView(sp);
        return sp;
    }

    private void Label(String text)
    {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(EMBER_DIM);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.06f);
        tv.setPadding(0, dp(10), 0, dp(2));
        root.addView(tv);
    }

    private void Title(String text)
    {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(40);
        tv.setTextColor(EMBER);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.14f);
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        // A blurred shadow is the only glow a TextView can do, and it needs the software layer.
        tv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        tv.setShadowLayer(dp(14), 0f, 0f, 0xB3FF2200);
        root.addView(tv);

        ObjectAnimator pulse = ObjectAnimator.ofFloat(tv, "alpha", 0.72f, 1f);
        pulse.setDuration(1700);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();
    }

    private void Space()
    {
        View v = new View(this);
        v.setMinimumHeight(dp(10));
        v.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, 0x80FF4A1E, 0x00000000}));
        root.addView(v);
    }

    private int dp(int value)
    {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---------------------------------------------------------------- data

    private String gameDirectory()
    {
        return AppInfo.getAppDirectory() + "/" + SUB_DIR;
    }

    private static String quote(String s)
    {
        return s.contains(" ") ? "\"" + s + "\"" : s;
    }

    /**
     * Raze only auto-detects an addon's level-definition ini when it is the single ini with an
     * [EpisodeN] section on the search path (levels.cpp DefFile()), and the base blood.ini always
     * competes with it. '-ini' sets DefaultCon and skips that detection, so titles, songs and the
     * EndingA next-level chain of an addon survive.
     */
    private static String defIni(File addonDir)
    {
        File[] files = addonDir.listFiles();
        if (files == null)
            return null;

        for (File f : files)
        {
            String name = f.getName();
            if (!name.toLowerCase().endsWith(".ini") || !f.isFile())
                continue;
            if (contains(readSmall(f), "[episode"))
            {
                return name;
            }
        }
        return null;
    }

    private static boolean contains(String haystack, String needle)
    {
        return haystack != null && haystack.toLowerCase().contains(needle);
    }

    private static String readSmall(File f)
    {
        if (f.length() > 512 * 1024)
            return null;

        try
        {
            java.io.InputStream in = new java.io.FileInputStream(f);
            try
            {
                byte[] buf = new byte[(int) f.length()];
                int off = 0;
                while (off < buf.length)
                {
                    int n = in.read(buf, off, buf.length - off);
                    if (n <= 0)
                        break;
                    off += n;
                }
                return new String(buf, 0, off, "ISO-8859-1");
            }
            finally
            {
                in.close();
            }
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * levelLoadDefaults() (levels.cpp:205-214) resolves '-map' against the [EpisodeN] Map1.. keys of
     * the def ini and errors out on a name that has no section there, so the addon's own first level
     * is the only safe one to name. Section and key names are case insensitive to Raze's IniFile.
     */
    private static String episodeFirstMap(File addonDir, String iniName)
    {
        if (iniName == null)
            return null;

        String text = readSmall(new File(addonDir, iniName));
        if (text == null)
            return null;

        boolean inEpisode1 = false;

        for (String line : text.split("\\r?\\n"))
        {
            String s = line.trim();

            if (s.startsWith("["))
            {
                int end = s.indexOf(']');
                String section = (end > 0 ? s.substring(1, end) : s.substring(1)).trim();
                inEpisode1 = section.equalsIgnoreCase("episode1");
                continue;
            }

            if (!inEpisode1 || s.isEmpty() || s.startsWith(";") || !s.contains("="))
                continue;

            String key = s.substring(0, s.indexOf('=')).trim();
            if (key.equalsIgnoreCase("map1"))
            {
                String value = s.substring(s.indexOf('=') + 1).trim();
                if (value.startsWith("\""))
                    value = value.substring(1);
                if (value.endsWith("\""))
                    value = value.substring(0, value.length() - 1);
                return value.isEmpty() ? null : value;
            }
        }

        return null;
    }

    private static String firstMap(File addonDir)
    {
        File[] maps = addonDir.listFiles();
        String best = null;

        if (maps != null)
        {
            for (File f : maps)
            {
                String name = f.getName();
                if (!name.toLowerCase().endsWith(".map"))
                    continue;
                name = name.substring(0, name.length() - 4);
                if (best == null || name.compareToIgnoreCase(best) < 0)
                    best = name;
            }
        }

        return best;
    }

    private void refreshStatus()
    {
        String dir = gameDirectory();

        boolean playable = new File(dir, "BLOOD.RFF").exists();
        boolean hasIni = new File(dir, "blood.ini").exists();

        StringBuilder sb = new StringBuilder();
        sb.append("Папка с игрой: ").append(dir).append("\n");
        sb.append(playable ? "BLOOD.RFF найден" : "BLOOD.RFF ОТСУТСТВУЕТ — скопируйте сюда файлы Fresh Supply").append("\n");
        if (!hasIni)
            sb.append("blood.ini не найден (Raze нужен он, чтобы выбрать эпизод)\n");

        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<String> args = new java.util.ArrayList<>();
        java.util.List<File> dirs = new java.util.ArrayList<>();
        java.util.List<String> inis = new java.util.ArrayList<>();

        File base = new File(dir);
        names.add("Blood - Fresh Supply");
        args.add("");
        dirs.add(base);
        inis.add(defIni(base));

        File cryptic = new File(dir, "addons/cryptic");
        if (new File(cryptic, "CP01.MAP").exists())
        {
            names.add("Cryptic Passage");
            args.add(" -cryptic -game_dir " + cryptic.getAbsolutePath());
            dirs.add(cryptic);
            inis.add(defIni(cryptic));
        }

        File addons = new File(dir, "addons");
        File[] files = addons.listFiles();
        if (files != null)
        {
            java.util.Arrays.sort(files);

            for (File f : files)
            {
                if (f.isDirectory() && !f.getName().equalsIgnoreCase("cryptic"))
                {
                    String ini = defIni(f);
                    names.add(addonName(f, ini));
                    // Raze only hardcodes addons/cryptic|td|wt as episodes. Anything else is merely
                    // added to the search path, so the first level must be named or Blood starts
                    // the base campaign.
                    String addonArgs = " -game_dir " + quote(f.getAbsolutePath());
                    if (ini != null)
                        addonArgs += " -ini " + quote(ini);
                    String mapName = episodeFirstMap(f, ini);
                    if (mapName == null)
                        mapName = firstMap(f);
                    if (mapName != null)
                        addonArgs += " -map " + mapName;
                    args.add(addonArgs);
                    dirs.add(f);
                    inis.add(ini);
                }
            }
        }

        campaigns = names.toArray(new String[names.size()]);
        campaignArgs = args.toArray(new String[args.size()]);
        campaignDirs = dirs.toArray(new File[dirs.size()]);
        campaignInis = inis.toArray(new String[inis.size()]);
        campaignSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, campaigns));
        int keep = AppSettings.getIntOption(this, "bt_campaign", 0);
        if (keep < 0 || keep >= campaigns.length)
            keep = 0;
        campaignSpinner.setSelection(keep);
        showCampaignInfo(keep);

        int hd = countWithExt(new File(base, "movie"), ".ivf");
        int original = countWithExt(new File(base, "movie"), ".smk");

        sb.append("Кампаний: ").append(campaigns.length).append(" · ");
        sb.append(hd).append(" HD + ")
          .append(original).append(" оригинальных роликов · ")
          .append(countWithExt(new File(base, "music"), ".ogg")).append(" треков саундтрека OGG\n");
        sb.append("Raze 1.11 (raze_dev) · arm64\n");
        sb.append("Автор порта: Telegram @VestronVulture\n");
        // Installing here as well means he sees the count on the launcher before pressing Play, which
        // is the only way to tell a shipped layout from the engine's built-in rects.
        if (!storageBlocked())
            installTouchLayouts();
        sb.append(touchLayoutStatus());

        statusText.setText(sb.toString());
        grantButton.setVisibility(storageBlocked() ? View.VISIBLE : View.GONE);
    }

    private static int countWithExt(File dir, String ext)
    {
        File[] files = dir.listFiles();
        int n = 0;

        if (files != null)
        {
            for (File f : files)
            {
                if (f.isFile() && f.getName().toLowerCase().endsWith(ext))
                    n++;
            }
        }

        return n;
    }

    /**
     * Folder names in the pack are lower-case engine paths ('deathwish'), while the human name of a
     * mission pack is only in its def ini, and even there [EpisodeN] holds one episode title, not the
     * pack title - so the known packs are named and the rest fall back to a prettified folder.
     */
    private String addonName(File addonDir, String iniName)
    {
        String key = addonDir.getName().toLowerCase();

        if (key.equals("deathwish"))
            return "Death Wish";
        if (key.equals("marrow"))
            return "Marrow";

        String episode = iniName == null ? null : episodeTitleOf(new File(addonDir, iniName), 1);
        if (episode != null)
            return capitalize(key) + " - " + episode;

        return capitalize(key);
    }

    private static String episodeTitleOf(File iniFile, int episode)
    {
        String text = readSmall(iniFile);
        if (text == null)
            return null;

        String wanted = "episode" + episode;
        String section = null;

        for (String line : text.split("\\r?\\n"))
        {
            String s = line.trim();
            if (s.startsWith("["))
            {
                int end = s.indexOf(']');
                section = (end > 0 ? s.substring(1, end) : s.substring(1)).trim();
                continue;
            }
            if (!wanted.equalsIgnoreCase(section) || s.isEmpty() || s.startsWith(";") || !s.contains("="))
                continue;
            if (!s.substring(0, s.indexOf('=')).trim().equalsIgnoreCase("title"))
                continue;

            String value = stripQuotes(s.substring(s.indexOf('=') + 1).trim());
            return value.isEmpty() ? null : value;
        }

        return null;
    }

    private static String capitalize(String s)
    {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String stripQuotes(String value)
    {
        if (value.startsWith("\""))
            value = value.substring(1);
        if (value.endsWith("\""))
            value = value.substring(0, value.length() - 1);
        return value.trim();
    }

    /**
     * Blood keeps every human-readable string in the def ini: [EpisodeN] Title plus one [mapname]
     * section with its own Title, so the campaign can be described from that file alone.
     */
    private void showCampaignInfo(int index)
    {
        if (index < 0 || index >= campaignDirs.length)
        {
            campaignInfoText.setText("");
            return;
        }

        campaignInfoText.setText(campaignInfo(campaignDirs[index], campaignInis[index]));
    }

    private String campaignInfo(File dir, String iniName)
    {
        if (dir == null)
            return "";

        File base = new File(gameDirectory());
        File iniFile = new File(dir, iniName == null ? "blood.ini" : iniName);

        if (!iniFile.exists())
            return "Нет определений уровней в " + iniFile.getName();

        String text = readSmall(iniFile);
        if (text == null)
            return "Не удалось прочитать " + iniFile.getName();

        java.util.Map<String, String> titles = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<String>> maps = new java.util.HashMap<>();
        java.util.List<String> episodes = new java.util.ArrayList<>();
        java.util.List<String> videos = new java.util.ArrayList<>();
        java.util.List<String> cutwavs = new java.util.ArrayList<>();
        java.util.List<String> songs = new java.util.ArrayList<>();
        java.util.List<String> tracks = new java.util.ArrayList<>();

        String section = null;

        for (String line : text.split("\\r?\\n"))
        {
            String s = line.trim();

            if (s.startsWith("["))
            {
                int end = s.indexOf(']');
                section = (end > 0 ? s.substring(1, end) : s.substring(1)).trim().toLowerCase();
                if (section.startsWith("episode") && !episodes.contains(section))
                    episodes.add(section);
                continue;
            }
            if (section == null || s.isEmpty() || s.startsWith(";") || !s.contains("="))
                continue;

            String key = s.substring(0, s.indexOf('=')).trim().toLowerCase();
            String value = stripQuotes(s.substring(s.indexOf('=') + 1).trim());
            if (value.isEmpty())
                continue;

            if (key.equals("title"))
            {
                titles.put(section, value);
            }
            else if (key.length() > 3 && key.startsWith("map") && isDigits(key.substring(3)))
            {
                java.util.List<String> list = maps.get(section);
                if (list == null)
                {
                    list = new java.util.ArrayList<>();
                    maps.put(section, list);
                }
                list.add(value);
            }
            else if (key.startsWith("cutscene") && !key.contains("level"))
            {
                videos.add(value.replace('\\', '/'));
            }
            else if (key.startsWith("cutwav"))
            {
                cutwavs.add(value.replace('\\', '/'));
            }
            else if (key.equals("song") && !section.equals("menusounds") && !songs.contains(value.toLowerCase()))
            {
                songs.add(value.toLowerCase());
            }
            else if (key.equals("track") && isDigits(value) && !tracks.contains(value))
            {
                tracks.add(value);
            }
        }

        StringBuilder sb = new StringBuilder();
        int shownLevels = 0;

        for (String episode : episodes)
        {
            String title = titles.get(episode);
            java.util.List<String> list = maps.get(episode);

            sb.append("Эпизод ").append(episode.substring("episode".length())).append(" - ")
              .append(title == null ? "(без названия)" : title).append(':')
              .append(list == null ? " нет карт" : " " + list.size() + " уровней").append('\n');

            if (shownLevels > 0 || list == null)
                continue;

            for (String map : list)
            {
                String levelTitle = titles.get(map.toLowerCase());
                sb.append("  ").append(pad(map, 9)).append(levelTitle == null ? "(без названия)" : levelTitle).append('\n');
                shownLevels++;
            }
        }

        if (videos.isEmpty())
        {
            sb.append("Катсцены: в этом ini их нет\n");
        }
        else
        {
            int found = 0;
            int hd = 0;
            int silent = 0;
            StringBuilder missing = new StringBuilder();

            for (String video : videos)
            {
                File here = new File(dir, video);
                File atBase = new File(base, video);
                File host = here.exists() ? here : atBase;

                // OpenMovie() prefers <same name>.ivf (VP8/VP9 in an IVF container) over the video
                // the ini names, so an .ivf makes the cutscene playable even without the 1997 file -
                // Death Wish and Marrow ship no Smacker videos at all, only the remaster's HD ones.
                int dot = video.lastIndexOf('.');
                String stem = dot < 0 ? video : video.substring(0, dot);
                boolean ivf = dot >= 0 && (new File(dir, stem + ".ivf").exists() || new File(base, stem + ".ivf").exists());

                if (!host.exists() && !ivf)
                {
                    if (missing.length() < 80)
                        missing.append(missing.length() == 0 ? "" : ", ").append(new File(video).getName());
                    continue;
                }

                found++;
                if (ivf)
                {
                    hd++;
                    // IVF carries no audio, and the engine streams the soundtrack that is named
                    // after the video, so an .ivf without one falls back to the ini CutWav as a
                    // plain sound effect - audible, but not the path the remaster assets expect.
                    if (!hasSoundFileFor(dir, base, stem))
                        silent++;
                }
            }

            sb.append("Катсцены: ").append(hd).append(" HD из ").append(found).append(" играбельных");
            if (found < videos.size())
                sb.append(" (").append(videos.size() - found).append(" отсутствует)");
            if (silent > 0)
                sb.append(" — ").append(silent).append(" HD без подходящего аудиофайла");
            if (missing.length() > 0)
                sb.append(" — нет файла ").append(missing).append(" (Raze их пропускает)");
            sb.append('\n');

            // An .ivf is only attempted when the ini's CutWav resolves to a registered sound,
            // so a missing CutWav file silently downgrades every HD cutscene of that campaign.
            int noWav = 0;
            for (String wav : cutwavs)
            {
                if (!new File(dir, wav).exists() && !new File(base, wav).exists())
                    noWav++;
            }
            if (noWav > 0)
                sb.append(noWav).append(" аудиофайл(ов) катсцен из ini отсутствует — HD-ролики не пойдут\n");
        }

        if (!songs.isEmpty())
        {
            int ogg = 0;
            for (String song : songs)
            {
                if (trackFound(dir, base, song))
                    ogg++;
            }
            sb.append("Саундтрек: ").append(ogg).append(" из ").append(songs.size())
              .append(ogg == songs.size() ? " треков в OGG" : " треков в OGG — остальные на MIDI");
            if (ogg > 0)
                sb.append(" (ремастерный SC-55)");
            sb.append('\n');
        }

        // mus_redbook only defaults to off for Blood (gamecontrol.cpp SetGenericRepDefault), so the
        // CD-rip checkbox really does switch to blood<Track>.ogg - report whether those files exist.
        if (!tracks.isEmpty())
        {
            int rip = 0;
            for (String id : tracks)
            {
                if (trackFound(dir, base, String.format("blood%02d", Integer.parseInt(id))))
                    rip++;
            }
            sb.append("CD-rip саундтрек: ").append(rip).append(" из ").append(tracks.size())
              .append(rip == tracks.size() ? " треков на диске\n" : " треков на диске — остальные молчат\n");
        }

        return sb.toString();
    }

    // Mirrors Raze's music lookup with mus_extendedlookup: <name>.ogg anywhere in the game
    // folder, or music/<name>.ogg for the ripped-track folder it also checks.
    private static boolean trackFound(File dir, File base, String song)
    {
        String[] hosts = { song + ".ogg", "music/" + song + ".ogg" };
        for (File root : new File[] { dir, base })
        {
            if (root == null)
                continue;
            for (String rel : hosts)
            {
                if (new File(root, rel).exists())
                    return true;
            }
        }
        return false;
    }

    // The extensions VpxPlayer tries for the soundtrack of an .ivf (movieplayer.cpp knownSoundExts).
    private static boolean hasSoundFileFor(File dir, File base, String stem)
    {
        String[] exts = { ".ogg", ".flac", ".mp3", ".opus", ".wav" };
        for (File root : new File[] { dir, base })
        {
            if (root == null)
                continue;
            for (String ext : exts)
            {
                if (new File(root, stem + ext).exists())
                    return true;
            }
        }
        return false;
    }

    private static String pad(String s, int width)
    {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width)
            sb.append(' ');
        return sb.toString() + " ";
    }

    private static boolean isDigits(String s)
    {
        for (int i = 0; i < s.length(); i++)
        {
            if (!Character.isDigit(s.charAt(i)))
                return false;
        }
        return s.length() > 0;
    }

    private boolean storageBlocked()
    {
        File probe = new File(AppInfo.getAppDirectory(), ".write_test");
        try
        {
            if (!probe.getParentFile().exists())
                probe.getParentFile().mkdirs();
            java.io.FileOutputStream out = new java.io.FileOutputStream(probe);
            out.write(1);
            out.close();
            probe.delete();
            return false;
        }
        catch (Exception e)
        {
            return true;
        }
    }

    private void openStorageSettings()
    {
        Intent intent = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            try
            {
                intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
            }
            catch (Exception ignored)
            {
                intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            }
        }

        if (intent == null)
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));

        try
        {
            startActivity(intent);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Экран настроек недоступен на этом устройстве", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- launch

    private String buildArgs()
    {
        StringBuilder a = new StringBuilder();
        boolean classic = versionSpinner.getSelectedItemPosition() == 1;

        // Only cvars that actually exist in libraze_dev.so (Raze 1.11); Zeta Touch also emits
        // vid_rendermode/vid_renderer/gl_sort_textures which this build silently ignores.
        a.append("-width $W -height $H +set gl_pipeline_depth 4 ");
        // The brightness slider is out of the menu now, but vid_brightness is archived, so a value
        // dragged on an earlier run would keep changing the picture with nothing left to fix it.
        a.append("+set vid_brightness 0 ");

        if (checkModern.isChecked())
        {
            a.append("+set vid_preferbackend 0 ");
        }
        else
        {
            // Stock Zeta Touch default backend: the GLES renderer, whose GLSL needs the
            // compatibility shader set. -gles2_renderer is a parameter of this engine build.
            a.append("-gles2_renderer +set vid_preferbackend 3 +set gl_customshader 0 ");
        }

        if (checkV100.isChecked())
            a.append("+set gles_force_glsl_v100 1 ");
        if (checkMedPrec.isChecked())
            a.append("+set gles_glsl_precision 1 ");

        a.append("+set gles_use_mapped_buffer 1 +set cl_syncinput 1 ");
        a.append(classic ? "+set showendoom 1 +set vid_scalefactor 0.35 " : "+set showendoom 0 ");

        // Mode table comes from HqResizeModes in raze_dev.pk3/menudef.txt: 1=ScaleNx, 2=HQNx,
        // 3=HQNx+MMX (x86 only), 4=xBRZ, 6=NormalNx.
        if (checkUpscale.isChecked())
            a.append("+set gl_texture_hqresizemode 4 +set gl_texture_hqresizemult 4 +set gl_texture_hqresize_targets 15 " +
                     "+set gl_texture_hqresize_maxinputsize 512 +set gl_texture_filter 3 +set gl_texture_filter_anisotropic 8 ");
        else
            a.append("+set gl_texture_hqresizemode 0 +set gl_texture_filter 0 +set gl_texture_filter_anisotropic 1 ");

        // Bloom is what makes fire, muzzle flash and fullbright sprites glow, so it follows its own
        // checkbox only - the renderer fallback and the 1997 look must not switch it off.
        if (checkBloom.isChecked())
            a.append("+set gl_bloom 1 +set gl_lens 1 ");
        else
            a.append("+set gl_bloom 0 +set gl_lens 0 ");

        if (checkVoxels.isChecked())
            a.append("+set r_voxels 2 ");
        else
            a.append("+set r_voxels 0 ");

        a.append("+set mus_redbook ").append(checkMusic.isChecked() ? "1" : "0").append(" ");
        // Blood always plays the ini 'Song' name with a .mid extension, so let the engine match
        // music/<song>.ogg - that is how the Remastered SC-55 soundtrack gets used.
        a.append("+set mus_extendedlookup 1 ");

        // Losing window focus makes Raze call alcDevicePauseSOFT/ResumeSOFT, which deadlocks the
        // Oboe backend on Android; forcing the always-active branch removes the resume freeze.
        a.append("+set i_soundinbackground 1 +set i_pauseinbackground 0 ");

        // Both overlays post the original 1997 keys (Zeta's own Blood layout and ours alike use Ctrl
        // to fire, Space to open, A to jump, Z to crouch), so the engine has to be bound with that
        // preset whichever one is on. filter/blood/engine/origbinds.txt adds X = +Alt_Fire and the
        // useitem keys on top of it, so no extra binds are needed for either overlay.
        a.append("+controlpreset 1 ");

        int fpsIndex = fpsSpinner.getSelectedItemPosition();
        if (fpsIndex > 0)
            a.append("+set vid_fps ").append(new String[]{"", "60", "75", "90", "120"}[fpsIndex]).append(" ");

        int pos = campaignSpinner.getSelectedItemPosition();
        if (pos >= 0 && pos < campaignArgs.length)
            a.append(campaignArgs[pos]);

        return a.toString();
    }

    private static final String[] TOUCH_LAYOUTS = {"menu.xml", "game_raze_dev101.xml",
            "weapons_raze_dev101.xml", "inventory_raze_dev101.xml", "custom_buttons_0_raze_dev101.xml",
            "weaponwheel_raze_dev101.xml", "touch_settings101.xml"};

    /**
     * libtouchcontrols keeps a built-in rect for any group without a file, so a failed copy shows up as
     * the engine's own layout replacing ours - say it on screen instead of leaving it as a mystery.
     */
    private String touchLayoutStatus()
    {
        File dir = new File(AppInfo.getUserFiles() + "/touch_layouts");
        int ok = 0;

        for (String name : TOUCH_LAYOUTS)
        {
            if (new File(dir, name).length() > 0)
                ok++;
        }

        if (ok == TOUCH_LAYOUTS.length)
            return "Раскладка тача: установлена (" + ok + " файлов в " + dir + ")";
        return "Раскладка тача: только " + ok + " из " + TOUCH_LAYOUTS.length + " файлов в " + dir
               + " - остальное движок берёт своё встроенное";
    }

    /**
     * The six widget groups the overlay is made of, taken off the phone as the layout it actually had
     * and shipped as files, so the arrangement survives instead of depending on the engine's handbuilt
     * default.  libtouchcontrols reads each group's file from user_files/touch_layouts before it falls
     * back to its built-in rects.
     */
    private void installTouchLayouts()
    {
        File dir = new File(AppInfo.getUserFiles() + "/touch_layouts");
        if (!dir.exists() && !dir.mkdirs())
            return;

        for (String name : TOUCH_LAYOUTS)
        {
            try
            {
                java.io.InputStream in = getAssets().open(name);
                try
                {
                    java.io.OutputStream out = new java.io.FileOutputStream(new File(dir, name));
                    try
                    {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0)
                            out.write(buf, 0, n);
                    }
                    finally
                    {
                        out.close();
                    }
                }
                finally
                {
                    in.close();
                }
            }
            catch (IOException e)
            {
                Toast.makeText(this, "Не удалось поставить " + name + ": " + e, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startGame()
    {
        if (storageBlocked())
        {
            Toast.makeText(this, "Нет доступа на запись: " + AppInfo.getAppDirectory(), Toast.LENGTH_LONG).show();
            openStorageSettings();
            return;
        }

        String resFiles = AppInfo.getResFiles();
        // Utils.copyAsset never overwrites an existing file, so a changed engine pk3 only reaches the
        // phone after the stale copy has been dropped.
        if (AppSettings.getIntOption(this, "bt_res_rev", 0) != RES_REV)
        {
            for (String name : new String[]{"raze_dev.pk3", "raze.sf2"})
                new File(resFiles, name).delete();

            // The old version preset switched these off behind the user's back.
            for (String key : new String[]{"bt_bloom", "bt_upscale", "bt_voxels"})
                AppSettings.setBoolOption(this, key, true);

            AppSettings.setIntOption(this, "bt_res_rev", RES_REV);
        }
        Utils.copyAsset(this, "raze_dev.pk3", resFiles);
        Utils.copyAsset(this, "raze.sf2", resFiles);

        File logs = new File(AppInfo.getUserFiles() + "/logs");
        if (!logs.exists())
            logs.mkdirs();

        String args = buildArgs();
        lastArgs = args;
        launchRequestedAt = System.currentTimeMillis();

        Intent intent = new Intent(this, SDLActivity.class);
        intent.putExtra("app", AppInfo.app.name());
        intent.putExtra("game_type", GAME_TYPE_BLOOD);
        intent.putExtra("gles_version", 3);
        intent.putExtra("use_gl4es", false);
        intent.putExtra("framebuffer_width", (String) null);
        intent.putExtra("framebuffer_height", (String) null);
        intent.putExtra("framebuffer_maintain_aspect", false);
        intent.putExtra("wheel_nbr", 10);
        intent.putExtra("audio_freq", 0);
        intent.putExtra("audio_samples", 0);
        intent.putExtra("audio_backend", 0);
        intent.putExtra("sdl_midi_player", 0);
        intent.putExtra("res_div_float", 1.0f);
        intent.putExtra("own_controls", checkOwnControls.isChecked());
        intent.putExtra("control_mode", controlModeSpinner.getSelectedItemPosition());
        intent.putExtra("look_sensitivity", Float.parseFloat(lookSensValues[lookSenseSpinner.getSelectedItemPosition()]));
        intent.putExtra("invert_look", checkInvertLook.isChecked());
        intent.putExtra("load_libs", LOAD_LIBS);
        intent.putExtra("log_filename", logs.getAbsolutePath() + "/blood_raze.txt");
        intent.putExtra("game_path", gameDirectory());
        intent.putExtra("user_files", AppInfo.getUserFiles());
        intent.putExtra("res_files", resFiles);
        intent.putExtra("args", args);
        intent.putExtra("quick_command_main_path", AppInfo.getUserFiles() + "/QC");
        intent.putExtra("quick_command_mod_path", AppInfo.getUserFiles() + "/QC/" + SUB_DIR);

        startActivity(intent);
    }

    // ---------------------------------------------------------------- diagnostics

    private File logFile()
    {
        return new File(AppInfo.getUserFiles() + "/logs/blood_raze.txt");
    }

    /**
     * Raze only flushes its journal on a clean exit and the engine lives in the ":game" process,
     * so the launcher reports the log here instead of requiring adb on the first run.
     */
    private void showEngineLog()
    {
        StringBuilder sb = new StringBuilder();
        if (lastArgs != null)
            sb.append("Аргументы: ").append(lastArgs).append("\n\n");

        File f = logFile();
        if (!f.exists())
        {
            sb.append(launchRequestedAt == 0
                    ? "Ещё не запускалось."
                    : "Нет журнала в " + f.getAbsolutePath() + " — игровой процесс умер, не успев его открыть.");
            logText.setText(sb);
            return;
        }

        long age = System.currentTimeMillis() - f.lastModified();
        if (launchRequestedAt != 0 && f.lastModified() < launchRequestedAt - 1500)
            sb.append("Журнал устарел (записан ").append(humanAge(age)).
                    append(" назад, до последнего запуска): движок вышел рано.\n\n");
        else
            sb.append("Журнал записан ").append(humanAge(age)).append(" назад.\n\n");

        try
        {
            String tail = tail(f, 16384, 45);
            sb.append(tail);

            // A first run has to be judged from this screen alone, so pull the engine's own
            // complaints out of the tail instead of leaving them buried in 45 lines of startup noise.
            String[] markers = {"Unknown command", "Fatal", "Error:", "Unable to open"};
            StringBuilder complaints = new StringBuilder();
            for (String line : tail.split("\\r?\\n"))
            {
                for (String marker : markers)
                {
                    if (line.contains(marker))
                    {
                        complaints.append(line.trim()).append('\n');
                        break;
                    }
                }
            }

            if (complaints.length() > 0)
                sb.insert(sb.indexOf("\n\n") + 2, "Движок сообщает:\n" + complaints + '\n');
        }
        catch (IOException e)
        {
            sb.append("Не удалось прочитать журнал: ").append(e);
        }

        logText.setText(sb);
    }

    private static String humanAge(long ms)
    {
        if (ms < 60000L)
            return (ms / 1000L) + " с";
        if (ms < 3600000L)
            return (ms / 60000L) + " мин";
        return (ms / 3600000L) + " ч";
    }

    private static String tail(File f, int maxBytes, int maxLines) throws IOException
    {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try
        {
            long len = raf.length();
            int n = (int) Math.min(len, maxBytes);
            raf.seek(len - n);

            byte[] buf = new byte[n];
            raf.readFully(buf);

            int from = 0;
            if (n < len)
            {
                for (int i = buf.length - 1; i >= 0; i--)
                {
                    if (buf[i] == '\n')
                    {
                        from = i + 1;
                        break;
                    }
                }
            }

            String[] lines = new String(buf, from, buf.length - from, Charset.forName("UTF-8")).split("\\r?\\n");
            StringBuilder sb = new StringBuilder();
            int first = Math.max(0, lines.length - maxLines);
            for (int i = first; i < lines.length; i++)
            {
                if (lines[i].isEmpty())
                    continue;
                if (sb.length() > 0)
                    sb.append("\n");
                sb.append(lines[i]);
            }
            return sb.toString();
        }
        finally
        {
            raf.close();
        }
    }
}
