package org.winlogon.servermanager.util;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.stream.Stream;

/**
 * A pair of values, using a {@link Map.Entry} as backing storage
 *
 * @param <A> the type of the values
 */
public class Pair<A> implements Iterable<A>, Comparable<Pair<A>> {

    protected Map.Entry<A, A> entry;

    public Pair(A first, A second) {
        this.entry = Map.entry(first, second);
    }

    /**
     * Returns an {@link Iterable} that iterates over the two values
     *
     * @return an {@link Iterable} containing the two values
     */
    public Iterable<A> values() {
        @SuppressWarnings("unchecked")
        A[] array = (A[]) new Object[]{entry.getKey(), entry.getValue()};

        // Anonymous {@link Iterable} that supplies an {@link Iterator} over that array
        return () -> new Iterator<A>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < array.length;
            }

            @Override
            public A next() {
                return array[index++];
            }
        };
    }

    public A first() {
        return entry.getKey();
    }

    public A second() {
        return entry.getValue();
    }

    public void setFirst(A first) {
        entry = Map.entry(first, entry.getValue());
    }

    public void setSecond(A second) {
        entry = Map.entry(entry.getKey(), second);
    }

    public List<A> asList() {
        return List.of(entry.getKey(), entry.getValue());
    }

    @Override
    public String toString() {
        return "Pair{" + "first=" + entry.getKey() + ", second=" + entry.getValue() + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pair<?> pair = (Pair<?>) o;
        return entry.equals(pair.entry);
    }

    @Override
    public int hashCode() {
        return entry.hashCode();
    }

    @Override
    public Iterator<A> iterator() {
        return values().iterator();
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compareTo(Pair<A> o) {
        var firstThis = (Comparable<A>) this.first();
        var firstOther = (Comparable<A>) o.first();

        int cmp = firstThis.compareTo((A) firstOther);
        if (cmp != 0) return cmp;

        var secondThis = (Comparable<A>) this.second();
        var secondOther = (Comparable<A>) o.second();
        return secondThis.compareTo((A) secondOther);
    }

    @Override
    public Spliterator<A> spliterator() {
        return stream().spliterator();
    }

    public Stream<A> stream() {
        return Stream.of(entry.getKey(), entry.getValue());
    }
}
