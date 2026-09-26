package ml.mypals.vectorthree.flashback.loop;

public record Loop(int count, boolean endsScope) {
    public record Scope(int start, int end, int count) {}
}
