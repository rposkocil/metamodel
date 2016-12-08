/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.metamodel.jdbc.dialects;

import static org.apache.metamodel.jdbc.JdbcDataContext.DATABASE_PRODUCT_ORACLE;
import static org.apache.metamodel.jdbc.JdbcDataContext.DATABASE_PRODUCT_SQLSERVER;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.apache.metamodel.jdbc.JdbcDataContext;
import org.apache.metamodel.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query rewriter for databases that support OFFSET and FETCH keywords for max
 * rows and first row properties.
 */
public abstract class OffsetFetchQueryRewriter extends DefaultQueryRewriter {

    private static final Logger logger = LoggerFactory.getLogger(OffsetFetchQueryRewriter.class);
    private String databaseName;
    private int databaseVersion;

    public OffsetFetchQueryRewriter(JdbcDataContext dataContext) {
        super(dataContext);
        DatabaseMetaData metaData;
        try {
            metaData = dataContext.getConnection().getMetaData();
            String version = "";
            if(metaData != null) {
                databaseName = metaData.getDatabaseProductName();
                version = metaData.getDatabaseProductVersion();
            }
            int firstDot = -1;
            if(version != null) {
                firstDot = version.indexOf('.');
            }
            if(firstDot >= 0 && version != null) {
                databaseVersion = Integer.valueOf(version.substring(0, firstDot));
            } else {
                databaseVersion = 0;
            }
        } catch (SQLException e) {
            logger.error("Problem to get a metadatada of DB connection.", e);
        }
    }

    @Override
    public final boolean isFirstRowSupported() {
        return true;
    }

    @Override
    public final boolean isMaxRowsSupported() {
        return true;
    }

    /**
     * {@inheritDoc}
     * 
     * If the Max rows and First row property of the query is set, then we
     * will use the database's "OFFSET i ROWS FETCH NEXT j ROWS ONLY" construct.
     */
    @Override
    public String rewriteQuery(Query query) {
        String queryString = super.rewriteQuery(query);
        if(isSupportedDatabase()) {
            Integer maxRows = query.getMaxRows();
            Integer firstRow = query.getFirstRow();
            if (maxRows != null && firstRow != null) {
                queryString = queryString + " OFFSET " + (firstRow-1) + " ROWS FETCH NEXT " + maxRows + " ROWS ONLY";
            }
        }

        return queryString;
    }

    private boolean isSupportedDatabase() {

        if(databaseName.equals(DATABASE_PRODUCT_SQLSERVER) && databaseVersion >= 11) {
            return true;
        }

        if(databaseName.equals(DATABASE_PRODUCT_ORACLE) && databaseVersion >= 12) {
            return true;
        }

        return false;
    }
}
