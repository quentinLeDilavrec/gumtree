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
import com.github.gumtreediff.gen.SyntaxException;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import com.github.gumtreediff.tree.Type;
import jsitter.api.*;
import jsitter.impl.TSZipper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.Stack;

import static com.github.gumtreediff.tree.TypeSet.type;

@Register(id = "java-treesitter", accept = "\\.java$", priority = Registry.Priority.LOW)
public class JSitterJavaTreeGenerator extends TreeGenerator implements AutoCloseable {

    private final Parser parser;
    private TreeContext treeContext;
    private String s;

    public JSitterJavaTreeGenerator() {
        super();
        NodeType nodetype = new NodeType("source_file");
        Language<NodeType> lang = Language.load(
                nodetype,
                "java",
                "tree_sitter_java",
                "libtsjava.so",
                Language.class.getClassLoader());
        lang.register(nodetype);

        this.parser = lang.parser();
    }

    enum Has {
        DOWN, UP, RIGHT;
    }

    @Override
    public TreeContext generate(Reader r) throws IOException {
        this.s = new String(readerToCharArray(r));

        System.out.println(s.length());
        Tree<NodeType> tree = parser.parse(new StringText(s),null);

        treeContext = new TreeContext();
        Zipper<?> zipper = tree.getRoot().zipper();

        Stack<ITree> parents = new Stack<>();

        parents.add(extractTreeContext(zipper, null));

        Has has = Has.DOWN;
        while (zipper != null) {
            Zipper<?> down = has==Has.UP ? null : zipper.down();
            if (down!=null) {
                ITree newParent = extractTreeContext(down, parents.peek());
                parents.add(newParent);
                zipper = down;
                has = Has.DOWN;
            } else {
                Zipper<?> right = zipper.right();
                if (right != null) {
                    if (has!=Has.UP) {
                        parents.pop();
                    }
                    ITree newParent = extractTreeContext(right, parents.peek());
                    parents.add(newParent);
                    zipper = right;
                    has = Has.RIGHT;
                } else {
                    zipper = zipper.up();
                    if (zipper != null) {
                        parents.pop();
                        if (has!=Has.UP) {
                            parents.pop();
                        }
                    }
                    has = Has.UP;
                }
            }
        }
        return treeContext;
    }

    private ITree extractTreeContext(Zipper<?> node, ITree parent) {
        Type type = type(node.getType().getName());
        int pos = node.getByteOffset() / 2;
        int length = node.getByteSize() / 2;
        String label = extractLabel((TSZipper<?>)node, pos, length);
        ITree tree = treeContext.createTree(type, label);
        if (parent == null)
            treeContext.setRoot(tree);
        else
            tree.setParentAndUpdateChildren(parent);

        tree.setPos(pos);
        tree.setLength(length);

        return tree;
    }

    private String extractLabel(TSZipper<?> node, int pos, int length) {
        if(node.getType().equals("identifier")){
            return s.substring(pos,pos+length);
        } else if (node.visibleChildCount()>0) {
            return "";
        } else {
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
