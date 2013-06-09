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

package org.eobjects.metamodel.jdbc.dialects;

import java.sql.Types;

import org.eobjects.metamodel.jdbc.JdbcDataContext;
import org.eobjects.metamodel.query.FilterItem;
import org.eobjects.metamodel.query.FromItem;
import org.eobjects.metamodel.query.Query;
import org.eobjects.metamodel.schema.ColumnType;

/**
 * A query rewriter can be used for rewriting (part of) a query's string
 * representation. This is usefull for databases that deviate from the SQL 99
 * compliant syntax which is delievered by the query and it's query item's
 * toString() methods.
 * 
 * @see AbstractQueryRewriter
 * @see JdbcDataContext
 */
public interface IQueryRewriter {

	public String rewriteFromItem(FromItem item);

	public String rewriteQuery(Query query);

	public String rewriteFilterItem(FilterItem whereItem);

	/**
	 * Gets whether this query rewriter is able to write the "Max rows" query
	 * property to the query string.
	 * 
	 * @return whether this query rewriter is able to write the "Max rows" query
	 *         property to the query string.
	 */
	public boolean isMaxRowsSupported();

	/**
	 * Gets whether this query rewriter is able to write the "First row" query
	 * property to the query string.
	 * 
	 * @return whether this query rewriter is able to write the "First row"
	 *         query property to the query string.
	 */
	public boolean isFirstRowSupported();

	/**
	 * Escapes the quotes within a String literal of a query item.
	 * 
	 * @return String item with quotes escaped.
	 */
	public String escapeQuotes(String item);

	/**
	 * Rewrites the name of a column type, as it is written in CREATE TABLE
	 * statements. Some databases dont support all column types, or have
	 * different names for them. The implementation of this method will do that
	 * conversion.
	 * 
	 * @param columnType
	 * @return
	 */
	public String rewriteColumnType(ColumnType columnType);

	/**
	 * Gets the column type for a specific JDBC type (as defined in
	 * {@link Types}), native type name and column size.
	 * 
	 * @param jdbcType
	 * @param nativeType
	 * @param columnSize
	 * @return
	 */
	public ColumnType getColumnType(int jdbcType, String nativeType, Integer columnSize);

}