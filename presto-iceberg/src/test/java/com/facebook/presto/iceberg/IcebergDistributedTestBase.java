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
package com.facebook.presto.iceberg;

import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.testing.QueryRunner;
import com.facebook.presto.tests.AbstractTestDistributedQueries;
import com.google.common.collect.ImmutableMap;
import org.intellij.lang.annotations.Language;
import org.testng.annotations.Test;

import static com.facebook.presto.common.type.VarcharType.VARCHAR;
import static com.facebook.presto.testing.MaterializedResult.resultBuilder;
import static com.facebook.presto.testing.TestingAccessControlManager.TestingPrivilegeType.SELECT_COLUMN;
import static com.facebook.presto.testing.TestingAccessControlManager.privilege;
import static com.facebook.presto.testing.assertions.Assert.assertEquals;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.testng.Assert.assertTrue;

public class IcebergDistributedTestBase
        extends AbstractTestDistributedQueries
{
    private final CatalogType catalogType;

    protected IcebergDistributedTestBase(CatalogType catalogType)
    {
        this.catalogType = requireNonNull(catalogType, "catalogType is null");
    }

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        return IcebergQueryRunner.createIcebergQueryRunner(ImmutableMap.of(), catalogType);
    }

    @Override
    protected boolean supportsNotNullColumns()
    {
        return false;
    }

    @Override
    public void testRenameTable()
    {
    }

    @Override
    public void testRenameColumn()
    {
    }

    @Override
    public void testDelete()
    {
    }

    @Test
    public void testTruncate()
    {
        // Test truncate empty table
        assertUpdate("CREATE TABLE test_truncate_empty (i int)");
        assertQuerySucceeds("TRUNCATE TABLE test_truncate_empty");
        assertEquals(getQueryRunner().execute("SELECT count(*) FROM test_truncate_empty").getOnlyValue(), 0L);
        assertQuerySucceeds("DROP TABLE test_truncate_empty");

        // Test truncate table with rows
        assertUpdate("CREATE TABLE test_truncate AS SELECT * FROM orders", "SELECT count(*) FROM orders");
        assertQuerySucceeds("TRUNCATE TABLE test_truncate");
        MaterializedResult result = getQueryRunner().execute("SELECT count(*) FROM test_truncate");
        assertEquals(result.getOnlyValue(), 0L);
        assertUpdate("DROP TABLE test_truncate");

        // test truncate -> insert -> truncate
        assertUpdate("CREATE TABLE test_truncate_empty (i int)");
        assertQuerySucceeds("TRUNCATE TABLE test_truncate_empty");
        assertEquals(getQueryRunner().execute("SELECT count(*) FROM test_truncate_empty").getOnlyValue(), 0L);
        assertUpdate("INSERT INTO test_truncate_empty VALUES 1", 1);
        assertEquals(getQueryRunner().execute("SELECT count(*) FROM test_truncate_empty").getOnlyValue(), 1L);
        assertQuerySucceeds("TRUNCATE TABLE test_truncate_empty");
        assertEquals(getQueryRunner().execute("SELECT count(*) FROM test_truncate_empty").getOnlyValue(), 0L);
        assertQuerySucceeds("DROP TABLE test_truncate_empty");

        // Test truncate access control
        assertUpdate("CREATE TABLE test_truncate AS SELECT * FROM orders", "SELECT count(*) FROM orders");
        assertAccessAllowed("TRUNCATE TABLE test_truncate", privilege("orders", SELECT_COLUMN));
        assertUpdate("DROP TABLE test_truncate");
    }

    @Override
    public void testShowColumns()
    {
        MaterializedResult actual = computeActual("SHOW COLUMNS FROM orders");

        MaterializedResult expectedParametrizedVarchar = resultBuilder(getSession(), VARCHAR, VARCHAR, VARCHAR, VARCHAR)
                .row("orderkey", "bigint", "", "")
                .row("custkey", "bigint", "", "")
                .row("orderstatus", "varchar", "", "")
                .row("totalprice", "double", "", "")
                .row("orderdate", "date", "", "")
                .row("orderpriority", "varchar", "", "")
                .row("clerk", "varchar", "", "")
                .row("shippriority", "integer", "", "")
                .row("comment", "varchar", "", "")
                .build();

        assertEquals(actual, expectedParametrizedVarchar);
    }

    @Override
    public void testDescribeOutput()
    {
    }

    @Override
    public void testDescribeOutputNamedAndUnnamed()
    {
    }

    @Override
    @Test
    public void testStringFilters()
    {
        // Type not supported for Iceberg: CHAR(10). Only test VARCHAR(10).
        assertUpdate("CREATE TABLE test_varcharn_filter (shipmode VARCHAR(10))");
        assertTrue(getQueryRunner().tableExists(getSession(), "test_varcharn_filter"));
        assertTableColumnNames("test_varcharn_filter", "shipmode");
        assertUpdate("INSERT INTO test_varcharn_filter SELECT shipmode FROM lineitem", 60175);

        assertQuery("SELECT count(*) FROM test_varcharn_filter WHERE shipmode = 'AIR'", "VALUES (8491)");
        assertQuery("SELECT count(*) FROM test_varcharn_filter WHERE shipmode = 'AIR    '", "VALUES (0)");
        assertQuery("SELECT count(*) FROM test_varcharn_filter WHERE shipmode = 'AIR       '", "VALUES (0)");
        assertQuery("SELECT count(*) FROM test_varcharn_filter WHERE shipmode = 'AIR            '", "VALUES (0)");
        assertQuery("SELECT count(*) FROM test_varcharn_filter WHERE shipmode = 'NONEXIST'", "VALUES (0)");
    }

    private void assertExplainAnalyze(@Language("SQL") String query)
    {
        String value = (String) computeActual(query).getOnlyValue();

        assertTrue(value.matches("(?s:.*)CPU:.*, Input:.*, Output(?s:.*)"), format("Expected output to contain \"CPU:.*, Input:.*, Output\", but it is %s", value));
    }
}
