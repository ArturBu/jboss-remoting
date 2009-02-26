/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting.stream;

import junit.framework.TestCase;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.Vector;
import java.io.EOFException;

/**
 *
 */
public final class StreamsTestCase extends TestCase {

    public void testCollectionObjectSink() throws Throwable {
        final ArrayList<String> strings = new ArrayList<String>();
        final ObjectSink<String> sink = Streams.getCollectionObjectSink(strings);
        sink.accept("Instance 1");
        sink.accept("Instance 2");
        sink.accept("Instance 3");
        sink.accept("Instance 4");
        sink.accept("Instance 5");
        sink.close();
        final Iterator<String> i = strings.iterator();
        assertEquals(i.next(), "Instance 1");
        assertEquals(i.next(), "Instance 2");
        assertEquals(i.next(), "Instance 3");
        assertEquals(i.next(), "Instance 4");
        assertEquals(i.next(), "Instance 5");
        assertFalse(i.hasNext());
    }

    public void testIteratorObjectSource() throws Throwable {
        final ObjectSource<String> source = Streams.getIteratorObjectSource(Arrays.asList("One", "Two", "Three", "Four", "Five").iterator());
        assertTrue(source.hasNext());
        assertEquals(source.next(), "One");
        assertTrue(source.hasNext());
        assertEquals(source.next(), "Two");
        assertTrue(source.hasNext());
        assertEquals(source.next(), "Three");
        assertTrue(source.hasNext());
        assertEquals(source.next(), "Four");
        assertTrue(source.hasNext());
        assertTrue(source.hasNext()); // tricky!
        assertEquals(source.next(), "Five");
        assertFalse(source.hasNext());
        assertFalse(source.hasNext()); // also tricky!
        source.close();
        assertFalse(source.hasNext()); // also also tricky!
        assertFalse(source.hasNext()); // also also also tricky!
        try {
            source.next();
        } catch (EOFException t) {
            return;
        }
        fail("No exception thrown at end of iterator");
    }

    public void testEnumerationObjectSource() throws Throwable {
        final ObjectSource<String> source = Streams.getEnumerationObjectSource(new Vector<String>(Arrays.asList("One", "Two", "Three", "Four", "Five")).elements());
        assertTrue(source.hasNext());
        assertEquals(source.next(), "One");
        assertTrue(source.hasNext());
        assertEquals(source.next(), "Two");
        assertTrue(source.hasNext());
        assertEquals(source.next(), "Three");
        assertTrue(source.hasNext());
        assertEquals(source.next(), "Four");
        assertTrue(source.hasNext());
        assertTrue(source.hasNext()); // tricky!
        assertEquals(source.next(), "Five");
        assertFalse(source.hasNext());
        assertFalse(source.hasNext()); // also tricky!
        source.close();
        assertFalse(source.hasNext()); // also also tricky!
        assertFalse(source.hasNext()); // also also also tricky!
        try {
            source.next();
        } catch (EOFException t) {
            return;
        }
        fail("No exception thrown at end of iterator");
    }
}
