package dev.vpfps;

import java.util.Arrays;

/**
 * Fixed-capacity ring buffer of floats (frame times, mesh costs, ...).
 * Thread-safe enough for this mod's usage: one writer thread (render/game),
 * readers get slightly stale snapshots which is fine for statistics.
 */
public final class Stats {

    private final float[] data;
    private int head;
    private int size;
    private float last;

    public Stats(int capacity) {
        data = new float[Math.max(1, capacity)];
    }

    public synchronized void push(float v) {
        data[head] = v;
        head = (head + 1) % data.length;
        if (size < data.length) size++;
        last = v;
    }

    public synchronized float last()  { return last; }
    public synchronized int size()    { return size; }

    public synchronized float mean() {
        if (size == 0) return 0f;
        double s = 0;
        for (int i = 0; i < size; i++) s += data[i];
        return (float) (s / size);
    }

    public synchronized float max() {
        if (size == 0) return 0f;
        float m = data[0];
        for (int i = 1; i < size; i++) m = Math.max(m, data[i]);
        return m;
    }

    /** p in [0,1]; nearest-rank percentile over a sorted snapshot. */
    public synchronized float percentile(float p) {
        if (size == 0) return 0f;
        float[] copy = new float[size];
        System.arraycopy(data, 0, copy, 0, size);
        Arrays.sort(copy);
        int idx = (int) Math.min(size - 1, Math.max(0, Math.round(p * (size - 1))));
        return copy[idx];
    }

    public synchronized void reset() {
        head = 0;
        size = 0;
        last = 0f;
    }
}
