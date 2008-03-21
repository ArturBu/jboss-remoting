package org.jboss.cx.remoting.util;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A set of helpful utility functions for collections.
 */
public final class CollectionUtil {
    private CollectionUtil() {
    }

    /**
     * Create a concurrent map instance.
     *
     * @return a new concurrent map
     */
    public static <K, V> ConcurrentMap<K, V> concurrentMap() {
        if (true) {
            return synchronizedHashMap();
        } else {
            return new ConcurrentHashMap<K, V>();
        }
    }

    /**
     * Create a synchronized map that obeys the contract for {@code ConcurrentMap}.
     *
     * @param original the map to be wrapped
     * @return a synchronized map
     */
    public static <K, V> ConcurrentMap<K, V> synchronizedMap(Map<K, V> original) {
        return new SynchronizedMap<K, V>(original);
    }

    /**
     * Create a synchronized hash map that obeys the contract for {@code ConcurrentMap}.
     *
     * @return a synchronized hash map
     */
    public static <K, V> ConcurrentMap<K, V> synchronizedHashMap() {
        return synchronizedMap(CollectionUtil.<K, V>hashMap());
    }

    /**
     * Create an array-backed list.
     *
     * @return an array-backed list
     */
    public static <T> List<T> arrayList() {
        return new ArrayList<T>();
    }

    /**
     * Create a linked list.
     *
     * @return a linked list
     */
    public static <T> LinkedList<T> linkedList() {
        return new LinkedList<T>();
    }

    /**
     * Create a synchronized array-backed list.
     *
     * @return a synchronized array-backed list
     */
    public static <T> List<T> synchronizedArrayList() {
        return Collections.synchronizedList(CollectionUtil.<T>arrayList());
    }

    /**
     * Create an array-backed list whose contents are a copy of the given list.
     *
     * @param orig the original list
     * @return an array backed list
     */
    public static <T> List<T> arrayList(List<T> orig) {
        return new ArrayList<T>(orig);
    }

    /**
     * Create a synchronized wrapper for the given set.
     *
     * @param nested the nested set
     * @return a synchronized version of the nested set
     */
    public static <T> Set<T> synchronizedSet(Set<T> nested) {
        return new SynchronizedSet<T>(nested);
    }

    /**
     * Create a synchronized hash set.
     *
     * @return a synchronized hash set
     */
    public static <T> Set<T> synchronizedHashSet() {
        return synchronizedSet(CollectionUtil.<T>hashSet());
    }

    /**
     * Create a synchronized weak hash set.
     *
     * @return a synchronized weak hash set
     */
    public static <T> Set<T> synchronizedWeakHashSet() {
        return synchronizedSet(CollectionUtil.<T>weakHashSet());
    }

    /**
     * Create a synchronized version of the nested queue that obeys the contract for {@code BlockingQueue}.
     *
     * @param nested the nested queue
     * @return the blocking queue
     */
    public static <T> BlockingQueue<T> synchronizedQueue(Queue<T> nested) {
        return new SynchronizedQueue<T>(nested);
    }

    /**
     * Create a weak hash set.
     *
     * @return a weak hash set
     */
    public static <T> Set<T> weakHashSet() {
        return new WeakHashSet<T>();
    }

    /**
     * Create a fixed-capacity blocking queue.
     *
     * @param size the fixed size
     * @return a fixed-capacity blocking queue
     */
    public static <T> BlockingQueue<T> blockingQueue(int size) {
        return new ArrayBlockingQueue<T>(size);
    }

    /**
     * Create a hash set.
     *
     * @return a hash set
     */
    public static <T> Set<T> hashSet() {
        return new HashSet<T>();
    }

    /**
     * Create a hash map with weak keys.  See {@link java.util.WeakHashMap}.
     *
     * @return a hash map with weak keys
     */
    public static <K, V> Map<K, V> weakHashMap() {
        return new WeakHashMap<K, V>();
    }

    /**
     * Create a synchronized hash map with weak keys, which obeys the {@code ConcurrentMap} contract.
     *
     * @return a synchronized weak hash map
     */
    public static <K, V> ConcurrentMap<K, V> synchronizedWeakHashMap() {
        return CollectionUtil.<K,V>synchronizedMap(CollectionUtil.<K,V>weakHashMap());
    }

    /**
     * Create an unmodifiable list view of an array.
     *
     * @param entries the array
     * @return an unmodifiable list
     */
    public static <T> List<T> unmodifiableList(final T[] entries) {
        return new UnmodifiableArrayList<T>(entries);
    }

    /**
     * Create a hash map.
     *
     * @return a hash map
     */
    public static <K, V> Map<K, V> hashMap() {
        return new HashMap<K, V>();
    }

    /**
     * Create an {@code Iterable} view of another {@code Iterable} that exposes no other methods.
     *
     * @param original the wrapped instance
     * @return a new {@code Iterable}
     */
    public static <T> Iterable<T> protectedIterable(Iterable<T> original) {
        return new DelegateIterable<T>(original);
    }

    /**
     * Create an {@code Iterable} view of an {@code Enumeration}.  The view can be used only once.
     *
     * @param enumeration the enumeration
     * @return the {@code Iterable} view
     */
    public static <T> Iterable<T> loop(final Enumeration<T> enumeration) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return CollectionUtil.iterator(enumeration);
            }
        };
    }

    /**
     * Create an {@code Iterable} view of an {@code Iterator}.  The view can be used only once.
     *
     * @param iterator the iterator
     * @return the {@code Iterable} view
     */
    public static <T> Iterable<T> loop(final Iterator<T> iterator) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return iterator;
            }
        };
    }

    /**
     * Create an {@code Iterator} view of an {@code Enumeration}.
     *
     * @param enumeration the enumeration
     * @return the {@code Iterator} view
     */
    public static <T> Iterator<T> iterator(final Enumeration<T> enumeration) {
        return new Iterator<T>() {
            public boolean hasNext() {
                return enumeration.hasMoreElements();
            }

            public T next() {
                return enumeration.nextElement();
            }

            public void remove() {
                throw new UnsupportedOperationException("remove()");
            }
        };
    }

    /**
     * Run a translation function for each element of an {@code Enumeration}.
     *
     * @param input the input data
     * @param translator the translator
     * @return the translated data
     */
    public static <I,O> Enumeration<O> translate(final Enumeration<I> input, final Translator<I, O> translator) {
        return new Enumeration<O>() {
            public boolean hasMoreElements() {
                return input.hasMoreElements();
            }

            public O nextElement() {
                return translator.translate(input.nextElement());
            }
        };
    }

    /**
     * Run a translation function for each element of an {@code Iterator}.
     *
     * @param input the input data
     * @param translator the translator
     * @return the translated data
     */
    public static <I,O> Iterator<O> translate(final Iterator<I> input, final Translator<I, O> translator) {
        return new Iterator<O>() {
            public boolean hasNext() {
                return input.hasNext();
            }

            public O next() {
                return translator.translate(input.next());
            }

            public void remove() {
                input.remove();
            }
        };
    }

    /**
     * Run a translation function for each element of an {@code Iterable}.
     *
     * @param input the input data
     * @param translator the translator
     * @return the translated data
     */
    public static <I,O> Iterable<O> translate(final Iterable<I> input, final Translator<I, O> translator) {
        return new Iterable<O>() {
            public Iterator<O> iterator() {
                return translate(input.iterator(), translator);
            }
        };
    }

    /**
     * Create an iterable view of a string split by a given delimiter.
     *
     * @param delimiter the delimiter
     * @param subject the original string
     * @return the iterable split view
     */
    public static Iterable<String> split(final String delimiter, final String subject) {
        return new Iterable<String>() {
            public Iterator<String> iterator() {
                return new Iterator<String>(){
                    private int position = 0;

                    public boolean hasNext() {
                        return position != -1;
                    }

                    public String next() {
                        if (position == -1) {
                            throw new NoSuchElementException("next() past end of iterator");
                        }
                        final int nextDelim = subject.indexOf(delimiter, position);
                        try {
                            if (nextDelim == -1) {
                                return subject.substring(position);
                            } else {
                                return subject.substring(position, nextDelim);
                            }
                        } finally {
                            position = nextDelim;
                        }
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("remove() not supported");
                    }
                };
            }
        };
    }

    public static AttributeMap emptyAttributeMap() {
        return EMPTY_ATTRIBUTE_MAP;
    }

    private static final AttributeMap EMPTY_ATTRIBUTE_MAP = new EmptyAttributeMap();

    private static final class EmptyAttributeMap implements AttributeMap {

        public <T> T get(final AttributeKey<T> key) {
            return null;
        }

        public <T> T put(final AttributeKey<T> key, final T value) {
            throw new UnsupportedOperationException("put()");
        }

        public <T> T remove(final AttributeKey<T> key) {
            return null;
        }

        public <T> boolean remove(final AttributeKey<T> key, final T value) {
            return false;
        }

        public <T> T putIfAbsent(final AttributeKey<T> key, final T value) {
            throw new UnsupportedOperationException("putIfAbsent()");
        }

        public <T> boolean replace(final AttributeKey<T> key, final T oldValue, final T newValue) {
            return false;
        }

        public <T> boolean containsKey(final AttributeKey<T> key) {
            return false;
        }

        public <T> boolean containsValue(final T value) {
            return false;
        }

        public Iterable<Entry<?>> entries() {
            return emptyIterable();
        }

        public Set<AttributeKey<?>> keySet() {
            return Collections.emptySet();
        }

        public Collection<?> values() {
            return Collections.emptySet();
        }

        public boolean isEmpty() {
            return true;
        }

        public int size() {
            return 0;
        }

        public void clear() {
            // might as well let it succeed
        }
    }

    @SuppressWarnings ({"unchecked"})
    public static <T> Iterable<T> emptyIterable() {
        return (Iterable<T>) EMPTY_ITERABLE;
    }

    private static final Iterable<?> EMPTY_ITERABLE = new EmptyIterable();

    private static class EmptyIterable implements Iterable<Object> {
        public Iterator<Object> iterator() {
            return emptyIterator();
        }
    }

    @SuppressWarnings ({"unchecked"})
    public static <T> Iterator<T> emptyIterator() {
        return (Iterator<T>) EMPTY_ITERATOR;
    }

    private static final Iterator<?> EMPTY_ITERATOR = new EmptyIterator();

    private static final class EmptyIterator implements Iterator {

        public boolean hasNext() {
            return false;
        }

        public Object next() {
            throw new NoSuchElementException("next() past end of iterator");
        }

        public void remove() {
            throw new IllegalStateException("next() has not yet been called");
        }

    }
}
