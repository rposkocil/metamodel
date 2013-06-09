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
package org.eobjects.metamodel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eobjects.metamodel.create.TableCreationBuilder;
import org.eobjects.metamodel.data.CachingDataSetHeader;
import org.eobjects.metamodel.data.DataSet;
import org.eobjects.metamodel.data.DefaultRow;
import org.eobjects.metamodel.data.EmptyDataSet;
import org.eobjects.metamodel.data.InMemoryDataSet;
import org.eobjects.metamodel.data.Row;
import org.eobjects.metamodel.delete.AbstractRowDeletionBuilder;
import org.eobjects.metamodel.delete.RowDeletionBuilder;
import org.eobjects.metamodel.drop.TableDropBuilder;
import org.eobjects.metamodel.insert.AbstractRowInsertionBuilder;
import org.eobjects.metamodel.insert.RowInsertionBuilder;
import org.eobjects.metamodel.query.FilterItem;
import org.eobjects.metamodel.query.SelectItem;
import org.eobjects.metamodel.schema.Column;
import org.eobjects.metamodel.schema.ColumnType;
import org.eobjects.metamodel.schema.MutableColumn;
import org.eobjects.metamodel.schema.MutableSchema;
import org.eobjects.metamodel.schema.MutableTable;
import org.eobjects.metamodel.schema.Schema;
import org.eobjects.metamodel.schema.Table;

public class MockUpdateableDataContext extends QueryPostprocessDataContext implements UpdateableDataContext {

    private final List<Object[]> _values = new ArrayList<Object[]>();

    private final MutableTable _table;
    private final MutableSchema _schema;

    public MockUpdateableDataContext() {
        _values.add(new Object[] { "1", "hello" });
        _values.add(new Object[] { "2", "there" });
        _values.add(new Object[] { "3", "world" });

        _table = new MutableTable("table");
        _table.addColumn(new MutableColumn("foo", ColumnType.VARCHAR).setTable(_table).setColumnNumber(0));
        _table.addColumn(new MutableColumn("bar", ColumnType.VARCHAR).setTable(_table).setColumnNumber(1));
        _schema = new MutableSchema("schema", _table);
        _table.setSchema(_schema);
    }

    public MutableTable getTable() {
        return _table;
    }

    @Override
    protected DataSet materializeMainSchemaTable(Table table, Column[] columns, int maxRows) {

        List<Row> rows = new ArrayList<Row>();
        SelectItem[] items = MetaModelHelper.createSelectItems(columns);
        CachingDataSetHeader header = new CachingDataSetHeader(items);

        for (final Object[] values : _values) {
            Object[] rowValues = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                int columnNumber = columns[i].getColumnNumber();
                rowValues[i] = values[columnNumber];
            }
            rows.add(new DefaultRow(header, rowValues));
        }

        if (rows.isEmpty()) {
            return new EmptyDataSet(items);
        }
        return new InMemoryDataSet(header, rows);
    }

    @Override
    protected String getMainSchemaName() throws MetaModelException {
        return _schema.getName();
    }

    @Override
    protected Schema getMainSchema() throws MetaModelException {
        return _schema;
    }

    @Override
    public void executeUpdate(UpdateScript update) {
        update.run(new AbstractUpdateCallback(this) {

            @Override
            public boolean isDeleteSupported() {
                return true;
            }

            @Override
            public RowDeletionBuilder deleteFrom(Table table) throws IllegalArgumentException, IllegalStateException,
                    UnsupportedOperationException {
                return new AbstractRowDeletionBuilder(table) {
                    @Override
                    public void execute() throws MetaModelException {
                        delete(getWhereItems());
                    }
                };
            }

            @Override
            public RowInsertionBuilder insertInto(Table table) throws IllegalArgumentException, IllegalStateException,
                    UnsupportedOperationException {
                return new AbstractRowInsertionBuilder<UpdateCallback>(this, table) {

                    @Override
                    public void execute() throws MetaModelException {
                        Object[] values = toRow().getValues();
                        _values.add(values);
                    }
                };
            }

            @Override
            public boolean isDropTableSupported() {
                return false;
            }

            @Override
            public boolean isCreateTableSupported() {
                return false;
            }

            @Override
            public TableDropBuilder dropTable(Table table) throws IllegalArgumentException, IllegalStateException,
                    UnsupportedOperationException {
                throw new UnsupportedOperationException();
            }

            @Override
            public TableCreationBuilder createTable(Schema schema, String name) throws IllegalArgumentException,
                    IllegalStateException {
                throw new UnsupportedOperationException();
            }
        });
    }

    private void delete(List<FilterItem> whereItems) {
        final SelectItem[] selectItems = MetaModelHelper.createSelectItems(_table.getColumns());
        final CachingDataSetHeader header = new CachingDataSetHeader(selectItems);
        for (Iterator<Object[]> it = _values.iterator(); it.hasNext();) {
            Object[] values = (Object[]) it.next();
            DefaultRow row = new DefaultRow(header, values);
            boolean delete = true;
            for (FilterItem filterItem : whereItems) {
                if (!filterItem.evaluate(row)) {
                    delete = false;
                    break;
                }
            }
            if (delete) {
                it.remove();
            }
        }
    }

    public List<Object[]> getValues() {
        return _values;
    }
}