package com.github.gumtreediff.gen.treesitter.java;

import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.io.TreeIoUtils;
import com.github.gumtreediff.tree.*;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.UnmodifiableIntObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import static com.github.gumtreediff.tree.TypeSet.type;

public interface TreeContextCompressing extends TreeContext {

    class FullNode {
        CompressibleNode node;
        TreeMetrics metrics;
        // padding spaces
        String spaces;

        public FullNode(CompressibleNode node, TreeMetrics metrics, String s) {
            this.node = node;
            this.metrics = metrics;
            this.spaces = s;
        }
    }

    String serialize();

    class MapValue {
        MapValue next = null;
        CompressibleNode tree;
        int count = 0;
        Set<String> paddingSpaces = new ArraySet<>();

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

    FullNode createCompressedTree(Type type, String label, Acc acc, List<CompressibleNode> children, int pos, int length, int depth, int treePosition, String paddingSpaces);


    class Acc {
        int sumSize = 0;
        int maxHeight = 0;
        int currentHash = 0;
        int currentStructureHash = 0;

        public void add(FullNode fullNode) {
            TreeMetrics metrics = fullNode.metrics;
            int exponent = 2 * sumSize + 1;
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
        IntObjectHashMap<MapValue> hashMap = new IntObjectHashMap<>();

        @Override
        public IntObjectMap<MapValue> getHashMap() {
            return new UnmodifiableIntObjectMap<MapValue>(hashMap);
        }

        public Map<Integer, MapValue> getStructHashMap() {
            return Collections.unmodifiableMap(structHashMap);
        }

        @Override
        public FullNode createCompressedTree(Type type, String label, Acc acc, List<CompressibleNode> children, int pos, int length, int depth, int treePosition, String paddingSpaces) {

            int hash = Utils.innerNodeHash(type, label, 2 * acc.sumSize + 1, acc.currentHash);
            int structureHash = Utils.innerNodeStructureHash(type, 2 * acc.sumSize + 1, acc.currentStructureHash);

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

                    TreeMetrics metrics = TreeMetrics.create(
                            acc.sumSize + 1,
                            acc.maxHeight + 1,
                            hash,
                            structureHash,
                            depth, treePosition);

                    if (collision) {
                        System.out.println("collision!");
                        MapValue next = fromInHashMap.next;
                        if (next != null) {
                            fromInHashMap = next;
                        } else break;
                    } else {
                        fromInHashMap.count++;
                        return new FullNode(fromInHashMap.tree, metrics, paddingSpaces);
                    }
                } while (true);
            }

            MapValue fromInStructHashMap = structHashMap.get(structureHash);

            CompressibleNode tree;
            if (label.equals(""))
                if (children.size() <= 0)
                    tree = CompressibleNodeFactory.create(type, pos, length);
                else
                    tree = CompressibleNodeFactory.create(type, children, pos, length);
            else if (children.size() <= 0)
                tree = CompressibleNodeFactory.create(type, label, pos, length);
            else
                tree = CompressibleNodeFactory.create(type, label, children, pos, length);

            TreeMetrics metrics = TreeMetrics.create(
                    acc.sumSize + 1,
                    acc.maxHeight + 1,
                    hash,
                    structureHash,
                    depth, treePosition);

            MapValue data = new MapValue(tree);
            data.paddingSpaces.add(paddingSpaces);
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

            return new FullNode(tree, metrics, paddingSpaces);
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

class MainTreeContextCompressing extends TreeContextCompressing.TreeContextCompressingImpl {

    TreeContextCompressing getLocalTC() {
        return new LocalTreeContextCompressing();
    }

    class LocalTreeContextCompressing implements TreeContextCompressing {

        @Override
        public Object getMetadata(String key) {
            return MainTreeContextCompressing.this.getMetadata(key);
        }

        @Override
        public Object setMetadata(String key, Object value) {
            return MainTreeContextCompressing.this.setMetadata(key, value);
        }

        @Override
        public Iterator<Map.Entry<String, Object>> getMetadata() {
            return MainTreeContextCompressing.this.getMetadata();
        }

        @Override
        public MetadataSerializers getSerializers() {
            return MainTreeContextCompressing.this.getSerializers();
        }

        @Override
        public TreeContext export(MetadataSerializers s) {
            MainTreeContextCompressing.this.export(s);
            return this;
        }

        @Override
        public TreeContext export(String key, TreeIoUtils.MetadataSerializer s) {
            MainTreeContextCompressing.this.export(key, s);
            return this;
        }

        @Override
        public TreeContext export(String... name) {
            MainTreeContextCompressing.this.export(name);
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
            return MainTreeContextCompressing.this.deriveTree();
        }

        @Override
        public String serialize() {
            return null;
        }

        @Override
        public IntObjectMap<MapValue> getHashMap() {
            return MainTreeContextCompressing.this.getHashMap();
        }

        @Override
        public Map<Integer, MapValue> getStructHashMap() {
            return MainTreeContextCompressing.this.getStructHashMap();
        }

        @Override
        public FullNode createCompressedTree(Type type, String label, Acc acc, List<CompressibleNode> children, int pos, int length, int depth, int treePosition, String paddingSpaces) {
            return MainTreeContextCompressing.this.createCompressedTree(type, label, acc, children, pos, length, depth, treePosition, paddingSpaces);
        }
    }

}

class GlobalGenerator extends TreeGenerator {
    private final MainTreeContextCompressing treeContext = new MainTreeContextCompressing();

    MainTreeContextCompressing generateFrom(File rootDir) throws IOException {
        aux(rootDir);
        return this.treeContext;
    }

    private TreeContextCompressing.FullNode aux(File file) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            Type type = type("directory");
            TreeContextCompressing.Acc acc = new TreeContextCompressing.Acc();
            Map<String, CompressibleNode> children = new TreeMap<>();
            if (files != null)
                for (File f : files) {
                    TreeContextCompressing.FullNode fullNode = aux(f);
                    acc.add(fullNode);
                    children.put(f.getName(), fullNode.node);
                }
            TreeContextCompressing.FullNode r = treeContext.createCompressedTree(
                    type, file.getName(), acc,
                    new ArrayList<>(children.values()),
                    0, 0, 0, 0, "");
            return r;
        } else {
            assert file.isFile():file;
            String content;

            Type type = type("file");
            TreeContextCompressing.Acc acc = new TreeContextCompressing.Acc();
            TreeContextCompressing.FullNode fullNode;
            List<CompressibleNode> children;
            if (file.getName().toString().endsWith(".java")) {
                try {
                    Files.readAllBytes(file.toPath());
                    content = Files.readString(file.toPath());
                } catch (MalformedInputException e) {
                    content = Files.readString(file.toPath(), StandardCharsets.ISO_8859_1);
                }
                if (content!=null) {
                    fullNode = new JSitterJavaTreeGenerator2(treeContext.getLocalTC()).generate(content);
                    children = Collections.singletonList(fullNode.node);
                    acc.add(fullNode);
                } else {
                    children = Collections.emptyList();
                }
            } else {
                children = Collections.emptyList();
            }
            TreeContextCompressing.FullNode r = treeContext.createCompressedTree(
                    type, file.getName(), acc, children,
                    0, 0, 0, 0, "");
            return r;
        }
    }

    @Override
    protected TreeContext generate(Reader r) throws IOException {
        throw new RuntimeException();
    }
}