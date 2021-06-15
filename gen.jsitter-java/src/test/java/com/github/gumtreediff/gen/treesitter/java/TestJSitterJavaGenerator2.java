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

import com.github.gumtreediff.io.TreeIoUtils;
import com.github.gumtreediff.tree.GTComposableTypes;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.github.gumtreediff.tree.TypeSet.type;
import static org.junit.jupiter.api.Assertions.*;

public class TestJSitterJavaGenerator2 {

    @Test
    public void testSimpleSyntax() throws IOException {
        String input = "public class Foo { public int foo; }";
        ITree tree = new JSitterJavaTreeGenerator2().generateFrom().string(input).getRoot();
        assertNotNull(tree);
        assertEquals(type("program"), tree.getType());
        assertEquals(17, tree.getMetrics().size());
    }

     @Test
     public void testJava5Syntax() throws IOException {
         String input = "public class Foo<A> { public List<A> foo; public void foo() "
                 + "{ for (A f : foo) { System.out.println(f); } } }";
         ITree tree = new JSitterJavaTreeGenerator2().generateFrom().string(input).getRoot();
         assertEquals(type("program"), tree.getType());
         assertEquals(61, tree.getMetrics().size());
     }

    @Test
    public void testNL() throws IOException {
        String input = "class Main {\n}";
        TreeContext ctx = new JSitterJavaTreeGenerator2().generateFrom().string(input);
        System.out.println(ctx.getRoot().toTreeString());
    }

    @Test
    public void testMethodInvocation() throws IOException {
        String input = "class Main {\n"
                + "    public static void foo() {\n"
                + "        a(b);\n"
                + "    }\n"
                + "}\n";
        TreeContext ctx = new JSitterJavaTreeGenerator2().generateFrom().string(input);
        String o1 = TreeIoUtils.toLisp(ctx).toString();

        input = "class Main {\n"
                + "    public static void foo() {\n"
                + "        a.b();\n"
                + "    }\n"
                + "}";
        ctx = new JSitterJavaTreeGenerator2().generateFrom().string(input);
        String o2 = TreeIoUtils.toLisp(ctx).toString();
        assertNotEquals(o1, o2);
    }

     @Test
     public void testJava8Syntax() throws IOException {
         String input = "public class Foo { public void foo(){ new ArrayList<Object>().stream().forEach(a -> {}); } }";
         ITree tree = new JSitterJavaTreeGenerator2().generateFrom().string(input).getRoot();
         assertEquals(type("program"), tree.getType());
         assertEquals(51, tree.getMetrics().size());
     }

//     @Test
//     public void badSyntax() throws IOException {
//         String input = "public clas Foo {}";
//         assertThrows(SyntaxException.class, () -> {
//             TreeContext ct = new TreeSitterJavaTreeGenerator().generateFrom().string(input);
//         });
//     }

     @Test
     public void testTypeDefinition() throws IOException {
         String input1 = "public class Foo {}";
         String input2 = "public interface Foo {}";
         TreeContext ct1 = new JSitterJavaTreeGenerator2().generateFrom().string(input1);
         TreeContext ct2 = new JSitterJavaTreeGenerator2().generateFrom().string(input2);
         assertNotEquals(ct1.getRoot().getMetrics().hash(), ct2.getRoot().getMetrics().hash());
     }

     @Test
     public void testInfixOperator() throws IOException {
         String input = "class Foo { int i = 3 + 3}";
         TreeContext ct = new JSitterJavaTreeGenerator2().generateFrom().string(input);
         System.out.println(ct.getRoot().toTreeString());
     }

     @Test
     public void testAssignment() throws IOException {
         String input = "class Foo { void foo() { s.foo  = 12; } }";
         TreeContext ct = new JSitterJavaTreeGenerator2().generateFrom().string(input);
         System.out.println(ct.getRoot().toTreeString());
     }

     @Test
     public void testPrefixExpression() throws IOException {
         String input = "class Foo { void foo() { ++s.i; } }";
         TreeContext ct = new JSitterJavaTreeGenerator2().generateFrom().string(input);
         System.out.println(ct.getRoot().toTreeString());
     }

     @Test
     public void testPostfixExpression() throws IOException {
         String input = "class Foo { void foo() { s.i++; } }";
         TreeContext ct = new JSitterJavaTreeGenerator2().generateFrom().string(input);
         System.out.println(ct.getRoot().toTreeString());
     }

     @Test
     public void testArrayCreation() throws IOException {
         String input1 = "class Foo { void foo() { int[][] t = new int[12][]; } }";
         TreeContext ct1 = new JSitterJavaTreeGenerator2().generateFrom().string(input1);
         System.out.println(ct1.getRoot().toTreeString());

         String input2 = "class Foo { void foo() { int[][] t = new int[][12]; } }";
         TreeContext ct2 = new JSitterJavaTreeGenerator2().generateFrom().string(input2);
         System.out.println(ct2.getRoot().toTreeString());
     }

    @Test
    public void testLongInput() throws IOException {
        String input = "void f(){}\n";
        int count = 1;
        for (int i = 0; i < 10; i++) {
            input = input + input;
            count = count + count;
        }
        TreeContext ct = new JSitterJavaTreeGenerator2().generateFrom().string("class A {\n"+input+"}");
        assertEquals(count, ct.getRoot().getChild(0).getChild(2).getChildrenSize()-2);
    }



    int pad(int v) {
        return (v % 8 == 0) ? v : (v - (v % 8) + 8);
    }

    @Test
    public void testPad() throws IOException {
        for (int i = 0; i < 20; i++) {
            System.out.print(i);
            System.out.print(" ");
            System.out.println(pad(i));
        }
    }

    @Test
    public void testBigs() throws IOException {
        Path ori = Paths.get("C:\\Users\\quentin\\Documents\\dev\\gumtree");
//        Path ori = Paths.get("C:\\Users\\quentin\\resources\\Versions\\INRIA\\spoon");
        List<Path> paths = Files.find(ori,
                Integer.MAX_VALUE,
                (filePath, fileAttr) ->
                        fileAttr.isRegularFile() && filePath.getFileName().toString().endsWith(".java"))
                .collect(Collectors.toList());
        JSitterJavaTreeGenerator2 gen = new JSitterJavaTreeGenerator2();
        TreeContextCompressing ct1 = null;
        System.out.println(paths.size());
        for (Path p : paths) {
//            System.out.println(p);
            String content;
            try {
                content = Files.readString(p);
            } catch (MalformedInputException e) {
                content = Files.readString(p, StandardCharsets.ISO_8859_1);
            }
            System.err.println(p);
            System.err.println(content.length());
            ct1 = (TreeContextCompressing) gen.generateFrom().string(content);
            String c = "{";
            int countOri = getCount(c, content);
            System.out.println(c+getCount(c, content));
            String s = ct1.getRoot().toTreeString();
            int countParsed = getCount(c, s);
            System.out.println(c+getCount(c, s));
//            System.out.println(") { "+getCount(") {", content));
//            System.out.println("method_declaration "+getCount("method_declaration", s));
            assertEquals(countOri,countParsed);
//            assertEquals(getCount("@", content),getCount("@", s));
//            assertEquals(getCount("(", content),getCount("(", s));
//            System.out.println(ct1.getRoot().toTreeString());
        }
        stats(ct1);
    }

    @Test
    public void testBigs2() throws IOException {
        System.out.println(77);
//        Path ori = Paths.get("C:\\Users\\quentin\\Documents\\dev\\gumtree\\benchmark");
        Path ori = Paths.get("C:\\Users\\quentin\\resources\\Versions");//\\apache\\hive");

//        Path ori = Paths.get("C:\\Users\\quentin\\resources\\Versions\\");//\\INRIA\\spoon");//\\4b42324566bdd0da145a647d136a2f555c533978");
        TreeContextCompressing ct1 = new GlobalGenerator().generateFrom(ori.toFile());
        stats(ct1);
    }

    private int getCount(String c, String s) {
        int countParsed = 0;
        int index2 = s.indexOf(c);
        while (index2 >= 0) {
            countParsed++;
            index2 = s.indexOf(c, index2 + 1);
        }
        return countParsed;
    }

    @Test
    public void testBig() throws IOException {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        JSitterJavaTreeGenerator2 gen = new JSitterJavaTreeGenerator2();
        Path p = Paths.get("", "C:\\Users\\quentin\\Documents\\dev\\gumtree\\gen.jsitter-java\\src\\test\\resources\\DotDiff.java");
        String content = Files.readString(p);
        TreeContextCompressing ct1 = (TreeContextCompressing) gen.generateFrom().string(content);
        String c = "{";
        int countOri = getCount(c, content);
        System.out.println(c+getCount(c, content));
        String s = ct1.getRoot().toTreeString();
        int countParsed = getCount(c, s);
        System.out.println(c+getCount(c, s));
        System.out.println("method_declaration "+getCount("method_declaration", s));
//        System.out.println(ct1.getRoot().toTreeString());
        assertEquals(countOri,countParsed);
//        stats(ct1);
        System.out.println();
        System.out.println(ct1.serialize());
        System.out.println();
    }

    @Test
    public void testReSerialize() throws IOException {
        String input = "package x.y;\nimport x.B;\npublic class Foo extends B.C {\n\tpublic Integer foo() {\n\t\ts.i++; \n\t}\n}";
        System.out.println(input);
        TreeContextCompressing ct = (TreeContextCompressing)new JSitterJavaTreeGenerator2()
                .generateFrom().string(input);
        System.out.println(ct.serialize());

    }

    private void stats(TreeContextCompressing ct1) {
        int[] histoDup = new int[100];
        int totalNoDup = 0;
        int totalOriginal = 0;
        int totalNotSyntaxUniq = 0;
        int totalDup = 0;
        int totalSpaceCompressed = 0;
        int totalSpaceDeCompressed = 0;
        int totalSpaceOriginal = 0;
        int totalNotSyntaxDup = 0;
        int totalCollision = 0;
        int totalNotSinglePaddingSpaces = 0;
        for (TreeContextCompressing.MapValue d : ct1.getHashMap().values()) {
            totalCollision--;
            do {
                int count = d.count + 1;
                totalOriginal += count;
                totalDup += 1;
                histoDup[Math.min(d.count, 99)]++;
                if (d.tree.getType().toString().length() > 1) {
                    totalNotSyntaxDup += 1;
                    totalNotSyntaxUniq += count;
                }
                int childCount;
                if (d.tree instanceof GTComposableTypes.BasicTree)
                    childCount = ((GTComposableTypes.BasicTree) d.tree).getChildrenSize();
                else
                    childCount = 0;

                if (d.paddingSpaces.size()>1) {
                    totalNotSinglePaddingSpaces+=d.paddingSpaces.size()-1;
                    System.out.println(d.paddingSpaces);
                }

                int C = ptr_child * childCount + type_ptr + label_ptr;//(d.tree instanceof GTComposableTypes.LabeledNode ? label_ptr : 0);
                int B = C + ptr_parent + index_in_parent + type_ptr;
                int P = ptr_c + ptr_parent + index_in_parent + integer;
                int c = pad(obj + C);
                int b = pad(obj + B) * count;
                int p = pad(obj + P) * count;
                totalSpaceCompressed += c;
                totalSpaceDeCompressed += p;
                totalSpaceOriginal += b;

                if (d.count > 0) {
                    System.out.println();
                    System.out.println(count);
                    System.out.println(childCount);
                } else {
                    totalNoDup += count;
                }
                totalCollision++;
                d=d.next;
            } while (d!= null);
        }
        int totalStructDup = 0;
        int totalNotSyntaxStructDup = 0;
        for (TreeContextCompressing.MapValue d : ct1.getStructHashMap().values()) {
            totalStructDup += 1;
            if (d.tree.getType().toString().length() > 1)
                totalNotSyntaxStructDup += 1;
        }
        System.out.println("---stats-----");
        System.out.println("--------counts-----");
        System.out.print("Original:\t\t\t");
        System.out.println(totalOriginal);
        System.out.print("No Duplicates:\t\t\t");
        System.out.println(totalNoDup);
        System.out.print("Original Non Syntax Tokens:\t\t\t");
        System.out.println(totalNotSyntaxUniq);
        System.out.print("Duplicates:\t\t\t");
        System.out.println(totalDup);
        System.out.print("Duplicates Not Syntax:\t\t\t");
        System.out.println(totalNotSyntaxDup);
        System.out.print("Structural Duplicates:\t\t\t");
        System.out.println(totalStructDup);
        System.out.print("Structural Duplicates Not Syntax:\t\t\t");
        System.out.println(totalNotSyntaxStructDup);
        System.out.print("Collisions:\t\t\t");
        System.out.println(totalCollision);
        System.out.print("Not Single Padding Spaces:\t\t\t");
        System.out.println(totalNotSinglePaddingSpaces);
        System.out.print("histoDup:\t\t\t");
        System.out.println(Arrays.toString(histoDup));
        System.out.println("--------space-----");
        System.out.print("Original:\t\t\t");
        System.out.println(totalSpaceOriginal);
        System.out.print("Compressed:\t\t\t");
        System.out.println(totalSpaceCompressed);
        System.out.print("DeCompressed:\t\t\t");
        System.out.println(totalSpaceDeCompressed);
    }

    int duplicates = 6;
    int duplicated = 1;

    int ptr = 8; // depend on sys
    int obj = 2 * ptr;
    int integer = 4;
    int index_in_parent = 4;
    int ptr_parent = ptr;
    int ptr_child = ptr;
    int ptr_c = ptr;
    int type_ptr = ptr;
    int label_ptr = ptr;
    int parent = ptr_parent + index_in_parent;
    int PARENT = 16 + parent;

    @Test
    public void testSize() throws IOException {

        for (int i = 0; i < 20; i++) {
            int p = ptr_c + ptr_parent + index_in_parent;
            int c = ptr_child * i + type_ptr;
            int b = c + ptr_parent;
            int P = obj + p;
            int B = obj + b + index_in_parent;
            B = B % 8 == 0 ? B : B - B % 8 + 8;
            int C = obj + c;
            P = P % 8 == 0 ? P : P - P % 8 + 8;
            C = C % 8 == 0 ? C : C - C % 8 + 8;
            System.out.println(i);
            System.out.println(duplicates * B);
            System.out.println(duplicates * P + duplicated * C + duplicated * PARENT);
        }
    }

    @Test
    public void test2Throws() throws IOException {
        String input1 = "class Foo { void foo()  throws IOException, Exception { } }";
        TreeContext ct1 = new JSitterJavaTreeGenerator2().generateFrom().string(input1);
        System.out.println(ct1.getRoot().toTreeString());
    }

    @Test
    public void testPathCompression() throws IOException {
        byte[] c;
        int[] r;
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{0});
        System.out.println(Arrays.toString(c));
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{0, 0, 0, 0, 0, 0, 0});
        System.out.println(Arrays.toString(c));
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{15, 15, 15, 15, 15, 15});
        System.out.println(Arrays.toString(c));
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{14, 14, 14, 14, 14, 14});
        System.out.println(Arrays.toString(c));
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{1, 1, 1, 1, 1, 1, 1});
        System.out.println(Arrays.toString(c));
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{2, 2, 2, 2, 2, 2, 2});
        System.out.println(Arrays.toString(c));
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{100});
        r = ParitiallyDecompressedTree.decompressOffsets(c);
        System.out.print(Arrays.toString(c));
        System.out.print("");
        System.out.println(Arrays.toString(r));
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{100, 100, 100, 100, 100});
        System.out.println(Arrays.toString(c));
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{128, 128, 128, 128, 128});
        System.out.println(Arrays.toString(c));
        c = ParitiallyDecompressedTree.fillOffsets(new int[]{0, 0, 2, 1, 1, 100, 2, 1});
        r = ParitiallyDecompressedTree.decompressOffsets(c);
        System.out.print(Arrays.toString(c));
        System.out.print("");
        System.out.println(Arrays.toString(r));
    }

    @Test
    public void testPathDecompression() throws IOException {
        byte[] c;
        int[] r;
        c = new byte[]{0x14, 0x02};
        r = ParitiallyDecompressedTree.decompressOffsets(c);
        System.out.println(Arrays.toString(r));
        c = new byte[]{(byte) 0xF4, 0x20, -1, 0x0F};
        r = ParitiallyDecompressedTree.decompressOffsets(c);
        System.out.println(Arrays.toString(r));
        c = new byte[]{0x11};
        r = ParitiallyDecompressedTree.decompressOffsets(c);
        System.out.println(Arrays.toString(r));
        c = new byte[]{0x11, 0x20};
        r = ParitiallyDecompressedTree.decompressOffsets(c);
        System.out.println(Arrays.toString(r));
        c = new byte[]{0x11, 0x2F};
        r = ParitiallyDecompressedTree.decompressOffsets(c);
        System.out.println(Arrays.toString(r));
    }

    @Test
    public void testPathDecompression2() throws IOException {
        byte[] c;
        int[] r;
        c = new byte[]{(byte) 0xF4, 0x20, -1, 0x0F};
        r = ParitiallyDecompressedTree.decompressOffsets(c);
        System.out.println(Arrays.toString(r));
    }

    @Test
    public void testPathcompressionIdentity() throws IOException {

        for (int i = 0; i < 10; i++) {
            final int[] ints = randomIntArray();
            auxId(ints);
            if (ints.length > 0)
                auxIdEnd(ints);
        }
    }

    void auxId(int[] ints) {
        byte[] c = ParitiallyDecompressedTree.fillOffsets(ints);
        int[] r = ParitiallyDecompressedTree.decompressOffsets(c);

//        System.out.println();
//        System.out.println(Arrays.toString(ints));
//        System.out.println(Arrays.toString(c));
//        System.out.println(Arrays.toString(r));
        System.out.println();
        System.out.println(pad(c.length));
        System.out.println(pad(r.length * 4));
        assertArrayEquals(ints, r);
    }

    void auxIdEnd(int[] ints) {
        byte[] c = ParitiallyDecompressedTree.fillOffsets(ints);
        int r = ParitiallyDecompressedTree.lastPosition(c);

        assertEquals(ints[ints.length - 1], r);
    }

    int[] randomIntArray() {
        Random rnd = new Random();
        int[] r = new int[Math.abs(rnd.nextInt(20))];
        for (int i = 0; i < r.length; i++) {
            r[i] = Math.abs(rnd.nextInt(50));
        }
        return r;
    }

}
