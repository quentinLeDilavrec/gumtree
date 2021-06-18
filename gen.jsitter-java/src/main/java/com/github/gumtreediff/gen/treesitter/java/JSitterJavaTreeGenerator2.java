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

import com.github.gumtreediff.gen.Register;
import com.github.gumtreediff.gen.Registry;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.io.TreeIoUtils;
import com.github.gumtreediff.tree.*;
import jsitter.api.*;
import jsitter.api.Tree;
import jsitter.impl.TSZipper;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectByteHashMap;

import java.io.*;
import java.util.*;

import static com.github.gumtreediff.tree.TypeSet.type;

@Register(id = "jsitter-java", accept = "\\.java$", priority = Registry.Priority.LOW)
public class JSitterJavaTreeGenerator2 extends TreeGenerator implements AutoCloseable {

    private Language<NodeType> lang;
    private Parser parser;
    private TreeContextCompressing treeContext;
    private String s;

    public JSitterJavaTreeGenerator2() {
        this(new TreeContextCompressing.TreeContextCompressingImpl());
    }
    public JSitterJavaTreeGenerator2(TreeContextCompressing treeContext) {
        super();
        NodeType nodetype = new NodeType("source_file");
        this.lang = Language.load(
                nodetype,
                "java",
                "tree_sitter_java",
                "libtsjava",
                Language.class.getClassLoader());
        this.lang.register(nodetype);
        lang.getRootNodeType().getName();
        this.treeContext = treeContext;
    }

    enum Has {
        DOWN, UP, RIGHT;
    }

    @Override
    public TreeContext generate(Reader r) throws IOException {

        TreeContextCompressing.FullNode fullNode = generate(new String(readerToCharArray(r)));
        treeContext.setRoot(new DecompressedTree(fullNode.node, null, -1));

        return treeContext;
    }

    public TreeContextCompressing.FullNode generate(String s) throws IOException {
        this.s = s;
        this.parser = lang.parser();
        StringText text = new StringText(s);
        Tree<NodeType> tree = parser.parse(text, null);

        Zipper<?> zipper = tree.getRoot().zipper();

        return new TreeBuilderCompressed().buildFrom(zipper);
    }

    static class AccC extends TreeContextCompressing.Acc {
        public String indentation;
        List<CompressibleNode> children = new ArrayList<>();

        public void add(TreeContextCompressing.FullNode fullNode) {
            super.add(fullNode);
            children.add(fullNode.node);
        }
    }

    class TreeBuilderCompressed {
        Stack<AccC> aStack = new Stack<>();
        int position = 0;
        int depth = 0;

        {
            aStack.add(new AccC());
        }

        void inc() {
            position++;
            depth++;
            aStack.add(new AccC());
        }

        public AccC pop() {
            depth--;
            return aStack.pop();
        }

        public void acc(TreeContextCompressing.FullNode fullNode) {
            AccC acc = aStack.peek();
            acc.add(fullNode);
        }

        TreeContextCompressing.FullNode create(Zipper<?> node) {
            Type type = type(node.getType().getName());
            int pos = node.getByteOffset() / 2;
            int length = node.getByteSize() / 2;
            int padding = node.getPadding() / 2;
            String label = extractLabel((TSZipper<?>) node, pos, length);
            if(type.toString().equals("identifier") && length == 0)
                System.out.println();
            AccC popedAcc = pop();
            if (!aStack.isEmpty() && padding > 0 && aStack.peek().children.size() > 0) {
                String spaces = s.substring(pos - padding, pos);
                TreeContextCompressing.FullNode spacesLeaf = treeContext.createCompressedTree(type("gumtree_spaces"), spaces, popedAcc, Collections.emptyList(), spaces.length()).locate(depth, position);
                acc(spacesLeaf);
                inc();
                pop();
            }
            return treeContext.createCompressedTree(type, label, popedAcc, popedAcc.children, length).locate(depth, position);
        }

        TreeContextCompressing.FullNode buildFrom(Zipper<?> zipper) {
            Has has = Has.DOWN;
            while (zipper != null) {
                Zipper<?> down = has == Has.UP ? null : zipper.down();
                if (down != null) {
                    inc();
                    String indentation = aStack.peek().indentation; // TODO
                    zipper = down;
                    has = Has.DOWN;
                } else {
                    TreeContextCompressing.FullNode fullNode = create(zipper);

                    Zipper<?> right = zipper.right();
                    if (right != null) {
                        acc(fullNode);
                        inc();

                        zipper = right;
                        has = Has.RIGHT;
                    } else {
                        // postOrder
                        zipper = zipper.up();
                        if (zipper == null) {
//                            System.err.println(fullNode.metrics.height());
//                            System.err.println(fullNode.metrics.size());
                            try {
                                return fullNode;
                            } catch (Exception e){
                            }
                            return fullNode;
                        } else {
                            acc(fullNode);
                        }
                        has = Has.UP;
                    }
                }
            }
            return null;
        }
    }

    private String extractLabel(TSZipper<?> node, int pos, int length) {
        String name = node.getType().getName();
        if (name.equals("scoped_identifier")) {
            return "";
        } else if (name.contains("identifier")) {
            return s.substring(pos, pos + length);
        } else if (name.contains("literal")) {
            return s.substring(pos, pos + length);
        } else if (name.contains("comment")) {
            return s.substring(pos, pos + length);
        } else if (node.visibleChildCount() > 0) {
            return "";
        } else {
            String sub = s.substring(pos, pos + length);
            if (sub.equals(node.getType().getName()))
                return "";
            return s.substring(pos,pos+length);
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

    @Override
    public void close() throws Exception {
    }
}

class Utils {
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

    public static byte[] compressSpaces(String spaces) {
        byte[] r = new byte[0];

        return r;
    }

    public static Object debugSpaces(String label) {
        return  label.replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t").replaceAll(" ", "\\s");
    }

}

class SpacesStore {
    static SpacesStore INSTANCE = new SpacesStore();
    static SpacesStore INSTANCE_NLS = new SpacesStore();
    private final String[] index2Spaces = new String[Byte.MAX_VALUE*2];
    private final ObjectByteHashMap<String> spaces2Index = new ObjectByteHashMap<>();

    String spaces(byte index) {
        assert index < spaces2Index.size();
        return index2Spaces[Byte.toUnsignedInt(index)];
    }

    byte index(String spaces) {
        if (spaces2Index.containsKey(spaces))
            return spaces2Index.get(spaces);
        int size = spaces2Index.size();
        assert size < Byte.MAX_VALUE*2: Arrays.toString(spaces.toCharArray());
        index2Spaces[size] = spaces;
        spaces2Index.put(spaces, (byte)size);
        return (byte) size;
    }

    public int size() {
        return spaces2Index.size();
    }
}

abstract class CompressibleNode implements GTComposableTypes.BasicNode, Serializable { // TODO Serializable
    private final Type type;
//    private final byte nls;
//    private final byte spaces;

    public CompressibleNode(Type type) {
        this.type = type;
//        int lastNL = spaces.lastIndexOf("\n");
//        if (lastNL==-1) lastNL = 0; else lastNL++;
//        this.nls = SpacesStore.INSTANCE_NLS.index(lastNL==0 ? "" : spaces.substring(0,lastNL-1));
//        this.spaces = SpacesStore.INSTANCE.index(spaces.substring(lastNL));
    }

    @Override
    public Type getType() {
        return type;
    }

//    public String getSpaces() {
//        return SpacesStore.INSTANCE_NLS.spaces(nls)+SpacesStore.INSTANCE.spaces(spaces);
//    }

    @Override
    public int getLength() {
        return type.toString().length();
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
        return TreeMetrics.create(
                1,
                1,
                Utils.innerNodeHash(getType(), "", 1, 0),
                Utils.innerNodeStructureHash(getType(), 1, 0),
                0,
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

    public abstract void serialize(Writer writer) throws IOException;
}

class CompressibleLeaf extends CompressibleNode {

    public CompressibleLeaf(Type type) {
        super(type);
    }

    @Override
    public String toString() {
        return String.format("%s", getType());
    }

    public void serialize(Writer writer) throws IOException {
        writer.write(getType().toString());
    }
}

class CompressibleLabeledNode extends CompressibleNode implements GTComposableTypes.LabeledNode {
    String label;

    public CompressibleLabeledNode(Type type, String label) {
        super(type);
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        if (hasLabel())
            return String.format("%s: %s", getType(), type("gumtree_spaces").equals(getType()) ? Utils.debugSpaces(getLabel()) : getLabel());
        else
            return String.format("%s", getType());
    }

    @Override
    public int getLength() {
        return label.length();
    }

    @Override
    public TreeMetrics getMetrics() {
        return TreeMetrics.create(
                1,
                1,
                Utils.innerNodeHash(getType(), label, 1, 0),
                Utils.innerNodeStructureHash(getType(), 1, 0),
                -1,
                0
        );
    }

    @Override
    public boolean isIsomorphicTo(CompressibleNode tree) {
        return GTComposableTypes.LabeledNode.isIsomorphicTo(this, tree);
    }

    public void serialize(Writer writer) throws IOException {
        writer.write(getLabel());
    }
}

class CompressibleTree extends CompressibleNode implements GTComposableTypes.BasicArrayTree {

    protected final List<CompressibleNode> children;
    private final int length;

    public CompressibleTree(Type type, List<CompressibleNode> children, int length) {
        super(type);
        this.children = children;
        this.length = length;
    }

    @Override
    public int getLength() {
        return length;
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
        TreeContextCompressing.Acc acc = new TreeContextCompressing.Acc();

        for (int i = 0; i < getChildrenSize(); i++) {
            CompressibleNode child = children.get(i);
            TreeMetrics metrics = child.getMetrics();
            int exponent = 2 * acc.sumSize + 1;
            acc.currentHash += metrics.hash() * TreeMetricComputer.hashFactor(exponent);
            acc.currentHash += metrics.hash() * TreeMetricComputer.hashFactor(exponent);
            acc.currentStructureHash += metrics.structureHash() * TreeMetricComputer.hashFactor(exponent);
            acc.sumSize += metrics.size();
            if (metrics.height() > acc.maxHeight)
                acc.maxHeight = metrics.height();
        }

        int hash = Utils.innerNodeHash(getType(), label, 2 * acc.sumSize + 1, acc.currentHash);
        int structureHash = Utils.innerNodeStructureHash(getType(), 2 * acc.sumSize + 1, acc.currentStructureHash);

        return TreeMetrics.create(
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

        if (this.getMetrics() != null && tree.getMetrics() != null && this.getMetrics().hash() != tree.getMetrics().hash())
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

    public void serialize(Writer writer) throws IOException {
        for(CompressibleNode child: children) {
            child.serialize(writer);
        }
    }
}

class CompressibleLabeledTree extends CompressibleTree implements GTComposableTypes.LabeledNode {
    String label;

    public CompressibleLabeledTree(Type type, String label, List<CompressibleNode> children, int length) {
        super(type, children, length);
        this.label = label;
    }

    @Override
    public String toString() {
        if (hasLabel())
            return String.format("%s: %s", getType(), type("gumtree_spaces").equals(getType()) ? Utils.debugSpaces(getLabel()) : getLabel());
        else
            return String.format("%s", getType());
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public TreeMetrics getMetrics() {
        return metrics(label);
    }

    public void serialize(Writer writer) throws IOException {
        writer.write(getLabel());
        for(CompressibleNode child: children) {
            child.serialize(writer);
        }
    }
}

class CompressibleNodeFactory {
    public static CompressibleNode create(Type type) {
        return new CompressibleLeaf(type);
    }

    public static CompressibleNode create(Type type, String label) {
        return new CompressibleLabeledNode(type, label);
    }

    public static CompressibleNode create(Type type, List<CompressibleNode> children, int length) {
        return new CompressibleTree(type, children, length);
    }

    public static CompressibleNode create(Type type, String label, List<CompressibleNode> children, int length) {
        return new CompressibleLabeledTree(type, label, children, length);
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
            return 0;//-1 * 100 + indexInParent;
        if (indexInParent == 0)
            return parent.getPos();
        ITree leftS = parent.getChild(indexInParent - 1);
        return leftS.getEndPos();
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
    public ParitiallyDecompressedTree getChild(int... position) {
        if (!(compressed instanceof CompressibleTree))
            return null;
        CompressibleTree curr = (CompressibleTree) compressed;
        for (int i : position) {
            List<CompressibleNode> children = ((CompressibleTree) compressed).getCompressibleChildren();
            curr = (CompressibleTree) children.get(i);
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
                    getType(), type("gumtree_spaces").equals(getType()) ? Utils.debugSpaces(getLabel()) : getLabel(), getPos(), getEndPos());
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
        else if (indexInParent == 0)
            position = parent.getMetrics().position();
        else {
            TreeMetrics m = parent.getChild(indexInParent - 1).getMetrics();
            position = m.position() + m.size();
        }
        return TreeMetrics.create(
                metrics.size(),
                metrics.height(),
                metrics.hash(),
                metrics.structureHash(),
                (parent == null ? 0 : parent.getMetrics().depth()) + 1,
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
        if (position != -1)
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
        this(compressed, ascendant, fillOffsets(path));
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
            for (int i : decompressOffsets(offsets)) {
                result = result.getChild(i);
            }
            return result;
        } else {
            int[] arr = decompressOffsets(offsets);
            return (ITree) acsendent.getChild(Arrays.copyOfRange(arr, 0, arr.length - 1));
        }
    }

    @Override
    public List<ITree> getParents() {
        ITree tmp = acsendent;
        List<ITree> result = new ArrayList<>();
        for (int i : decompressOffsets(offsets)) {
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
        if (offsets.length <= 0)
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
                if ((curr & (byte) 0x0f) == 0x0f) {
                    if (!once) {
                        half = !half;
                        continue;
                    }
                    r += 32;
                } else if ((curr & (byte) 0x0f) == 0x0e) {
                    r += 14;
                } else {
                    if (once) break;
                    once = true;
                    r += ((int) curr) & 0x0f;
                }
            } else {
                if ((curr & (byte) 0xf0) == (byte) (0xf0)) {
                    if (!once) {
                        half = !half;
                        continue;
                    }
                    r += 32;
                } else if ((curr & (byte) 0xf0) == (byte) (0xe0)) {
                    r += 14;
                } else {
                    if (once) break;
                    once = true;
                    r += (((int) curr) & 0xf0) >> 4;
                }
            }
            half = !half;
        } while (i > 0 || !half);
        return r;
    }

    static byte[] fillOffsets(int[] path) {
        byte[] result = new byte[path.length];
        int oI = 0;
        boolean half = false;
        for (int pI = 0; pI < path.length; pI++) {
            int curr = path[pI];
            boolean b = true;
            while (b) {
                if (oI >= result.length)
                    result = Arrays.copyOf(result, oI + (path.length - pI) * 2);
                byte r;
                if (curr >= LARGEST) {
                    r = (byte) (half ? 0x0f : 0xf0);
                    curr -= LARGEST;
                } else if (curr >= 14) {
                    r = (byte) (half ? 0x0e : 0xe0);
                    curr -= 14;
                } else {
                    byte c = (byte) curr;
                    r = (byte) (half ? (c & 0xf) : (c & 0xf) << 4);
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
        result = Arrays.copyOf(result, half ? oI + 1 : oI);
        return result;
    }


    static int[] decompressOffsets(byte[] offsets) {
        int[] result = new int[offsets.length * 2];
        int pI = 0;
        boolean half = false;
        boolean each = false;
        int r = 0;
        int oI = 0;
        while (oI < offsets.length) {
            byte curr = offsets[oI];
            if (half) {
                if ((curr & (byte) 0x0f) == 0x0f) {
                    r += LARGEST;
                    each = false;
                } else if ((curr & (byte) 0x0f) == 0x0e) {
                    r += 14;
                    each = false;
                } else {
                    r += curr & 0x0f;
                    result[pI] = r;
                    pI++;
                    r = 0;
                    each = true;
                }
            } else {
                if ((curr & (byte) 0xf0) == (byte) (0xf0)) {
                    r += LARGEST;
                    each = false;
                } else if ((curr & (byte) 0xf0) == (byte) (0xe0)) {
                    r += 14;
                    each = false;
                } else {
                    r += (((int) curr) & 0xf0) >> 4;
                    result[pI] = r;
                    pI++;
                    r = 0;
                    each = true;
                }
            }
            if (half)
                oI++;
            half = !half;
            if (pI >= result.length)
                result = Arrays.copyOf(result, pI + (offsets.length - oI) * 2 * 2 + 1);
        }
        if (each) {
            result[pI] = r;
        }
        result = Arrays.copyOf(result, pI);
        return result;
    }

}

class ReSerializer {
    private final TreeContextCompressing ctx;
    final StringBuilder builder;
    int position = 0;
    int indent = 0;

    ReSerializer(TreeContextCompressing ctx) {
        this.ctx = ctx;
        this.builder = new StringBuilder();
    }

    void program(ITree root) {
        assert root.getType().toString().equals("program");
        for (ITree child : root.getChildren()) {
            programElement(child);
        }
    }

    boolean nl = true;

    void nl() {
        builder.append("\n");
        position++;
        nl = true;
    }

    void write(String s) {
        if (nl)
            indent(indent);
        nl = false;
        builder.append(s);
        position += s.length();
    }

    private void indent(int indent) {
        for (int i = 0; i < indent; i++) {
            builder.append("  ");
        }
    }

    private void programElement(ITree node) {
        switch (node.getType().toString()) {
            case "comment": {
                comment(node);
                break;
            }
            case "package_declaration": {
                package_declaration(node);
                break;
            }
            case "import_declaration": {
                import_declaration(node);
                break;
            }
            case "class_declaration": {
                class_declaration(node);
                break;
            }
            default: {
                other(node);
            }
        }
    }

    private void other(ITree node) {
        write(node.toTreeString());
        nl();
    }

    private void class_declaration(ITree node) {
        int i = 0;
        ITree child = node.getChild(i);
        if (child.getType().toString().equals("modifiers")) {
            modifiers(child);
            i++;
            child = node.getChild(i);
        }
        if (child.getType().toString().equals("class")) {
            token(child);
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("identifier")) {
            general_identifier(child);
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("superclass")) {
            superclass(child);
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("class_body")) {
            class_body(child);
        }
        nl();
    }

    private void class_body(ITree node) {
        int i = 0;
        write("{");
        nl();
        i++;
        indent++;
        int size = node.getChildrenSize() - 1;
        while (i < size) {
            classMember(node.getChild(i));
            i++;
        }
        indent--;
        write("}");
        nl();
    }

    private void classMember(ITree node) {
        switch (node.getType().toString()) {
            case "method_declaration": {
                method_declaration(node);
                break;
            }
            case "constructor_declaration": {
                constructor_declaration(node);
                break;
            }
            default: {
                other(node);
            }
        }
    }

    private void method_declaration(ITree node) {
        int i = 0;
        ITree child = node.getChild(i);
        if (child.getType().toString().equals("modifiers")) {
            modifiers(child);
            i++;
            child = node.getChild(i);
        }
        if (child.getType().toString().equals("type_identifier")) {
            general_identifier(child);
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("void_type")) {
            write("void");
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("identifier")) {
            general_identifier(child);
            i++;
            child = node.getChild(i);
        }
        if (child.getType().toString().equals("formal_parameters")) {
            formal_parameters(child);
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("throws")) {
            throwss(child);
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("block")) {
            block(child);
        }
    }

    private void throwss(ITree node) {
        int i = 0;
        token(node.getChild(i));
        space();
        i++;
        int size = node.getChildrenSize();
        while (i < size) {
            if (i > 1)
                write(", ");
            general_identifier(node.getChild(i));
            i += 2;
        }
    }

    private void space() {
        write(" ");
    }

    private void constructor_declaration(ITree node) {
        int i = 0;
        ITree child = node.getChild(i);
        if (child.getType().toString().equals("modifiers")) {
            modifiers(child);
            i++;
            child = node.getChild(i);
        }
        if (child.getType().toString().equals("identifier")) {
            general_identifier(child);
            i++;
            child = node.getChild(i);
        }
        if (child.getType().toString().equals("formal_parameters")) {
            formal_parameters(child);
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("constructor_body")) {
            block(child);
        }
    }

    private void formal_parameters(ITree node) {
        int i = 0;
        write("(");
        i++;
        int size = node.getChildrenSize() - 1;
        while (i < size) {
            if (i > 1)
                write(", ");
            formal_parameter(node.getChild(i));
            i += 2;
        }
        write(")");
    }

    private void formal_parameter(ITree node) {
        general_identifier(node.getChild(0));
        general_identifier(node.getChild(1));
    }

    private void block(ITree node) {
        int i = 0;
        write("{");
        nl();
        i++;
        indent++;
        int size = node.getChildrenSize() - 1;
        while (i < size) {
            statement(node.getChild(i));
            i++;
        }
        indent--;
        write("}");
        nl();
    }

    private void statement(ITree node) {
        switch (node.getType().toString()) {
            case "expression_statement": {
                expression(node.getChild(0));
                write(";");
                nl();
                break;
            }
            case "local_variable_declaration": {
                local_variable_declaration(node);
                break;
            }
            case "try_statement": {
                try_statement(node);
                break;
            }
            case "return_statement": {
                token(node.getChild(0));
                space();
                expression(node.getChild(1));
                write(";");
                nl();
                break;
            }
            case "enhanced_for_statement": {
                enhanced_for_statement(node);
                break;
            }
            case "if_statement": {
                if_statement(node);
                break;
            }
            case "block": {
                block(node);
                break;
            }
            case "explicit_constructor_invocation": {
                explicit_constructor_invocation(node);
                write(";");
                nl();
                break;
            }
            default: {
                other(node);
            }
        }
    }

    private void if_statement(ITree node) {
        token(node.getChild(0));
        argument_list(node.getChild(1));
        space();
        statement(node.getChild(2));
        int i = 3;
        int size = node.getChildrenSize();
        while (i < size) {
            token(node.getChild(i));
            space();
            i++;
            statement(node.getChild(i));
            i++;
        }
    }

    private void enhanced_for_statement(ITree node) {
        token(node.getChild(0));
        token(node.getChild(1));
        general_identifier(node.getChild(2));
        space();
        general_identifier(node.getChild(3));
        space();
        token(node.getChild(4));
        space();
        expression(node.getChild(5));
        token(node.getChild(6));
        block(node.getChild(7));
    }

    private void try_statement(ITree node) {
        int i = 0;
        token(node.getChild(i));
        space();
        i++;
        {
            block(node.getChild(i));
            i++;
        }
        int size = node.getChildrenSize();
        while (i < size) {
            ITree child = node.getChild(i);
            if (child.getType().toString().equals("catch_clause")) {
                catch_clause(child);
            } else {
                //if (child.getType().toString().equals("finally_clause")) {
                other(child);//finally_clause(child);
            }
            i++;
        }
        nl();
    }

    private void catch_clause(ITree node) {
        token(node.getChild(0));
        token(node.getChild(1));
        catch_formal_parameter(node.getChild(2));
        token(node.getChild(3));
        block(node.getChild(4));
    }

    private void catch_formal_parameter(ITree node) {
        catch_type(node.getChild(0));
        space();
        identifier(node.getChild(1));
    }

    private void catch_type(ITree node) {
        int i = 0;
        int size = node.getChildrenSize();
        while (i < size) {
            if (i > 1) {
                space();
                write("|");
                space();
            }
            general_identifier(node.getChild(i));
            i += 2;
        }

    }

    private void local_variable_declaration(ITree node) {
        int i = 0;
        ITree child = node.getChild(i);
        if (child.getType().toString().equals("modifiers")) {
            modifiers(child);
            i++;
            child = node.getChild(i);
        }
        if (child.getType().toString().equals("type_identifier")) {
            general_identifier(child);
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("void_type")) {
            write("void");
            i++;
            child = node.getChild(i);
            space();
        }
        if (child.getType().toString().equals("variable_declarator")) {
            variable_declarator(child);
        }
        write(";");
        nl();
    }

    private void variable_declarator(ITree node) {
        general_identifier(node.getChild(0));
        space();
        token(node.getChild(1));
        space();
        expression(node.getChild(2));
    }

    private void explicit_constructor_invocation(ITree node) {
        token(node.getChild(0));
        argument_list(node.getChild(1));
    }

    private void argument_list(ITree node) {
        int i = 0;
        write("(");
        i++;
        int size = node.getChildrenSize() - 1;
        while (i < size) {
            if (i > 1)
                write(", ");
            expression(node.getChild(i));
            i += 2;
        }
        write(")");
    }

    private void expression(ITree node) {
        if (node.getType().toString().endsWith("_literal")) {
            write(node.getLabel());
            return;
        }
        switch (node.getType().toString()) {
            case "update_expression": {
                update_expression(node);
                break;
            }
            case "identifier":
            case "field_access": {
                general_identifier(node);
                break;
            }
            case "method_invocation": {
                method_invocation(node);
                break;
            }
            case "assignment_expression": {
                general_identifier(node.getChild(0));
                token(node.getChild(1));
                expression(node.getChild(2));
                break;
            }
            case "binary_expression": {
                binary_expression(node);
                break;
            }
            case "object_creation_expression": {
                object_creation_expression(node);
                break;
            }
            default: {
                other(node);
            }
        }
    }

    private void binary_expression(ITree node) {
        int i = 0;
        int size = node.getChildrenSize();
        expression(node.getChild(i));
        i++;
        while (i < size) {
            space();
            token(node.getChild(i));
            i++;
            space();
            expression(node.getChild(i));
            i++;
        }
    }

    private void object_creation_expression(ITree node) {
        token(node.getChild(0));
        space();
        general_identifier(node.getChild(1));
        argument_list(node.getChild(2));
    }

    private void method_invocation(ITree node) {
        if (node.getChildrenSize() == 4) {
            expression(node.getChild(0));
            write(".");
            general_identifier(node.getChild(2));
            argument_list(node.getChild(3));
        } else {
            general_identifier(node.getChild(0));
            argument_list(node.getChild(1));
        }
    }

    private void update_expression(ITree node) {
        general_identifier(node.getChild(0));
        token(node.getChild(1));
    }

    private void superclass(ITree node) {
        token(node.getChild(0));
        space();
        general_identifier(node.getChild(1));
    }

    private void token(ITree node) {
        write(node.getType().name);
    }

    private void modifiers(ITree node) {
        int i = 0;
        int size = node.getChildrenSize();
        while (i < size) {
            modifier(node.getChild(i));
            i++;
        }
    }

    private void modifier(ITree node) {
        switch (node.getType().toString()) {
            case "marker_annotation":
            case "annotation": {
                annotation(node);
                nl();
                break;
            }
            default: {
                token(node);
                space();
            }
        }
    }

    private void annotation(ITree node) {
        write("@");
        general_identifier(node.getChild(1));
        if (node.getChildrenSize() > 2)
            annotation_argument_list(node.getChild(2));
    }

    private void annotation_argument_list(ITree node) {
        int i = 0;
        write("(");
        i++;
        int size = node.getChildrenSize() - 1;
        while (i < size) {
            if (i > 1)
                write(", ");
            element_value_pair(node.getChild(i));
            i += 2;
        }
        write(")");
    }

    private void element_value_pair(ITree node) {
        general_identifier(node.getChild(0));
        write("=");
        expression(node.getChild(2));
    }

    private void import_declaration(ITree node) {
        write("import ");
        general_identifier(node.getChild(1));
        write(";");
        nl();
    }

    private void package_declaration(ITree node) {
        write("package ");
        general_identifier(node.getChild(1));
        write(";");
        nl();
    }

    private void scoped_identifier(ITree node) {
        general_identifier(node.getChild(0));
        write(".");
        general_identifier(node.getChild(2));
    }

    private void general_identifier(ITree node) {
        switch (node.getType().toString()) {
            case "scoped_type_identifier":
            case "field_access":
            case "scoped_identifier": {
                scoped_identifier(node);
                break;
            }
            case "generic_type": {
                generic_type(node);
                break;
            }
            case "array_type": {
                array_type(node);
                break;
            }
            case "type_identifier":
            case "identifier": {
                identifier(node);
                break;
            }
            case "super":
            case "this": {
                token(node);
                break;
            }
            default: {
                other(node);
            }
        }
    }

    private void array_type(ITree node) {
        general_identifier(node.getChild(0));
        dimensions(node.getChild(1));
    }

    private void dimensions(ITree node) {
        int i = 0;
        int size = node.getChildrenSize();
        while (i < size) {
            token(node.getChild(i));
            i++;
        }
    }

    private void generic_type(ITree node) {
        general_identifier(node.getChild(0));
        type_arguments(node.getChild(1));
    }

    private void type_arguments(ITree node) {
        int i = 0;
        write("<");
        i++;
        int size = node.getChildrenSize() - 1;
        while (i < size) {
            if (i > 1)
                write(", ");
            general_identifier(node.getChild(i));
            i += 2;
        }
        write(">");
    }

    private void identifier(ITree node) {
        write(node.getLabel());
    }

    private void comment(ITree node) {
        write(node.getLabel());
        nl();
    }
}