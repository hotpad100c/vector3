package ml.mypals.vectorthree.clips;

/**
 * One clip on the Clips track: {@code in}..{@code out} of the replay at {@code source}, in source ticks. The
 * working replay is built from whole Flashback chunks, so a clip really occupies the chunk span starting at source
 * tick {@code spanStart} and {@code spanLength} ticks long, placed at tick {@code placedAt} of the working replay;
 * the parts of that span outside in..out are skipped. {@code placedAt} is -1 until the clip has been composed in.
 */
public record ClipRef(String source, String label, int in, int out, int placedAt, int spanStart, int spanLength) {
    public boolean composed() {
        return placedAt >= 0;
    }

    public int length() {
        return Math.max(0, out - in);
    }

    public int visibleStart() {
        return placedAt + in - spanStart;
    }

    public int visibleEnd() {
        return placedAt + out - spanStart;
    }

    public ClipRef withRange(int in, int out) {
        return new ClipRef(source, label, in, out, placedAt, spanStart, spanLength);
    }

    public ClipRef placed(int placedAt, int spanStart, int spanLength) {
        return new ClipRef(source, label, in, out, placedAt, spanStart, spanLength);
    }
}
