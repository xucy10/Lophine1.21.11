package fun.bm.mili.utils.concurrent;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConcurrentWeakHashMap<K, V> {
    private final ConcurrentHashMap<WeakKey<K>, V> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<K> queue = new ReferenceQueue<>();

    private static final class WeakKey<K> extends WeakReference<K> {
        private final int hash;

        WeakKey(K key, ReferenceQueue<K> queue) {
            super(key, queue);
            this.hash = System.identityHashCode(key);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof WeakKey<?> other)) return false;
            Object thisRef = this.get();
            Object otherRef = other.get();
            if (thisRef == null || otherRef == null) {
                return thisRef == null && otherRef == null && this.hash == other.hash;
            }
            return thisRef == otherRef;
        }
    }

    public V getOrDefault(K key, V defaultValue) {
        expunge();
        V value = map.get(new WeakKey<>(key, null));
        return value != null ? value : defaultValue;
    }

    public V put(K key, V value) {
        expunge();
        return map.put(new WeakKey<>(key, queue), value);
    }

    public void remove(K key) {
        expunge();
        map.remove(new WeakKey<>(key, null));
    }

    public int size() {
        expunge();
        return map.size();
    }

    private void expunge() {
        WeakKey<?> ref;
        while ((ref = (WeakKey<?>) queue.poll()) != null) {
            map.remove(ref);
        }
    }
}
