package com.retro.explorer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/** Shared vector-drawing helpers. All icons are drawn on a fixed design grid and scaled to their bounds. */
abstract class VectorIcon extends Drawable {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float grid;

    VectorIcon(float grid) { this.grid = grid; }

    @Override
    public final void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / grid;
        c.save();
        c.translate(b.left + (b.width() - grid * s) / 2f, b.top + (b.height() - grid * s) / 2f);
        c.scale(s, s);
        paint(c);
        c.restore();
    }

    abstract void paint(Canvas c);

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    static Path poly(float... xy) {
        Path pt = new Path();
        pt.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) pt.lineTo(xy[i], xy[i + 1]);
        pt.close();
        return pt;
    }

    static Path open(float... xy) {
        Path pt = new Path();
        pt.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) pt.lineTo(xy[i], xy[i + 1]);
        return pt;
    }

    static Path rect(float l, float t, float r, float b, float rad) {
        Path pt = new Path();
        pt.addRoundRect(new RectF(l, t, r, b), rad, rad, Path.Direction.CW);
        return pt;
    }

    static Path circle(float x, float y, float r) {
        Path pt = new Path();
        pt.addCircle(x, y, r, Path.Direction.CW);
        return pt;
    }

    void fill(Canvas c, Path pt, int color) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(pt, p);
    }

    void grad(Canvas c, Path pt, float y0, float y1, int... cols) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000);
        p.setShader(new LinearGradient(0, y0, 0, y1, cols, null, Shader.TileMode.CLAMP));
        c.drawPath(pt, p);
        p.setShader(null);
    }

    void stroke(Canvas c, Path pt, int color, float w) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        c.drawPath(pt, p);
    }

    void line(Canvas c, float x1, float y1, float x2, float y2, int color, float w) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        c.drawLine(x1, y1, x2, y2, p);
    }

    void text(Canvas c, String t, float x, float y, float size, int color) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(t, x, y, p);
    }
}

/** Large file/folder icons: glossy Windows style, or Snaptu-style coloured tiles. */
final class IconDrawable extends VectorIcon {
    private final int kind;
    private final boolean snap, xp;
    private final String ext;

    IconDrawable(int kind, String theme, String ext) {
        super(48);
        this.kind = kind;
        this.snap = "snaptu".equals(theme);
        this.xp = "xp".equals(theme);
        this.ext = ext == null ? "" : ext;
    }

    @Override
    void paint(Canvas c) {
        if (snap) drawSnap(c); else drawWin(c);
    }

    private void page(Canvas c) {
        Path pg = poly(10, 3.5f, 29.5f, 3.5f, 39, 13, 39, 44.5f, 10, 44.5f);
        grad(c, pg, 3, 45, 0xFFFFFFFF, 0xFFE3E9F1);
        stroke(c, pg, 0xFF8796A8, 1);
        Path fold = poly(29.5f, 3.5f, 29.5f, 13, 39, 13);
        fill(c, fold, 0xFFDFE6EE);
        stroke(c, fold, 0xFF8796A8, 1);
    }

    private void label(Canvas c, String t, int color) {
        fill(c, rect(7, 27, 35, 38, 1.5f), color);
        text(c, t, 21, 35.5f, 8, 0xFFFFFFFF);
    }

    private void drawWin(Canvas c) {
        int fs = xp ? 0xFFB48B33 : 0xFFC08F2F;
        switch (kind) {
            case FileOps.K_FOLDER: {
                Path back = poly(3.5f, 9, 17, 9, 20.5f, 12.5f, 43, 12.5f, 44.5f, 14, 44.5f, 34, 3.5f, 34);
                if (xp) grad(c, back, 9, 34, 0xFFECC96F, 0xFFD3A53B);
                else grad(c, back, 9, 34, 0xFFF7D374, 0xFFDEA63A);
                stroke(c, back, fs, 0.9f);
                if (!xp) {
                    Path paper = rect(8, 13, 39, 28, 0);
                    fill(c, paper, 0xFFFFFFFF);
                    stroke(c, paper, 0xFFC9C9C9, 0.7f);
                    line(c, 11, 17, 32, 17, 0xFFBBBBBB, 0.9f);
                    line(c, 11, 20.5f, 28, 20.5f, 0xFFBBBBBB, 0.9f);
                }
                Path front = poly(2.5f, 18.5f, 45.5f, 18.5f, 42.8f, 39.5f, 5.2f, 39.5f);
                if (xp) grad(c, front, 18.5f, 39.5f, 0xFFFDEBA9, 0xFFF0C759, 0xFFE9B947);
                else grad(c, front, 18.5f, 39.5f, 0xFFFFF4C6, 0xFFF9D46C, 0xFFEFB63C);
                stroke(c, front, fs, 0.9f);
                if (!xp) fill(c, poly(4, 20, 44, 20, 43.4f, 24, 4.6f, 24), 0x73FFFFFF);
                break;
            }
            case FileOps.K_DRIVE: {
                Path top = poly(6, 17, 42, 17, 46, 29, 2, 29);
                fill(c, top, 0xFFE8EDF3);
                stroke(c, top, 0xFF7B8796, 1);
                Path body = rect(2, 29, 46, 39, 2);
                grad(c, body, 29, 39, 0xFFF4F6F9, 0xFFCFD6DF, 0xFF9AA6B4);
                stroke(c, body, 0xFF6F7B8A, 1);
                fill(c, rect(34, 32.5f, 42, 35.5f, 1), 0xFF4FD14F);
                line(c, 6, 34, 28, 34, 0xFF7B8796, 1.2f);
                break;
            }
            case FileOps.K_SD: {
                Path card = poly(12, 4, 32, 4, 38, 10, 38, 44, 12, 44);
                grad(c, card, 4, 44, 0xFF4A5A6C, 0xFF2C3846);
                stroke(c, card, 0xFF1C242E, 1);
                for (int i = 0; i < 5; i++) fill(c, rect(15 + i * 4, 6, 17.5f + i * 4, 13, 0.5f), 0xFFE8C04A);
                fill(c, rect(15, 24, 35, 36, 1.5f), 0xFFFFFFFF);
                text(c, "SD", 25, 33.5f, 9, 0xFF2C3846);
                break;
            }
            case FileOps.K_BIN:
            case FileOps.K_BINFULL: {
                if (kind == FileOps.K_BINFULL) {
                    Path a = poly(14, 12, 20, 5, 25, 9, 31, 6, 35, 12);
                    fill(c, a, 0xFFFFFFFF);
                    stroke(c, a, 0xFF999999, 0.7f);
                    Path b = poly(18, 11, 27, 5, 30, 11);
                    fill(c, b, 0xFFF3E3A2);
                }
                Path body = poly(10.5f, 13, 37.5f, 13, 34.5f, 43.5f, 13.5f, 43.5f);
                grad(c, body, 13, 44, 0xFFF0F6FC, 0xFFB9CDE1);
                stroke(c, body, 0xFF6D8299, 1);
                Path rim = new Path();
                rim.addOval(new RectF(10.5f, 10, 37.5f, 16), Path.Direction.CW);
                fill(c, rim, 0xFFE6EEF7);
                stroke(c, rim, 0xFF6D8299, 1);
                line(c, 17, 18, 18.5f, 40, 0xFF8DA3BB, 1.1f);
                line(c, 24, 18.5f, 24, 40.5f, 0xFF8DA3BB, 1.1f);
                line(c, 31, 18, 29.5f, 40, 0xFF8DA3BB, 1.1f);
                break;
            }
            case FileOps.K_STAR: {
                Path s = poly(24, 4, 30, 17, 44, 18.5f, 33.5f, 28, 36.5f, 42, 24, 35, 11.5f, 42, 14.5f, 28, 4, 18.5f, 18, 17);
                fill(c, s, 0xFFFFD34D);
                stroke(c, s, 0xFFD39B12, 1);
                break;
            }
            case FileOps.K_TEXT:
                page(c);
                for (float y : new float[]{19, 23.5f, 28, 32.5f}) line(c, 15, y, 34, y, 0xFF8A99AD, 1.5f);
                line(c, 15, 37, 27, 37, 0xFF8A99AD, 1.5f);
                break;
            case FileOps.K_CODE:
                page(c);
                stroke(c, open(20, 22, 14, 28, 20, 34), 0xFF2F6FD0, 2.4f);
                stroke(c, open(28, 22, 34, 28, 28, 34), 0xFF2F6FD0, 2.4f);
                break;
            case FileOps.K_IMAGE: {
                Path fr = rect(5, 8, 43, 40, 1.5f);
                fill(c, fr, 0xFFFFFFFF);
                stroke(c, fr, 0xFF8796A8, 1);
                fill(c, rect(8, 11, 40, 35, 0), 0xFF8FD0FF);
                fill(c, poly(8, 35, 18, 23, 25, 31, 30, 26, 40, 35), 0xFF47A447);
                fill(c, circle(32, 17, 3.2f), 0xFFFFD84D);
                break;
            }
            case FileOps.K_AUDIO:
                page(c);
                stroke(c, open(22, 37, 22, 22, 33, 19, 33, 34), 0xFF8A3CB7, 2.2f);
                fill(c, circle(19, 37, 3.6f), 0xFF8A3CB7);
                fill(c, circle(30, 34, 3.6f), 0xFF8A3CB7);
                break;
            case FileOps.K_VIDEO:
                fill(c, rect(4, 9, 44, 39, 2), 0xFF2F3B4A);
                for (int i = 0; i < 4; i++) {
                    fill(c, rect(7, 12 + i * 6.5f, 10, 15 + i * 6.5f, 0.5f), 0xFF9AA6B4);
                    fill(c, rect(38, 12 + i * 6.5f, 41, 15 + i * 6.5f, 0.5f), 0xFF9AA6B4);
                }
                fill(c, poly(20, 17, 20, 31, 32, 24), 0xFFFFFFFF);
                break;
            case FileOps.K_ARCHIVE:
                page(c);
                fill(c, rect(21, 3.5f, 27, 44.5f, 0), 0xFFF2C94C);
                for (int i = 0; i < 6; i++) line(c, i % 2 == 0 ? 21 : 24, 8 + i * 3, i % 2 == 0 ? 24 : 27, 8 + i * 3, 0xFF7A5D16, 1.4f);
                fill(c, rect(20, 27, 28, 35, 1), 0xFFDDDDDD);
                stroke(c, rect(20, 27, 28, 35, 1), 0xFF666666, 0.8f);
                break;
            case FileOps.K_PDF: page(c); label(c, "PDF", 0xFFD32F2F); break;
            case FileOps.K_APK: page(c); label(c, "APK", 0xFF2EB872); break;
            case FileOps.K_DOC: page(c); label(c, "DOC", 0xFF2B5797); break;
            case FileOps.K_SHEET: page(c); label(c, "XLS", 0xFF1E7145); break;
            case FileOps.K_PPT: page(c); label(c, "PPT", 0xFFD24726); break;
            default:
                page(c);
                if (!ext.isEmpty()) {
                    String t = ext.length() > 4 ? ext.substring(0, 4) : ext;
                    text(c, t.toUpperCase(Locale.ROOT), 24, 34, t.length() > 3 ? 7 : 8.5f, 0xFF7A8899);
                }
        }
    }

    private static int snapColor(int k) {
        switch (k) {
            case FileOps.K_FOLDER: return 0xFFFF8A00;
            case FileOps.K_TEXT: return 0xFF29B6F6;
            case FileOps.K_CODE: return 0xFF7E57C2;
            case FileOps.K_IMAGE: return 0xFF43A047;
            case FileOps.K_AUDIO: return 0xFFEC407A;
            case FileOps.K_VIDEO: return 0xFFE53935;
            case FileOps.K_ARCHIVE: return 0xFFF9A825;
            case FileOps.K_PDF: return 0xFFC62828;
            case FileOps.K_APK: return 0xFF7CB342;
            case FileOps.K_DOC: return 0xFF1E88E5;
            case FileOps.K_SHEET: return 0xFF2E7D32;
            case FileOps.K_PPT: return 0xFFF4511E;
            case FileOps.K_DRIVE: return 0xFF00897B;
            case FileOps.K_SD: return 0xFF5E35B1;
            case FileOps.K_BIN: case FileOps.K_BINFULL: return 0xFF8D6E63;
            case FileOps.K_STAR: return 0xFFFFB300;
            default: return 0xFF78909C;
        }
    }

    private void drawSnap(Canvas c) {
        int col = snapColor(kind);
        int W = 0xFFFFFFFF;
        Path tile = rect(3, 3, 45, 45, 12);
        fill(c, tile, col);
        c.save();
        c.clipRect(3, 3, 45, 19);
        fill(c, tile, 0x38FFFFFF);
        c.restore();
        switch (kind) {
            case FileOps.K_FOLDER: {
                Path f = new Path();
                f.moveTo(12, 15); f.lineTo(20.5f, 15); f.lineTo(23.5f, 18); f.lineTo(36, 18); f.lineTo(37.5f, 19.5f);
                f.lineTo(37.5f, 33); f.lineTo(36, 34.5f); f.lineTo(12, 34.5f); f.lineTo(10.5f, 33); f.lineTo(10.5f, 16.5f); f.close();
                fill(c, f, W);
                break;
            }
            case FileOps.K_IMAGE:
                stroke(c, rect(11.5f, 14, 36.5f, 34, 2.5f), W, 2.6f);
                fill(c, poly(14, 32, 21, 24, 26, 29, 29, 26, 35, 32), W);
                fill(c, circle(30, 20, 2.6f), W);
                break;
            case FileOps.K_AUDIO:
                stroke(c, open(21, 33, 21, 18, 33, 15, 33, 30), W, 2.6f);
                fill(c, circle(18, 33, 3.8f), W);
                fill(c, circle(30, 30, 3.8f), W);
                break;
            case FileOps.K_VIDEO:
                fill(c, poly(19, 14.5f, 33, 24, 19, 33.5f), W);
                break;
            case FileOps.K_DRIVE:
                fill(c, rect(11, 18, 37, 30, 3), W);
                fill(c, circle(32, 24, 1.8f), col);
                break;
            case FileOps.K_SD:
                fill(c, poly(16, 12, 28, 12, 33, 17, 33, 36, 16, 36), W);
                break;
            case FileOps.K_BIN:
            case FileOps.K_BINFULL:
                fill(c, poly(16, 18, 32, 18, 30.4f, 35, 17.6f, 35), W);
                fill(c, rect(13.5f, 13.5f, 34.5f, 16.5f, 1.5f), W);
                fill(c, rect(21, 11, 27, 14, 1), W);
                break;
            case FileOps.K_STAR:
                fill(c, poly(24, 12, 27.6f, 19.6f, 35.8f, 20.5f, 29.7f, 26.1f, 31.4f, 34.3f, 24, 30.2f, 16.6f, 34.3f, 18.3f, 26.1f, 12.2f, 20.5f, 20.4f, 19.6f), W);
                break;
            default: {
                String t = ext.isEmpty() ? "?" : (ext.length() > 4 ? ext.substring(0, 4) : ext).toUpperCase(Locale.ROOT);
                text(c, t, 24, t.length() > 3 ? 28.5f : 29.5f, t.length() > 3 ? 10 : t.length() > 2 ? 12 : 14, W);
            }
        }
    }
}

/** Small toolbar glyphs, drawn on a 16-unit grid. */
final class GlyphDrawable extends VectorIcon {
    private final String g;
    private final int ink;

    GlyphDrawable(String g, int ink) {
        super(16);
        this.g = g;
        this.ink = ink;
    }

    private void folder(Canvas c, float y) {
        Path f = poly(1, y, 6, y, 7.5f, y + 1.5f, 14, y + 1.5f, 14, y + 9.5f, 1, y + 9.5f);
        fill(c, f, 0xFFF4C94F);
        stroke(c, f, 0xFFB58A2A, 0.8f);
    }

    private void sheet(Canvas c, float l, float t, float r, float b) {
        Path s = rect(l, t, r, b, 0.5f);
        fill(c, s, 0xFFFFFFFF);
        stroke(c, s, 0xFF5A6F8A, 1);
    }

    @Override
    void paint(Canvas c) {
        int green = 0xFF38A538, red = 0xFFD9342B, blue = 0xFF3B7DD8;
        switch (g) {
            case "newfolder":
                folder(c, 3.5f);
                fill(c, circle(12, 11.5f, 3.6f), green);
                line(c, 12, 9.6f, 12, 13.4f, 0xFFFFFFFF, 1.5f);
                line(c, 10.1f, 11.5f, 13.9f, 11.5f, 0xFFFFFFFF, 1.5f);
                break;
            case "newfile":
                sheet(c, 2.5f, 1.5f, 11.5f, 14.5f);
                line(c, 4.5f, 5, 9.5f, 5, 0xFF8AA0BB, 1);
                line(c, 4.5f, 7.5f, 9.5f, 7.5f, 0xFF8AA0BB, 1);
                fill(c, circle(12, 11.5f, 3.6f), green);
                line(c, 12, 9.6f, 12, 13.4f, 0xFFFFFFFF, 1.5f);
                line(c, 10.1f, 11.5f, 13.9f, 11.5f, 0xFFFFFFFF, 1.5f);
                break;
            case "cut":
                stroke(c, circle(4.5f, 12, 2.4f), 0xFFC0392B, 1.6f);
                stroke(c, circle(11.5f, 12, 2.4f), 0xFFC0392B, 1.6f);
                line(c, 6, 10.3f, 12, 1.5f, ink, 1.5f);
                line(c, 10, 10.3f, 4, 1.5f, ink, 1.5f);
                break;
            case "copy":
                sheet(c, 1.5f, 1.5f, 10.5f, 11.5f);
                sheet(c, 5.5f, 4.5f, 14.5f, 14.5f);
                line(c, 7.5f, 7.5f, 12.5f, 7.5f, 0xFF8AA0BB, 1);
                line(c, 7.5f, 10, 12.5f, 10, 0xFF8AA0BB, 1);
                break;
            case "paste": {
                Path b = rect(2, 2.5f, 12, 14.5f, 1);
                fill(c, b, 0xFFC99A52);
                stroke(c, b, 0xFF8C6528, 0.9f);
                fill(c, rect(5, 1, 9, 4, 1), 0xFFDDDDDD);
                sheet(c, 6.5f, 6, 14.5f, 15);
                break;
            }
            case "delete":
                line(c, 3.5f, 3.5f, 12.5f, 12.5f, red, 2.6f);
                line(c, 12.5f, 3.5f, 3.5f, 12.5f, red, 2.6f);
                break;
            case "rename":
                sheet(c, 1, 5, 15, 12);
                line(c, 3.5f, 7, 3.5f, 10, ink == 0xFFFFFFFF ? 0xFF1E395B : ink, 1.2f);
                line(c, 10, 3.5f, 10, 13.5f, blue, 1.3f);
                line(c, 8.5f, 3.5f, 11.5f, 3.5f, blue, 1.3f);
                line(c, 8.5f, 13.5f, 11.5f, 13.5f, blue, 1.3f);
                break;
            case "props":
                sheet(c, 2, 1.5f, 14, 14.5f);
                line(c, 4.5f, 5, 11.5f, 5, blue, 1.4f);
                line(c, 4.5f, 8, 11.5f, 8, blue, 1.4f);
                line(c, 4.5f, 11, 8.5f, 11, blue, 1.4f);
                break;
            case "view":
                for (float[] q : new float[][]{{1.5f, 1.5f}, {9, 1.5f}, {1.5f, 9}, {9, 9}}) {
                    Path s = rect(q[0], q[1], q[0] + 5.5f, q[1] + 5.5f, 0.5f);
                    fill(c, s, 0xFF6AA7E8);
                    stroke(c, s, 0xFF2F5F9A, 0.8f);
                }
                break;
            case "sort":
                line(c, 4, 2, 4, 14, blue, 1.6f);
                stroke(c, open(1.5f, 11.5f, 4, 14, 6.5f, 11.5f), blue, 1.6f);
                line(c, 9, 4, 15, 4, ink, 1.6f);
                line(c, 9, 8, 13.5f, 8, ink, 1.6f);
                line(c, 9, 12, 12, 12, ink, 1.6f);
                break;
            case "theme": {
                Path pal = circle(8, 8, 6.5f);
                fill(c, pal, 0xFFFFFFFF);
                stroke(c, pal, 0xFF6B5A3C, 1);
                fill(c, circle(5, 6, 1.4f), 0xFFE53935);
                fill(c, circle(8, 4.2f, 1.4f), 0xFFFBC02D);
                fill(c, circle(11, 5.8f, 1.4f), 0xFF43A047);
                fill(c, circle(4.8f, 9.7f, 1.4f), 0xFF1E88E5);
                break;
            }
            case "more":
                fill(c, circle(8, 3, 1.6f), ink);
                fill(c, circle(8, 8, 1.6f), ink);
                fill(c, circle(8, 13, 1.6f), ink);
                break;
            case "menu":
                line(c, 2, 4, 14, 4, ink, 1.8f);
                line(c, 2, 8, 14, 8, ink, 1.8f);
                line(c, 2, 12, 14, 12, ink, 1.8f);
                break;
            case "restore": {
                Path a = new Path();
                a.addArc(new RectF(3, 3, 13, 13), 200, 290);
                stroke(c, a, green, 1.9f);
                stroke(c, open(3, 2.5f, 3.3f, 6.3f, 7, 5.6f), green, 1.9f);
                break;
            }
            case "empty": {
                Path b = poly(3.5f, 4.5f, 12.5f, 4.5f, 11.5f, 14.5f, 4.5f, 14.5f);
                fill(c, b, 0xFFDBE7F3);
                stroke(c, b, 0xFF5A6F8A, 1);
                line(c, 2.5f, 4.5f, 13.5f, 4.5f, 0xFF5A6F8A, 1.4f);
                line(c, 6, 2.5f, 10, 2.5f, 0xFF5A6F8A, 1.4f);
                break;
            }
            case "up":
                folder(c, 5);
                line(c, 8, 13, 8, 7.5f, 0xFF2E7D32, 1.8f);
                stroke(c, open(5.5f, 10, 8, 7.5f, 10.5f, 10), 0xFF2E7D32, 1.8f);
                break;
            case "places":
                folder(c, 2);
                folder(c, 7);
                break;
            default:
                fill(c, circle(8, 8, 3), ink);
        }
    }
}

/** Round glossy Back / Forward / Up buttons. */
final class NavDrawable extends VectorIcon {
    private final String dir;
    private final int[] colors;
    private final boolean enabled;

    NavDrawable(String dir, int[] colors, boolean enabled) {
        super(32);
        this.dir = dir;
        this.colors = colors;
        this.enabled = enabled;
    }

    @Override
    void paint(Canvas c) {
        Path ci = circle(16, 16, 14.5f);
        int[] cols = enabled ? colors : new int[]{0xFFD5DAE0, 0xFFA3ADB9, 0xFF8793A1};
        if (cols.length >= 2) grad(c, ci, 1.5f, 30.5f, cols); else fill(c, ci, cols[0]);
        stroke(c, ci, enabled ? 0xFF1B3D66 : 0xFF7A8594, 1);
        Path gl = new Path();
        gl.addOval(new RectF(7, 3, 25, 15), Path.Direction.CW);
        fill(c, gl, 0x55FFFFFF);
        int w = 0xFFFFFFFF;
        float sw = 3.2f;
        if ("back".equals(dir)) {
            stroke(c, open(17.5f, 9, 10.5f, 16, 17.5f, 23), w, sw);
            line(c, 11.5f, 16, 22.5f, 16, w, sw);
        } else if ("fwd".equals(dir)) {
            stroke(c, open(14.5f, 9, 21.5f, 16, 14.5f, 23), w, sw);
            line(c, 20.5f, 16, 9.5f, 16, w, sw);
        } else {
            stroke(c, open(9, 15.5f, 16, 8.5f, 23, 15.5f), w, sw);
            line(c, 16, 9.5f, 16, 24, w, sw);
        }
    }
}
