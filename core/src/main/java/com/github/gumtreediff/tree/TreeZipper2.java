package com.github.gumtreediff.tree;

import java.util.*;

interface TreeZipper2<T> {
    public TreeUtils.TreeZipper<T> clone();

    // moves

    public boolean moveUp();

    public boolean moveDownLeft();

    public boolean moveDownRight();

    public boolean moveLeft();

    public boolean moveRight();

    // accesses

    public T getNode();

    public T getParent();
}

interface Test {
    interface BasicNode {
        Type type();
    }

    interface LabeledNode extends Node {
        LabelNode label();
    }

    interface LabelNode extends Node {
        String value();
    }

    interface ModifierNode extends Node {
        String value();
    }

    interface BasicArrayTree extends BasicNode {
        BasicNode children(int index);
        int size();
    }

    class Indexed<T> {
        public final T content;
        public final int index;
        Indexed(int index, T content ) {
            this.content = content;
            this.index = index;
        }

    }
    interface BasicMapTree extends BasicNode {
        Indexed<? extends BasicNode> children(String name);
    }

    /**
     * mostly for reference
     * will lead me to more generalized node types
     */
    interface JavaConstructs {
        interface Import extends LabeledNode {
        }

        interface Package extends LabeledNode, BasicArrayTree {
        }

        interface File extends BasicNode, BasicArrayTree {
            Import imp(int index);

            Class clas(int index);
        }

        interface Annotation extends Call {
        }

        interface Annotable extends BasicMapTree {
            Annotation annotation(int index);
        }

        interface TypeRef extends LabeledNode {
        }

        interface Generic extends TypeRef {
        }

        interface GenericDeclaration extends BasicMapTree {
            Generic genericType(int index);
        }

        interface FieldRef extends LabeledNode {
        }

        interface Target extends LabeledNode {
        }

        interface TypeTarget extends Target, TypeRef {
        }

        interface FieldTarget extends Target, FieldRef {
        }

        interface Named extends LabeledNode {
        }

        interface Class extends Named, Annotable, BasicArrayTree {
        }

        interface Interface extends BasicNode, Annotable, BasicArrayTree {
        }

        interface Parameters extends BasicNode {
        }

        interface Modifiers extends BasicNode {
        }

        interface Modifiable extends BasicMapTree {
        }

        interface Body extends BasicNode, BasicArrayTree {
            boolean curly(); // not sure if needed
        }

        interface Callable extends BasicMapTree {
            Parameters parameters();
        }

        interface AnonymousMethod extends Callable {
            Body body();
        }

        interface Lambda extends Callable {
        }

        interface Method extends AnonymousMethod, Named, Modifiable, Annotable {
        }

        interface Call extends BasicNode {
        }

        interface Argument extends BasicNode {
        } // contextual, just a coma separated list

        interface Variable extends LabeledNode {
        }

        interface Field extends Variable {
        } // contextual

        interface LocalVariable extends Variable {
        } // contextual

        interface BinaryOp {
        } // just infix notation
    }

    interface Versions {
        boolean contains(Versions versions);
    }

    interface VersionedNode extends BasicNode {
        Versions version();
    }

    /**
     * keep implementations final
     **/
    interface Path {
        /**
         * first? parent matching type
         **/
        Path parent(Type type);

        /**
         * direct parent
         **/
        Path parent();
    }

    interface OffsetedPath extends Path {
        /**
         * offset in parent()
         **/
        int offset();
    }

    interface LiteralPath extends Path {
        /**
         * literal index in parent
         **/
        String literal();
    }

    class PathDefaultOffset implements OffsetedPath {

        private Path parent;
        private int offset;

        PathDefaultOffset(Path parent, int offset) {
            this.parent = parent;
            this.offset = offset;
        }

        @Override
        public Path parent(Type type) {
            if (type == null) {
                return parent();
            }
            return parent().parent(type);
        }

        @Override
        public Path parent() {
            return this.parent;
        }

        @Override
        public int offset() {
            return this.offset;
        }

        // TODO equal + hashCode
    }

    class PathFile implements LiteralPath {

        private PathFile parent;
        private String literal;

        PathFile(PathFile parent, String literal) {
            this.parent = parent;
            this.literal = literal;
        }

        @Override
        public Path parent(Type type) {
            if (type == null) {
                return parent();
            }
            return parent().parent(type);
        }

        @Override
        public PathFile parent() {
            return this.parent;
        }

        @Override
        public String literal() {
            return this.literal;
        }

        // TODO equal + hashCode
    }

    class PathSrcDir extends PathFile {
        PathSrcDir(PathFile parent, String literal) {
            super(parent, literal);
        }
    }

    /**
     * ie. something inside a class member (for java at least)
     * the goal is to better index trees
     * and also share things
     **/
    class PathStatement implements OffsetedPath {

        private final Path member;
        private final PathDefaultOffset offsets;

        PathStatement(Path member, PathDefaultOffset offsets) {
            this.member = member;
            this.offsets = offsets;
        }

        @Override
        public Path parent(Type type) {
            if (type == null) {
                return parent();
            }
            return parent().parent(type);
        }

        @Override
        public Path parent() {
            return this.member;
        }

        @Override
        public int offset() {
            return this.offsets.offset();
        }

        public Path parentMember() {
            return member;
        }
    }

    interface NodeWithLocalStatistics extends BasicNode {
        LocalStatistics localStatistics();
    }

    interface NodeWithGlobalStatistics extends NodeWithLocalStatistics {
        GlobalStatistics globalStatistics();
//        int treePosition(Zipper<NodeWithContextualStatistics> zipper);
//
//        int treeDepth(Zipper<NodeWithContextualStatistics> zipper);
    }

    interface NodeWithByteStatistics extends NodeWithGlobalStatistics {
        ByteStatistics byteStatistics();
    }

    interface ReferencingNode extends BasicNode {
        BasicNode solve(Zipper<ReferencingNode> zipper);
    }

    class RefSolver {
        static BasicNode solve(ReferencingNode node, Zipper<ReferencingNode> zipper) {
            Versions tmp = zipper.when();
            return null; //TODO
        }
    }

    /**
     * needed if we want to be able to exactly re-serialize
     * non the less we also need the original file to be able to determine the exact sequence of \s (space, tab, newline)
     */
    interface PositionedNode extends BasicNode {
    }

    interface AbsolutelyPositionedNode extends PositionedNode {
        int startByte();

        int endByte();
    }

    interface ContextuallyPositionedNode extends PositionedNode {
        int startByte(Zipper<ContextuallyPositionedNode> zipper);

        int endByte(Zipper<ContextuallyPositionedNode> zipper);
    }

    interface Serializer<T extends PositionedNode> {
        String serialize();
    }

    interface SerializerAtVersion<T extends PositionedNode & VersionedNode> {
        String serialize(Version version);
    }

    interface Node extends PositionedNode, NodeWithGlobalStatistics {
    }

//    abstract class LocalNode implements Node {
//
//    }

    interface GeneralSuperTree extends Node {
        BasicNode get(Path where, Version when);

        OffsetedPath create(Path parent, int offset);

        LiteralPath create(Path parent, String literal);
//        String language();
    }

    interface Statistics {
    }

    interface LocalStatistics extends Statistics {
        public int treeSize();

        public int treeHeight();

        public int hash();

        public int structHash();
    }

    interface GlobalStatistics extends LocalStatistics {
        public int treePosition();

        public int treeDepth();
    }

    interface ByteStatistics extends GlobalStatistics {
        public int startByte();

        public int size();
    }

    class SuperTree extends DirectoryImpl implements GeneralSuperTree {
        SimilarTreeStore store;
        private String language;
        Statistics statistics = null;

        @Override
        public BasicNode get(Path where, Version when) {
            if (where instanceof PathFile) {
                return children((PathFile) where);
            } else if (where instanceof PathStatement) {
                File file = (File) get(((PathStatement) where).parentMember(), when);
            }
            return null; // TODO
        }

        @Override
        public OffsetedPath create(Path parent, int offset) {
            return null;
        }

        @Override
        public LiteralPath create(Path parent, String literal) {
            return null;
        }
//
//        @Override
//        public String language() {
//            return this.language;
//        }

        @Override
        public Type type() {
            return TypeSet.type("superAST");
        }

        /**
         * @return Directory (ie. Package) or File (code file (most probably with an extension))
         **/
        public BasicNode children(PathFile fileName) {
            return null;
        }
    }

    interface Directory extends Node, LabeledNode, BasicMapTree {
    }

    interface File extends Node, LabeledNode, BasicArrayTree {
    }

    class DirectoryImpl implements Directory {

        List<Directory> subDirs = null;
        List<File> files = null;
        Statistics statistics = null;

        // unique files not checked
        void add(File file) {
            String name = file.label().value();
            if (files == null) {
                files = new ArrayList<>();
            }
            files.add(file);
        }

        // unique files not checked
        void add(Directory dir) {
            String name = dir.label().value();
            if (subDirs == null) {
                subDirs = new ArrayList<>();
            }
            subDirs.add(dir);
        }

        // unmodifiable list
        List<File> files() {
            files.sort(((a, b) -> a.label().value().compareTo(b.label().value())));
            return Collections.unmodifiableList(files);
        }

        // unmodifiable list
        List<Directory> directories() {
            subDirs.sort(((a, b) -> a.label().value().compareTo(b.label().value())));
            return Collections.unmodifiableList(subDirs);
        }

        List<LabeledNode> children() {
            return new AbstractList<LabeledNode>() {
                @Override
                public LabeledNode get(int i) {
                    if (i < subDirs.size())
                        return subDirs.get(i);
                    return files.get(i - subDirs.size());
                }

                @Override
                public int size() {
                    int r = 0;
                    if (subDirs != null) {
                        r += subDirs.size();
                    }
                    if (files != null) {
                        r += files.size();
                    }
                    return r;
                }
            };
        }

        @Override
        public Type type() {
            return TypeSet.type("directory");
        }

        @Override
        public Indexed<LabeledNode> children(String name) {
            int i = 0;
            if (subDirs != null)
                for (Directory d : subDirs) {
                    if (d.label().equals(name))
                        return new Indexed<>(i, d);
                    i++;
                }
            if (files != null)
                for (File f : files) {
                    if (f.label().equals(name))
                        return new Indexed<>(i, f);
                    i++;
                }
            return null;
        }

        public LabeledNode children(int i) {
            if (i < subDirs.size())
                return subDirs.get(i);
            return files.get(i - subDirs.size());
        }

//        @Override
//        public int treePosition(Zipper<NodeWithContextualStatistics> zipper) {
//            return zipper.treePostion();
//        }
//
//        @Override
//        public int treeDepth(Zipper<NodeWithContextualStatistics> zipper) {
//            return zipper.treeDepth();
//        }

        @Override
        public LabelNode label() {
            return null;
        }

        @Override
        public LocalStatistics localStatistics() {
            if (statistics != null) {
                if (statistics instanceof LocalStatistics) {
                    return ((LocalStatistics) statistics);
                }
            }
            computeLocalStatistics();
            return (LocalStatistics) statistics;
        }

        public void statistics(Statistics statistics) {
            this.statistics = statistics;
        }

        @Override
        public GlobalStatistics globalStatistics() {
            return null;
        }

        private void computeLocalStatistics() {
            int accHash = 0;
            int accStructHash = 0;
            int treeSize = 0;
            int treeHeight = 0;
            if (subDirs != null) {
                for (Directory d : subDirs) {
                    int exponent = 2 * treeSize + 1;
                    LocalStatistics tmp = d.localStatistics();
                    accHash += tmp.hash() * hashFactor(exponent);
                    accStructHash += tmp.structHash() * hashFactor(exponent);
                    treeSize += tmp.treeSize();
                    if (tmp.treeHeight() > treeHeight)
                        treeHeight = tmp.treeHeight();
                }
            }
            if (files != null) {
                for (File f : files) {
                    LocalStatistics tmp = f.localStatistics();
                    accHash += tmp.hash();
                    accStructHash += tmp.structHash();
                    treeSize += tmp.treeSize();
                    if (tmp.treeHeight() > treeHeight)
                        treeHeight = tmp.treeHeight();
                }
            }
            int hash = innerNodeHash(this, 2 * treeSize + 1, accHash);
            int structHash = innerNodeStructureHash(this, 2 * treeSize + 1, accStructHash);
            statistics = new LocalStatisticsImpl(treeSize + 1, treeHeight + 1, hash, structHash);
        }
    }


    abstract class AbstractZipperImpl implements Zipper<BasicNode> {
        TreeStack<SimpleData> stack;
        SuperTree root;
        // Always valid if did not ignore non-computed nodes
        int byteStart = -1;
        // only valid when going up

        AbstractZipperImpl(SuperTree root) {
            this.root = root;
            this.stack = new TreeStackOptimized<>(new SimpleData(root));
        }

        @Override
        public SuperTree root() {
            return root;
        }

        @Override
        public BasicNode current() {
            return stack.peekData().node; // TODO should be the one updated ?
        }

        @Override
        public boolean up() {
            if (stack.depth()>0) {
                // postOrder
                SimpleData tmp = stack.pop();
                // TODO update leaved tree

            }
            return false;
        }

        @Override
        public boolean down(int index) {
            if (current() instanceof BasicArrayTree) {
                // preOrder
                if (((BasicArrayTree) current()).size()>index) {
                    SimpleData d = new SimpleData(((BasicArrayTree) current()).children(index));
                    stack.push(index,d);
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean left() {
            return false;
        }

        @Override
        public boolean right() {
            if (stack.depth()<=1) {
                return false;
            }
            int index = stack.peekOffset();
            SimpleData prevCurrentData = stack.pop();
            // postOrder
            // TODO

            SimpleData parent = stack.peekData();


            if (current() instanceof BasicArrayTree) {
                int newIndex = index + 1;
                if (((BasicArrayTree) current()).size()>newIndex) {
                    // update data of parent
                    parent.treeSize += prevCurrentData.treeSize;
                    if (parent.maxHeight<=prevCurrentData.maxHeight)
                        parent.maxHeight = prevCurrentData.maxHeight+1;
                    // TODO

                    SimpleData d = new SimpleData(((BasicArrayTree) current()).children(newIndex));
                    // preOrder
                    // TODO
                    stack.push(newIndex,d);
                    return true;
                }
            }
            stack.push(index, prevCurrentData);
            return false;
        }

        @Override
        public Versions when() {
            return null;
        }

        @Override
        public int depth() {
            return 0;
        }

        @Override
        public int position() {
            return 0;
        }

        @Override
        public int bytePosition() {
            return 0;
        }
    }

    interface Data {
    }

    class SimpleData implements Data {
        public BasicNode node;
        public int sumhash = -1; // sum when going side way (right for now)
        public int sumStructHash = -1; // sum when going side way (right for now)
        public int treeSize = -1; // increment when going side way (right for now)
        public int maxHeight = -1; // update when when going side way (right for now)
        public int treePosition = -1; // set when going down
        public SimpleData(BasicNode node) {this.node = node;}
    }

    /** for now should only be actuated through ordered Actuator **/
    class ZipperImpl implements Zipper<BasicNode> {
        TreeStack<SimpleData> stack;
        SuperTree root;
        // Always valid if did not ignore non-computed nodes
        int byteStart = -1;
        // only valid when going up

        ZipperImpl(SuperTree root) {
            this.root = root;
            this.stack = new TreeStackOptimized<>(new SimpleData(root));
        }

        @Override
        public SuperTree root() {
            return root;
        }

        @Override
        public BasicNode current() {
            return stack.peekData().node; // TODO should be the one updated ?
        }

        protected BasicNode onEnter(Data parent, Data current, int offset){
            // TODO
            return null;
        }

        protected BasicNode onLeave(Data parent, Data current, int offset){
            // TODO
            return null;
        }

        @Override
        public boolean up() {
            if (stack.depth()>0) {
                int index = stack.peekOffset();
                SimpleData tmp = stack.pop();
                onLeave(stack.peekData(), tmp, index);
                // TODO update leaved tree

            }
            return false;
        }

        @Override
        public boolean down(int index) {
            if (current() instanceof BasicArrayTree) {
                if (((BasicArrayTree) current()).size()>index) {
                    SimpleData d = new SimpleData(((BasicArrayTree) current()).children(index));
                    onEnter(stack.peekData(),d, index);
                    stack.push(index,d);
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean left() {
            return false;
        }

        @Override
        public boolean right() {
            if (stack.depth()<=1) {
                return false;
            }
            int index = stack.peekOffset();
            SimpleData prevCurrentData = stack.pop();


            SimpleData parent = stack.peekData();
            if (parent.node instanceof BasicArrayTree) {
                int newIndex = index + 1;
                BasicArrayTree parentTree = (BasicArrayTree) parent.node;
                if (parentTree.size()>newIndex) {
                    onLeave(parent, prevCurrentData, index);

                    // update data of parent
                    parent.treeSize += prevCurrentData.treeSize;
                    if (parent.maxHeight<=prevCurrentData.maxHeight)
                        parent.maxHeight = prevCurrentData.maxHeight+1;
                    // TODO

                    SimpleData d = new SimpleData(parentTree.children(newIndex));
                    onEnter(parent,d,newIndex);
                    stack.push(newIndex,d);
                    return true;
                }
            }
            stack.push(index, prevCurrentData);
            return false;
        }

        @Override
        public Versions when() {
            return null;
        }

        @Override
        public int depth() {
            return 0;
        }

        @Override
        public int position() {
            return 0;
        }

        @Override
        public int bytePosition() {
            return 0;
        }
    }

    class LocalStatisticsImpl implements LocalStatistics {
        private final int treeSize;
        private final int treeHeight;
        private final int hash;
        private final int structHash;

        public LocalStatisticsImpl(int treeSize, int treeHeight, int hash, int structHash) {
            this.treeSize = treeSize;
            this.treeHeight = treeHeight;
            this.hash = hash;
            this.structHash = structHash;
        }

        @Override
        public int treeSize() {
            return this.treeSize;
        }

        @Override
        public int treeHeight() {
            return this.treeHeight;
        }

        @Override
        public int hash() {
            return this.hash;
        }

        @Override
        public int structHash() {
            return this.structHash;
        }
    }

    interface MemberContainer extends Node, LabeledNode, BasicArrayTree {
        @Override
        Member children(int index);
    }

    interface Member extends Node, LabeledNode, BasicArrayTree {

    }

    interface SimilarTreeStore {
        interface Key {
            Path path();

            Versions versions();
        }

        List<Key> k();

        //        Node get(int structHash);
        Path get(int hash);
    }

    interface Zipper<T> {

        GeneralSuperTree root();

        T current();

        //void set(T tree);

        /**
         * @return false when at {@code root()}
         **/
        boolean up();

        /**
         * move to children of {@code current()} at index
         *
         * @return false when no children at index
         **/
        boolean down(int index);

        boolean left();

        boolean right();

        Versions when();

        /**
         * @return depth from {@code root()} at versions {@code when()}
         **/
        int depth();

        int position();

        int bytePosition();
    }

    interface VersionedZipper<T> extends Zipper<T> {
        Versions when();
    }

    interface Actioner<T> {
        boolean next();
    }

    enum Has {
        DOWN, UP, LEFT, RIGHT;
    }

    class OrderedActioner<U, T> implements Actioner<T> {

        private Zipper<U> zipper;
        protected Has has = Has.DOWN;
        boolean returnedToStart = false;

        OrderedActioner(Zipper<U> zipper) {
            this.zipper = zipper;
        }

        @Override
        public boolean next() {
            while (zipper != null) {
                boolean down = has == Has.UP && zipper.down(0);
                if (down) {
                    has = Has.DOWN;
                    return true;
                } else {
                    boolean side = zipper.right();
                    if (side) {
                        has = Has.RIGHT;
                        return true;
                    } else {
                        boolean up = zipper.up();
                        has = Has.UP;
                        if (up) {
                        } else {
                            returnedToStart = true;
                            return false;
                        }
                    }
                }
            }
            return false;
        }
    }

    interface Action {
    }

    interface Atomic extends Action {
        Path where();
    }

    interface Composed extends Action {
    }

    interface Insert extends Atomic {
        Path where();

        BasicNode what();
    }

    interface Delete extends Atomic {
        Path where();
    }

    interface Update extends Atomic {
        Path where();

        String how();
    }


    public static final String ENTER = "enter";
    public static final String LEAVE = "leave";
    public static final int BASE = 33;

    public static int innerNodeHash(Test.LabeledNode tree, int size, int middleHash) {
        return Objects.hash(tree.type(), tree.label(), ENTER)
                + middleHash
                + Objects.hash(tree.type(), tree.label(), LEAVE) * hashFactor(size);
    }

    public static int innerNodeStructureHash(Test.BasicNode tree, int size, int middleHash) {
        return Objects.hash(tree.type(), ENTER)
                + middleHash
                + Objects.hash(tree.type(), LEAVE) * hashFactor(size);
    }

    public static int leafHash(Test.LabeledNode tree) {
        return innerNodeHash(tree, 1, 0);
    }

    public static int leafStructureHash(Test.BasicNode tree) {
        return innerNodeStructureHash(tree, 1, 0);
    }

    private static int hashFactor(int exponent) {
        return fastExponentiation(BASE, exponent);
    }

    private static int fastExponentiation(int base, int exponent) {
        if (exponent == 0)
            return 1;
        if (exponent == 1)
            return base;
        int result = 1;
        while (exponent > 0) {
            if ((exponent & 1) != 0)
                result *= base;
            exponent >>= 1;
            base *= base;
        }
        return result;
    }
}

interface TreeStack<T> {

    public void push(int offset, T data);

    public T pop();

    public T peekData();

    public void setData(T data);

    public int peekOffset();

    public int depth();

    public void shiftOffset(int d);
}

interface TreePath {
    TreePath cons(int offset);

    int depth();

    int car();

    TreePath cdr();
}

class TreeStackOptimized<T> implements TreeStack<T> {
    static int INITIAL_SIZE = 2;
    static int GROW_FACTOR = 2;
    static byte MAX = -1;
    @SuppressWarnings("unchecked")
    T[] parents = (T[]) new Object[INITIAL_SIZE];
    byte[] offsets = new byte[INITIAL_SIZE];
    int pI = 0;
    int oI = 0;

    public TreeStackOptimized(T data) {
        parents[pI] = data;
        offsets[oI] = 0;
    }

    @Override
    public void push(int offset, T data) {
        incP();
        parents[pI] = data;
        incO();
        int s = offset;
        while (s > Byte.toUnsignedInt(MAX)) {
            offsets[oI] = MAX;
            s -= Byte.toUnsignedInt(MAX);
            incO();
        }
        offsets[oI] = (byte) s;
    }

    @Override
    public T pop() {
        T tmp = parents[pI];
        parents[pI] = null;
        pI--;
        do oI--; while (offsets[oI] == MAX);
        return tmp;
    }

    @Override
    public T peekData() {
        return parents[pI];
    }

    @Override
    public void setData(T data) {
        parents[pI] = data;
    }

    @Override
    public int peekOffset() {
        int j = oI;
        int s = 0;
        do {
            s += Byte.toUnsignedInt(offsets[j]);
            j--;
        } while (j > 0 && offsets[j - 1] == MAX);
        return s;
    }

    @Override
    public int depth() {
        return pI;
    }

    @Override
    public void shiftOffset(int d) {
        int s = d;
        while (s > Byte.toUnsignedInt(MAX)) {
            offsets[oI] = MAX;
            s -= Byte.toUnsignedInt(MAX);
            incO();
        }
        offsets[oI] = (byte) s;
    }

    protected void incP() {
        pI++;
        if (pI >= parents.length) {
            parents = Arrays.copyOf(parents, parents.length * GROW_FACTOR);
        }
    }

    protected void incO() {
        oI++;
        if (oI >= offsets.length) {
            offsets = Arrays.copyOf(offsets, offsets.length * GROW_FACTOR);
        }
    }
}

interface IntIterator {
    int next() throws NoSuchElementException;

    boolean hasNext();
}

class TreePathSimpleImpl implements TreePath {
    TreePath parent;
    int offset;

    TreePathSimpleImpl(int offset) {
        this.offset = offset;
    }

    TreePathSimpleImpl(TreePath parent, int offset) {
        this(offset);
        this.parent = parent;
    }

    @Override
    public TreePath cons(int offset) {
        return new TreePathSimpleImpl(this, offset);
    }

    @Override
    public int depth() {
        if (parent == null) {
            return 0;
        }
        return parent.depth() + 1;
    }

    @Override
    public int car() {
        return offset;
    }

    @Override
    public TreePath cdr() {
        return parent;
    }

//    @Override
//    public IntIterator iterator() {
//        return new IntIterator() {
//            int i = 0;
//
//            @Override
//            public int next() throws NoSuchElementException {
//                return 0;
//            }
//
//            @Override
//            public boolean hasNext() {
//                return false;
//            }
//        };
//    }
}