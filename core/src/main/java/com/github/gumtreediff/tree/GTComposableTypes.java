package com.github.gumtreediff.tree;

import java.util.*;
import java.util.regex.Pattern;

public interface GTComposableTypes {

    interface BasicNode {
        /**
         * Returns the type (i.e. IfStatement).
         */
        Type getType();

        /**
         * @return a boolean indicating if the trees have the same type.
         */
        default boolean hasSameType(ITree t) {
            return BasicNode.hasSameType(this, t);
        }

        static <T extends BasicNode, U extends BasicNode> boolean hasSameType(T a, U b) {
            return a.getType() == b.getType();
        }

        /**
         * Returns a string description of the node as well as its descendants.
         */
        String toTreeString();

        /*
         * Returns the metrics object computed for this node. This object is lazily computed
         * when first requested. When metrics have been computed, the tree must remain unchanged.
         */
        TreeMetrics getMetrics();

        /**
         * Returns the metadata with the given key for this node.
         */
        Object getMetadata(String key);

        /**
         * Returns an iterator for all metadata of this node.
         */
        Iterator<Map.Entry<String, Object>> getMetadata();

        /**
         * Make a deep copy of the tree. Deep copy of node however shares Metadata
         */
        ITree deepCopy();

        /**
         * Indicates whether or not this node and its descendants are isostructural (isomorphism without labels) to the node
         * given in parameter and its descendants (which must not be null).
         * This test fails fast.
         */
        default boolean isIsoStructuralTo(ITree tree) {
            return BasicNode.isIsoStructuralTo(this, tree);
        }

        static <T extends BasicNode, U extends BasicNode> boolean isIsoStructuralTo(T a, U b) {
            return BasicNode.hasSameType(a, b);
        }

        /**
         * Indicates whether or not this node and its descendants are isomorphic to the node
         * given in parameter and its descendants (which must not be null).
         * This test fails fast.
         */
        boolean isIsomorphicTo(ITree tree);

    }

    interface MutableBasicNode extends BasicNode {

        /**
         * Sets the type of the node (i.e. IfStatement).
         */
        void setType(Type type);

        /**
         * Sets the metric object for this node.
         */
        void setMetrics(TreeMetrics metrics);

        /**
         * Set the metadata with the given key and value for this node.
         */
        Object setMetadata(String key, Object value);

    }

    interface LabeledNode extends BasicNode {

        String NO_LABEL = "";

        /**
         * Indicates whether the node has a label or not.
         */
        default boolean hasLabel() {
            return !NO_LABEL.equals(getLabel());
        }

        /**
         * Returns the label of the node. If the node has no label, an empty string is returned.
         *
         * @see #hasLabel()
         */
        String getLabel();

        /**
         * Indicates whether or not the tree is similar to the given tree.
         *
         * @return true if they are compatible and have same label, false either
         */
        default boolean hasSameTypeAndLabel(ITree t) {
            return LabeledNode.hasSameTypeAndLabel(this, t);
        }

        static <T extends BasicNode, U extends BasicNode> boolean hasSameTypeAndLabel(T a, U b) {
            if (!(a instanceof LabeledNode))
                if (!(b instanceof LabeledNode))
                    return BasicNode.hasSameType(a, b);
                else return false;
            return BasicNode.hasSameType(a, b) && ((LabeledNode) a).getLabel().equals(((LabeledNode) b).getLabel());
        }

        default boolean isIsomorphicTo(ITree tree) {
            return LabeledNode.isIsomorphicTo(this, tree);
        }

        static <T extends BasicNode, U extends BasicNode> boolean isIsomorphicTo(T a, U b) {
            return LabeledNode.hasSameTypeAndLabel(a, b);
        }
    }

    interface MutableLabeledNode extends LabeledNode {

        /**
         * Sets the label of the node.
         */
        void setLabel(String label);

    }

    interface UniqueNode extends BasicNode {

        int NO_POS = -1;

        /**
         * Set the parent of this node. The parent will have this node in its
         * children list, at the last position.
         *
         * @see #setParentAndUpdateChildren(ITree)
         */
        void setParentAndUpdateChildren(ITree parent);

        /**
         * Returns a boolean indicating if the tree has a parent or not, and therefore is the root.
         * New: if it is a root can have not parents and not being a root
         */
        default boolean isRoot() {
            return getParent() == null;
        }

        /**
         * Returns the parent node of the node. If the node is a root, the method returns null.
         *
         * @see #isRoot()
         */
        ITree getParent();

        /**
         * @return the list of all parents of the node (parent, parent of parent, etc.)
         */
        default List<ITree> getParents() {
            List<ITree> parents = new ArrayList<>();
            if (getParent() == null)
                return parents;
            else {
                parents.add(getParent());
                parents.addAll(getParent().getParents());
            }
            return parents;
        }

        /**
         * @return the position of the node in its parent children list
         */
        default int positionInParent() {
            ITree p = getParent();
            if (p == null)
                return -1;
            else
                return p.getChildren().indexOf((ITree) this);
        }

        default boolean isUnique() {
            return getParent() == null && !isRoot();
        }
    }

    interface MutableUniqueNode extends UniqueNode {

        /**
         * Set the parent of this node. Be careful that the parent node won't have this node in its
         * children list.
         */
        void setParent(ITree parent);
        // TODO QLD deprecate at least from this interface because it is only used a few times
    }

    interface BasicTree extends BasicNode {
        Pattern urlPattern = Pattern.compile("\\d+(\\.\\d+)*");

        int getChildrenSize();

        /**
         * @param path the child position as a sequence of child index (left child is at index 0)
         */
        default BasicTree getChild(int ...path) {
            BasicTree current = this;
            for (int index : path) {
                current = current.getChild(index);
            }
            return current;
        }

        /**
         * @param position the child position, starting at 0
         */
        default ITree getChild(int position) {
            return getChildren().get(position);
        }

        /**
         * Returns the child node at the given URL.
         *
         * @param url the URL, such as <code>0.1.2</code>
         */
        default BasicTree getChild(String url) {
            if (!urlPattern.matcher(url).matches())
                throw new IllegalArgumentException("Wrong URL format : " + url);

            List<String> path = new LinkedList<>(Arrays.asList(url.split("\\.")));
            BasicTree current = this;
            while (path.size() > 0) {
                int next = Integer.parseInt(path.remove(0));
                current = current.getChild(next);
            }

            return current;
        }

        /**
         * Returns a list containing the node's children. If the node has no children, the list is empty.
         *
         * @see #isLeaf()
         */
        List<ITree> getChildren();

        /**
         * @return a boolean indicating if the tree has at least one child or not.
         */
        default boolean isLeaf() {
            return getChildren().isEmpty();
        }


        /**
         * @return all the descendants (children, children of children, etc.) of the tree, using a pre-order.
         */
        default List<ITree> getDescendants() {
            List<ITree> trees = TreeUtils.preOrder((ITree) this);
            trees.remove(0);
            return trees;
        }
    }

    interface MutableBasicTree extends BasicTree {

        /**
         * Add the given tree as a child, at the last position and update its parent.
         */
        void addChild(ITree t);

        /**
         * Sets the list of children of this node.
         */
        void setChildren(List<ITree> children);
    }

    interface BasicArrayTree extends BasicTree {

        /**
         * @return the position of the child, or -1 if the given child is not in the children list.
         */
        default int getChildPosition(ITree child) {
            return getChildren().indexOf(child);
        }
    }

    interface MutableBasicArrayTree extends BasicTree {
        /**
         * Insert the given tree as the position-th child, and update its parent.
         */
        void insertChild(ITree t, int position);
    }

    interface PositionedNode extends BasicNode {
        /**
         * Returns the absolute character beginning position of the node in its defining stream.
         */
        int getPos();

        /**
         * Returns the number of character corresponding to the node in its defining stream.
         */
        int getLength();

        /**
         * @return the absolute character index where the node ends in its defining stream.
         */
        default int getEndPos() {
            return getPos() + getLength();
        }
    }

    interface MutablePositionedNode extends BasicNode {

        /**
         * Sets the absolute character beginning index of the node in its defining stream.
         */
        void setPos(int pos);

        /**
         * Sets the number of character corresponding to the node in its defining stream.
         */
        void setLength(int length);

    }

    interface FullTree extends BasicNode, UniqueNode, BasicArrayTree, LabeledNode, PositionedNode {
        static <T extends BasicNode, U extends BasicNode> boolean isIsomorphicTo(T a, U b) {
            if (!LabeledNode.isIsomorphicTo(a, b))
                return false;

            if (a.getMetrics() != null && b.getMetrics() != null && a.getMetrics().hash != b.getMetrics().hash)
                return false;

            if (!(a instanceof BasicTree))
                return !(b instanceof BasicTree);

            if (((BasicTree) a).getChildrenSize() != ((BasicTree)b).getChildrenSize())
                return false;

            for (int i = 0; i < ((BasicTree) a).getChildrenSize(); i++) {
                boolean isChildrenIsomophic = FullTree.isIsomorphicTo(((BasicTree) a).getChild(i), ((BasicTree) b).getChild(i));
                if (!isChildrenIsomophic)
                    return false;
            }

            return true;
        }

        /**
         * Indicates whether or not this node and its descendants are isomorphic to the node
         * given in parameter and its descendants (which must not be null).
         * This test fails fast.
         */
        default boolean isIsomorphicTo(ITree tree) {
            return FullTree.isIsomorphicTo(this, tree);
        }

        static <T extends BasicNode, U extends BasicNode> boolean isIsoStructuralTo(T a, U b) {
            if (!BasicNode.isIsoStructuralTo(a, b))
                return false;

            if (a.getMetrics() != null && b.getMetrics() != null && a.getMetrics().structureHash != b.getMetrics().structureHash)
                return false;

            if (!(a instanceof BasicTree))
                return !(b instanceof BasicTree);

            if (((BasicTree) a).getChildrenSize() != ((BasicTree)b).getChildrenSize())
                return false;

            for (int i = 0; i < ((BasicTree) a).getChildrenSize(); i++) {
                boolean isChildrenStructural = FullTree.isIsoStructuralTo(((BasicTree) a).getChild(i), ((BasicTree) b).getChild(i));
                if (!isChildrenStructural)
                    return false;
            }

            return true;
        }

        /**
         * Indicates whether or not this node and its descendants are isostructural (isomorphism without labels) to the node
         * given in parameter and its descendants (which must not be null).
         * This test fails fast.
         */
        default boolean isIsoStructuralTo(ITree tree) {
            return FullTree.isIsoStructuralTo(this, tree);
        }
    }

    interface MutableFullTree extends FullTree, MutablePositionedNode, MutableBasicArrayTree, MutableBasicNode, MutableUniqueNode, MutableBasicTree, MutableLabeledNode {

        /**
         * Indicates whether or not this node and its descendants are isomorphic to the node
         * given in parameter and its descendants (which must not be null).
         * This test fails fast.
         */
        default boolean isIsomorphicTo(ITree tree) {
            if (getMetrics() != null && tree.getMetrics() != null && getMetrics().hash != tree.getMetrics().hash)
                return false;

            if (!hasSameTypeAndLabel(tree))
                return false;

            if (getChildren().size() != tree.getChildren().size())
                return false;

            for (int i = 0; i < getChildren().size(); i++) {
                boolean isChildrenIsomophic = getChild(i).isIsomorphicTo(tree.getChild(i));
                if (!isChildrenIsomophic)
                    return false;
            }

            return true;
        }

        /**
         * Indicates whether or not this node and its descendants are isostructural (isomorphism without labels) to the node
         * given in parameter and its descendants (which must not be null).
         * This test fails fast.
         */
        default boolean isIsoStructuralTo(ITree tree) {
            if (getMetrics() != null && tree.getMetrics() != null && getMetrics().structureHash != tree.getMetrics().structureHash)
                return false;

            if (this.getType() != tree.getType())
                return false;

            if (getChildren().size() != tree.getChildren().size())
                return false;

            for (int i = 0; i < getChildren().size(); i++) {
                boolean isChildrenStructural = getChild(i).isIsoStructuralTo(tree.getChild(i));
                if (!isChildrenStructural)
                    return false;
            }

            return true;
        }
    }
}
