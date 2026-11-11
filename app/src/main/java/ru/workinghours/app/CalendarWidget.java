package ru.workinghours.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/** Пиксельный календарь на главном экране. Данные дней пишет приложение (saveWidget). */
public class CalendarWidget extends AppWidgetProvider {

    static final String PREV = "ru.workinghours.app.WIDGET_PREV";
    static final String NEXT = "ru.workinghours.app.WIDGET_NEXT";

    static final String[] MONTHS = {"ЯНВАРЬ", "ФЕВРАЛЬ", "МАРТ", "АПРЕЛЬ", "МАЙ", "ИЮНЬ",
            "ИЮЛЬ", "АВГУСТ", "СЕНТЯБРЬ", "ОКТЯБРЬ", "НОЯБРЬ", "ДЕКАБРЬ"};
    static final String[] WD = {"ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"};

    static final String[] HOUSE = {"......rr....", "....rrrrrc..", "...rrrrrrc..", "..rrrrrrrrr.", ".rrrrrrrrrrr", "rrrrrrrrrrrr", ".wwwwwwwwww.", ".wbbwwwwbbw.", ".wbbwwwwbbw.", ".wwwwddwwww.", ".wwwwddwwww.", ".kkkkkkkkkk."};
    static final String[] CASE = {"....kkkk....", "...k....k...", "kkkkkkkkkkkk", "khmmmmmmmmhk", "kmmmmmmmmmmk", "kddddyyddddk", "kmmmmyymmmmk", "kmmmmmmmmmmk", "kmmmmmmmmmmk", "kkkkkkkkkkkk"};
    static final String[] CROSS = {"kk......kk", "krk....krk", ".krk..krk.", "..krkkrk..", "...krrk...", "...krrk...", "..krkkrk..", ".krk..krk.", "krk....krk", "kk......kk"};
    static final String[] PALM = {"...GG..GG...", "..GggGGggG..", ".Gg..gg..gG.", "G....cb....G", ".....bb.....", "......bb....", "......bb....", ".....bb.....", ".....bb.....", "..sssbbsss..", ".ssssssssss.", "SSSSSSSSSSSS"};
    static final String[] LEFT = {"....gg", "...ggg", "..ggg.", ".ggg..", "ggg...", "ggg...", ".ggg..", "..ggg.", "...ggg", "....gg"};

    static final String P_HOUSE = "r#e8262f c#8d111a w#ffffff b#2aa6c9 d#2aa6c9 k#6b3a2a";
    static final String P_CASE_RED = "k#5a0a10 m#b01923 h#e04a52 d#7a0d14 y#f4c542";
    static final String P_CROSS = "k#5a0a10 r#ff2a36";
    static final String P_PALM = "G#6ee87a g#13762a b#8a5a2b c#3e230c s#f6dc96 S#d9a94a";

    static final int BG = 0xFF1F2224;
    static Typeface font;

    // ---------- lifecycle ----------

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        update(c, m, id);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (int id : ids) e.remove("off_" + id);
        e.apply();
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i.getAction();
        if (PREV.equals(a) || NEXT.equals(a)) {
            int id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (id < 0) return;
            SharedPreferences p = prefs(c);
            int off = p.getInt("off_" + id, 0) + (PREV.equals(a) ? -1 : 1);
            p.edit().putInt("off_" + id, off).apply();
            update(c, AppWidgetManager.getInstance(c), id);
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("widget", Context.MODE_PRIVATE);
    }

    static void saveData(Context c, String json) {
        prefs(c).edit().putString("data", json).apply();
        updateAll(c);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, CalendarWidget.class));
        for (int id : ids) update(c, m, id);
    }

    // ---------- render ----------

    static void update(Context c, AppWidgetManager m, int id) {
        try {
            doUpdate(c, m, id);
        } catch (Exception ignored) {
        }
    }

    static void doUpdate(Context c, AppWidgetManager m, int id) {
        SharedPreferences p = prefs(c);
        int off = p.getInt("off_" + id, 0);

        Calendar now = Calendar.getInstance();
        int ty = now.get(Calendar.YEAR), tm = now.get(Calendar.MONTH), td = now.get(Calendar.DAY_OF_MONTH);
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(ty, tm, 1);
        cal.add(Calendar.MONTH, off);
        int y = cal.get(Calendar.YEAR), mo = cal.get(Calendar.MONTH);
        int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // понедельник = 0
        int weeks = (first + days + 6) / 7;
        String ym = String.format(Locale.US, "%04d-%02d", y, mo + 1);
        int monthCmp = Integer.compare(y * 12 + mo, ty * 12 + tm);

        JSONArray arr = null;
        try {
            JSONObject o = new JSONObject(p.getString("data", "{}"));
            JSONObject ms = o.optJSONObject("m");
            if (ms != null) arr = ms.optJSONArray(ym);
        } catch (Exception ignored) {
        }

        Bundle opt = m.getAppWidgetOptions(id);
        int wDp = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int hDp = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (wDp < 150) wDp = 300;
        if (hDp < 150) hDp = 320;
        int gridWdp = Math.max(120, wDp - 16);
        int gridHdp = Math.max(100, hDp - 16 - 36 - 24);
        float dens = c.getResources().getDisplayMetrics().density;
        // ограничиваем размер картинки, чтобы виджет точно передался системе
        float s = (float) Math.min(dens, Math.sqrt(230000.0 / (gridWdp * gridHdp)));
        s = Math.max(1f, s);

        int gw = Math.round(gridWdp * s), gh = Math.round(gridHdp * s);
        Bitmap grid = drawGrid(c, gw, gh, s, weeks, first, days, arr, monthCmp, td);
        Bitmap wd = drawWeekdays(c, gw, Math.round(16 * s), s);
        Bitmap title = drawTitle(c, MONTHS[mo] + " " + y, Math.round((gridWdp - 80) * s), Math.round(36 * s), s);
        Bitmap prev = drawArrow(false, s);
        Bitmap next = drawArrow(true, s);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_calendar);
        v.setImageViewBitmap(R.id.w_grid, grid);
        v.setImageViewBitmap(R.id.w_wd, wd);
        v.setImageViewBitmap(R.id.w_title, title);
        v.setImageViewBitmap(R.id.w_prev, prev);
        v.setImageViewBitmap(R.id.w_next, next);

        v.setOnClickPendingIntent(R.id.w_prev, broadcast(c, id, PREV));
        v.setOnClickPendingIntent(R.id.w_next, broadcast(c, id, NEXT));
        v.setOnClickPendingIntent(R.id.w_title, openApp(c, id * 100 + 99, "wh://month/" + ym));

        String pkg = c.getPackageName();
        for (int r = 0; r < 6; r++) {
            int rid = c.getResources().getIdentifier("r" + r, "id", pkg);
            if (rid != 0) v.setViewVisibility(rid, r < weeks ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < 42; i++) {
            int cid = c.getResources().getIdentifier("c" + i, "id", pkg);
            if (cid == 0) continue;
            int d = i - first + 1;
            String uri = (d >= 1 && d <= days)
                    ? "wh://day/" + ym + String.format(Locale.US, "-%02d", d)
                    : "wh://month/" + ym;
            v.setOnClickPendingIntent(cid, openApp(c, id * 100 + i, uri));
        }
        m.updateAppWidget(id, v);
    }

    static PendingIntent broadcast(Context c, int id, String action) {
        Intent i = new Intent(c, CalendarWidget.class);
        i.setAction(action);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.setData(Uri.parse("wh://nav/" + id + "/" + action));
        return PendingIntent.getBroadcast(c, id * 10 + (PREV.equals(action) ? 1 : 2), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openApp(Context c, int req, String uri) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(uri));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---------- drawing helpers ----------

    static Typeface font(Context c) {
        if (font == null) {
            try {
                font = Typeface.createFromAsset(c.getAssets(), "fonts/PressStart2P-Regular.ttf");
            } catch (Exception e) {
                font = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
            }
        }
        return font;
    }

    static Paint textPaint(Context c, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(font(c));
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    static int col(String hex) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return 0xFFF0B404;
        }
    }

    static void rect(Canvas cv, Paint p, float l, float t, float r, float b, int color) {
        p.setColor(color);
        cv.drawRect(l, t, r, b, p);
    }

    /** Рисует пиксельную картинку: rows — строки символов, pal — "ключ#цвет ключ#цвет". */
    static void sprite(Canvas cv, String[] rows, String pal, float x, float y, float ps) {
        Paint p = new Paint();
        String[] parts = pal.split(" ");
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int k = 0; k < row.length(); k++) {
                char ch = row.charAt(k);
                if (ch == '.') continue;
                String hex = null;
                for (String part : parts) if (part.charAt(0) == ch) hex = part.substring(1);
                if (hex == null) continue;
                p.setColor(col(hex));
                cv.drawRect(x + k * ps, y + r * ps, x + (k + 1) * ps, y + (r + 1) * ps, p);
            }
        }
    }

    static String extraPal(String hex) {
        String h = hex == null ? "" : hex.toLowerCase(Locale.US);
        if (h.equals("#9146e0")) return "k#2e0f52 m#5e23a0 h#c08cf5 d#45177a y#ffffff";
        if (h.equals("#f062ab")) return "k#6a1440 m#c2357c h#ffa8d3 d#8f1f57 y#ffffff";
        return "k#6a4b00 m#c99200 h#ffd75a d#8a6300 y#ffffff";
    }

    static String comboPal(String hex) {
        String h = hex == null ? "#f0b404" : hex;
        String hi = extraPal(h).split(" ")[2].substring(1);
        return "k#3b0a0a m" + h + " h" + hi + " d#a8131c y#ffffff";
    }

    static String fh(double h) {
        if (h == Math.floor(h)) return ((long) h) + "ч";
        return String.valueOf(h).replace('.', ',') + "ч";
    }

    static Bitmap drawGrid(Context c, int W, int H, float s, int weeks, int first, int days,
                           JSONArray arr, int monthCmp, int today) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(b);
        cv.drawColor(0xFF141617);
        Paint p = new Paint();
        float gap = Math.max(2f, 3 * s);
        float cw = (W - 6 * gap) / 7f;
        float ch = (H - (weeks - 1) * gap) / weeks;
        float u = Math.max(1f, Math.round(1.5f * s));
        float numSize = Math.min(cw * 0.30f, ch * 0.24f);
        float hrsSize = numSize * 0.62f;

        for (int d = 1; d <= days; d++) {
            int idx = first + d - 1;
            int r = idx / 7, k = idx % 7;
            float l = k * (cw + gap), t = r * (ch + gap), rr = l + cw, bb = t + ch;

            String type = "pre";
            double hours = 0;
            String xc = "#f0b404";
            boolean note = false;
            if (arr != null && d - 1 < arr.length()) {
                JSONArray e = arr.optJSONArray(d - 1);
                if (e != null) {
                    type = e.optString(0, "pre");
                    hours = e.optDouble(1, 0);
                    xc = e.optString(2, "#f0b404");
                    note = e.optInt(3, 0) == 1;
                }
            }
            boolean isToday = monthCmp == 0 && d == today;
            boolean past = monthCmp < 0 || (monthCmp == 0 && d < today);

            if (isToday) {
                rect(cv, p, l, t, rr, bb, Color.WHITE);
                l += u * 1.5f; t += u * 1.5f; rr -= u * 1.5f; bb -= u * 1.5f;
            }

            int base;
            switch (type) {
                case "work": base = 0xFFD62230; break;
                case "off": base = 0xFF22B33A; break;
                case "vac": base = 0xFF38B2E6; break;
                case "none": base = 0xFF454B4F; break;
                case "extra": base = col(xc); break;
                case "combo": base = 0xFFD62230; break;
                default: base = 0xFF2C3134;
            }
            rect(cv, p, l, t, rr, bb, base);
            if ("combo".equals(type)) rect(cv, p, (l + rr) / 2f, t, rr, bb, col(xc));
            // объём: светлая полоса сверху, тень снизу
            p.setColor(Color.argb(70, 255, 255, 255));
            cv.drawRect(l, t, rr, t + u, p);
            p.setColor(Color.argb(80, 0, 0, 0));
            cv.drawRect(l, bb - u * 1.5f, rr, bb, p);
            // пиксельные уголки
            p.setColor(isToday ? Color.WHITE : 0xFF141617);
            cv.drawRect(l, t, l + u, t + u, p);
            cv.drawRect(rr - u, t, rr, t + u, p);
            cv.drawRect(l, bb - u, l + u, bb, p);
            cv.drawRect(rr - u, bb - u, rr, bb, p);

            float cx = (l + rr) / 2f;
            boolean pre = "pre".equals(type);
            Paint np = textPaint(c, numSize, pre ? 0xFF7B847E : Color.WHITE);
            if (!pre) np.setShadowLayer(0.1f, u, u, 0x59000000);
            cv.drawText(String.valueOf(d), cx, t + u * 2 + numSize, np);

            boolean showHours = hours > 0 && !"off".equals(type) && !"none".equals(type)
                    && !"vac".equals(type) && !pre;
            if (showHours) {
                Paint hp = textPaint(c, hrsSize, Color.WHITE);
                cv.drawText(fh(hours), cx, t + u * 2 + numSize + hrsSize * 1.7f, hp);
            }

            if (!pre) {
                String[] spr;
                String pal;
                switch (type) {
                    case "off": spr = HOUSE; pal = P_HOUSE; break;
                    case "vac": spr = PALM; pal = P_PALM; break;
                    case "none": spr = CROSS; pal = P_CROSS; break;
                    case "extra": spr = CASE; pal = extraPal(xc); break;
                    case "combo": spr = CASE; pal = comboPal(xc); break;
                    default: spr = CASE; pal = P_CASE_RED;
                }
                int sw = spr[0].length(), sh = spr.length;
                float ps = Math.max(1f, (float) Math.floor(Math.min(cw * 0.5f / sw, ch * 0.32f / sh)));
                float iy = bb - u * 2.5f - sh * ps;
                sprite(cv, spr, pal, cx - sw * ps / 2f, iy, ps);
            }

            if (note) {
                p.setColor(Color.WHITE);
                cv.drawRect(rr - u * 3.5f, t + u * 2, rr - u * 1.5f, t + u * 4, p);
            }

            if (past) {
                p.setColor(Color.argb(105, 0, 0, 0));
                cv.drawRect(l, t, rr, bb, p);
            }
        }
        return b;
    }

    static Bitmap drawWeekdays(Context c, int W, int H, float s) {
        Bitmap b = Bitmap.createBitmap(W, Math.max(8, H), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float gap = Math.max(2f, 3 * s);
        float cw = (W - 6 * gap) / 7f;
        float size = Math.min(H * 0.6f, cw * 0.28f);
        for (int k = 0; k < 7; k++) {
            Paint p = textPaint(c, size, k >= 5 ? 0xFFC98A8F : 0xFF98A29B);
            float cx = k * (cw + gap) + cw / 2f;
            cv.drawText(WD[k], cx, (H + size) / 2f, p);
        }
        return b;
    }

    static Bitmap drawTitle(Context c, String text, int W, int H, float s) {
        W = Math.max(60, W);
        Bitmap b = Bitmap.createBitmap(W, Math.max(12, H), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float size = H * 0.42f;
        Paint p = textPaint(c, size, Color.WHITE);
        while (p.measureText(text) > W * 0.96f && size > 6) {
            size -= 1;
            p.setTextSize(size);
        }
        p.setShadowLayer(0.1f, Math.max(1, s * 1.5f), Math.max(1, s * 1.5f), 0xFF0C0D0E);
        cv.drawText(text, W / 2f, (H + size) / 2f, p);
        return b;
    }

    static Bitmap drawArrow(boolean right, float s) {
        float ps = Math.max(2f, Math.round(2.4f * s));
        int w = Math.round(6 * ps), h = Math.round(10 * ps);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        String[] rows = LEFT;
        if (right) {
            rows = new String[LEFT.length];
            for (int i = 0; i < LEFT.length; i++) rows[i] = new StringBuilder(LEFT[i]).reverse().toString();
        }
        sprite(cv, rows, "g#22b33a", 0, 0, ps);
        return b;
    }
}
