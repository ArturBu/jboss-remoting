/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

/**
 * A request whose replies should be of a specific type.  Request classes may choose to implement this interface
 * in order to provide additional type checking and convenience to Remoting API users by causing the reply type
 * to be chosen based upon the request type.
 *
 * @param <I> the request type
 * @param <O> the reply type for this request type
 */
@SuppressWarnings({ "UnusedDeclaration" })
public interface TypedRequest<I, O> {

    /**
     * Check the reply type.  If the reply type is incorrect in any way, a {@code java.lang.ClassCastException} is
     * thrown.
     *
     * @param reply the raw reply
     * @return the typesafe reply
     * @throws ClassCastException if a type conversion error occurs
     */
    O castReply(Object reply) throws ClassCastException;
}
