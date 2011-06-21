/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.sql.pg;

import com.akiban.ais.model.TableIndex;
import com.akiban.qp.exec.UpdatePlannable;
import com.akiban.qp.physicaloperator.PhysicalOperator;
import com.akiban.sql.StandardException;

import com.akiban.sql.optimizer.OperatorCompiler;
import static com.akiban.sql.optimizer.SimplifiedQuery.*;
import com.akiban.sql.optimizer.ExpressionRow;

import com.akiban.sql.parser.DMLStatementNode;
import com.akiban.sql.parser.SQLParser;
import com.akiban.sql.parser.StatementNode;
import com.akiban.sql.parser.ValueNode;
import com.akiban.sql.types.DataTypeDescriptor;

import com.akiban.sql.views.ViewDefinition;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.UserTable;

import com.akiban.qp.expression.Expression;

import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Compile SQL SELECT statements into operator trees if possible.
 */
public class PostgresOperatorCompiler extends OperatorCompiler
                                      implements PostgresStatementGenerator
{
    private static final Logger logger = LoggerFactory.getLogger(PostgresOperatorCompiler.class);

    public PostgresOperatorCompiler(PostgresServerSession server) {
        super(server.getParser(), server.getAIS(), server.getDefaultSchemaName());

        server.setAttribute("aisBinder", binder);
        server.setAttribute("compiler", this);
    }

    @Override
    public PostgresStatement parse(PostgresServerSession server,
                                   String sql, int[] paramTypes) 
            throws StandardException {
        // This very inefficient reparsing by every generator is actually avoided.
        return generate(server, server.getParser().parseStatement(sql), paramTypes);
    }

    @Override
    public void sessionChanged(PostgresServerSession server) {
        binder.setDefaultSchemaName(server.getDefaultSchemaName());
    }

    static class PostgresResultColumn extends ResultColumnBase {
        private PostgresType type;
        
        public PostgresResultColumn(String name, PostgresType type) {
            super(name);
            this.type = type;
        }

        public PostgresType getType() {
            return type;
        }
    }

    @Override
    public ResultColumnBase getResultColumn(SimpleSelectColumn selectColumn) 
            throws StandardException {
        String name = selectColumn.getName();
        PostgresType type = null;
        SimpleExpression selectExpr = selectColumn.getExpression();
        if (selectExpr.isColumn()) {
            ColumnExpression columnExpression = (ColumnExpression)selectExpr;
            Column column = columnExpression.getColumn();
            if (selectColumn.isNameDefaulted())
                name = column.getName(); // User-preferred case.
            type = PostgresType.fromAIS(column);
        }
        else {
            type = PostgresType.fromDerby(selectColumn.getType());
        }
        return new PostgresResultColumn(name, type);
    }

    @Override
    public PostgresStatement generate(PostgresServerSession session,
                                      StatementNode stmt, int[] paramTypes)
            throws StandardException {
        if (!(stmt instanceof DMLStatementNode))
            return null;
        DMLStatementNode dmlStmt = (DMLStatementNode)stmt;
        Result result = compile(dmlStmt);

        List<PostgresType> parameterTypes = null;
        {
            List<ValueNode> parameterNodes = session.getParser().getParameterList();
            if ((parameterNodes != null) && (parameterNodes.size() > 0)) {
                parameterTypes = new ArrayList<PostgresType>(parameterNodes.size());
                for (ValueNode valueNode : parameterNodes) {
                    DataTypeDescriptor sqlType = valueNode.getType();
                    PostgresType pgType = null;
                    if (sqlType != null) {
                        pgType = PostgresType.fromDerby(sqlType);
                        if (pgType != null)
                            pgType.pickEncoder();
                    }
                    parameterTypes.add(pgType);
                }
            }
        }

        logger.debug("Operator:\n{}", result);

        if (result.isModify())
            return new PostgresModifyOperatorStatement(stmt.statementToString(),
                                                       (UpdatePlannable) result.getResultOperator(),
                                                       parameterTypes);
        else {
            int ncols = result.getResultColumns().size();
            List<String> columnNames = new ArrayList<String>(ncols);
            List<PostgresType> columnTypes = new ArrayList<PostgresType>(ncols);
            for (ResultColumnBase rcBase : result.getResultColumns()) {
                PostgresResultColumn resultColumn = (PostgresResultColumn)rcBase;
                columnNames.add(resultColumn.getName());
                columnTypes.add(resultColumn.getType());
            }
            return new PostgresOperatorStatement((PhysicalOperator)result.getResultOperator(),
                                                 columnNames, columnTypes,
                                                 parameterTypes,
                                                 result.getOffset(),
                                                 result.getLimit());
        }
    }

    protected Schema getSchema() {
        return schema;
    }

}
