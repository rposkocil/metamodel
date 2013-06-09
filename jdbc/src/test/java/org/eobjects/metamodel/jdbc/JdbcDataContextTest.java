/**
 * eobjects.org MetaModel
 * Copyright (C) 2010 eobjects.org
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.eobjects.metamodel.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import javax.sql.DataSource;
import javax.swing.table.TableModel;

import org.easymock.EasyMock;
import org.eobjects.metamodel.DataContext;
import org.eobjects.metamodel.MetaModelException;
import org.eobjects.metamodel.QueryPostprocessDataContext;
import org.eobjects.metamodel.data.DataSet;
import org.eobjects.metamodel.data.DataSetTableModel;
import org.eobjects.metamodel.jdbc.dialects.DefaultQueryRewriter;
import org.eobjects.metamodel.jdbc.dialects.IQueryRewriter;
import org.eobjects.metamodel.query.CompiledQuery;
import org.eobjects.metamodel.query.FilterItem;
import org.eobjects.metamodel.query.FunctionType;
import org.eobjects.metamodel.query.OperatorType;
import org.eobjects.metamodel.query.OrderByItem;
import org.eobjects.metamodel.query.Query;
import org.eobjects.metamodel.query.QueryParameter;
import org.eobjects.metamodel.query.SelectItem;
import org.eobjects.metamodel.schema.Column;
import org.eobjects.metamodel.schema.Relationship;
import org.eobjects.metamodel.schema.Schema;
import org.eobjects.metamodel.schema.Table;
import org.eobjects.metamodel.schema.TableType;

public class JdbcDataContextTest extends JdbcTestCase {

    public void testGetDefaultSchema() throws Exception {
        Connection con = getTestDbConnection();
        DataContext dc = new JdbcDataContext(con);
        Schema schema = dc.getDefaultSchema();
        assertEquals("PUBLIC", schema.getName());
        con.close();
    }

    public void testGetNonExistingSchema() throws Exception {
        Connection con = getTestDbConnection();
        DataContext dc = new JdbcDataContext(con);
        Schema schema = dc.getSchemaByName("foobar");
        assertNull("found schema: " + schema, schema);
    }

    public void testUsingDataSource() throws Exception {
        Connection con = getTestDbConnection();
        DataSource ds = EasyMock.createMock(DataSource.class);

        CloseableConnectionWrapper con1 = new CloseableConnectionWrapper(con);
        CloseableConnectionWrapper con2 = new CloseableConnectionWrapper(con);
        CloseableConnectionWrapper con3 = new CloseableConnectionWrapper(con);
        CloseableConnectionWrapper con4 = new CloseableConnectionWrapper(con);
        CloseableConnectionWrapper con5 = new CloseableConnectionWrapper(con);

        assertFalse(con1.isClosed());
        assertFalse(con2.isClosed());
        assertFalse(con3.isClosed());
        assertFalse(con4.isClosed());
        assertFalse(con5.isClosed());

        EasyMock.expect(ds.getConnection()).andReturn(con1);
        EasyMock.expect(ds.getConnection()).andReturn(con2);
        EasyMock.expect(ds.getConnection()).andReturn(con3);
        EasyMock.expect(ds.getConnection()).andReturn(con4);
        EasyMock.expect(ds.getConnection()).andReturn(con5);

        EasyMock.replay(ds);

        JdbcDataContext dc = new JdbcDataContext(ds);
        dc.refreshSchemas();
        dc.refreshSchemas();

        Schema schema = dc.getDefaultSchema();
        Query q = new Query();
        q.from(schema.getTableByName("CUSTOMERS")).select(new SelectItem("COUNT(*)", null));
        DataSet data = dc.executeQuery(q);
        TableModel tableModel = new DataSetTableModel(data);
        assertEquals(1, tableModel.getRowCount());
        assertEquals(1, tableModel.getColumnCount());
        assertEquals(122, tableModel.getValueAt(0, 0));

        EasyMock.verify(ds);

        String assertionFailMsg = "Expected 5x true: " + con1.isClosed() + "," + con2.isClosed() + ","
                + con3.isClosed() + "," + con4.isClosed() + "," + con5.isClosed();

        assertTrue(assertionFailMsg, con1.isClosed());
        assertTrue(assertionFailMsg, con2.isClosed());
        assertTrue(assertionFailMsg, con3.isClosed());
        assertTrue(assertionFailMsg, con4.isClosed());
        assertTrue(assertionFailMsg, con5.isClosed());
    }

    public void testExecuteQuery() throws Exception {
        Connection connection = getTestDbConnection();
        JdbcDataContext strategy = new JdbcDataContext(connection, new TableType[] { TableType.TABLE, TableType.VIEW },
                null);
        Schema schema = strategy.getSchemaByName(strategy.getDefaultSchemaName());

        Query q = new Query();
        Table table = schema.getTables()[0];
        q.from(table, "a");
        q.select(table.getColumns());
        assertEquals(
                "SELECT a._CUSTOMERNUMBER_, a._CUSTOMERNAME_, a._CONTACTLASTNAME_, a._CONTACTFIRSTNAME_, a._PHONE_, a._ADDRESSLINE1_, a._ADDRESSLINE2_, a._CITY_, a._STATE_, a._POSTALCODE_, a._COUNTRY_, a._SALESREPEMPLOYEENUMBER_, a._CREDITLIMIT_ FROM PUBLIC._CUSTOMERS_ a",
                q.toString().replace('\"', '_'));
        DataSet result = strategy.executeQuery(q);
        assertTrue(result.next());
        assertEquals(
                "Row[values=[103, Atelier graphique, Schmitt, Carine, 40.32.2555, 54, rue Royale, null, Nantes, null, 44000, France, 1370, 21000.0]]",
                result.getRow().toString());
        assertTrue(result.next());
        assertTrue(result.next());
        assertTrue(result.next());
        assertTrue(result.next());
        assertEquals(
                "Row[values=[121, Baane Mini Imports, Bergulfsen, Jonas, 07-98 9555, Erling Skakkes gate 78, null, Stavern, null, 4110, Norway, 1504, 81700.0]]",
                result.getRow().toString());
        result.close();
    }

    public void testExecuteQueryWithParams() throws Exception {
        Connection connection = getTestDbConnection();
        JdbcDataContext dataContext = new JdbcDataContext(connection,
                new TableType[] { TableType.TABLE, TableType.VIEW }, null);
        Schema schema = dataContext.getSchemaByName(dataContext.getDefaultSchemaName());

        QueryParameter queryParameter = new QueryParameter();

        Query q = new Query();
        Table table = schema.getTables()[0];
        q.select(table.getColumns());
        q.from(table, "a");
        q.where(table.getColumnByName("CUSTOMERNUMBER"), OperatorType.EQUALS_TO, queryParameter);
        q.where(table.getColumnByName("CUSTOMERNAME"), OperatorType.EQUALS_TO, queryParameter);

        final CompiledQuery compiledQuery = dataContext.compileQuery(q);

        final JdbcCompiledQuery jdbcCompiledQuery = (JdbcCompiledQuery) compiledQuery;
        assertEquals(0, jdbcCompiledQuery.getActiveLeases());
        assertEquals(0, jdbcCompiledQuery.getIdleLeases());

        String compliedQueryString = compiledQuery.toSql();

        assertEquals(
                "SELECT a._CUSTOMERNUMBER_, a._CUSTOMERNAME_, a._CONTACTLASTNAME_, a._CONTACTFIRSTNAME_, a._PHONE_, a._ADDRESSLINE1_, a._ADDRESSLINE2_, a._CITY_, "
                        + "a._STATE_, a._POSTALCODE_, a._COUNTRY_, a._SALESREPEMPLOYEENUMBER_, a._CREDITLIMIT_ FROM PUBLIC._CUSTOMERS_ a WHERE a._CUSTOMERNUMBER_ = ? "
                        + "AND a._CUSTOMERNAME_ = ?", compliedQueryString.replace('\"', '_'));
        DataSet result1 = dataContext.executeQuery(compiledQuery, new Object[] { 103, "Atelier graphique" });
        assertTrue(result1.next());
        assertEquals(
                "Row[values=[103, Atelier graphique, Schmitt, Carine, 40.32.2555, 54, rue Royale, null, Nantes, null, 44000, France, 1370, 21000.0]]",
                result1.getRow().toString());
        assertFalse(result1.next());

        assertEquals(1, jdbcCompiledQuery.getActiveLeases());
        assertEquals(0, jdbcCompiledQuery.getIdleLeases());

        DataSet result2 = dataContext.executeQuery(compiledQuery, new Object[] { 103, "Atelier graphique" });

        assertEquals(2, jdbcCompiledQuery.getActiveLeases());
        assertEquals(0, jdbcCompiledQuery.getIdleLeases());

        result1.close();

        assertEquals(1, jdbcCompiledQuery.getActiveLeases());
        assertEquals(1, jdbcCompiledQuery.getIdleLeases());

        result2.close();

        assertEquals(0, jdbcCompiledQuery.getActiveLeases());
        assertEquals(2, jdbcCompiledQuery.getIdleLeases());

        compiledQuery.close();

        assertEquals(0, jdbcCompiledQuery.getActiveLeases());
        assertEquals(0, jdbcCompiledQuery.getIdleLeases());
    }

    public void testGetSchemaNormalTableTypes() throws Exception {
        Connection connection = getTestDbConnection();
        JdbcDataContext dc = new JdbcDataContext(connection, new TableType[] { TableType.TABLE, TableType.VIEW }, null);
        Schema[] schemas = dc.getSchemas();

        assertEquals(2, schemas.length);
        assertEquals("Schema[name=INFORMATION_SCHEMA]", schemas[0].toString());
        assertEquals(0, schemas[0].getTableCount());
        assertEquals("Schema[name=PUBLIC]", schemas[1].toString());
        assertEquals(13, schemas[1].getTableCount());

        connection.close();
    }

    public void testParseColumns() throws Exception {
        Connection connection = getTestDbConnection();
        JdbcDataContext dc = new JdbcDataContext(connection, new TableType[] { TableType.OTHER,
                TableType.GLOBAL_TEMPORARY }, null);
        Schema schema = dc.getDefaultSchema();
        Table customersTable = schema.getTableByName("CUSTOMERS");
        Column[] columns = customersTable.getColumns();
        assertEquals(
                "[Column[name=CUSTOMERNUMBER,columnNumber=0,type=INTEGER,nullable=false,nativeType=INTEGER,columnSize=0], "
                        + "Column[name=CUSTOMERNAME,columnNumber=1,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=CONTACTLASTNAME,columnNumber=2,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=CONTACTFIRSTNAME,columnNumber=3,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=PHONE,columnNumber=4,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=ADDRESSLINE1,columnNumber=5,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=ADDRESSLINE2,columnNumber=6,type=VARCHAR,nullable=true,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=CITY,columnNumber=7,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=STATE,columnNumber=8,type=VARCHAR,nullable=true,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=POSTALCODE,columnNumber=9,type=VARCHAR,nullable=true,nativeType=VARCHAR,columnSize=15], "
                        + "Column[name=COUNTRY,columnNumber=10,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=SALESREPEMPLOYEENUMBER,columnNumber=11,type=INTEGER,nullable=true,nativeType=INTEGER,columnSize=0], "
                        + "Column[name=CREDITLIMIT,columnNumber=12,type=NUMERIC,nullable=true,nativeType=NUMERIC,columnSize=17]]",
                Arrays.toString(columns));
        connection.close();
    }

    public void testParseRelations() throws Exception {
        Connection connection = getTestDbConnection();
        JdbcDataContext dc = new JdbcDataContext(connection, new TableType[] { TableType.TABLE }, null);
        Schema schema = dc.getDefaultSchema();
        Table productsTable = schema.getTableByName("PRODUCTS");
        Relationship[] relations = productsTable.getRelationships();

        /**
         * TODO: A single constraint now exists, create more ...
         */
        assertEquals(
                "[Relationship[primaryTable=PRODUCTS,primaryColumns=[PRODUCTCODE],foreignTable=ORDERFACT,foreignColumns=[PRODUCTCODE]]]",
                Arrays.toString(relations));

        Column[] indexedColumns = productsTable.getIndexedColumns();
        assertEquals(
                "[Column[name=PRODUCTCODE,columnNumber=0,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50]]",
                Arrays.toString(indexedColumns));

        connection.close();
    }

    public void testGetCatalogNames() throws Exception {
        Connection connection = getTestDbConnection();
        JdbcDataContext strategy = new JdbcDataContext(connection);
        String[] catalogNames = strategy.getCatalogNames();
        assertEquals(0, catalogNames.length);
    }

    public void testMaxRows() throws Exception {
        final Connection realCon = getTestDbConnection();
        final Statement realStatement = realCon.createStatement();
        
        final Connection mockCon = EasyMock.createMock(Connection.class);
        final Statement mockStatement = EasyMock.createMock(Statement.class);

        EasyMock.expect(mockCon.getMetaData()).andReturn(realCon.getMetaData()).anyTimes();

        EasyMock.expect(mockCon.getAutoCommit()).andReturn(true);

        EasyMock.expect(mockCon.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)).andReturn(
                mockStatement);

        EasyMock.expect(mockStatement.getFetchSize()).andReturn(10);
        mockStatement.setFetchSize(EasyMock.anyInt());
        mockStatement.setMaxRows(3);
        EasyMock.expectLastCall().andThrow(new SQLException("I wont allow max rows"));

        EasyMock.expect(
                mockStatement
                        .executeQuery("SELECT a.\"CUSTOMERNUMBER\", a.\"CUSTOMERNAME\", a.\"CONTACTLASTNAME\", a.\"CONTACTFIRSTNAME\", a.\"PHONE\", a.\"ADDRESSLINE1\", a.\"ADDRESSLINE2\", a.\"CITY\", a.\"STATE\", a.\"POSTALCODE\", a.\"COUNTRY\", a.\"SALESREPEMPLOYEENUMBER\", a.\"CREDITLIMIT\" FROM PUBLIC.\"CUSTOMERS\" a"))
                .andReturn(
                        realStatement
                                .executeQuery("SELECT a.\"CUSTOMERNUMBER\", a.\"CUSTOMERNAME\", a.\"CONTACTLASTNAME\", a.\"CONTACTFIRSTNAME\", a.\"PHONE\", a.\"ADDRESSLINE1\", a.\"ADDRESSLINE2\", a.\"CITY\", a.\"STATE\", a.\"POSTALCODE\", a.\"COUNTRY\", a.\"SALESREPEMPLOYEENUMBER\", a.\"CREDITLIMIT\" FROM PUBLIC.\"CUSTOMERS\" a"));

        mockStatement.close();

        EasyMock.replay(mockCon, mockStatement);

        JdbcDataContext dc = new JdbcDataContext(mockCon, new TableType[] { TableType.TABLE, TableType.VIEW }, null);
        dc.setQueryRewriter(new DefaultQueryRewriter(dc));
        Schema schema = dc.getDefaultSchema();

        Query q = new Query().setMaxRows(3);
        Table table = schema.getTables()[0];
        q.from(table, "a");
        q.select(table.getColumns());
        assertEquals(
                "SELECT a.\"CUSTOMERNUMBER\", a.\"CUSTOMERNAME\", a.\"CONTACTLASTNAME\", a.\"CONTACTFIRSTNAME\", a.\"PHONE\", a.\"ADDRESSLINE1\", a.\"ADDRESSLINE2\", a.\"CITY\", a.\"STATE\", a.\"POSTALCODE\", a.\"COUNTRY\", a.\"SALESREPEMPLOYEENUMBER\", a.\"CREDITLIMIT\" FROM PUBLIC.\"CUSTOMERS\" a",
                q.toString());
        DataSet result = dc.executeQuery(q);
        assertTrue(result.next());
        assertEquals(
                "Row[values=[103, Atelier graphique, Schmitt, Carine, 40.32.2555, 54, rue Royale, null, Nantes, null, 44000, France, 1370, 21000.0]]",
                result.getRow().toString());
        assertTrue(result.next());
        assertTrue(result.next());
        assertFalse(result.next());

        result.close();

        EasyMock.verify(mockCon, mockStatement);
        realStatement.close();
    }

    public void testMaxRowsRewrite() throws Exception {
        JdbcDataContext dc = new JdbcDataContext(getTestDbConnection(), new TableType[] { TableType.TABLE }, null);
        IQueryRewriter rewriter = new DefaultQueryRewriter(dc) {
            @Override
            public String rewriteQuery(Query query) {
                return "SELECT COUNT(*) FROM PUBLIC.CUSTOMERS";
            }
        };
        dc = dc.setQueryRewriter(rewriter);
        TableModel tm = new DataSetTableModel(dc.executeQuery(new Query().selectCount()));
        assertEquals(1, tm.getRowCount());
        assertEquals(1, tm.getColumnCount());
        assertEquals("122", tm.getValueAt(0, 0).toString());
    }

    /**
     * Executes the same query on two diffent strategies, one with database-side
     * query execution and one with Query postprocessing
     * 
     * @throws Exception
     */
    public void testCsvQueryResultComparison() throws Exception {
        Connection connection = getTestDbConnection();
        final DataContext dataContext1 = new JdbcDataContext(connection);
        final DataContext dataContext2 = new QueryPostprocessDataContext() {
            @Override
            public DataSet materializeMainSchemaTable(Table table, Column[] columns, int maxRows) {
                Query q = new Query();
                q.from(table, "a");
                q.select(columns);
                return dataContext1.executeQuery(q);
            }

            @Override
            protected Schema getMainSchema() throws MetaModelException {
                throw new UnsupportedOperationException();
            }

            @Override
            protected String getMainSchemaName() throws MetaModelException {
                return "PUBLIC";
            }
        };

        Schema schema2 = dataContext1.getDefaultSchema();
        Table customersTable = schema2.getTableByName("CUSTOMERS");
        Table employeeTable = schema2.getTableByName("EMPLOYEES");
        assertEquals(
                "[Column[name=CUSTOMERNUMBER,columnNumber=0,type=INTEGER,nullable=false,nativeType=INTEGER,columnSize=0], "
                        + "Column[name=CUSTOMERNAME,columnNumber=1,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=CONTACTLASTNAME,columnNumber=2,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=CONTACTFIRSTNAME,columnNumber=3,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=PHONE,columnNumber=4,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=ADDRESSLINE1,columnNumber=5,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=ADDRESSLINE2,columnNumber=6,type=VARCHAR,nullable=true,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=CITY,columnNumber=7,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=STATE,columnNumber=8,type=VARCHAR,nullable=true,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=POSTALCODE,columnNumber=9,type=VARCHAR,nullable=true,nativeType=VARCHAR,columnSize=15], "
                        + "Column[name=COUNTRY,columnNumber=10,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=SALESREPEMPLOYEENUMBER,columnNumber=11,type=INTEGER,nullable=true,nativeType=INTEGER,columnSize=0], "
                        + "Column[name=CREDITLIMIT,columnNumber=12,type=NUMERIC,nullable=true,nativeType=NUMERIC,columnSize=17]]",
                Arrays.toString(customersTable.getColumns()));
        assertEquals(
                "[Column[name=EMPLOYEENUMBER,columnNumber=0,type=INTEGER,nullable=false,nativeType=INTEGER,columnSize=0], "
                        + "Column[name=LASTNAME,columnNumber=1,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=FIRSTNAME,columnNumber=2,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50], "
                        + "Column[name=EXTENSION,columnNumber=3,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=10], "
                        + "Column[name=EMAIL,columnNumber=4,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=100], "
                        + "Column[name=OFFICECODE,columnNumber=5,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=20], "
                        + "Column[name=REPORTSTO,columnNumber=6,type=INTEGER,nullable=true,nativeType=INTEGER,columnSize=0], "
                        + "Column[name=JOBTITLE,columnNumber=7,type=VARCHAR,nullable=false,nativeType=VARCHAR,columnSize=50]]",
                Arrays.toString(employeeTable.getColumns()));

        Column employeeNumberColumn1 = customersTable.getColumnByName("SALESREPEMPLOYEENUMBER");
        Column countryColumn = customersTable.getColumnByName("COUNTRY");
        Column employeeNumberColumn2 = employeeTable.getColumnByName("EMPLOYEENUMBER");
        Column creditLimitColumn = customersTable.getColumnByName("CREDITLIMIT");

        Query q = new Query();
        q.from(customersTable, "c");
        q.from(employeeTable, "o");
        SelectItem countrySelect = new SelectItem(countryColumn);
        q.select(countrySelect, new SelectItem(FunctionType.SUM, creditLimitColumn));
        q.groupBy(countryColumn);
        q.orderBy(new OrderByItem(countrySelect));
        q.where(new FilterItem(new SelectItem(employeeNumberColumn1), OperatorType.EQUALS_TO, new SelectItem(
                employeeNumberColumn2)));

        assertEquals(
                "SELECT c.\"COUNTRY\", SUM(c.\"CREDITLIMIT\") FROM PUBLIC.\"CUSTOMERS\" c, PUBLIC.\"EMPLOYEES\" o WHERE c.\"SALESREPEMPLOYEENUMBER\" = o.\"EMPLOYEENUMBER\" GROUP BY c.\"COUNTRY\" ORDER BY c.\"COUNTRY\" ASC",
                q.toString());

        DataSet data1 = dataContext1.executeQuery(q);
        assertTrue(data1.next());
        assertEquals("Row[values=[Australia, 430300.0]]", data1.getRow().toString());
        DataSet data2 = dataContext2.executeQuery(q);
        assertTrue(data2.next());
        assertEquals("Row[values=[Australia, 430300.0]]", data2.getRow().toString());
        assertEquals(new DataSetTableModel(data1), new DataSetTableModel(data2));
    }
}