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
 * Copyright 2019 Jean-Rémy Falleri <jr.falleri@gmail.com>
 * Copyright 2019 Floréal Morandat <florealm@gmail.com>
 */

package com.github.gumtreediff.tree;

public interface TreeMetrics {
    int size();

    int height();

    int hash();

    int structureHash();

    int depth();

    int position();

    static TreeMetrics create(int size, int height, int hash, int structureHash, int depth, int position){
        return new TreeMetricsImpl(size, height, hash, structureHash, depth, position);
    }

    static TreeMetrics create(int size, int height, int hash, int structureHash){
        return new SubTreeMetricsImpl(size, height, hash, structureHash);
    }

    TreeMetrics located(int depth, int position);

    class SubTreeMetricsImpl implements TreeMetrics {

        final int size;

        final int height;

        final int hash;

        final int structureHash;

        public int size() {
            return size;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int hash() {
            return hash;
        }

        @Override
        public int structureHash() {
            return structureHash;
        }

        @Override
        public int depth() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int position() {
            throw new UnsupportedOperationException();
        }

        public SubTreeMetricsImpl(int size, int height, int hash, int structureHash) {
            this.size = size;
            this.height = height;
            this.hash = hash;
            this.structureHash = structureHash;
        }

        @Override
        public TreeMetrics located(int depth, int position) {
            return new TreeMetricsImpl(this.size(), this.height(), this.hash(), this.structureHash(), depth, position);
        }
    }

    class TreeMetricsImpl extends SubTreeMetricsImpl {

        final int depth;

        final int position;

        public int size() {
            return size;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int hash() {
            return hash;
        }

        @Override
        public int structureHash() {
            return structureHash;
        }

        @Override
        public int depth() {
            return depth;
        }

        @Override
        public int position() {
            return position;
        }

        public TreeMetricsImpl(int size, int height, int hash, int structureHash, int depth, int position) {
            super(size,height,hash,structureHash);
            this.depth = depth;
            this.position = position;
        }

    }
}
