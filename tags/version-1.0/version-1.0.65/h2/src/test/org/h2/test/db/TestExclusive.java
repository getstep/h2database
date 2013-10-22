/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.h2.test.TestBase;

/**
 * Test for the exclusive mode.
 */
public class TestExclusive extends TestBase {

    public void test() throws Exception {
        deleteDb("exclusive");
        Connection conn = getConnection("exclusive");
        Statement stat = conn.createStatement();
        stat.execute("set exclusive true");
        try {
            Connection conn2 = getConnection("exclusive");
            conn2.close();
            error("unexpected success");
        } catch (SQLException e) {
            checkNotGeneralException(e);
        }

        stat.execute("set exclusive false");
        Connection conn2 = getConnection("exclusive");
        final Statement stat2 = conn2.createStatement();
        stat.execute("set exclusive true");
        final int[] state = new int[1];
        Thread t = new Thread() {
            public void run() {
                try {
                    stat2.execute("select * from dual");
                    if (state[0] != 1) {
                        new Error("unexpected state: " + state[0]).printStackTrace();
                    }
                    state[0] = 2;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
        state[0] = 1;
        stat.execute("set exclusive false");
        Thread.sleep(100);
        check(state[0], 2);
        stat.execute("set exclusive true");
        conn.close();

        // check that exclusive mode is off when disconnected
        stat2.execute("select * from dual");
        conn2.close();

    }

}