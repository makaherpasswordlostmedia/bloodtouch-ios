package com.bloodtouch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.NativeLib;
import org.libsdl.app2012.SDLActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mobile control scheme: left thumb stick, swipe to look on the right, seven large action buttons.
 * Buttons post real keyboard events through SDL, so the game keeps its 1997 bindings
 * ('+controlpreset 1'); the stick and the look use the engine's analog axes, and 'keys' mode
 * replaces those axes with the matching key presses.
 */
public class BloodControls extends View
{
    public static final int MODE_ANALOG = 0;
    public static final int MODE_KEYS = 1;

    // LOOK_MODE_MOUSE: the value is a per-event delta normalised by the screen, which is what a
    // swipe produces. The stock gamepad path uses LOOK_MODE_JOYSTICK (2) with a held rate instead.
    private static final int LOOK_MODE_MOUSE = 0;

    private static int mode = MODE_ANALOG;
    private static float lookSensitivity = 1.0f;
    private static boolean invertPitch;

    public static void configure(int controlMode, float lookSense, boolean invert)
    {
        mode = controlMode;
        lookSensitivity = lookSense;
        invertPitch = invert;
    }

    /** A button is a circle: centre in screen fractions, diameter as a fraction of the height. */
    private static final class Button
    {
        final String label;
        final int keycode;
        final float cx;
        final float cy;
        final float d;
        final boolean keysOnly;
        final boolean holdToFire;
        int pointer = -1;

        Button(String label, int keycode, float cx, float cy, float d)
        {
            this(label, keycode, cx, cy, d, false, false);
        }

        Button(String label, int keycode, float cx, float cy, float d, boolean keysOnly)
        {
            this(label, keycode, cx, cy, d, keysOnly, false);
        }

        Button(String label, int keycode, float cx, float cy, float d, boolean keysOnly, boolean holdToFire)
        {
            this.label = label;
            this.keycode = keycode;
            this.cx = cx;
            this.cy = cy;
            this.d = d;
            this.keysOnly = keysOnly;
            this.holdToFire = holdToFire;
        }

        boolean active()
        {
            return !keysOnly || mode == MODE_KEYS;
        }

        boolean contains(float px, float py, float w, float h)
        {
            float r = bRadius(w, h);
            float dx = px - cx * w;
            float dy = py - cy * h;
            return dx * dx + dy * dy <= r * r;
        }

        float bRadius(float w, float h)
        {
            return d * h / 2f;
        }
    }

    private static final Button[] BUTTONS =
    {
        new Button("FIRE", KeyEvent.KEYCODE_CTRL_LEFT, 0.900f, 0.690f, 0.30f),
        new Button("JUMP", KeyEvent.KEYCODE_A, 0.905f, 0.420f, 0.19f),
        new Button("USE", KeyEvent.KEYCODE_SPACE, 0.695f, 0.420f, 0.19f),
        new Button("DUCK", KeyEvent.KEYCODE_Z, 0.690f, 0.690f, 0.19f),
        new Button("ALT", KeyEvent.KEYCODE_X, 0.660f, 0.890f, 0.15f),
        new Button("MED", KeyEvent.KEYCODE_M, 0.905f, 0.890f, 0.15f),
        new Button("WPN", KeyEvent.KEYCODE_APOSTROPHE, 0.790f, 0.900f, 0.15f),

        new Button("MENU", KeyEvent.KEYCODE_ESCAPE, 0.045f, 0.075f, 0.13f, false, true),
        new Button("MAP", KeyEvent.KEYCODE_TAB, 0.125f, 0.075f, 0.13f),

        // The stick only reaches forward/back and turn with keys, so strafe needs its own pair.
        new Button("<", KeyEvent.KEYCODE_COMMA, 0.270f, 0.250f, 0.15f, true),
        new Button(">", KeyEvent.KEYCODE_PERIOD, 0.370f, 0.250f, 0.15f, true),
    };

    private static final int MENU_KEY = KeyEvent.KEYCODE_ESCAPE;

    // On the title screen ESC is Blood's 'quit' action, so a burst of taps there killed the engine.
    // MENU now has to be held, and one hold blocks the button until the cooldown runs out.
    private static final long MENU_HOLD_MS = 250;
    private static final long MENU_COOLDOWN_MS = 900;

    private static final float STICK_CX = 0.150f;
    private static final float STICK_CY = 0.660f;
    private static final float STICK_ZONE = 0.34f;
    private static final float STICK_DEAD_ZONE = 0.16f;
    private static final float LOOK_DEAD_ZONE = 0.0016f;

    // Blood 1997: arrows move and turn, comma/period strafe, Shift runs, KP4/KP6 turn, KP9/KP3 look.
    private static final int KEY_FWD = KeyEvent.KEYCODE_DPAD_UP;
    private static final int KEY_BACK = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int KEY_TURN_LEFT = KeyEvent.KEYCODE_DPAD_LEFT;
    private static final int KEY_TURN_RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;
    private static final int KEY_STRAFE_LEFT = KeyEvent.KEYCODE_COMMA;
    private static final int KEY_STRAFE_RIGHT = KeyEvent.KEYCODE_PERIOD;
    private static final int KEY_LOOK_LEFT = KeyEvent.KEYCODE_NUMPAD_4;
    private static final int KEY_LOOK_RIGHT = KeyEvent.KEYCODE_NUMPAD_6;
    private static final int KEY_LOOK_UP = KeyEvent.KEYCODE_NUMPAD_9;
    private static final int KEY_LOOK_DOWN = KeyEvent.KEYCODE_NUMPAD_3;
    private static final int KEY_RUN = KeyEvent.KEYCODE_SHIFT_LEFT;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Button> held = new ArrayList<>();
    private final Set<Integer> downKeys = new HashSet<>();

    private final float baseTextSize;

    private Button menuPending;
    private boolean menuArmed;
    private long menuBlockedUntil;

    private final Runnable menuArm = new Runnable()
    {
        public void run()
        {
            if (menuPending == null || menuPending.pointer < 0)
                return;

            menuArmed = true;
            keyDown(MENU_KEY);
            invalidate();
        }
    };

    private int stickPointer = -1;
    private float stickOriginX;
    private float stickOriginY;
    private float knobX;
    private float knobY;

    private int lookPointer = -1;
    private float lookLastX;
    private float lookLastY;
    private long lookMovedAt;

    private final Runnable lookSettle = new Runnable()
    {
        public void run()
        {
            if (lookPointer < 0)
                return;

            if (SystemClock.uptimeMillis() - lookMovedAt > 120)
                releaseLookKeys();

            postDelayed(this, 60);
        }
    };

    public BloodControls(Context context)
    {
        super(context);

        fill.setColor(Color.argb(46, 220, 40, 40));
        fill.setStyle(Paint.Style.FILL);
        active.setColor(Color.argb(120, 235, 70, 50));
        active.setStyle(Paint.Style.FILL);

        ring.setColor(Color.argb(110, 255, 90, 70));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(2));

        knob.setColor(Color.argb(90, 255, 110, 90));
        knob.setStyle(Paint.Style.FILL);

        text.setColor(Color.argb(225, 255, 240, 240));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        baseTextSize = dp(13);

        setFocusable(false);
    }

    private float dp(int value)
    {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getContext().getResources().getDisplayMetrics());
    }

    private float stickRadius()
    {
        return Math.min(getWidth(), getHeight()) * 0.145f;
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        float w = getWidth();
        float h = getHeight();
        float r = stickRadius();
        float cx = STICK_CX * w;
        float cy = STICK_CY * h;

        canvas.drawCircle(cx, cy, r, ring);
        if (stickPointer >= 0)
            canvas.drawCircle(cx + knobX, cy + knobY, r * 0.45f, knob);

        for (Button b : BUTTONS)
        {
            if (!b.active())
                continue;

            float radius = b.bRadius(w, h);
            float bx = b.cx * w;
            float by = b.cy * h;
            boolean on = b.pointer >= 0;

            canvas.drawCircle(bx, by, radius, on ? active : fill);
            String label = (on && b.holdToFire && !menuArmed) ? "HOLD" : b.label;
            text.setTextSize(Math.min(baseTextSize, 1.8f * radius / label.length()));
            canvas.drawText(label, bx, by - (text.ascent() + text.descent()) / 2, text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        switch (event.getActionMasked())
        {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            {
                int i = event.getActionIndex();
                touchDown(event.getPointerId(i), event.getX(i), event.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
            {
                for (int i = 0; i < event.getPointerCount(); i++)
                    touchMove(event.getPointerId(i), event.getX(i), event.getY(i));
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            {
                int i = event.getActionIndex();
                touchUp(event.getPointerId(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            {
                stopAll();
                break;
            }
        }

        return true;
    }

    private void touchDown(int pid, float x, float y)
    {
        for (Button b : BUTTONS)
        {
            if (b.active() && b.contains(x, y, getWidth(), getHeight()))
            {
                press(b, pid);
                return;
            }
        }

        if (x < getWidth() * STICK_ZONE && stickPointer < 0)
        {
            // Dynamic centre: the stick appears under the thumb instead of forcing an exact grab.
            stickPointer = pid;
            stickOriginX = x;
            stickOriginY = y;
            knobX = 0;
            knobY = 0;
            invalidate();
            return;
        }

        if (lookPointer < 0)
        {
            lookPointer = pid;
            lookLastX = x;
            lookLastY = y;
            lookMovedAt = SystemClock.uptimeMillis();
            removeCallbacks(lookSettle);
            postDelayed(lookSettle, 60);
        }
    }

    private void touchMove(int pid, float x, float y)
    {
        if (pid == stickPointer)
        {
            moveStick(x, y);
            return;
        }

        Button b = findByPointer(pid);
        if (b != null)
        {
            if (!b.contains(x, y, getWidth(), getHeight()))
                release(b);
            return;
        }

        if (pid == lookPointer)
            moveLook(x, y);
    }

    private void touchUp(int pid)
    {
        release(findByPointer(pid));

        if (pid == stickPointer)
        {
            stickPointer = -1;
            knobX = 0;
            knobY = 0;
            stopStickKeys();

            if (mode == MODE_ANALOG)
            {
                NativeLib.analogFwd(0, 0);
                NativeLib.analogSide(0, 0);
            }

            invalidate();
            return;
        }

        if (pid == lookPointer)
        {
            removeCallbacks(lookSettle);
            releaseLookKeys();

            if (mode == MODE_ANALOG)
            {
                NativeLib.analogYaw(LOOK_MODE_MOUSE, 0, 0);
                NativeLib.analogPitch(LOOK_MODE_MOUSE, 0, 0);
            }

            lookPointer = -1;
        }
    }

    private void moveStick(float x, float y)
    {
        float r = stickRadius();
        float dx = (x - stickOriginX) / r;
        float dy = (y - stickOriginY) / r;

        float mag = (float) Math.hypot(dx, dy);
        if (mag > 1f)
        {
            dx /= mag;
            dy /= mag;
            mag = 1f;
        }

        knobX = dx * r;
        knobY = dy * r;
        invalidate();

        float fwd = -dy;
        float side = dx;

        if (mag < STICK_DEAD_ZONE)
        {
            fwd = 0;
            side = 0;
        }

        press(KEY_RUN, mag > 0.86f);

        if (mode == MODE_ANALOG)
        {
            NativeLib.analogFwd(fwd, fwd);
            NativeLib.analogSide(side, side);
            return;
        }

        // Keys mode: the stick angle picks the 1997 keys. Strafe only when not also moving forward,
        // otherwise a diagonal push would turn and strafe at once.
        boolean forward = fwd > 0.45f;
        boolean back = fwd < -0.45f;
        boolean strafeLeft = side < -0.62f && !forward && !back;
        boolean strafeRight = side > 0.62f && !forward && !back;

        press(KEY_FWD, forward);
        press(KEY_BACK, back);
        press(KEY_STRAFE_LEFT, strafeLeft);
        press(KEY_STRAFE_RIGHT, strafeRight);
        press(KEY_TURN_LEFT, side < -0.45f && !strafeLeft);
        press(KEY_TURN_RIGHT, side > 0.45f && !strafeRight);
    }

    private void moveLook(float x, float y)
    {
        float dx = x - lookLastX;
        float dy = y - lookLastY;
        lookLastX = x;
        lookLastY = y;
        lookMovedAt = SystemClock.uptimeMillis();

        if (mode == MODE_ANALOG)
        {
            float yaw = -dx / getWidth() * lookSensitivity;
            float pitch = (invertPitch ? dy : -dy) / getHeight() * lookSensitivity;
            NativeLib.analogYaw(LOOK_MODE_MOUSE, yaw, yaw);
            NativeLib.analogPitch(LOOK_MODE_MOUSE, pitch, pitch);
            return;
        }

        float fx = dx / getWidth();

        if (fx > LOOK_DEAD_ZONE)
        {
            keyDown(KEY_LOOK_RIGHT);
            keyUp(KEY_LOOK_LEFT);
        }
        else if (fx < -LOOK_DEAD_ZONE)
        {
            keyDown(KEY_LOOK_LEFT);
            keyUp(KEY_LOOK_RIGHT);
        }
        else
        {
            keyUp(KEY_LOOK_LEFT);
            keyUp(KEY_LOOK_RIGHT);
        }

        float fy = dy / getHeight();
        float vertical = invertPitch ? fy : -fy;

        if (vertical > LOOK_DEAD_ZONE)
        {
            keyDown(KEY_LOOK_UP);
            keyUp(KEY_LOOK_DOWN);
        }
        else if (vertical < -LOOK_DEAD_ZONE)
        {
            keyDown(KEY_LOOK_DOWN);
            keyUp(KEY_LOOK_UP);
        }
        else
        {
            keyUp(KEY_LOOK_UP);
            keyUp(KEY_LOOK_DOWN);
        }
    }

    private void press(int keycode, boolean on)
    {
        if (on)
            keyDown(keycode);
        else
            keyUp(keycode);
    }

    private Button findByPointer(int pointerId)
    {
        for (Button b : BUTTONS)
        {
            if (b.pointer == pointerId)
                return b;
        }
        return null;
    }

    private void press(Button b, int pointerId)
    {
        if (b == null)
            return;

        if (b.pointer >= 0)
            release(b);

        if (b.holdToFire)
        {
            long now = SystemClock.uptimeMillis();
            if (now < menuBlockedUntil)
                return;

            b.pointer = pointerId;
            held.add(b);
            menuPending = b;
            menuArmed = false;
            // The cooldown covers the hold itself, so hammering the button posts no ESC at all.
            menuBlockedUntil = now + MENU_HOLD_MS + MENU_COOLDOWN_MS;
            postDelayed(menuArm, MENU_HOLD_MS);
            invalidate();
            return;
        }

        b.pointer = pointerId;
        held.add(b);
        keyDown(b.keycode);
        invalidate();
    }

    private void release(Button b)
    {
        if (b == null || b.pointer < 0)
            return;

        b.pointer = -1;
        held.remove(b);

        if (b.holdToFire)
        {
            removeCallbacks(menuArm);
            if (menuPending == b)
            {
                if (menuArmed)
                    keyUp(MENU_KEY);
                menuPending = null;
                menuArmed = false;
            }
            invalidate();
            return;
        }

        keyUp(b.keycode);
        invalidate();
    }

    private void releaseLookKeys()
    {
        keyUp(KEY_LOOK_LEFT);
        keyUp(KEY_LOOK_RIGHT);
        keyUp(KEY_LOOK_UP);
        keyUp(KEY_LOOK_DOWN);
    }

    private void stopStickKeys()
    {
        keyUp(KEY_FWD);
        keyUp(KEY_BACK);
        keyUp(KEY_TURN_LEFT);
        keyUp(KEY_TURN_RIGHT);
        keyUp(KEY_STRAFE_LEFT);
        keyUp(KEY_STRAFE_RIGHT);
        keyUp(KEY_RUN);
    }

    private void stopAll()
    {
        removeCallbacks(lookSettle);
        releaseLookKeys();
        lookPointer = -1;
        stickPointer = -1;
        knobX = 0;
        knobY = 0;
        stopStickKeys();

        if (mode == MODE_ANALOG)
        {
            NativeLib.analogFwd(0, 0);
            NativeLib.analogSide(0, 0);
            NativeLib.analogYaw(LOOK_MODE_MOUSE, 0, 0);
            NativeLib.analogPitch(LOOK_MODE_MOUSE, 0, 0);
        }

        removeCallbacks(menuArm);
        menuPending = null;
        menuArmed = false;

        while (!held.isEmpty())
            release(held.get(held.size() - 1));

        invalidate();
    }

    private void keyDown(int keycode)
    {
        if (downKeys.add(keycode))
            SDLActivity.onNativeKeyDown(keycode);
    }

    private void keyUp(int keycode)
    {
        if (downKeys.remove(keycode))
            SDLActivity.onNativeKeyUp(keycode);
    }

    @Override
    protected void onDetachedFromWindow()
    {
        stopAll();
        super.onDetachedFromWindow();
    }
}
