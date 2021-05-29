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
import ai.serenade.treesitter.Parser;
import ai.serenade.treesitter.Node;
import ai.serenade.treesitter.Tree;

import com.github.gumtreediff.gen.Register;
import com.github.gumtreediff.gen.Registry;
import com.github.gumtreediff.gen.SyntaxException;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import com.github.gumtreediff.tree.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import static com.github.gumtreediff.tree.TypeSet.type;

@Register(id = "java-treesitter", accept = "\\.java$", priority = Registry.Priority.LOW)
public class TreeSitterJavaTreeGenerator extends TreeGenerator implements AutoCloseable {

    private final Parser parser;
    private TreeContext treeContext;
    private String s;

    public TreeSitterJavaTreeGenerator() {
        super();
        this.parser = new Parser();
        parser.setLanguage(Languages.java());
    }

    @Override
    public TreeContext generate(Reader r) throws IOException {
        this.s = new String(readerToCharArray(r));
        try (Tree n = parser.parseString(s);) {
            treeContext = new TreeContext();
            extractTreeContext(n.getRootNode(), null);
            return treeContext;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void extractTreeContext(Node node, ITree parent) {
        Type type = type(node.getType());
        String label = extractLabel(node);
        ITree tree = treeContext.createTree(type, label);
        if (parent == null)
            treeContext.setRoot(tree);
        else
            tree.setParentAndUpdateChildren(parent);

        int pos = node.getStartByte();
        int length = node.getEndByte() - pos;
        tree.setPos(pos);
        tree.setLength(length);

        for (int i = 0; i < node.getChildCount(); i++) {
            extractTreeContext(node.getChild(i), tree);
        }
    }

    private String extractLabel(Node node) {
        if(node.getType().equals("identifier")){
            return s.substring(node.getStartByte(),node.getEndByte());
        } else if (node.getChildCount()>0) {
            return "";
        } else {
            return s.substring(node.getStartByte(),node.getEndByte());
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
        this.parser.close();
    }
}
