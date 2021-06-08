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

package com.github.gumtreediff.gen.treesitter.java;


import ai.serenade.treesitter.Languages;
import ai.serenade.treesitter.Node;
import ai.serenade.treesitter.Parser;
import ai.serenade.treesitter.Tree;
import com.github.gumtreediff.gen.Register;
import com.github.gumtreediff.gen.Registry;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.io.TreeIoUtils;
import com.github.gumtreediff.tree.*;

import java.io.*;
import java.util.*;

import static com.github.gumtreediff.tree.TypeSet.type;

@Register(id = "java-treesitter", accept = "\\.java$", priority = Registry.Priority.LOW)
public class TreeSitterJavaTreeGenerator2 extends TreeGenerator implements AutoCloseable {

    private final Parser parser;
    private TreeContext2 treeContext = new TreeContext2();
    private String s;

    public static class Data {
        ITree tree;
        int count = 0;

        Data(ITree tree) {
            this.tree = tree;
        }
    }

    public static class Data2 {
        Data2 next = null;
        CompressibleNode tree;
        int count = 0;

        Data2(CompressibleNode tree) {
            this.tree = tree;
        }
    }

    public static class A {
        CompressibleNode d;
        TreeMetrics m;

        public A(CompressibleNode d, TreeMetrics m) {
            this.d = d;
            this.m = m;
        }
    }

    public static class TreeContext2 extends TreeContext {

        Map<Integer, TreeSitterJavaTreeGenerator2.Data> hashMap = new HashMap<>();
        HashMap<Integer, TreeSitterJavaTreeGenerator2.Data> structHashMap = new HashMap<>();

        Map<Integer, TreeSitterJavaTreeGenerator2.Data2> hashMap2 = new HashMap<>();
        HashMap<Integer, TreeSitterJavaTreeGenerator2.Data2> structHashMap2 = new HashMap<>();

        public A createCompressedTree(Type type, String label, Acc acc, List<CompressibleNode> children, int pos, int length) {

            int hash = innerNodeHash(type, label, 2 * acc.sumSize + 1, acc.currentHash);
            int structureHash = innerNodeStructureHash(type, 2 * acc.sumSize + 1, acc.currentStructureHash);

            TreeSitterJavaTreeGenerator2.Data2 fromInHashMap = hashMap2.get(hash);
            if (fromInHashMap != null) {
                do {
                    // detect collisions
                    boolean collision = false;
                    if (!fromInHashMap.tree.getType().equals(type))
                        collision = true;
                    else if (fromInHashMap.tree instanceof GTComposableTypes.LabeledNode && label.equals(""))
                        collision = true;
                    else if (fromInHashMap.tree instanceof GTComposableTypes.LabeledNode && !((GTComposableTypes.LabeledNode) fromInHashMap.tree).getLabel().equals(label))
                        collision = true;
                    else if (fromInHashMap.tree instanceof CompressibleTree) {
                        List<CompressibleNode> otherChildren = ((CompressibleTree) fromInHashMap.tree).getCompressibleChildren();
                        if (otherChildren.size() != children.size())
                            collision = true;

                        for (int i = 0; i < children.size(); i++) {
                            if (otherChildren.get(i) != children.get(i)) {
                                collision = true;
                                break;
                            }
                        }
                    }

                    TreeMetrics metrics = new TreeMetrics(
                            acc.sumSize + 1,
                            acc.maxHeight + 1,
                            hash,
                            structureHash,
                            -1, -1);

                    if (collision) {
                        System.out.println("collision!");
                        Data2 next = fromInHashMap.next;
                        if (next != null) {
                            fromInHashMap = next;
                        } else break;
                    } else {
                        fromInHashMap.count++;
                        return new A(fromInHashMap.tree, metrics);
                    }
                } while (true);
            }

            TreeSitterJavaTreeGenerator2.Data2 fromInStructHashMap = structHashMap2.get(structureHash);

            CompressibleNode tree;
            if (label.equals(""))
                if (children.size() <= 0)
                    tree = FactoryCompressibleTree.create(type, pos, length);
                else
                    tree = FactoryCompressibleTree.create(type, children, pos, length);
            else if (children.size() <= 0)
                tree = FactoryCompressibleTree.create(type, label, pos, length);
            else
                tree = FactoryCompressibleTree.create(type, label, children, pos, length);

            TreeMetrics metrics = new TreeMetrics(
                    acc.sumSize + 1,
                    acc.maxHeight + 1,
                    hash,
                    structureHash,
                    -1, -1);

            TreeSitterJavaTreeGenerator2.Data2 data = new TreeSitterJavaTreeGenerator2.Data2(tree);
            if (fromInHashMap != null) {
                fromInHashMap.next = data;
            } else {
                hashMap2.put(hash, data);
            }
            if (fromInStructHashMap != null) {
                fromInStructHashMap.count++;
            } else {
                structHashMap2.put(structureHash, data);
            }

            return new A(tree, metrics);
        }

        public ITree createTree(Type type, String label, Acc acc, List<ITree> children, int pos, int length) {

            int hash = innerNodeHash(type, label, 2 * acc.sumSize + 1, acc.currentHash);
            int structureHash = innerNodeStructureHash(type, 2 * acc.sumSize + 1, acc.currentStructureHash);

            TreeSitterJavaTreeGenerator2.Data fromInHashMap = hashMap.get(hash);
            TreeSitterJavaTreeGenerator2.Data fromInStructHashMap = structHashMap.get(structureHash);

            if (fromInHashMap != null && fromInStructHashMap != null) {
                if (!fromInHashMap.tree.isIsomorphicTo(fromInStructHashMap.tree))
                    System.out.println("collision 1");
                else {
                    fromInHashMap.count++;
                    return fromInHashMap.tree;
                }
            } else if (fromInHashMap != null) {
                System.out.println("collision 0");
            }

            ITree tree = createTree(type, label);
//        tree.setChildren(children); // CAUTION for ATRee also set parents
            tree.getChildren().addAll(children);
            for (ITree child : children) {
                if (child.getParent() == null)
                    child.setParent(tree); // That way original keep its info and other by comparing parent should be able to know if they are duplicates
            }
            tree.setMetrics(new TreeMetrics(
                    acc.sumSize + 1,
                    acc.maxHeight + 1,
                    hash,
                    structureHash,
                    -1, -1));

            TreeSitterJavaTreeGenerator2.Data data = new TreeSitterJavaTreeGenerator2.Data(tree);
            hashMap.put(hash, data);
            structHashMap.put(structureHash, data);

            tree.setPos(pos);
            tree.setLength(length);

            return tree;
        }

        public Map<Integer, TreeSitterJavaTreeGenerator2.Data> getHashMap() {
            return Collections.unmodifiableMap(hashMap);
        }
    }


    public TreeSitterJavaTreeGenerator2() {
        super();
        this.parser = new Parser();
        parser.setLanguage(Languages.java());
    }

    @Override
    public TreeContext2 generate(Reader r) throws IOException {
        this.s = new String(readerToCharArray(r));
        try (Tree n = parser.parseString(s);) {
//            treeContext.setRoot(extractTreeContext(n.getRootNode()));
            A a = extractTreeContextCompressed(n.getRootNode());
            treeContext.setRoot(new DecompressedTree(a.d, null, -1));
            return treeContext;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    int currentDepth = 0;
    int currentPosition = 0;

    static class Acc {
        int sumSize = 0;
        int maxHeight = 0;
        int currentHash = 0;
        int currentStructureHash = 0;
    }

    private A extractTreeContextCompressed(Node node) {

        Acc acc = new Acc();

        List<CompressibleNode> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            A a = extractTreeContextCompressed(node.getChild(i));
            CompressibleNode child = a.d;
            TreeMetrics metrics = a.m;
            int exponent = 2 * acc.sumSize + 1;
            acc.currentHash += metrics.hash * TreeMetricComputer.hashFactor(exponent);
            acc.currentStructureHash += metrics.structureHash * TreeMetricComputer.hashFactor(exponent);
            acc.sumSize += metrics.size;
            if (metrics.height > acc.maxHeight)
                acc.maxHeight = metrics.height;
            children.add(child);
        }

        // postfix
        Type type = type(node.getType());
        String label = extractLabel(node);
        int pos = node.getStartByte();
        int length = node.getEndByte() - pos;

        return treeContext.createCompressedTree(type, label, acc, children, pos, length);
    }

    private ITree extractTreeContext(Node node) {
//        currentDepth++;

        Acc acc = new Acc();

        List<ITree> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            ITree child = extractTreeContext(node.getChild(i));
            TreeMetrics metrics = child.getMetrics();
            int exponent = 2 * acc.sumSize + 1;
            acc.currentHash += metrics.hash * TreeMetricComputer.hashFactor(exponent);
            acc.currentStructureHash += metrics.structureHash * TreeMetricComputer.hashFactor(exponent);
            acc.sumSize += metrics.size;
            if (metrics.height > acc.maxHeight)
                acc.maxHeight = metrics.height;
            children.add(child);
        }

        // postfix
        Type type = type(node.getType());
        String label = extractLabel(node);
        int pos = node.getStartByte();
        int length = node.getEndByte() - pos;

        return treeContext.createTree(type, label, acc, children, pos, length);
    }

    private String extractLabel(Node node) {
        if (node.getType().contains("identifier")) {
            return s.substring(node.getStartByte(), node.getEndByte());
        } else if (node.getChildCount() > 0) {
            return "";
        } else {
            String tmp = s.substring(node.getStartByte(), node.getEndByte());
            if (tmp.equals(node.getType()))
                return "";
            else return tmp;
        }
    }

    private static char[] readerToCharArray(Reader r) throws IOException {
        StringBuilder fileData = new StringBuilder();
        try (BufferedReader br = new BufferedReader(r)) {
            char[] buf = new char[10];
            int numRead = 0;
            while ((numRead = br.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }
        }
        return fileData.toString().toCharArray();
    }

    public static final String ENTER = "enter";
    public static final String LEAVE = "leave";

    public static int innerNodeHash(Type type, String label, int size, int middleHash) {
        return Objects.hash(type, label, ENTER)
                + middleHash
                + Objects.hash(type, label, LEAVE) * TreeMetricComputer.hashFactor(size);
    }

    public static int innerNodeStructureHash(Type type, int size, int middleHash) {
        return Objects.hash(type, ENTER)
                + middleHash
                + Objects.hash(type, LEAVE) * TreeMetricComputer.hashFactor(size);
    }

    @Override
    public void close() throws Exception {
        this.parser.close();
    }
}

class CompressibleNode implements GTComposableTypes.BasicNode, GTComposableTypes.PositionedNode {
    private Type type;
    private int pos;
    private int length;

    public CompressibleNode(Type type, int pos, int length) {
        this.type = type;
        this.pos = pos;
        this.length = length;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public int getPos() {
        return pos;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public String toString() {
        return String.format("%s [%d,%d]",
                getType(), getPos(), getEndPos());
    }

    @Override
    public String toTreeString() {
        return toString();
    }

    @Override
    public ITree deepCopy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isIsomorphicTo(ITree tree) {
        return tree.getLabel().equals("") && this.isIsoStructuralTo(tree);
    }

    public boolean isIsomorphicTo(CompressibleNode tree) {
        if (tree instanceof GTComposableTypes.LabeledNode)
            return ((GTComposableTypes.LabeledNode) tree).getLabel().equals("")
                    && GTComposableTypes.BasicNode.isIsoStructuralTo(this, tree);
        else return GTComposableTypes.BasicNode.isIsoStructuralTo(this, tree);
    }

    @Override
    public TreeMetrics getMetrics() {
        return new TreeMetrics(
                1,
                1,
                TreeSitterJavaTreeGenerator2.innerNodeHash(getType(), "", 1, 0),
                TreeSitterJavaTreeGenerator2.innerNodeStructureHash(getType(), 1, 0),
                -1,
                0
        );
    }

    @Override
    public Object getMetadata(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Map.Entry<String, Object>> getMetadata() {
        throw new UnsupportedOperationException();
    }
}

class CompressibleLabeledNode extends CompressibleNode implements GTComposableTypes.LabeledNode {
    String label;

    public CompressibleLabeledNode(Type type, String label, int pos, int length) {
        super(type, pos, length);
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        if (hasLabel())
            return String.format("%s: %s [%d,%d]",
                    getType(), getLabel(), getPos(), getEndPos());
        else
            return String.format("%s [%d,%d]",
                    getType(), getPos(), getEndPos());
    }

    @Override
    public TreeMetrics getMetrics() {
        return new TreeMetrics(
                1,
                1,
                TreeSitterJavaTreeGenerator2.innerNodeHash(getType(), label, 1, 0),
                TreeSitterJavaTreeGenerator2.innerNodeStructureHash(getType(), 1, 0),
                -1,
                0
        );
    }

    @Override
    public boolean isIsomorphicTo(CompressibleNode tree) {
        return GTComposableTypes.LabeledNode.isIsomorphicTo(this, tree);
    }

}

class CompressibleTree extends CompressibleNode implements GTComposableTypes.BasicArrayTree {

    private final List<CompressibleNode> children;

    public CompressibleTree(Type type, List<CompressibleNode> children, int pos, int length) {
        super(type, pos, length);
        this.children = children;
    }

    @Override
    public int getChildrenSize() {
        return children.size();
    }

    @Override
    public List<ITree> getChildren() {
        return null;
    }

    public List<CompressibleNode> getCompressibleChildren() {
        return Collections.unmodifiableList(children);
    }

    protected TreeMetrics metrics(String label) {
        TreeSitterJavaTreeGenerator2.Acc acc = new TreeSitterJavaTreeGenerator2.Acc();

        for (int i = 0; i < getChildrenSize(); i++) {
            CompressibleNode child = children.get(i);
            TreeMetrics metrics = child.getMetrics();
            int exponent = 2 * acc.sumSize + 1;
            acc.currentHash += metrics.hash * TreeMetricComputer.hashFactor(exponent);
            acc.currentStructureHash += metrics.structureHash * TreeMetricComputer.hashFactor(exponent);
            acc.sumSize += metrics.size;
            if (metrics.height > acc.maxHeight)
                acc.maxHeight = metrics.height;
        }

        int hash = TreeSitterJavaTreeGenerator2.innerNodeHash(getType(), label, 2 * acc.sumSize + 1, acc.currentHash);
        int structureHash = TreeSitterJavaTreeGenerator2.innerNodeStructureHash(getType(), 2 * acc.sumSize + 1, acc.currentStructureHash);

        return new TreeMetrics(
                acc.sumSize + 1,
                acc.maxHeight + 1,
                hash,
                structureHash,
                -1, -1);
    }

    @Override
    public TreeMetrics getMetrics() {
        return metrics("");
    }

    private void buildTreeString(StringBuilder builder, int depth) {
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
        builder.append(this);
        builder.append("\n");
        for (CompressibleNode child : getCompressibleChildren()) {
            if (child instanceof CompressibleTree)
                ((CompressibleTree) child).buildTreeString(builder, depth + 1);
            else {
                for (int i = 0; i < depth + 1; i++) {
                    builder.append("  ");
                }
                builder.append(child.toString());
                builder.append("\n");
            }
        }
    }

    @Override
    public String toTreeString() {
        final StringBuilder builder = new StringBuilder();
        buildTreeString(builder, 0);
        return builder.toString();
    }

    @Override
    public boolean isIsomorphicTo(CompressibleNode tree) {
        if (!GTComposableTypes.LabeledNode.isIsomorphicTo(this, tree))
            return false;

        if (this.getMetrics() != null && tree.getMetrics() != null && this.getMetrics().hash != tree.getMetrics().hash)
            return false;

        if (!(tree instanceof GTComposableTypes.BasicTree))
            return false;

        if (this.getChildrenSize() != ((GTComposableTypes.BasicTree) tree).getChildrenSize())
            return false;

        for (int i = 0; i < this.getChildrenSize(); i++) {
            boolean isChildrenIsomophic;
            CompressibleNode child = this.children.get(i);
            if (tree instanceof CompressibleTree) {
                CompressibleNode otherChild = ((CompressibleTree) tree).children.get(i);
                isChildrenIsomophic = child.isIsomorphicTo(otherChild);
            } else {
                ITree otherChild = ((GTComposableTypes.BasicTree) tree).getChild(i);
                isChildrenIsomophic = child.isIsomorphicTo(otherChild);
            }
            if (!isChildrenIsomophic)
                return false;
        }
        return true;
    }
}

class CompressibleLabeledTree extends CompressibleTree implements GTComposableTypes.LabeledNode {
    String label;

    public CompressibleLabeledTree(Type type, String label, List<CompressibleNode> children, int pos, int length) {
        super(type, children, pos, length);
        this.label = label;
    }

    @Override
    public String toString() {
        if (hasLabel())
            return String.format("%s: %s [%d,%d]",
                    getType(), getLabel(), getPos(), getEndPos());
        else
            return String.format("%s [%d,%d]",
                    getType(), getPos(), getEndPos());
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public TreeMetrics getMetrics() {
        return metrics(label);
    }

}

class FactoryCompressibleTree {
    public static CompressibleNode create(Type type, int pos, int length) {
        return new CompressibleNode(type, pos, length);
    }

    public static CompressibleNode create(Type type, String label, int pos, int length) {
        return new CompressibleLabeledNode(type, label, pos, length);
    }

    public static CompressibleNode create(Type type, List<CompressibleNode> children, int pos, int length) {
        return new CompressibleTree(type, children, pos, length);
    }

    public static CompressibleNode create(Type type, String label, List<CompressibleNode> children, int pos, int length) {
        return new CompressibleLabeledTree(type, label, children, pos, length);
    }
}

abstract class ADecompressedTree implements ITree {
    protected CompressibleNode compressed;

    public ADecompressedTree(CompressibleNode compressed) {
        this.compressed = compressed;
    }

    @Override
    public Type getType() {
        return compressed.getType();
    }

    @Override
    public String getLabel() {
        if (compressed instanceof GTComposableTypes.LabeledNode)
            return ((GTComposableTypes.LabeledNode) compressed).getLabel();
        else return NO_LABEL;
    }

    /**
     * Lossy but similar {@see GTComposableTypes.PositionedNode.getPos()}
     */
    @Override
    public int getPos() {
        ITree parent = getParent();
        int indexInParent = positionInParent();
        if (parent == null && indexInParent != -1)
            throw new RuntimeException();
        if (parent == null)
            return -1 * 100 + indexInParent;
        if (indexInParent == 0)
            return parent.getPos() + compressed.getPos();
        ITree leftS = parent.getChild(indexInParent - 1);
        return leftS.getEndPos() + compressed.getPos();
    }

    /**
     * Lossy but similar {@see GTComposableTypes.PositionedNode.getLength()}
     */
    @Override
    public int getLength() {
        return compressed.getLength();
    }

    @Override
    public int getChildrenSize() {
        if (compressed instanceof GTComposableTypes.BasicTree)
            return ((GTComposableTypes.BasicTree) compressed).getChildrenSize();
        return 0;
    }

    @Override
    public List<ITree> getChildren() {
        if (!(compressed instanceof CompressibleTree))
            return Collections.EMPTY_LIST;
        List<ITree> r = new ArrayList<>();
        List<CompressibleNode> children = ((CompressibleTree) compressed).getCompressibleChildren();
        for (int i = 0; i < children.size(); i++) {
            r.add(new DecompressedTree(children.get(i), this, i));
        }
        return Collections.unmodifiableList(r);
    }

    @Override
    public ParitiallyDecompressedTree getChild(int ...position) {
        if (!(compressed instanceof CompressibleTree))
            return null;
        CompressibleTree curr = (CompressibleTree) compressed;
        for (int i : position) {
            List<CompressibleNode> children = ((CompressibleTree) compressed).getCompressibleChildren();
            curr = (CompressibleTree)children.get(i);
        }
        return new ParitiallyDecompressedTree(curr, this, position);
    }

    @Override
    public DecompressedTree getChild(int position) {
        if (!(compressed instanceof CompressibleTree))
            return null;
        List<CompressibleNode> children = ((CompressibleTree) compressed).getCompressibleChildren();
        return new DecompressedTree(children.get(position), this, position);
    }

    @Override
    public String toString() {
        if (hasLabel())
            return String.format("%s: %s [%d,%d]",
                    getType(), getLabel(), getPos(), getEndPos());
        else
            return String.format("%s [%d,%d]",
                    getType(), getPos(), getEndPos());
    }

    @Override
    public String toTreeString() {
        return TreeIoUtils.toShortText(this).toString();
    }

    @Override
    public TreeMetrics getMetrics() {
        ITree parent = getParent();
        int indexInParent = positionInParent();
        final TreeMetrics metrics = compressed.getMetrics();
        int position;
        if (parent == null)
            position = 0;
        if (indexInParent == 0)
            position = parent.getMetrics().position;
        else {
            TreeMetrics m = parent.getChild(indexInParent - 1).getMetrics();
            position = m.position + m.size;
        }
        return new TreeMetrics(
                metrics.size,
                metrics.height,
                metrics.hash,
                metrics.structureHash,
                parent.getMetrics().depth + 1,
                position
        );
    }

    @Override
    public Object getMetadata(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Map.Entry<String, Object>> getMetadata() {
        return Collections.emptyIterator();
    }

    @Override
    public void setParent(ITree parent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addChild(ITree t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setChildren(List<ITree> children) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void insertChild(ITree t, int position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setType(Type type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMetrics(TreeMetrics metrics) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object setMetadata(String key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLabel(String label) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setParentAndUpdateChildren(ITree parent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPos(int pos) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLength(int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isIsomorphicTo(ITree tree) { // TODO CAUTION only true if compressed are done with hash
        if (tree instanceof ADecompressedTree && compressed == ((ADecompressedTree) tree).compressed)
            return true;
        return ITree.super.isIsomorphicTo(tree);
    }
}

class DecompressedTree extends ADecompressedTree {
    ITree parent;
    int indexInParent;
    int position = -1;

    public DecompressedTree(CompressibleNode compressed, ITree parent, int indexInParent) {
        super(compressed);
        if (parent == null && indexInParent != -1)
            throw new RuntimeException();
        this.parent = parent;
        this.indexInParent = indexInParent;
    }

    @Override
    public ITree deepCopy() {
        ITree parent = getParent();
        int indexInParent = positionInParent();
        return new DecompressedTree(compressed, parent, indexInParent);
    }

    @Override
    public int getPos() {
        if (position!=-1)
            return position;
        return position = super.getPos();
    }

    @Override
    public ITree getParent() {
        return parent;
    }

    @Override
    public int positionInParent() {
        return indexInParent;
    }
}

class ParitiallyDecompressedTree extends ADecompressedTree {
    final ITree acsendent;
    final byte[] offsets;
    static final int LARGEST = 32;

    public ParitiallyDecompressedTree(CompressibleNode compressed, ITree ascendant, int[] path) {
        this(compressed,ascendant,fillOffsets(path));
    }

    public ParitiallyDecompressedTree(CompressibleNode compressed, ITree ascendant, byte[] compressedPath) {
        super(compressed);
        this.acsendent = ascendant;
        this.offsets = compressedPath;
    }

    @Override
    public ITree deepCopy() {
        return new ParitiallyDecompressedTree(compressed, acsendent, offsets);
    }

    @Override
    public ITree getParent() {
        if (false) {
            ITree result = acsendent;
            for(int i : decompressOffsets(offsets)){
                result = result.getChild(i);
            }
            return result;
        } else {
            int[] arr = decompressOffsets(offsets);
            return (ITree) acsendent.getChild(Arrays.copyOfRange(arr,0,arr.length-1));
        }
    }

    @Override
    public List<ITree> getParents() {
        ITree tmp = acsendent;
        List<ITree> result = new ArrayList<>();
        for(int i : decompressOffsets(offsets)){
            result.add(tmp);
            tmp = tmp.getChild(i);
        }
        return result;
    }

    @Override
    public int positionInParent() {
        return lastPosition(offsets);
    }

    static int lastPosition(byte[] offsets) {
        if (offsets.length<=0)
            return -1;
        boolean half = true;
        boolean once = false;
        int r = 0;
        int i = offsets.length;
        do {
            if (half)
                i--;
            byte curr = offsets[i];
            if (half) {
                if ((curr & (byte)0x0f) == 0x0f) {
                    if (!once) {
                        half = !half;
                        continue;
                    }
                    r+=32;
                } else if ((curr & (byte)0x0f) == 0x0e) {
                    r+=14;
                } else {
                    if (once) break;
                    once = true;
                    r += ((int) curr) & 0x0f;
                }
            } else {
                if ((curr & (byte)0xf0) == (byte)(0xf0)) {
                    if (!once) {
                        half = !half;
                        continue;
                    }
                    r+=32;
                } else if ((curr & (byte)0xf0) == (byte)(0xe0)) {
                    r+=14;
                } else {
                    if (once) break;
                    once = true;
                    r += (((int) curr) & 0xf0) >> 4;
                }
            }
            half = !half;
        } while (i>0 || !half);
        return r;
    }

    static byte[] fillOffsets(int[] path) {
        byte[] result = new byte[path.length];
        int oI = 0;
        boolean half = false;
        for (int pI = 0; pI < path.length; pI++) {
            int curr = path[pI];
            boolean b=true;
            while (b) {
                if (oI>=result.length)
                    result = Arrays.copyOf(result, oI + (path.length-pI) * 2);
                byte r;
                if (curr >= LARGEST) {
                    r = (byte)(half ?  0x0f : 0xf0);
                    curr-=LARGEST;
                } else if (curr >= 14) {
                    r = (byte)(half ?  0x0e : 0xe0);
                    curr-=14;
                } else {
                    byte c = (byte) curr;
                    r = (byte)(half ? (c & 0xf) : (c & 0xf) << 4);
                    b = false;
                }
                result[oI] |= r;
                if (half)
                    oI++;
                half = !half;
            }
        }
        if (half)
            result[oI] |= 0x0f;
        result = Arrays.copyOf(result, half ? oI+1 : oI);
        return result;
    }


    static int[] decompressOffsets(byte[] offsets) {
        int[] result = new int[offsets.length*2];
        int pI = 0;
        boolean half = false;
        boolean each = false;
        int r = 0;
        int oI = 0;
        while( oI < offsets.length) {
            byte curr = offsets[oI];
            if (half) {
                if ((curr & (byte)0x0f) == 0x0f) {
                    r+=LARGEST;
                    each = false;
                } else if ((curr & (byte)0x0f) == 0x0e) {
                    r+=14;
                    each = false;
                } else {
                    r += curr & 0x0f;
                    result[pI] = r;
                    pI++;
                    r=0;
                    each = true;
                }
            } else {
                if ((curr & (byte)0xf0) == (byte)(0xf0)) {
                    r+=LARGEST;
                    each = false;
                } else if ((curr & (byte)0xf0) == (byte)(0xe0)) {
                    r+=14;
                    each = false;
                } else {
                    r += (((int) curr) & 0xf0) >> 4;
                    result[pI] = r;
                    pI++;
                    r=0;
                    each = true;
                }
            }
            if (half)
                oI++;
            half = !half;
            if (pI>=result.length)
                result = Arrays.copyOf(result, pI + (offsets.length-oI) * 2 * 2 + 1);
        }
        if (each) {
            result[pI] = r;
        }
        result = Arrays.copyOf(result, pI);
        return result;
    }

}