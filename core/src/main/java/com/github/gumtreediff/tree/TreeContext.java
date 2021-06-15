/*
 * This file is part of GumTree.
 *
 * GumTree is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GumTree is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GumTree.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2011-2015 Jean-Rémy Falleri <jr.falleri@gmail.com>
 * Copyright 2011-2015 Floréal Morandat <florealm@gmail.com>
 */

package com.github.gumtreediff.tree;

import com.github.gumtreediff.io.TreeIoUtils;
import com.github.gumtreediff.io.TreeIoUtils.MetadataSerializer;
import com.github.gumtreediff.io.TreeIoUtils.MetadataUnserializer;
import com.github.gumtreediff.io.TreeIoUtils.TreeFormatter;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

interface Context {

    /**
     * Get a global metadata.
     * There is no way to know if the metadata is really null or does not exists.
     *
     * @param key of metadata
     * @return the metadata or null if not found
     */
    Object getMetadata(String key);

    /**
     * Store a global metadata.
     *
     * @param key   of the metadata
     * @param value of the metadata
     * @return the previous value of metadata if existed or null
     */
    Object setMetadata(String key, Object value);

    /**
     * Get an iterator on global metadata only
     */
    Iterator<Map.Entry<String, Object>> getMetadata();

    /**
     * Get serializers for this tree context
     */
    TreeContext.MetadataSerializers getSerializers();

    Context export(TreeContext.MetadataSerializers s);

    Context export(String key, TreeIoUtils.MetadataSerializer s);

    Context export(String... name);
}

public interface TreeContext extends Context {
    void setRoot(ITree root);

    ITree getRoot();

    default ITree createTree(Type type, String label) {
        return new Tree(type, label);
    }

    default ITree createTree(Type type) {
        return new Tree(type);
    }

    default ITree createFakeTree(ITree... trees) {
        return new FakeTree(trees);
    }

    /**
     * Get a local metadata, if available. Otherwise get a global metadata.
     * There is no way to know if the metadata is really null or does not exists.
     *
     * @param key of metadata
     * @return the metadata or null if not found
     */
    default Object getMetadata(ITree node, String key) {
        Object metadata;
        if (node == null || (metadata = node.getMetadata(key)) == null)
            return getMetadata(key);
        return metadata;
    }

    /**
     * Store a local metadata
     *
     * @param key   of the metadata
     * @param value of the metadata
     * @return the previous value of metadata if existed or null
     */
    default Object setMetadata(ITree node, String key, Object value) {
        if (node == null)
            return setMetadata(key, value);
        else {
            Object res = node.setMetadata(key, value);
            if (res == null)
                return getMetadata(key);
            return res;
        }
    }

    /**
     * Get an iterator on local and global metadata.
     * To only get local metadata, simply use : `node.getMetadata()`
     */
    default Iterator<Entry<String, Object>> getMetadata(ITree node) {
        if (node == null)
            return getMetadata();
        return new Iterator<Entry<String, Object>>() {
            final Iterator<Entry<String, Object>> localIterator = node.getMetadata();
            final Iterator<Entry<String, Object>> globalIterator = getMetadata();
            final Set<String> seenKeys = new HashSet<>();

            Iterator<Entry<String, Object>> currentIterator = localIterator;
            Entry<String, Object> nextEntry;

            {
                next();
            }

            @Override
            public boolean hasNext() {
                return nextEntry != null;
            }

            @Override
            public Entry<String, Object> next() {
                Entry<String, Object> n = nextEntry;
                if (currentIterator == localIterator) {
                    if (localIterator.hasNext()) {
                        nextEntry = localIterator.next();
                        seenKeys.add(nextEntry.getKey());
                        return n;
                    } else {
                        currentIterator = globalIterator;
                    }
                }
                nextEntry = null;
                while (globalIterator.hasNext()) {
                    Entry<String, Object> e = globalIterator.next();
                    if (!(seenKeys.contains(e.getKey()) || (e.getValue() == null))) {
                        nextEntry = e;
                        seenKeys.add(nextEntry.getKey());
                        break;
                    }
                }
                return n;
            }
        };
    }

    TreeContext deriveTree();

    class ContextImpl implements Context {

        protected final Map<String, Object> metadata = new HashMap<>();

        protected final MetadataSerializers serializers = new MetadataSerializers();

        @Override
        public Object getMetadata(String key) {
            return metadata.get(key);
        }

        @Override
        public Object setMetadata(String key, Object value) {
            return metadata.put(key, value);
        }

        @Override
        public Iterator<Entry<String, Object>> getMetadata() {
            return metadata.entrySet().iterator();
        }

        @Override
        public MetadataSerializers getSerializers() {
            return serializers;
        }

        @Override
        public Context export(MetadataSerializers s) {
            serializers.addAll(s);
            return this;
        }

        @Override
        public Context export(String key, MetadataSerializer s) {
            serializers.add(key, s);
            return this;
        }

        @Override
        public Context export(String... name) {
            for (String n : name)
                serializers.add(n, x -> x.toString());
            return this;
        }
    }

    class TreeContextImpl extends ContextImpl implements TreeContext {

        private ITree root;

        @Override
        public String toString() {
            return TreeIoUtils.toText(this).toString();
        }

        @Override
        public void setRoot(ITree root) {
            this.root = root;
        }

        @Override
        public ITree getRoot() {
            return root;
        }

        @Override
        public TreeContext deriveTree() { // FIXME Should we refactor TreeContext class to allow shared metadata etc ...
            TreeContextImpl newContext = new TreeContextImpl();
            newContext.setRoot(getRoot().deepCopy());
            newContext.metadata.putAll(metadata);
            newContext.serializers.addAll(serializers);
            return newContext;
        }
    }

    class Marshallers<E> {
        Map<String, E> serializers = new HashMap<>();

        public static final Pattern valid_id = Pattern.compile("[a-zA-Z0-9_]*");

        public void addAll(Marshallers<E> other) {
            addAll(other.serializers);
        }

        public void addAll(Map<String, E> serializers) {
            serializers.forEach((k, s) -> add(k, s));
        }

        public void add(String name, E serializer) {
            if (!valid_id.matcher(name).matches()) // TODO I definitely don't like this rule, we should think twice
                throw new RuntimeException("Invalid key for serialization");
            serializers.put(name, serializer);
        }

        public void remove(String key) {
            serializers.remove(key);
        }

        public Set<String> exports() {
            return serializers.keySet();
        }
    }

    class MetadataSerializers extends Marshallers<MetadataSerializer> {

        public void serialize(TreeFormatter formatter, String key, Object value) throws Exception {
            MetadataSerializer s = serializers.get(key);
            if (s != null)
                formatter.serializeAttribute(key, s.toString(value));
        }
    }

    class MetadataUnserializers extends Marshallers<MetadataUnserializer> {

        public void load(ITree tree, String key, String value) throws Exception {
            MetadataUnserializer s = serializers.get(key);
            if (s != null) {
                if (key.equals("pos"))
                    tree.setPos(Integer.parseInt(value));
                else if (key.equals("length"))
                    tree.setLength(Integer.parseInt(value));
                else
                    tree.setMetadata(key, s.fromString(value));
            }
        }
    }
}
