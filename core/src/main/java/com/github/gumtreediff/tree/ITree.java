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
import java.util.Map.Entry;
import java.util.regex.Pattern;


/**
 * Interface to represent abstract syntax trees.
 */
public interface ITree extends GTComposableTypes.MutableFullTree {
    /**
     * Returns a list containing the node and its descendants, ordered using a pre-order.
     *
     */
    default Iterable<ITree> preOrder() {
        return () -> TreeUtils.preOrderIterator(ITree.this);
    }

    /**
     * Returns a list containing the node and its descendants, ordered using a post-order.
     *
     */
    default Iterable<ITree> postOrder() {
        return () -> TreeUtils.postOrderIterator(ITree.this);
    }

    /**
     * Returns a list containing the node and its descendants, ordered using a breadth-first order.
     *
     */
    default Iterable<ITree> breadthFirst() {
        return () -> TreeUtils.breadthFirstIterator(ITree.this);
    }
}
