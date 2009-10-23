/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.HashMap;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableSet;
import java.util.concurrent.ConcurrentMap;

final class CopyOnWriteHashMap<K, V> implements ConcurrentMap<K, V> {
    private final Object writeLock;
    private volatile Map<K, V> map = emptyMap();

    CopyOnWriteHashMap() {
        this(new Object());
    }

    CopyOnWriteHashMap(final Object writeLock) {
        this.writeLock = writeLock;
    }

    public V putIfAbsent(final K key, final V value) {
        if (key == null) {
            throw new NullPointerException("key is null");
        }
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        synchronized (writeLock) {
            final Map<K, V> map = this.map;
            final V old = map.get(key);
            if (old != null) return old;
            if (map.size() == 0) {
                this.map = singletonMap(key, value);
            } else {
                final HashMap<K, V> copy = new HashMap<K, V>(map);
                map.put(key, value);
                this.map = copy;
            }
            return null;
        }
    }

    public boolean remove(final Object key, final Object value) {
        if (key == null || value == null) return false;
        synchronized (writeLock) {
            final Map<K, V> map = this.map;
            final V old = map.get(key);
            if (old == null) {
                return false;
            }
            if (map.size() == 1) {
                this.map = emptyMap();
            } else {
                final HashMap<K, V> copy = new HashMap<K, V>(map);
                map.remove(key);
                this.map = copy;
            }
            return true;
        }
    }

    public boolean replace(final K key, final V oldValue, final V newValue) {
        if (key == null || oldValue == null) return false;
        if (newValue == null) {
            throw new NullPointerException("newValue is null");
        }
        synchronized (writeLock) {
            final Map<K, V> map = this.map;
            final V old = map.get(key);
            if (old == null) {
                return false;
            }
            if (map.size() == 1) {
                this.map = singletonMap(key, newValue);
            } else {
                final HashMap<K, V> copy = new HashMap<K, V>(map);
                map.put(key, newValue);
                this.map = copy;
            }
            return true;
        }
    }

    public V replace(final K key, final V value) {
        if (key == null) {
            return null;
        }
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        synchronized (writeLock) {
            final Map<K, V> map = this.map;
            final V old = map.get(key);
            if (old != null) {
                if (map.size() == 1) {
                    this.map = singletonMap(key, value);
                } else {
                    final HashMap<K, V> copy = new HashMap<K, V>(map);
                    map.put(key, value);
                    this.map = copy;
                }
            }
            return old;
        }
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean containsKey(final Object key) {
        return map.containsKey(key);
    }

    public boolean containsValue(final Object value) {
        return map.containsValue(value);
    }

    public V get(final Object key) {
        return map.get(key);
    }

    public V put(final K key, final V value) {
        if (key == null) {
            throw new NullPointerException("key is null");
        }
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        synchronized (writeLock) {
            final Map<K, V> map = this.map;
            final V old = map.get(key);
            if (map.size() == 0) {
                this.map = singletonMap(key, value);
            } else {
                final HashMap<K, V> copy = new HashMap<K, V>(map);
                map.put(key, value);
                this.map = copy;
            }
            return old;
        }
    }

    public V remove(final Object key) {
        if (key == null) return null;
        synchronized (writeLock) {
            final Map<K, V> map = this.map;
            final V old = map.get(key);
            if (old != null) {
                if (map.size() == 1) {
                    this.map = emptyMap();
                } else {
                    final HashMap<K, V> copy = new HashMap<K, V>(map);
                    map.remove(key);
                    this.map = copy;
                }
            }
            return old;
        }
    }

    public void putAll(final Map<? extends K, ? extends V> m) {
    }

    public void clear() {
        synchronized (writeLock) {
            map = emptyMap();
        }
    }

    public Set<K> keySet() {
        return unmodifiableSet(map.keySet());
    }

    public Collection<V> values() {
        return unmodifiableCollection(map.values());
    }

    public Set<Entry<K, V>> entrySet() {
        return unmodifiableMap(map).entrySet();
    }
}
