/*
 * Copyright 2004-2008 H2 Group. Multiple-Licensed under the H2 License, 
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import java.util.Random;

import org.h2.test.TestBase;
import org.h2.util.IntIntHashMap;

/**
 * Tests the IntHashMap class.
 */
public class TestIntIntHashMap extends TestBase {

    Random rand = new Random();

    public void test() throws Exception {
        rand.setSeed(10);
        test(true);
        test(false);
    }

    public void test(boolean random) throws Exception {
        int len = 2000;
        int[] x = new int[len];
        for (int i = 0; i < len; i++) {
            int key = random ? rand.nextInt() : i;
            x[i] = key;
        }
        IntIntHashMap map = new IntIntHashMap();
        for (int i = 0; i < len; i++) {
            map.put(x[i], i);
        }
        for (int i = 0; i < len; i++) {
            if (map.get(x[i]) != i) {
                throw new Error("get " + x[i] + " = " + map.get(i) + " should be " + i);
            }
        }
        for (int i = 1; i < len; i += 2) {
            map.remove(x[i]);
        }
        for (int i = 1; i < len; i += 2) {
            if (map.get(x[i]) != -1) {
                throw new Error("get " + x[i] + " = " + map.get(i) + " should be <=0");
            }
        }
        for (int i = 1; i < len; i += 2) {
            map.put(x[i], i);
        }
        for (int i = 0; i < len; i++) {
            if (map.get(x[i]) != i) {
                throw new Error("get " + x[i] + " = " + map.get(i) + " should be " + i);
            }
        }
    }
}