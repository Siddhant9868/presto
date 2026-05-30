/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.lance;

import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.testing.QueryRunner;
import com.facebook.presto.tests.AbstractTestQueryFramework;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * End-to-end tests for {@code ALTER TABLE ... ADD/DROP/RENAME COLUMN} on the
 * Lance connector. These exercise the SQL path through
 * {@link LanceMetadata#addColumn}, {@link LanceMetadata#dropColumn} and
 * {@link LanceMetadata#renameColumn}, which are backed by Lance's metadata-only
 * schema-evolution operations.
 */
public class TestLanceAlterTable
        extends AbstractTestQueryFramework
{
    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        return LanceQueryRunner.createLanceQueryRunner(ImmutableMap.of());
    }

    @Test
    public void testAddColumn()
    {
        assertUpdate("CREATE TABLE test_lance_add_column AS SELECT 123 x", 1);

        // Engine-level validation happens before reaching the connector.
        assertQueryFails("ALTER TABLE test_lance_add_column ADD COLUMN x bigint", ".* Column 'x' already exists");
        assertQueryFails("ALTER TABLE test_lance_add_column ADD COLUMN q bad_type", ".* Unknown type 'bad_type' for column 'q'");

        // New columns are back-filled with nulls for existing rows.
        assertUpdate("ALTER TABLE test_lance_add_column ADD COLUMN a bigint");
        assertUpdate("INSERT INTO test_lance_add_column SELECT 234, 111", 1);
        MaterializedResult rows = computeActual("SELECT x, a FROM test_lance_add_column ORDER BY x");
        assertEquals(rows.getMaterializedRows().get(0).getField(0), 123);
        assertNull(rows.getMaterializedRows().get(0).getField(1));
        assertEquals(rows.getMaterializedRows().get(1).getField(0), 234);
        assertEquals(rows.getMaterializedRows().get(1).getField(1), 111L);

        assertUpdate("DROP TABLE test_lance_add_column");
    }

    @Test
    public void testDropColumn()
    {
        assertUpdate("CREATE TABLE test_lance_drop_column AS SELECT 1 a, 2 b, 3 c", 1);

        assertUpdate("ALTER TABLE test_lance_drop_column DROP COLUMN b");
        assertQuery("SHOW COLUMNS FROM test_lance_drop_column", "VALUES ('a', 'integer', '', ''), ('c', 'integer', '', '')");
        assertQuery("SELECT * FROM test_lance_drop_column", "VALUES (1, 3)");

        assertUpdate("DROP TABLE test_lance_drop_column");
    }

    @Test
    public void testRenameColumn()
    {
        assertUpdate("CREATE TABLE test_lance_rename_column AS SELECT 1 a, 2 b", 1);

        assertUpdate("ALTER TABLE test_lance_rename_column RENAME COLUMN b TO c");
        assertQuery("SHOW COLUMNS FROM test_lance_rename_column", "VALUES ('a', 'integer', '', ''), ('c', 'integer', '', '')");
        assertQuery("SELECT a, c FROM test_lance_rename_column", "VALUES (1, 2)");

        assertUpdate("DROP TABLE test_lance_rename_column");
    }
}
