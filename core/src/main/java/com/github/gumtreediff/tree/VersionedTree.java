package com.github.gumtreediff.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Map.Entry;

import com.github.gumtreediff.tree.AbstractTree;
import com.github.gumtreediff.tree.AssociationMap;
import com.github.gumtreediff.tree.ITree;

import com.github.gumtreediff.tree.Type;

public class VersionedTree extends AbstractVersionedTree {

    protected Type type;

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public void setType(Type type) {
        this.type = type;
    }

    private String label;

    // Begin position of the tree in terms of absolute character index and length
    private int pos;
    private int length;

    private AssociationMap metadata;

    private VersionedTree(ITree other, Version version) {
        this.type = other.getType();
        this.label = other.getLabel();
        this.pos = other.getPos();
        this.length = other.getLength();
        this.insertVersion = version;
        this.children = new LinkedList<>();
        this.metadata = new AssociationMap();
        this.metadata.set("type", other.getMetadata("type"));
    }

    private VersionedTree(ITree other, String... wantedMD) {
        this(other, (Version) null);
        for (String key : wantedMD) {
            this.metadata.set(key, other.getMetadata(key));
        }
    }

    public VersionedTree(ITree other, Version version, String... wantedMD) {
        this(other, version);
        if (version == null) {
            throw new UnsupportedOperationException("version must be non null");
        }
        for (String key : wantedMD) {
            this.metadata.set(key, other.getMetadata(key));
        }
    }

    @Override
    public VersionedTree deepCopy() {
        VersionedTree copy = new VersionedTree(this);
        copy.insertVersion = this.insertVersion;
        copy.removeVersion = this.removeVersion;
        for (ITree child : getChildren()) {
            ITree tmp = child.deepCopy();
            copy.addChild(tmp);
            tmp.setParent(copy);
        }
        return copy;
    }

    public static VersionedTree deepCreate(ITree other, Version version) {
        VersionedTree r = new VersionedTree(other, version);
        for (ITree child : other.getChildren()) {
            VersionedTree tmp = deepCreate(child, version);
            r.addChild(tmp);
            tmp.setParent(r);
        }
        return r;
    }

    public static final String ORIGINAL_SPOON_OBJECT = "original_spoon_object";
    // public static final String MIDDLE_GUMTREE_NODE = "middle_gumtree_node";

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public int getPos() {
        return pos;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public void setPos(int pos) {
        this.pos = pos;
    }

    @Override
    public Object getMetadata(String key) {
        if (metadata == null)
            return null;
        return metadata.get(key);
    }

    @Override
    public Object setMetadata(String key, Object value) {
        if (value == null) {
            if (metadata == null)
                return null;
            else
                return metadata.remove(key);
        }
        if (metadata == null)
            metadata = new AssociationMap();
        return metadata.set(key, value);
    }

    @Override
    public Iterator<Entry<String, Object>> getMetadata() {
        if (metadata == null)
            return new EmptyEntryIterator();
        return metadata.iterator();
    }

    @Override
    public int getChildrenSize() {
        return children.size();
    }

    protected static class EmptyEntryIterator implements Iterator<Map.Entry<String, Object>> {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Map.Entry<String, Object> next() {
            throw new NoSuchElementException();
        }
    }
}