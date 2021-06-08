package com.github.gumtreediff.tree;

import com.github.gumtreediff.actions.model.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VersionedTreeMerger {

    private final Set<Version> versions = new HashSet<>();
    private final AbstractVersionedTree root;

    public static VersionedTreeMerger from(ITree tree, Version version) {
        if (tree instanceof AbstractVersionedTree) {
            throw new UnsupportedOperationException("tree is already versioned");
        }
        return new VersionedTreeMerger(VersionedTree.deepCreate(tree, version), version);
    }

    public AbstractVersionedTree getRoot() {
        return root;
    }

    protected VersionedTreeMerger(AbstractVersionedTree tree, Version version) {
        this.root = tree;
        this.versions.add(version);
    }

//    public void merge(AbstractVersionedTree tree) {
//        new VersionedTreeMerger(((AbstractVersionedTree) tree).deepCopy(), version);
//    }

    public void add(Version oldVersion, ITree oldTree, Version newVersion, Iterable<Action> actions) {
        if (!versions.contains(oldVersion)) {
            throw new RuntimeException("the old version need to be in the super-AST");
        }
//        if (tree instanceof AbstractVersionedTree) {
//            throw new UnsupportedOperationException("tree is already versioned, you should use merge");
//        }
        Map<ITree, Action> needingAction = new HashMap<>();
        for (Action a : actions) {
            if (a instanceof Insert) {
                Insert insert = (Insert) a;
                needingAction.put(insert.getParent(), a);
            } else if (a instanceof TreeInsert) {
                TreeInsert insert = (TreeInsert) a;
                needingAction.put(insert.getParent(), a);
            } else if (a instanceof Delete) {
                Delete delete = (Delete) a;
                needingAction.put(delete.getNode(), a);
            } else if (a instanceof TreeDelete) {
                TreeDelete delete = (TreeDelete) a;
                needingAction.put(delete.getNode(), a);
            } else if (a instanceof Update) {
                Update update = (Update) a;
                needingAction.put(update.getNode(), a);
            }
        }

        TreeUtils.VersionedTreeZipper versionedCursor = new TreeUtils.VersionedTreeZipper(root, oldVersion);
        TreeUtils.CompressedTreeZipper cursor = new TreeUtils.CompressedTreeZipper(oldTree);
        TreeUtils.TreeBiZipper<AbstractVersionedTree,ITree> biCursor = new TreeUtils.TreeBiZipper<>(versionedCursor, cursor);
        TreeUtils.PreOrderActuator actuator = new TreeUtils.PreOrderActuator(biCursor);
        while(true) {
            Action action = needingAction.get(cursor.getNode());
            AbstractVersionedTree current = versionedCursor.getNode();
            if (action instanceof Insert) {
                // insert right or left of current tree
                current.insertChild(VersionedTree.deepCreate(action.getNode(), newVersion),((Insert) action).getPosition());
            } else if (action instanceof Delete) {
                actuator.skip();
                delete(current,oldVersion,newVersion);
            } else if (action instanceof Update) {
                current.delete(newVersion);
                VersionedTree neww = VersionedTree.deepCreate(action.getNode(), newVersion);
                neww.setLabel(((Update) action).getValue());
                current.insertChild(neww,current.getParent().getChildPosition(current));
            } else if (action instanceof TreeInsert) {
                current.insertChild(VersionedTree.deepCreate(action.getNode(), newVersion),((TreeInsert) action).getPosition());
            } else if (action instanceof TreeDelete) {
                actuator.skip();
                delete(current,oldVersion,newVersion);
            }

            if (!actuator.next()) {
                if (biCursor.isInvalid())
                    throw new RuntimeException("olTree do not match any tree at given old version");
                break;
            }
        }

        //new VersionedTreeMerger(VersionedTree.deepCreate(tree, version), version);
    }

    private static void delete(AbstractVersionedTree current, Version oldVersion, Version newVersion) {
        current.delete(newVersion);
        for(AbstractVersionedTree child : current.getAllChildren()) {
            if (child.existsAt(oldVersion))
                delete(child, oldVersion, newVersion);
        }
    }

    static class MyHashMap<T,U> extends HashMap<T,U> {

    }

}
