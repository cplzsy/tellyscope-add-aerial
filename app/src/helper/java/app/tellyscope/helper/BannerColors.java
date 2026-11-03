// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.helper;

/**
 * The two colours of a letter tile for an app that only ships a TV banner: the banner's main
 * colour behind the letter, and its accent for the letter itself. Pure pixel arithmetic, so the
 * app's unit tests run it too.
 */
public final class BannerColors {
    /** WCAG's minimum for large text: below it the letter is swapped for white or black. */
    static final double MIN_CONTRAST = 3.0;

    private BannerColors() {}

    /**
     * {@code {background, accent}} as opaque ARGB, from a banner's ARGB pixels, or null when it has
     * none that aren't see-through.
     */
    public static int[] pick(int[] pixels) {
        // 4 bits a channel: close shades of one colour count together, and a bucket's colour is
        // the average of its pixels.
        int[] count = new int[4096];
        long[] r = new long[4096], g = new long[4096], b = new long[4096];
        int opaque = 0;
        for (int p : pixels) {
            if ((p >>> 24) < 128) continue;
            int pr = (p >> 16) & 0xff, pg = (p >> 8) & 0xff, pb = p & 0xff;
            int k = (pr >> 4) << 8 | (pg >> 4) << 4 | (pb >> 4);
            count[k]++;
            r[k] += pr;
            g[k] += pg;
            b[k] += pb;
            opaque++;
        }
        if (opaque == 0) return null;
        int main = 0;
        for (int k = 1; k < 4096; k++) if (count[k] > count[main]) main = k;
        int bg = average(count, r, g, b, main);

        // The accent: the most colourful shade that covers a real part of the banner and stands
        // apart from the background. Grey-only artwork falls back to its most common other shade.
        int min = Math.max(1, opaque / 200);
        int accent = 0, best = -1;
        double bestScore = 0;
        int common = -1;
        for (int k = 0; k < 4096; k++) {
            if (count[k] < min || k == main) continue;
            int c = average(count, r, g, b, k);
            if (contrast(c, bg) < 1.5) continue;
            if (common < 0 || count[k] > count[common]) common = k;
            double score = chroma(c);
            if (score > bestScore) {
                bestScore = score;
                best = k;
                accent = c;
            }
        }
        if (best < 0 || bestScore < 0.15) accent = common < 0 ? bg : average(count, r, g, b, common);
        return new int[] {bg, readable(accent, bg)};
    }

    /** [accent] when it reads on [bg], otherwise white or black, whichever reads better. */
    static int readable(int accent, int bg) {
        if (contrast(accent, bg) >= MIN_CONTRAST) return accent;
        return contrast(0xffffffff, bg) >= contrast(0xff000000, bg) ? 0xffffffff : 0xff000000;
    }

    /** WCAG contrast ratio, 1 to 21. */
    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int c) {
        return 0.2126 * linear((c >> 16) & 0xff) + 0.7152 * linear((c >> 8) & 0xff) + 0.0722 * linear(c & 0xff);
    }

    private static double linear(int v) {
        double s = v / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** How vivid, 0 for greys: unlike saturation, a near-black tint scores low. */
    private static double chroma(int c) {
        int max = Math.max((c >> 16) & 0xff, Math.max((c >> 8) & 0xff, c & 0xff));
        int min = Math.min((c >> 16) & 0xff, Math.min((c >> 8) & 0xff, c & 0xff));
        return (max - min) / 255.0;
    }

    private static int average(int[] count, long[] r, long[] g, long[] b, int k) {
        int n = count[k];
        return 0xff000000 | (int) (r[k] / n) << 16 | (int) (g[k] / n) << 8 | (int) (b[k] / n);
    }
}
