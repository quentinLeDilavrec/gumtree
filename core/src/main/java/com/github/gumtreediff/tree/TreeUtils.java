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

import java.util.*;

import com.github.gumtreediff.utils.Pair;

public final class TreeUtils {
    private TreeUtils() {
    }

    /**
     * Returns a list of every subtrees and the tree ordered using a pre-order.
     *
     * @param tree a Tree.
     */
    public static List<ITree> preOrder(ITree tree) {
        List<ITree> trees = new ArrayList<>();
        preOrder(tree, trees);
        return trees;
    }

    private static void preOrder(ITree tree, List<ITree> trees) {
        trees.add(tree);
        if (!tree.isLeaf())
            for (ITree c : tree.getChildren())
                preOrder(c, trees);
    }

    /**
     * Returns a list of every subtrees and the tree ordered using a breadth-first order.
     *
     * @param tree a Tree.
     */
    public static List<ITree> breadthFirst(ITree tree) {
        List<ITree> trees = new ArrayList<>();
        List<ITree> currents = new ArrayList<>();
        currents.add(tree);
        while (currents.size() > 0) {
            ITree c = currents.remove(0);
            trees.add(c);
            currents.addAll(c.getChildren());
        }
        return trees;
    }

    public static Iterator<ITree> breadthFirstIterator(final ITree tree) {
        return new Iterator<ITree>() {
            Deque<Iterator<ITree>> fifo = new ArrayDeque<>();

            {
                addLasts(new FakeTree(tree));
            }

            @Override
            public boolean hasNext() {
                return !fifo.isEmpty();
            }

            @Override
            public ITree next() {
                while (!fifo.isEmpty()) {
                    Iterator<ITree> it = fifo.getFirst();
                    if (it.hasNext()) {
                        ITree item = it.next();
                        if (!it.hasNext())
                            fifo.removeFirst();
                        addLasts(item);
                        return item;
                    }
                }
                throw new NoSuchElementException();
            }

            private void addLasts(ITree item) {
                List<ITree> children = item.getChildren();
                if (!children.isEmpty())
                    fifo.addLast(children.iterator());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Returns a list of every subtrees and the tree ordered using a post-order.
     *
     * @param tree a Tree.
     */
    public static List<ITree> postOrder(ITree tree) {
        List<ITree> trees = new ArrayList<>();
        postOrder(tree, trees);
        return trees;
    }

    private static void postOrder(ITree tree, List<ITree> trees) {
        if (!tree.isLeaf())
            for (ITree c : tree.getChildren())
                postOrder(c, trees);
        trees.add(tree);
    }

    public static Iterator<ITree> postOrderIterator(final ITree tree) {
        return new Iterator<ITree>() {
            Deque<Pair<ITree, Iterator<ITree>>> stack = new ArrayDeque<>();

            {
                push(tree);
            }

            @Override
            public boolean hasNext() {
                return stack.size() > 0;
            }

            @Override
            public ITree next() {
                if (stack.isEmpty())
                    throw new NoSuchElementException();
                return selectNextChild(stack.peek().second);
            }

            ITree selectNextChild(Iterator<ITree> it) {
                if (!it.hasNext())
                    return stack.pop().first;
                ITree item = it.next();
                if (item.isLeaf())
                    return item;
                return selectNextChild(push(item));
            }

            private Iterator<ITree> push(ITree item) {
                Iterator<ITree> it = item.getChildren().iterator();
                stack.push(new Pair<>(item, it));
                return it;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static Iterator<ITree> preOrderIterator(ITree tree) {
        return new Iterator<ITree>() {
            Deque<Iterator<ITree>> stack = new ArrayDeque<>();

            {
                push(new FakeTree(tree));
            }

            @Override
            public boolean hasNext() {
                return stack.size() > 0;
            }

            @Override
            public ITree next() {
                Iterator<ITree> it = stack.peek();
                if (it == null)
                    throw new NoSuchElementException();
                ITree t = it.next();
                while (it != null && !it.hasNext()) {
                    stack.pop();
                    it = stack.peek();
                }
                push(t);
                return t;
            }

            private void push(ITree tree) {
                if (!tree.isLeaf())
                    stack.push(tree.getChildren().iterator());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static Iterable<ITree> preOrderIterable(ITree tree) {
        return new Iterable<ITree>() {
            @Override
            public Iterator<ITree> iterator() {
                return preOrderIterator(tree);
            }
        };
    }

    public static Iterator<ITree> leafIterator(final Iterator<ITree> it) {
        return new Iterator<ITree>() {
            ITree current = it.hasNext() ? it.next() : null;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public ITree next() {
                ITree val = current;
                while (it.hasNext()) {
                    current = it.next();
                    if (current.isLeaf())
                        break;
                }
                if (!it.hasNext()) {
                    current = null;
                }
                return val;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    static interface TreeZipper<T> {
        public TreeZipper<T> clone();

        public boolean up();

        public boolean downLeft();

        public boolean downRight();

        public boolean left();

        public boolean right();

        public T getNode();

        public T getParent();
    }

    enum Has {
        DOWN, UP, LEFT, RIGHT;
    }

    static class ITreeZipper<T extends ITree> implements TreeZipper<T> {

        protected T current;

        ITreeZipper(T tree) {
            this.current = tree;
        }

        @Override
        public TreeZipper<T> clone() {
            return new ITreeZipper(current);
        }

        @Override
        public boolean up() {
            T parent = getParent();
            if (parent != null) {
                setCurrent(parent);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean downLeft() {
            T left = getChildLeft(getCurrent(), null);
            if (left != null) {
                setCurrent(left);
                return true;
            }
            return false;
        }

        @Override
        public boolean downRight() {
            T right = getChildRight(getCurrent(), null);
            if (right != null) {
                setCurrent(right);
                return true;
            }
            return false;
        }

        @Override
        public boolean left() {
            T parent = getParent();
            if (parent != null) {
                T left = getChildLeft(parent, getCurrent());
                if (left != null) {
                    setCurrent(left);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean right() {
            T parent = getParent();
            if (parent != null) {
                T right = getChildRight(parent, getCurrent());
                if (right != null) {
                    setCurrent(right);
                    return true;
                }
            }
            return false;
        }

        protected T getChildLeft(T tree, T child) {
            int index = child == null ? 0 : tree.getChildPosition(child) - 1;
            if (index >= 0) {
                return (T)tree.getChild(index);
            }
            return null;
        }

        protected T getChildRight(T tree, T child) {
            List<T> children = (List)tree.getChildren();
            int size = children.size();
            if (child == null) {
                return children.get(size - 1);
            } else {
                int index = children.indexOf(child) + 1;
                if (index < size) {
                    return children.get(index);
                }
            }
            return null;
        }

        @Override
        public T getNode() {
            return getCurrent();
        }

        @Override
        public T getParent() {
            return (T)getCurrent().getParent();
        }

        protected T getCurrent() {
            return current;
        }
        protected void setCurrent(T current) {
            this.current = current;
        }
    }

//    static class VersionedTreeZipper extends ITreeAbstractZipper<AbstractVersionedTree> {
//        Version version;
//
//        protected AbstractVersionedTree current;
//
//        VersionedTreeZipper(AbstractVersionedTree tree, Version version) {
//            this.current = tree;
//            this.version = version;
//        }
//
//        @Override
//        public VersionedTreeZipper clone() {
//            return new VersionedTreeZipper(current,version);
//        }
//
//        @Override
//        protected AbstractVersionedTree getCurrent() {
//            return current;
//        }
//
//        @Override
//        protected void setCurrent(AbstractVersionedTree current) {
//            this.current = current;
//        }
//
//        @Override
//        protected AbstractVersionedTree getChildLeft(AbstractVersionedTree tree, AbstractVersionedTree child) {
//            int index = child == null ? 0 : tree.getChildPosition(child, version) - 1;
//            if (index >= 0) {
//                return tree.getChild(version, index);
//            }
//            return null;
//        }
//
//        @Override
//        protected AbstractVersionedTree getChildRight(AbstractVersionedTree tree, AbstractVersionedTree child) {
//            // TODO might really need optimization at some point
//            List<AbstractVersionedTree> children = tree.getChildren(version);
//            int size = children.size();
//            if (child == null) {
//                return children.get(size - 1);
//            } else {
//                int index = children.indexOf(child) + 1;
//                if (index < size) {
//                    return children.get(index);
//                }
//            }
//            return null;
//        }
//    }

    static class CompressedTreeZipper implements TreeZipper<ITree> {
        static int INITIAL_SIZE = 2;
        static int GROW_FACTOR = 2;
        static byte MAX = -1;
        ITree[] parents;
        byte[] indexes;
        int i = 0;

        protected void inc() {
            i++;
            if (i>=parents.length) {
                parents = Arrays.copyOf(parents, parents.length * GROW_FACTOR);
                indexes = Arrays.copyOf(indexes, indexes.length * GROW_FACTOR);
            }
        }

        CompressedTreeZipper(ITree tree) {
            parents = new ITree[INITIAL_SIZE];
            indexes = new byte[INITIAL_SIZE];
            int i = 0;
            parents[i] = tree;
            indexes[i] = 0;
        }

        protected CompressedTreeZipper(ITree[] parents, byte[] indexes) {
            this.parents = Arrays.copyOf(parents, parents.length);
            this.indexes = Arrays.copyOf(indexes, parents.length);
        }

        @Override
        public CompressedTreeZipper clone() {
            return new CompressedTreeZipper(parents, indexes);
        }

        @Override
        public boolean up() {
            if (i==0) {
                return false;
            }
            do {
                parents[i] = null;
                i--;
            } while (indexes[i]==MAX);
            return true;
        }

        @Override
        public boolean downLeft() {
            ITree left = parents[i].getChild(0);
            if (left != null) {
                inc();
                parents[i] = left;
                indexes[i] = 0;
                return true;
            }
            return false;
        }

        @Override
        public boolean downRight() {
            int size = parents[i].getChildrenSize();
            if(size<1) return false;
            ITree right = parents[i].getChild(size-1);
            if (right != null) {
                inc();
                int s = size-1;
                while (s > Byte.toUnsignedInt(MAX)) {
                    parents[i] = right;
                    indexes[i] = MAX;
                    s -= Byte.toUnsignedInt(MAX);
                    inc();
                }
                parents[i] = right;
                indexes[i] = (byte) s;
                return true;
            }
            return false;
        }

        @Override
        public boolean left() {
            int ind=1; int j = i;
            do {
                ind += Byte.toUnsignedInt(indexes[j]);
                j--;
            } while (indexes[j]==MAX);
            ITree p = parents[j];
            if (1>ind) return false;
            ITree left = parents[i].getChild(ind);
            if (indexes[i]==0) {
                parents[i]=null;
                i--;
                indexes[i] = 0;
            }
            parents[i] = left;
            indexes[i] += 0;
            return true;
        }

        @Override
        public boolean right() {
            int ind=1; int j = i;
            do {
                ind += Byte.toUnsignedInt(indexes[j]);
                j--;
            } while (indexes[j]==MAX);
            ITree p = parents[j];
            if (ind>=p.getChildrenSize()) return false;
            ITree right = p.getChild(ind);
            if (indexes[i]==MAX) {
                inc();
                indexes[i] = 0;
            }
            parents[i] = right;
            indexes[i] += 1;
            return true;
        }

        @Override
        public ITree getNode() {
            return parents[i];
        }

        @Override
        public ITree getParent() {
            int j = i;
            while (indexes[j-1]==MAX) j--;
            return parents[j-1];
        }
    }

    static class VersionedTreeZipper implements TreeZipper<AbstractVersionedTree> {
        static int INITIAL_SIZE = 2;
        static int GROW_FACTOR = 2;
        static byte MAX = -1;
        private final Version version;
        AbstractVersionedTree[] parents;
        byte[] indexes;
        int i = 0;

        protected void inc() {
            i++;
            if (i>=parents.length) {
                parents = Arrays.copyOf(parents, parents.length * GROW_FACTOR);
                indexes = Arrays.copyOf(indexes, parents.length * GROW_FACTOR);
            }
        }

        VersionedTreeZipper(AbstractVersionedTree tree, Version version) {
            parents = new AbstractVersionedTree[INITIAL_SIZE];
            indexes = new byte[INITIAL_SIZE];
            int i = 0;
            parents[i] = tree;
            indexes[i] = 0;
            this.version = version;
        }

        protected VersionedTreeZipper(AbstractVersionedTree[] parents, byte[] indexes, Version version) {
            this.parents = Arrays.copyOf(parents, parents.length);
            this.indexes = Arrays.copyOf(indexes, parents.length);
            this.version = version;
        }

        @Override
        public VersionedTreeZipper clone() {
            return new VersionedTreeZipper(parents, indexes, version);
        }

        @Override
        public boolean up() {
            if (i==0) {
                return false;
            }
            do {
                parents[i] = null;
                i--;
            } while (indexes[i]==MAX);
            return true;
        }

        @Override
        public boolean downLeft() {
            AbstractVersionedTree left = parents[i].getChild(version,0);
            if (left != null) {
                inc();
                parents[i] = left;
                indexes[i] = 0;
                return true;
            }
            return false;
        }

        @Override
        public boolean downRight() {
            int size = parents[i].getChildrenSize();
            if(size<1) return false;
            AbstractVersionedTree right = parents[i].getChild(version,size-1);
            if (right != null) {
                inc();
                int s = size-1;
                while (s > Byte.toUnsignedInt(MAX)) {
                    parents[i] = right;
                    indexes[i] = MAX;
                    s -= Byte.toUnsignedInt(MAX);
                }
                parents[i] = right;
                indexes[i] = (byte) s;
                return true;
            }
            return false;
        }

        @Override
        public boolean left() {
            int ind=1; int j = i;
            do {
                ind += Byte.toUnsignedInt(indexes[j]);
                j--;
            } while (indexes[j]==MAX);
            if (1>ind) return false;
            AbstractVersionedTree p = parents[j];
            AbstractVersionedTree left = p.getChild(version, ind);
            if (indexes[i]==0) {
                parents[i]=null;
                i--;
                indexes[i] = 0;
            }
            parents[i] = left;
            indexes[i] += 0;
            return true;
        }

        @Override
        public boolean right() {
            int ind=1; int j = i;
            do {
                ind += Byte.toUnsignedInt(indexes[j]);
                j--;
            } while (indexes[j]==MAX);
            AbstractVersionedTree p = parents[j];
            if (ind>=p.getChildrenSize()) return false;
            AbstractVersionedTree right = p.getChild(version, ind);
            if (indexes[i]==MAX) {
                inc();
                indexes[i] = 0;
            }
            parents[i] = right;
            indexes[i] += 1;
            return true;
        }

        @Override
        public AbstractVersionedTree getNode() {
            return parents[i];
        }

        @Override
        public AbstractVersionedTree getParent() {
            int j = i;
            while (indexes[j-1]==MAX) j--;
            return parents[j-1];
        }
    }

    static class TreeBiZipper<T,U> implements TreeZipper<Pair<T,U>> {
        private final TreeZipper<T> left;
        private final TreeZipper<U> right;
        private boolean invalid = false;

        public TreeBiZipper(TreeZipper<T> left, TreeZipper<U> right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public TreeBiZipper<T,U> clone() {
            return new TreeBiZipper<>(left.clone(),right.clone());
        }

        public boolean isInvalid() {
            return invalid;
        }

        @Override
        public boolean up() {
            boolean l = left.up();
            boolean r = right.up();
            if (l==r) {
                return l;
            }
            invalid = true;
            return false;
        }

        @Override
        public boolean downLeft() {
            boolean l = left.downLeft();
            boolean r = right.downLeft();
            if (l==r) {
                return l;
            }
            invalid = true;
            return false;
        }

        @Override
        public boolean downRight() {
            boolean l = left.downRight();
            boolean r = right.downRight();
            if (l==r) {
                return l;
            }
            invalid = true;
            return false;
        }

        @Override
        public boolean left() {
            boolean l = left.left();
            boolean r = right.left();
            if (l==r) {
                return l;
            }
            invalid = true;
            return false;
        }

        @Override
        public boolean right() {
            boolean l = left.right();
            boolean r = right.right();
            if (l==r) {
                return l;
            }
            invalid = true;
            return false;
        }

        @Override
        public Pair<T,U> getNode() {
            return new Pair<>(left.getNode(),right.getNode());
        }

        @Override
        public Pair<T,U> getParent() {
            return new Pair<>(left.getParent(),right.getParent());
        }
    }

    static class PreOrderActuator {
        private TreeZipper zipper;
        protected Has has = Has.DOWN;
        protected boolean looped = false;

        PreOrderActuator(TreeZipper zipper) {
            this.zipper = zipper;
        }

        public void resetActionMemory() {
            has = Has.DOWN;
        }

        public final boolean next() {
            while (zipper != null) {
                boolean down = has == Has.UP && zipper.downLeft();
                if (down) {
                    has = Has.DOWN;
                    return true;
                } else {
                    boolean left = zipper.left();
                    if (left) {
                        has = Has.LEFT;
                        return true;
                    } else {
                        boolean up = zipper.up();
                        if (up) {
                        } else {
                            zipper = null;
                            return false;
                        }
                        has = Has.UP;
                    }
                }
            }
            return false;
        }

        public void skip() {
            has = Has.UP;
        }
    }

    static class RevPreOrderActioner {
        private TreeZipper zipper;
        protected Has has = Has.DOWN;

        RevPreOrderActioner(TreeZipper zipper) {
            this.zipper = zipper;
        }

        public void resetActionMemory() {
            has = Has.DOWN;
        }

        public final boolean next() {
            while (zipper != null) {
                boolean down = has == Has.UP && zipper.downRight();
                if (down) {
                    has = Has.DOWN;
                    return true;
                } else {
                    boolean right = zipper.right();
                    if (right) {
                        has = Has.RIGHT;
                        return true;
                    } else {
                        boolean up = zipper.up();
                        if (up) {
                        } else {
                            zipper = null;
                            return false;
                        }
                        has = Has.UP;
                    }
                }
            }
            return false;
        }

        public void skip() {
            has = Has.UP;
        }
    }

    static class PostOrderActuator {
        private TreeZipper zipper;
        protected Has has = Has.DOWN;
        protected boolean looped = false;

        PostOrderActuator(TreeZipper zipper) {
            this.zipper = zipper;
        }

        public void resetActionMemory() {
            has = Has.DOWN;
        }

        public final boolean next() {
            while (zipper != null) {



                boolean down = has == Has.UP && zipper.downLeft();
                if (down) {
                    has = Has.DOWN;
                    return true;
                } else {
                    boolean left = zipper.left();
                    if (left) {
                        has = Has.LEFT;
                        return true;
                    } else {
                        boolean up = zipper.up();
                        if (up) {
                        } else {
                            zipper = null;
                            return false;
                        }
                        has = Has.UP;
                    }
                }
            }
            return false;
        }

        public void skip() {
            has = Has.UP;
        }
    }

}
