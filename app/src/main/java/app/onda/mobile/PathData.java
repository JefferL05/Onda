package app.onda.mobile;

/**
 * SVG path-data parser: M L H V C S Q T A Z, absolute and relative.
 *
 * Deliberately free of Android types. Glyph draws the whole icon set from path
 * data, so this code runs on the very first frame of the app: a parser that
 * throws takes the process down before anything is visible. Keeping it plain
 * Java also means the grammar is covered by ordinary JVM tests.
 *
 * The first version made two wrong assumptions that real path data violates.
 * It required a space between every pair of numbers, but "l4.6-4.6" is two
 * numbers joined by the minus sign alone. And it assumed each command appeared
 * with a single parameter set, while "a6.6 6.6 0 1 1 0 13.2 6.6 6.6 0 0 1
 * 0-13.2" repeats the seven arc parameters inside one command. Both cases threw
 * NumberFormatException, which is what closed the app on launch.
 *
 * Rules kept from the spec: implicit command repetition, the implicit lineto
 * after a moveto, reflection of the previous control point for S and T, and
 * single-character large-arc/sweep flags. Malformed data stops parsing instead
 * of throwing, so a bad icon can never crash the app.
 */
final class PathData {

    /** Receives drawing commands; Glyph backs this with android.graphics.Path. */
    interface Sink {
        void moveTo(float x, float y);

        void lineTo(float x, float y);

        void cubicTo(float x1, float y1, float x2, float y2, float x, float y);

        void close();
    }

    private static final double HALF_PI = Math.PI / 2;
    private static final int CURVE_NONE = 0, CURVE_CUBIC = 1, CURVE_QUAD = 2;

    static void parse(String data, Sink sink) {
        if (data == null || data.isEmpty() || sink == null) return;
        new PathData(data, sink).run();
    }

    private final String data;
    private final int length;
    private final Sink sink;
    private int index;

    private float x, y;
    private float startX, startY;
    private char command;
    private int lastCurve;
    private float controlX, controlY;

    private PathData(String data, Sink sink) {
        this.data = data;
        this.length = data.length();
        this.sink = sink;
    }

    private void run() {
        while (true) {
            skipSeparators();
            if (index >= length) return;
            char c = data.charAt(index);
            if (isCommand(c)) {
                command = c;
                index++;
            } else if (command == 0) {
                return;  // numbers before any command: nothing to draw, no throw
            }
            if (command == 'Z' || command == 'z') {
                sink.close();
                x = startX;
                y = startY;
                lastCurve = CURVE_NONE;
                command = 0;
                continue;
            }
            int before = index;
            if (!oneSet(command)) return;
            if (index <= before) return;  // guarantee progress, never spin
        }
    }

    /** Executes one parameter set for the current command. */
    private boolean oneSet(char cmd) {
        boolean relative = Character.isLowerCase(cmd);
        switch (Character.toUpperCase(cmd)) {
            case 'M': {
                Float nx = number();
                Float ny = number();
                if (nx == null || ny == null) return false;
                x = relative ? x + nx : nx;
                y = relative ? y + ny : ny;
                startX = x;
                startY = y;
                sink.moveTo(x, y);
                lastCurve = CURVE_NONE;
                command = relative ? 'l' : 'L';  // implicit lineto after moveto
                return true;
            }
            case 'L': {
                Float nx = number();
                Float ny = number();
                if (nx == null || ny == null) return false;
                line(relative, nx, ny);
                return true;
            }
            case 'H': {
                Float nx = number();
                if (nx == null) return false;
                line(relative, nx, 0f);
                return true;
            }
            case 'V': {
                Float ny = number();
                if (ny == null) return false;
                line(relative, 0f, ny);
                return true;
            }
            case 'C': {
                Float[] a = numbers(6);
                if (a == null) return false;
                float x1 = a[0], y1 = a[1], x2 = a[2], y2 = a[3], ex = a[4], ey = a[5];
                if (relative) {
                    x1 += x; y1 += y; x2 += x; y2 += y; ex += x; ey += y;
                }
                cubic(x1, y1, x2, y2, ex, ey);
                return true;
            }
            case 'S': {
                Float[] a = numbers(4);
                if (a == null) return false;
                float x1 = lastCurve == CURVE_CUBIC ? 2 * x - controlX : x;
                float y1 = lastCurve == CURVE_CUBIC ? 2 * y - controlY : y;
                float x2 = a[0], y2 = a[1], ex = a[2], ey = a[3];
                if (relative) {
                    x2 += x; y2 += y; ex += x; ey += y;
                }
                cubic(x1, y1, x2, y2, ex, ey);
                return true;
            }
            case 'Q': {
                Float[] a = numbers(4);
                if (a == null) return false;
                float qx = a[0], qy = a[1], ex = a[2], ey = a[3];
                if (relative) {
                    qx += x; qy += y; ex += x; ey += y;
                }
                quadToCubic(qx, qy, ex, ey);
                return true;
            }
            case 'T': {
                Float[] a = numbers(2);
                if (a == null) return false;
                float qx = lastCurve == CURVE_QUAD ? 2 * x - controlX : x;
                float qy = lastCurve == CURVE_QUAD ? 2 * y - controlY : y;
                float ex = a[0], ey = a[1];
                if (relative) {
                    ex += x; ey += y;
                }
                quadToCubic(qx, qy, ex, ey);
                return true;
            }
            case 'A': {
                Float rx = number();
                Float ry = number();
                Float rotation = number();
                Float largeArc = flag();
                Float sweep = flag();
                Float ex = number();
                Float ey = number();
                if (rx == null || ry == null || rotation == null || largeArc == null
                        || sweep == null || ex == null || ey == null) return false;
                if (relative) {
                    ex += x;
                    ey += y;
                }
                arcTo(Math.abs(rx), Math.abs(ry), rotation, largeArc != 0f, sweep != 0f, ex, ey);
                return true;
            }
            default:
                return false;
        }
    }

    private void line(boolean relative, float dx, float dy) {
        float nx = relative ? x + dx : dx;
        float ny = relative ? y + dy : dy;
        sink.lineTo(nx, ny);
        x = nx;
        y = ny;
        lastCurve = CURVE_NONE;
    }

    private void cubic(float x1, float y1, float x2, float y2, float ex, float ey) {
        sink.cubicTo(x1, y1, x2, y2, ex, ey);
        x = ex;
        y = ey;
        controlX = x2;
        controlY = y2;
        lastCurve = CURVE_CUBIC;
    }

    private void quadToCubic(float qx, float qy, float ex, float ey) {
        float x1 = x + 2f / 3f * (qx - x);
        float y1 = y + 2f / 3f * (qy - y);
        float x2 = ex + 2f / 3f * (qx - ex);
        float y2 = ey + 2f / 3f * (qy - ey);
        sink.cubicTo(x1, y1, x2, y2, ex, ey);
        x = ex;
        y = ey;
        controlX = qx;
        controlY = qy;
        lastCurve = CURVE_QUAD;
    }

    /**
     * Elliptical arc as a chain of cubic segments, using the endpoint-to-centre
     * conversion from the SVG implementation notes. Honours the large-arc and
     * sweep flags, so both directions of travel draw correctly.
     */
    private void arcTo(float rx, float ry, float rotationDeg,
                       boolean largeArc, boolean sweep, float ex, float ey) {
        if (rx == 0f || ry == 0f) {
            line(false, ex, ey);
            return;
        }
        if (ex == x && ey == y) {
            lastCurve = CURVE_NONE;
            return;
        }
        double phi = Math.toRadians(rotationDeg % 360.0);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        double dx2 = (x - ex) / 2.0;
        double dy2 = (y - ey) / 2.0;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;

        double rx2 = (double) rx * rx;
        double ry2 = (double) ry * ry;
        double lambda = (x1p * x1p) / rx2 + (y1p * y1p) / ry2;
        if (lambda > 1.0) {
            double grow = Math.sqrt(lambda);
            rx *= (float) grow;
            ry *= (float) grow;
            rx2 = (double) rx * rx;
            ry2 = (double) ry * ry;
        }

        double numerator = rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p;
        double denominator = rx2 * y1p * y1p + ry2 * x1p * x1p;
        double factor = denominator <= 0 ? 0 : Math.sqrt(Math.max(0.0, numerator / denominator));
        if (largeArc == sweep) factor = -factor;
        double cxp = factor * rx * y1p / ry;
        double cyp = -factor * ry * x1p / rx;

        double cx = cosPhi * cxp - sinPhi * cyp + (x + ex) / 2.0;
        double cy = sinPhi * cxp + cosPhi * cyp + (y + ey) / 2.0;

        double ux = (x1p - cxp) / rx, uy = (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx, vy = (-y1p - cyp) / ry;
        double startAngle = angle(1, 0, ux, uy);
        double delta = angle(ux, uy, vx, vy);
        if (!sweep && delta > 0) delta -= Math.PI * 2;
        else if (sweep && delta < 0) delta += Math.PI * 2;

        int segments = Math.max(1, (int) Math.ceil(Math.abs(delta) / HALF_PI));
        double step = delta / segments;
        double alpha = 4.0 / 3.0 * Math.tan(step / 4.0);

        double theta = startAngle;
        for (int i = 0; i < segments; i++) {
            double cosA = Math.cos(theta), sinA = Math.sin(theta);
            double cosB = Math.cos(theta + step), sinB = Math.sin(theta + step);

            double p1x = ellipseX(rx, cx, cosPhi, sinPhi, cosA, sinA);
            double p1y = ellipseY(ry, cy, cosPhi, sinPhi, cosA, sinA);
            double p2x = ellipseX(rx, cx, cosPhi, sinPhi, cosB, sinB);
            double p2y = ellipseY(ry, cy, cosPhi, sinPhi, cosB, sinB);

            double d1x = ellipseDx(rx, cosPhi, sinPhi, cosA, sinA, alpha);
            double d1y = ellipseDy(ry, cosPhi, sinPhi, cosA, sinA, alpha);
            double d2x = ellipseDx(rx, cosPhi, sinPhi, cosB, sinB, alpha);
            double d2y = ellipseDy(ry, cosPhi, sinPhi, cosB, sinB, alpha);

            boolean last = i == segments - 1;
            sink.cubicTo((float) (p1x + d1x), (float) (p1y + d1y),
                    (float) (p2x - d2x), (float) (p2y - d2y),
                    last ? ex : (float) p2x, last ? ey : (float) p2y);
            theta += step;
        }
        x = ex;
        y = ey;
        // An S right after an A starts with its first control at the current point.
        lastCurve = CURVE_NONE;
        controlX = ex;
        controlY = ey;
    }

    private static double ellipseX(double r, double cx, double cosPhi, double sinPhi,
                                   double cosT, double sinT) {
        return cosPhi * r * cosT - sinPhi * r * sinT + cx;
    }

    private static double ellipseY(double r, double cy, double cosPhi, double sinPhi,
                                   double cosT, double sinT) {
        return sinPhi * r * cosT + cosPhi * r * sinT + cy;
    }

    private static double ellipseDx(double r, double cosPhi, double sinPhi,
                                    double cosT, double sinT, double alpha) {
        return alpha * (-cosPhi * r * sinT - sinPhi * r * cosT);
    }

    private static double ellipseDy(double r, double cosPhi, double sinPhi,
                                    double cosT, double sinT, double alpha) {
        return alpha * (-sinPhi * r * sinT + cosPhi * r * cosT);
    }

    private static double angle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt(ux * ux + uy * uy) * Math.sqrt(vx * vx + vy * vy);
        if (len == 0) return 0;
        double value = Math.max(-1.0, Math.min(1.0, dot / len));
        double angle = Math.acos(value);
        return ux * vy - uy * vx < 0 ? -angle : angle;
    }

    // ---------------------------------------------------------- tokenising
    private Float[] numbers(int count) {
        Float[] out = new Float[count];
        for (int k = 0; k < count; k++) {
            Float value = number();
            if (value == null) return null;
            out[k] = value;
        }
        return out;
    }

    private Float number() {
        skipSeparators();
        int end = scanNumber(index);
        if (end <= index) return null;
        try {
            float value = Float.parseFloat(data.substring(index, end));
            index = end;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Arc flags are a single character and may sit flush against the next number. */
    private Float flag() {
        skipSeparators();
        if (index >= length) return null;
        char c = data.charAt(index);
        if (c != '0' && c != '1') return null;
        index++;
        return c == '1' ? 1f : 0f;
    }

    private int scanNumber(int from) {
        int i = from;
        if (i < length && (data.charAt(i) == '+' || data.charAt(i) == '-')) i++;
        boolean digits = false;
        while (i < length && isDigit(data.charAt(i))) {
            i++;
            digits = true;
        }
        if (i < length && data.charAt(i) == '.') {
            i++;
            while (i < length && isDigit(data.charAt(i))) {
                i++;
                digits = true;
            }
        }
        if (!digits) return from;
        if (i < length && (data.charAt(i) == 'e' || data.charAt(i) == 'E')) {
            int exp = i + 1;
            if (exp < length && (data.charAt(exp) == '+' || data.charAt(exp) == '-')) exp++;
            int expDigits = exp;
            while (exp < length && isDigit(data.charAt(exp))) exp++;
            if (exp > expDigits) i = exp;
        }
        return i;
    }

    private void skipSeparators() {
        while (index < length && isSeparator(data.charAt(index))) index++;
    }

    private static boolean isSeparator(char c) {
        return c == ' ' || c == ',' || c == '\t' || c == '\n' || c == '\r';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isCommand(char c) {
        switch (c) {
            case 'M': case 'm': case 'L': case 'l': case 'H': case 'h': case 'V': case 'v':
            case 'C': case 'c': case 'S': case 's': case 'Q': case 'q': case 'T': case 't':
            case 'A': case 'a': case 'Z': case 'z':
                return true;
            default:
                return false;
        }
    }
}
