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

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eobjects.metamodel.data.DataSet;
import org.eobjects.metamodel.query.CompiledQuery;
import org.eobjects.metamodel.query.DefaultCompiledQuery;
import org.eobjects.metamodel.query.Query;
import org.eobjects.metamodel.query.builder.InitFromBuilder;
import org.eobjects.metamodel.query.builder.InitFromBuilderImpl;
import org.eobjects.metamodel.query.parser.QueryParser;
import org.eobjects.metamodel.schema.Column;
import org.eobjects.metamodel.schema.Schema;
import org.eobjects.metamodel.schema.Table;

/**
 * Abstract implementation of the DataContext interface. Provides convenient
 * implementations of all trivial and datastore-independent methods.
 * 
 * @author Kasper Sørensen
 */
public abstract class AbstractDataContext implements DataContext {

    private static final String NULL_SCHEMA_NAME_TOKEN = "<metamodel.schema.name.null>";
    private final ConcurrentMap<String, Schema> _schemaCache = new ConcurrentHashMap<String, Schema>();
    private final Comparator<? super String> _schemaNameComparator = SchemaNameComparator.getInstance();
    private String[] _schemaNameCache;

    /**
     * {@inheritDoc}
     */
    @Override
    public final DataContext refreshSchemas() {
        _schemaCache.clear();
        _schemaNameCache = null;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Schema[] getSchemas() throws MetaModelException {
        String[] schemaNames = getSchemaNames();
        Schema[] schemas = new Schema[schemaNames.length];
        for (int i = 0; i < schemaNames.length; i++) {
            final String name = schemaNames[i];
            final Schema schema = _schemaCache.get(getSchemaCacheKey(name));
            if (schema == null) {
                final Schema newSchema = getSchemaByName(name);
                if (newSchema == null) {
                    throw new MetaModelException("Declared schema does not exist: " + name);
                }
                final Schema existingSchema = _schemaCache.putIfAbsent(getSchemaCacheKey(name), newSchema);
                if (existingSchema == null) {
                    schemas[i] = newSchema;
                } else {
                    schemas[i] = existingSchema;
                }
            } else {
                schemas[i] = schema;
            }
        }
        return schemas;
    }

    private String getSchemaCacheKey(String name) {
        if (name == null) {
            return NULL_SCHEMA_NAME_TOKEN;
        }
        return name;
    }

    /**
     * m {@inheritDoc}
     */
    @Override
    public final String[] getSchemaNames() throws MetaModelException {
        if (_schemaNameCache == null) {
            _schemaNameCache = getSchemaNamesInternal();
        }
        String[] schemaNames = Arrays.copyOf(_schemaNameCache, _schemaNameCache.length);
        Arrays.sort(schemaNames, _schemaNameComparator);
        return schemaNames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Schema getDefaultSchema() throws MetaModelException {
        Schema result = null;
        String defaultSchemaName = getDefaultSchemaName();
        if (defaultSchemaName != null) {
            result = getSchemaByName(defaultSchemaName);
        }
        if (result == null) {
            Schema[] schemas = getSchemas();
            if (schemas.length == 1) {
                result = schemas[0];
            } else {
                int highestTableCount = -1;
                for (int i = 0; i < schemas.length; i++) {
                    final Schema schema = schemas[i];
                    String name = schema.getName();
                    if (schema != null) {
                        name = name.toLowerCase();
                        boolean isInformationSchema = name.startsWith("information") && name.endsWith("schema");
                        if (!isInformationSchema && schema.getTableCount() > highestTableCount) {
                            highestTableCount = schema.getTableCount();
                            result = schema;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final InitFromBuilder query() {
        return new InitFromBuilderImpl(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Query parseQuery(final String queryString) throws MetaModelException {
        final QueryParser parser = new QueryParser(this, queryString);
        final Query query = parser.parse();
        return query;
    }

    @Override
    public CompiledQuery compileQuery(final Query query) throws MetaModelException {
        return new DefaultCompiledQuery(query);
    }

    @Override
    public DataSet executeQuery(CompiledQuery compiledQuery, Object... values) {
        assert compiledQuery instanceof DefaultCompiledQuery;

        final DefaultCompiledQuery defaultCompiledQuery = (DefaultCompiledQuery) compiledQuery;
        final Query query = defaultCompiledQuery.cloneWithParameterValues(values);

        return executeQuery(query);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final DataSet executeQuery(final String queryString) throws MetaModelException {
        final Query query = parseQuery(queryString);
        final DataSet dataSet = executeQuery(query);
        return dataSet;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Schema getSchemaByName(String name) throws MetaModelException {
        Schema schema = _schemaCache.get(getSchemaCacheKey(name));
        if (schema == null) {
            if (name == null) {
                schema = getSchemaByNameInternal(null);
            } else {
                String[] schemaNames = getSchemaNames();
                for (String schemaName : schemaNames) {
                    if (name.equalsIgnoreCase(schemaName)) {
                        schema = getSchemaByNameInternal(name);
                        break;
                    }
                }
                if (schema == null) {
                    for (String schemaName : schemaNames) {
                        if (name.equalsIgnoreCase(schemaName)) {
                            // try again with "schemaName" as param instead of
                            // "name".
                            schema = getSchemaByNameInternal(schemaName);
                            break;
                        }
                    }
                }
            }
            if (schema != null) {
                Schema existingSchema = _schemaCache.putIfAbsent(getSchemaCacheKey(schema.getName()), schema);
                if (existingSchema != null) {
                    // race conditions may cause two schemas to be created.
                    // We'll favor the existing schema if possible, since schema
                    // may contain lazy-loading logic and so on.
                    return existingSchema;
                }
            }
        }
        return schema;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Column getColumnByQualifiedLabel(final String columnName) {
        if (columnName == null) {
            return null;
        }
        Schema schema = null;
        final String[] schemaNames = getSchemaNames();
        for (final String schemaName : schemaNames) {
            if (schemaName == null) {
                // search without schema name (some databases have only a single
                // schema with no name)
                schema = getSchemaByName(null);
                if (schema != null) {
                    Column column = getColumn(schema, columnName);
                    if (column != null) {
                        return column;
                    }
                }
            } else {
                // Search case-sensitive
                Column col = searchColumn(schemaName, columnName, columnName);
                if (col != null) {
                    return col;
                }
            }
        }

        final String columnNameInLowerCase = columnName.toLowerCase();
        for (final String schemaName : schemaNames) {
            if (schemaName != null) {
                // search case-insensitive
                String schameNameInLowerCase = schemaName.toLowerCase();
                Column col = searchColumn(schameNameInLowerCase, columnName, columnNameInLowerCase);
                if (col != null) {
                    return col;
                }
            }
        }

        schema = getDefaultSchema();
        if (schema != null) {
            Column column = getColumn(schema, columnName);
            if (column != null) {
                return column;
            }
        }

        return null;
    }

    /**
     * Searches for a particular column within a schema
     * 
     * @param schemaNameSearch
     *            the schema name to use for search
     * @param columnNameOriginal
     *            the original column name
     * @param columnNameSearch
     *            the column name as it should be searched for (either the same
     *            as original, or lower case in case of case-insensitive search)
     * @return
     */
    private Column searchColumn(String schemaNameSearch, String columnNameOriginal, String columnNameSearch) {
        if (columnNameSearch.startsWith(schemaNameSearch)) {
            Schema schema = getSchemaByName(schemaNameSearch);
            if (schema != null) {
                String tableAndColumnPath = columnNameOriginal.substring(schemaNameSearch.length());

                if (tableAndColumnPath.charAt(0) == '.') {
                    tableAndColumnPath = tableAndColumnPath.substring(1);

                    Column column = getColumn(schema, tableAndColumnPath);
                    if (column != null) {
                        return column;
                    }
                }
            }
        }
        return null;
    }

    private final Column getColumn(final Schema schema, final String tableAndColumnPath) {
        Table table = null;
        String columnPath = tableAndColumnPath;
        final String[] tableNames = schema.getTableNames();
        for (final String tableName : tableNames) {
            if (tableName != null) {
                // search case-sensitive
                if (isStartingToken(tableName, tableAndColumnPath)) {
                    table = schema.getTableByName(tableName);
                    columnPath = tableAndColumnPath.substring(tableName.length());

                    if (columnPath.charAt(0) == '.') {
                        columnPath = columnPath.substring(1);
                        break;
                    }
                }
            }
        }

        if (table == null) {
            final String tableAndColumnPathInLowerCase = tableAndColumnPath.toLowerCase();
            for (final String tableName : tableNames) {
                if (tableName != null) {
                    String tableNameInLowerCase = tableName.toLowerCase();
                    // search case-insensitive
                    if (isStartingToken(tableNameInLowerCase, tableAndColumnPathInLowerCase)) {
                        table = schema.getTableByName(tableName);
                        columnPath = tableAndColumnPath.substring(tableName.length());

                        if (columnPath.charAt(0) == '.') {
                            columnPath = columnPath.substring(1);
                            break;
                        }
                    }
                }
            }
        }

        if (table == null && tableNames.length == 1) {
            table = schema.getTables()[0];
        }

        if (table != null) {
            Column column = table.getColumnByName(columnPath);
            if (column != null) {
                return column;
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Table getTableByQualifiedLabel(final String tableName) {
        if (tableName == null) {
            return null;
        }
        Schema schema = null;
        String[] schemaNames = getSchemaNames();
        for (String schemaName : schemaNames) {
            if (schemaName == null) {
                // there's an unnamed schema present.
                schema = getSchemaByName(null);
                if (schema != null) {
                    Table table = schema.getTableByName(tableName);
                    return table;
                }
            } else {
                // case-sensitive search
                if (isStartingToken(schemaName, tableName)) {
                    schema = getSchemaByName(schemaName);
                }
            }
        }

        if (schema == null) {
            final String tableNameInLowerCase = tableName.toLowerCase();
            for (final String schemaName : schemaNames) {
                if (schemaName != null) {
                    // case-insensitive search
                    final String schemaNameInLowerCase = schemaName.toLowerCase();
                    if (isStartingToken(schemaNameInLowerCase, tableNameInLowerCase)) {
                        schema = getSchemaByName(schemaName);
                    }
                }
            }
        }

        if (schema == null) {
            schema = getDefaultSchema();
        }

        String tablePart = tableName.toLowerCase();
        String schemaName = schema.getName();
        if (schemaName != null && isStartingToken(schemaName.toLowerCase(), tablePart)) {
            tablePart = tablePart.substring(schemaName.length());
            if (tablePart.startsWith(".")) {
                tablePart = tablePart.substring(1);
            }
        }

        return schema.getTableByName(tablePart);
    }

    private boolean isStartingToken(String partName, String fullName) {
        if (fullName.startsWith(partName)) {
            final int length = partName.length();
            if (length == 0) {
                return false;
            }
            if (fullName.length() > length) {
                final char nextChar = fullName.charAt(length);
                if (isQualifiedPathDelim(nextChar)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isQualifiedPathDelim(char c) {
        return c == '.' || c == '"';
    }

    /**
     * Gets schema names from the non-abstract implementation. These schema
     * names will be cached except if the {@link #refreshSchemas()} method is
     * called.
     * 
     * @return an array of schema names.
     */
    protected abstract String[] getSchemaNamesInternal();

    /**
     * Gets the name of the default schema.
     * 
     * @return the default schema name.
     */
    protected abstract String getDefaultSchemaName();

    /**
     * Gets a specific schema from the non-abstract implementation. This schema
     * object will be cached except if the {@link #refreshSchemas()} method is
     * called.
     * 
     * @param name
     *            the name of the schema to get
     * @return a schema object representing the named schema, or null if no such
     *         schema exists.
     */
    protected abstract Schema getSchemaByNameInternal(String name);
}