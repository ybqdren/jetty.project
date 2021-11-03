//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.util;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attributes.
 * Interface commonly used for storing attributes.
 */
public interface Attributes
{
    void removeAttribute(String name);

    void setAttribute(String name, Object attribute);

    Object getAttribute(String name);

    Set<String> getAttributeNameSet();

    default Enumeration<String> getAttributeNames()
    {
        return Collections.enumeration(getAttributeNameSet());
    }

    void clearAttributes();

    /** Unwrap all  {@link Wrapper}s of the attributes
     * @param attributes The attributes to unwrap, which may be a  {@link Wrapper}.
     * @return The core attributes
     */
    static Attributes unwrap(Attributes attributes)
    {
        while (attributes instanceof Wrapper)
        {
            attributes = ((Wrapper)attributes).getAttributes();
        }
        return attributes;
    }

    /** Unwrap attributes to a specific attribute  {@link Wrapper}.
     * @param attributes The attributes to unwrap, which may be a {@link Wrapper}
     * @param target The target  {@link Wrapper} class.
     * @param <T> The type of the target  {@link Wrapper}.
     * @return The outermost {@link Wrapper} of the matching type of null if not found.
     */
    @SuppressWarnings("unchecked")
    static <T extends Attributes.Wrapper> T unwrap(Attributes attributes, Class<T> target)
    {
        while (attributes instanceof Wrapper)
        {
            if (target.isAssignableFrom(attributes.getClass()))
                return (T)attributes;
            attributes = ((Wrapper)attributes).getAttributes();
        }
        return null;
    }

    /**
     * A Wrapper of attributes
     */
    abstract class Wrapper implements Attributes
    {
        protected final Attributes _attributes;

        public Wrapper(Attributes attributes)
        {
            _attributes = attributes;
        }

        public Attributes getAttributes()
        {
            return _attributes;
        }

        @Override
        public void removeAttribute(String name)
        {
            _attributes.removeAttribute(name);
        }

        @Override
        public void setAttribute(String name, Object attribute)
        {
            _attributes.setAttribute(name, attribute);
        }

        @Override
        public Object getAttribute(String name)
        {
            return _attributes.getAttribute(name);
        }

        @Override
        public Set<String> getAttributeNameSet()
        {
            return _attributes.getAttributeNameSet();
        }

        @Override
        public void clearAttributes()
        {
            _attributes.clearAttributes();
        }
    }

    class Mapped implements Attributes
    {
        private final java.util.concurrent.ConcurrentMap<String, Object> _map = new ConcurrentHashMap<>();

        public Mapped()
        {
        }

        public Mapped(Mapped attributes)
        {
            _map.putAll(attributes._map);
        }

        @Override
        public void removeAttribute(String name)
        {
            _map.remove(name);
        }

        @Override
        public void setAttribute(String name, Object attribute)
        {
            if (attribute == null)
                removeAttribute(name);
            else
                _map.put(name, attribute);
        }

        @Override
        public Object getAttribute(String name)
        {
            return _map.get(name);
        }

        @Override
        public Set<String> getAttributeNameSet()
        {
            return _map.keySet();
        }

        @Override
        public void clearAttributes()
        {
            _map.clear();
        }

        public int size()
        {
            return _map.size();
        }

        public Set<Map.Entry<String, Object>> getAttributes()
        {
            return _map.entrySet();
        }

        @Override
        public String toString()
        {
            return _map.toString();
        }

        public void addAll(Attributes attributes)
        {
            Enumeration<String> e = attributes.getAttributeNames();
            while (e.hasMoreElements())
            {
                String name = e.nextElement();
                setAttribute(name, attributes.getAttribute(name));
            }
        }
    }

    class Layer implements Attributes
    {
        private static final Object REMOVED = new Object();
        private final Attributes _persistent;
        private final java.util.concurrent.ConcurrentMap<String, Object> _map = new ConcurrentHashMap<>();

        public Layer(Attributes persistent)
        {
            _persistent = persistent;
        }

        @Override
        public void removeAttribute(String name)
        {
            if (_persistent.getAttributeNameSet().contains(name))
               _map.put(name, REMOVED);
            else
                _map.remove(name);
        }

        @Override
        public void setAttribute(String name, Object attribute)
        {
            if (attribute == null)
                removeAttribute(name);
            else
                _map.put(name, attribute);
        }

        @Override
        public Object getAttribute(String name)
        {
            Object value = _map.get(name);
            if (value != null)
                return value == REMOVED ? null : value;
            return _persistent.getAttribute(name);
        }

        @Override
        public Set<String> getAttributeNameSet()
        {
            Set<String> names = _persistent.getAttributeNameSet();
            if (_map.isEmpty())
                return names;
            names = new HashSet<>(names);
            for (Map.Entry<String, Object> e : _map.entrySet())
            {
                if (e.getValue() == REMOVED)
                    names.remove(e.getKey());
                else
                    names.add(e.getKey());
            }
            return names;
        }

        @Override
        public void clearAttributes()
        {
            for (String n : _persistent.getAttributeNameSet())
                _map.put(n, REMOVED);
            _map.entrySet().removeIf(e -> e.getValue() != REMOVED);
        }
    }
}
