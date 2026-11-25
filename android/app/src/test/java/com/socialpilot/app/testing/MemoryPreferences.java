package com.socialpilot.app.testing;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Models Android's memory-first commit, including failure without rollback. Strings only. */
public final class MemoryPreferences implements SharedPreferences {
    public final Map<String, String> memory = new HashMap<>();
    public final Map<String, String> disk = new HashMap<>();
    public int commits;
    public volatile int failCommits;

    /** A new process reading only what reached disk. */
    public MemoryPreferences reboot() {
        MemoryPreferences result = new MemoryPreferences();
        result.memory.putAll(disk);
        result.disk.putAll(disk);
        return result;
    }

    @Override public Map<String, ?> getAll() { return new HashMap<>(memory); }
    @Override public String getString(String key, String fallback) { return memory.getOrDefault(key, fallback); }
    @Override public boolean contains(String key) { return memory.containsKey(key); }
    @Override public Editor edit() { return new MemoryEditor(); }
    @Override public Set<String> getStringSet(String key, Set<String> fallback) { throw new UnsupportedOperationException(); }
    @Override public int getInt(String key, int fallback) { throw new UnsupportedOperationException(); }
    @Override public long getLong(String key, long fallback) { throw new UnsupportedOperationException(); }
    @Override public float getFloat(String key, float fallback) { throw new UnsupportedOperationException(); }
    @Override public boolean getBoolean(String key, boolean fallback) { throw new UnsupportedOperationException(); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }

    private final class MemoryEditor implements Editor {
        final Map<String, String> changes = new HashMap<>();
        boolean clear;
        @Override public Editor putString(String key, String value) { changes.put(key, value); return this; }
        @Override public Editor remove(String key) { changes.put(key, null); return this; }
        @Override public Editor clear() { clear = true; return this; }
        @Override public boolean commit() {
            commits++;
            if (clear) memory.clear();
            changes.forEach((key, value) -> { if (value == null) memory.remove(key); else memory.put(key, value); });
            if (failCommits > 0) { failCommits--; return false; }
            disk.clear();
            disk.putAll(memory);
            return true;
        }
        @Override public void apply() { commit(); }
        @Override public Editor putStringSet(String key, Set<String> values) { throw new UnsupportedOperationException(); }
        @Override public Editor putInt(String key, int value) { throw new UnsupportedOperationException(); }
        @Override public Editor putLong(String key, long value) { throw new UnsupportedOperationException(); }
        @Override public Editor putFloat(String key, float value) { throw new UnsupportedOperationException(); }
        @Override public Editor putBoolean(String key, boolean value) { throw new UnsupportedOperationException(); }
    }
}
