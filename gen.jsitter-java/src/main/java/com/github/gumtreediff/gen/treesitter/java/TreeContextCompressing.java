package com.github.gumtreediff.gen.treesitter.java;

import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.io.TreeIoUtils;
import com.github.gumtreediff.tree.*;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.UnmodifiableIntObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import static com.github.gumtreediff.tree.TypeSet.type;

public interface TreeContextCompressing extends TreeContext {

    interface TreeMetrics2 extends TreeMetrics {

        int syntaxHash();

        @Override
        public TreeMetrics2 located(int depth, int position);


        static TreeMetrics2 create(int size, int height, int syntaxHash, int hash, int structureHash, int depth, int position){
            return new TreeMetrics2Impl(size, height, syntaxHash, hash, structureHash, depth, position);
        }

        static TreeMetrics2 create(int size, int height, int syntaxHash, int hash, int structureHash){
            return new SubTreeMetrics2Impl(size, height, syntaxHash, hash, structureHash);
        }
    }

    class SubTreeMetrics2Impl extends TreeMetrics.SubTreeMetricsImpl implements TreeMetrics2 {

        private final int syntaxHash;

        public int syntaxHash() {
            return syntaxHash;
        }

        public SubTreeMetrics2Impl(int size, int height, int syntaxHash, int hash, int structureHash) {
            super(size, height, hash, structureHash);
            this.syntaxHash = syntaxHash;
        }

        @Override
        public TreeMetrics2 located(int depth, int position) {
            return new TreeMetrics2Impl(this.size(), this.height(), this.syntaxHash(), this.hash(), this.structureHash(), depth, position);
        }
    }

    class TreeMetrics2Impl extends SubTreeMetrics2Impl {
        final int depth;

        final int position;

        @Override
        public int depth() {
            return depth;
        }

        @Override
        public int position() {
            return position;
        }

        public TreeMetrics2Impl(int size, int height, int syntaxHash, int hash, int structureHash, int depth, int position) {
            super(size, height, syntaxHash, hash, structureHash);
            this.depth = depth;
            this.position = position;
        }
    }

    class FullNode {
        CompressibleNode node;
        TreeMetrics2 metrics;

        public FullNode(CompressibleNode node, TreeMetrics2 metrics) {
            this.node = node;
            this.metrics = metrics;
        }

        /** it mutates this **/
        FullNode locate(int depth, int position) {
            this.metrics = metrics.located(depth, position);
            return this;
        }
    }

    String serialize();

    class MapValue {
        MapValue next = null;
        CompressibleNode tree;
        int count = 0;

        MapValue(CompressibleNode tree) {
            this.tree = tree;
        }
    }

    class ArraySet<T> extends ArrayList<T> implements Set<T> {

        @Override
        public boolean add(T t) {
            if (this.contains(t))
                return false;
            return super.add(t);
        }

        @Override
        public void add(int index, T element) {
            throw new UnsupportedOperationException();
        }
    }

    IntObjectMap<MapValue> getHashMap();

    Map<Integer, MapValue> getStructHashMap();

    FullNode createCompressedTree(Type type, String label, Acc acc, List<CompressibleNode> children, int length);


    class Acc {
        int sumSize = 0;
        int maxHeight = 0;
        int currentSyntaxHash = 0;
        int currentHash = 0;
        int currentStructureHash = 0;

        public void add(FullNode fullNode) {
            TreeMetrics2 metrics = fullNode.metrics;
            int exponent = 2 * sumSize + 1;
            currentSyntaxHash += metrics.syntaxHash() * TreeMetricComputer.hashFactor(exponent);
            currentHash += metrics.hash() * TreeMetricComputer.hashFactor(exponent);
            currentStructureHash += metrics.structureHash() * TreeMetricComputer.hashFactor(exponent);
            sumSize += metrics.size();
            if (metrics.height() > maxHeight)
                maxHeight = metrics.height();
        }
    }

    class TreeContextCompressingImpl extends TreeContext.ContextImpl implements TreeContextCompressing {

        @Override
        public void setRoot(ITree root) {
        }

        @Override
        public ITree getRoot() {
            return null;
        }

        @Override
        public TreeContext deriveTree() {
            return null;
        }

//        Map<Integer, MapValue> hashMap = new HashMap<>();
        HashMap<Integer, MapValue> structHashMap = new HashMap<>();
        IntObjectHashMap<MapValue> hashMap = new IntObjectHashMap<>(64);

        @Override
        public IntObjectMap<MapValue> getHashMap() {
            return new UnmodifiableIntObjectMap<MapValue>(hashMap);
        }

        public Map<Integer, MapValue> getStructHashMap() {
            return Collections.unmodifiableMap(structHashMap);
        }

        @Override
        public FullNode createCompressedTree(Type type, String label, Acc acc, List<CompressibleNode> children, int length) {

            int syntaxHash = Utils.innerNodeHash(type, label, 2 * acc.sumSize + 1, acc.currentSyntaxHash);
            int hash = type("gumtree_spaces").equals(type) ? 0 : Utils.innerNodeHash(type, label, 2 * acc.sumSize + 1, acc.currentHash);
            int structureHash = Utils.innerNodeStructureHash(type, 2 * acc.sumSize + 1, acc.currentStructureHash);

            int collisionCount = 0;

            MapValue fromInHashMap = hashMap.get(hash);
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

                    TreeMetrics2 metrics = TreeMetrics2.create(
                            acc.sumSize + 1,
                            acc.maxHeight + 1,
                            syntaxHash,
                            hash,
                            structureHash);

                    if (collision) {
                        collisionCount++;
                        MapValue next = fromInHashMap.next;
                        if (next != null) {
                            fromInHashMap = next;
                        } else {
                            System.out.println("collisions: " + collisionCount);
                            break;
                        }
                    } else {
                        fromInHashMap.count++;
                        return new FullNode(fromInHashMap.tree, metrics);
                    }
                } while (true);
            }

            MapValue fromInStructHashMap = structHashMap.get(structureHash);

            CompressibleNode tree;
            if (label.equals(""))
                if (children.size() <= 0) {
                    if (length != type.toString().length())
                        throw new RuntimeException("" + length + "!=" + type.toString().length() + " for " + type.toString());
                    tree = CompressibleNodeFactory.create(type);
                }
                else
                    tree = CompressibleNodeFactory.create(type, children, length);
            else if (children.size() <= 0) {
                assert length == label.length(): "" + length + "!=" + label.length() + " for " + label;
                tree = CompressibleNodeFactory.create(type, label);
            } else
                tree = CompressibleNodeFactory.create(type, label, children, length);

            TreeMetrics2 metrics = TreeMetrics2.create(
                    acc.sumSize + 1,
                    acc.maxHeight + 1,
                    syntaxHash,
                    hash,
                    structureHash);

            MapValue data = new MapValue(tree);
            if (fromInHashMap != null) {
                fromInHashMap.next = data;
            } else {
                hashMap.put(hash, data);
            }
            if (fromInStructHashMap != null) {
                fromInStructHashMap.count++;
            } else {
                structHashMap.put(structureHash, data);
            }

            return new FullNode(tree, metrics);
        }

        @Override
        public String serialize() {
            ReSerializer serializer = new ReSerializer(this);
            serializer.program(this.getRoot());
            return serializer.builder.toString();
        }
    }
}

class TreeContextVersionedCompressing extends TreeContext.ContextImpl {

    Map<Version, ITree> roots = new HashMap<>();

    public void putRoot(ITree root, Version when) {
        roots.put(when, root);
    }

    public ITree getRoot(Version when) {
        return roots.get(when);
    }
}

class VersionedTreeContextCompressing extends TreeContextCompressing.TreeContextCompressingImpl {

    Map<Version, ITree> roots = new HashMap<>();

    public void putRoot(Version when, ITree root) {
        roots.put(when, root);
    }

    public ITree getRoot(Version when) {
        return roots.get(when);
    }

    MainTreeContextCompressing getAtVersionTC() {
        return new MainTreeContextCompressing();
    }

    class MainTreeContextCompressing extends NestableTreeContextCompressing {

        TreeContextCompressing getLocalTC() {
            return new LocalTreeContextCompressing();
        }

        @Override
        protected TreeContextCompressing getParentContext() {
            return VersionedTreeContextCompressing.this;
        }

        class LocalTreeContextCompressing extends NestableTreeContextCompressing {

            @Override
            protected TreeContextCompressing getParentContext() {
                return MainTreeContextCompressing.this;
            }
        }

    }
}

abstract class NestableTreeContextCompressing implements TreeContextCompressing {
    protected abstract TreeContextCompressing getParentContext();

    @Override
    public Object getMetadata(String key) {
        return getParentContext().getMetadata(key);
    }

    @Override
    public Object setMetadata(String key, Object value) {
        return getParentContext().setMetadata(key, value);
    }

    @Override
    public Iterator<Map.Entry<String, Object>> getMetadata() {
        return getParentContext().getMetadata();
    }

    @Override
    public MetadataSerializers getSerializers() {
        return getParentContext().getSerializers();
    }

    @Override
    public TreeContext export(MetadataSerializers s) {
        getParentContext().export(s);
        return this;
    }

    @Override
    public TreeContext export(String key, TreeIoUtils.MetadataSerializer s) {
        getParentContext().export(key, s);
        return this;
    }

    @Override
    public TreeContext export(String... name) {
        getParentContext().export(name);
        return this;
    }

    @Override
    public void setRoot(ITree root) {
        throw new RuntimeException();
    }

    @Override
    public ITree getRoot() {
        throw new RuntimeException();
    }

    @Override
    public TreeContext deriveTree() {
        return getParentContext().deriveTree();
    }

    @Override
    public String serialize() {
        return null;
    }

    @Override
    public IntObjectMap<MapValue> getHashMap() {
        return getParentContext().getHashMap();
    }

    @Override
    public Map<Integer, MapValue> getStructHashMap() {
        return getParentContext().getStructHashMap();
    }

    @Override
    public FullNode createCompressedTree(Type type, String label, Acc acc, List<CompressibleNode> children, int length) {
        return getParentContext().createCompressedTree(type, label, acc, children, length);
    }
}

class VersionedGenerator extends TreeGenerator {
    private final VersionedTreeContextCompressing treeContext = new VersionedTreeContextCompressing();
    VersionedTreeContextCompressing generateFrom(File rootDir, Version when) throws IOException {
        TreeContextCompressing.FullNode fullNode = new GlobalGenerator()._generateFrom(rootDir);
        this.treeContext.putRoot(when, new DecompressedTree(fullNode.node, null, -1));
        return this.treeContext;
    }

    public VersionedTreeContextCompressing getTreeContext() {
        return treeContext;
    }

    @Override
    protected TreeContext generate(Reader r) throws IOException {
        throw new RuntimeException();
    }

    class GlobalGenerator extends TreeGenerator {
        private final VersionedTreeContextCompressing.MainTreeContextCompressing treeContext = VersionedGenerator.this.treeContext.getAtVersionTC();

        VersionedTreeContextCompressing.MainTreeContextCompressing generateFrom(File rootDir) throws IOException {
            TreeContextCompressing.FullNode fullNode = aux(rootDir);
            DecompressedTree a = new DecompressedTree(_generateFrom(rootDir).node, null, -1);
            this.treeContext.setRoot(a);
            return this.treeContext;
        }

        TreeContextCompressing.FullNode _generateFrom(File rootDir) throws IOException {
            TreeContextCompressing.FullNode fullNode = aux(rootDir);
            return fullNode;
        }

        private TreeContextCompressing.FullNode aux(File file) throws IOException {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                Type type = type("directory");
                TreeContextCompressing.Acc acc = new TreeContextCompressing.Acc();
                Map<String, CompressibleNode> children = new TreeMap<>();
                TreeContextCompressing.FullNode labelNode = treeContext.createCompressedTree(
                        type("file_name"), file.getName(), new TreeContextCompressing.Acc(), Collections.emptyList(),
                        file.getName().length());
                acc.add(labelNode);
                List<CompressibleNode> childrenList = new ArrayList<>();
                childrenList.add(labelNode.node);
                if (files != null)
                    for (File f : files) {
                        TreeContextCompressing.FullNode fullNode = aux(f);
                        acc.add(fullNode);
                        children.put(f.getName(), fullNode.node);
                    }
                childrenList.addAll(children.values());
                TreeContextCompressing.FullNode r = treeContext.createCompressedTree(
                        type, file.getName(), acc,
                        childrenList,
                        0);
                return r;
            } else {
                assert file.isFile():file;
                String content;

                Type type = type("file");
                TreeContextCompressing.Acc acc = new TreeContextCompressing.Acc();
                final List<CompressibleNode> children = new ArrayList<>();
                TreeContextCompressing.FullNode fullNode;

                fullNode = treeContext.createCompressedTree(
                        type("file_name"), file.getName(), new TreeContextCompressing.Acc(), Collections.emptyList(),
                        file.getName().length());
                acc.add(fullNode);
                children.add(fullNode.node);
                if (file.getName().toString().endsWith(".java") && !file.toPath().toString().contains("src\\main\\resources\\") && !file.toPath().toString().contains("src\\test\\resources\\")) {
                    try {
                        Files.readAllBytes(file.toPath());
                        content = Files.readString(file.toPath());
                    } catch (MalformedInputException e) {
                        content = Files.readString(file.toPath(), StandardCharsets.ISO_8859_1);
                    }
                    if (content!=null) {
                        fullNode = new JSitterJavaTreeGenerator2(treeContext.getLocalTC()).generate(content);
                        children.add(fullNode.node);
                        {
    //                    fullNode.node.serialize(new BufferedWriter(new OutputStreamWriter(System.err)));
    //                        DecompressedTree a = new DecompressedTree(fullNode.node, null, -1);
    //                        System.out.println(a.toTreeString());
    //                        System.out.println(toOriginal(a));
                        }
                        acc.add(fullNode);
                    }
                }
                TreeContextCompressing.FullNode r = treeContext.createCompressedTree(
                        type, "", acc, children,0);
                return r;
            }
        }

        @Override
        protected TreeContext generate(Reader r) throws IOException {
            throw new RuntimeException();
        }
    }

    public static TreeIoUtils.AbstractSerializer toOriginal(DecompressedTree root) {
        return new TreeIoUtils.AbstractSerializer() {
            @Override
            public void writeTo(Writer writer) throws Exception {
                aux(writer, root);
            }

            private void aux(Writer writer, ITree current) throws IOException {
                if (current.hasLabel())
                    writer.write(current.getLabel());
                else if (current.getChildrenSize()==0)
                    writer.write(current.getType().toString());
                for(ITree child : current.getChildren()) {
                    aux(writer, child);
                }
            }
        };
    }
}
