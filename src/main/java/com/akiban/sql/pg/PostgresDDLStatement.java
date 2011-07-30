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

import com.akiban.server.api.DDLFunctions;
import com.akiban.server.service.session.Session;
import com.akiban.sql.aisddl.*;

import com.akiban.sql.StandardException;

import com.akiban.sql.parser.CreateIndexNode;
import com.akiban.sql.parser.CreateTableNode;
import com.akiban.sql.parser.CreateSchemaNode;
import com.akiban.sql.parser.DropTableNode;
import com.akiban.sql.parser.DropSchemaNode;
import com.akiban.sql.parser.DDLStatementNode;
import com.akiban.sql.parser.DropViewNode;
import com.akiban.sql.parser.NodeTypes;

import com.akiban.sql.optimizer.AISBinder;
import com.akiban.sql.views.ViewDefinition;

import com.akiban.ais.model.AkibanInformationSchema;

import java.io.IOException;

/** SQL DDL statements. */
public class PostgresDDLStatement implements PostgresStatement
{
    private DDLStatementNode ddl;
    
    public PostgresDDLStatement(DDLStatementNode ddl) {
        this.ddl = ddl;
    }

    @Override
    public PostgresStatement getBoundStatement(String[] parameters,
                                               boolean[] columnBinary, 
                                               boolean defaultColumnBinary) 
            throws StandardException {
        if (parameters != null)
            throw new StandardException("Parameters not supported.");
        return this;
    }

    @Override
    public void sendDescription(PostgresServerSession server, boolean always) 
            throws IOException, StandardException {
        if (always) {
            PostgresMessenger messenger = server.getMessenger();
            messenger.beginMessage(PostgresMessenger.NO_DATA_TYPE);
            messenger.sendMessage();
        }
    }

    public int execute(PostgresServerSession server, int maxrows)
            throws IOException, StandardException {
        AkibanInformationSchema ais = server.getAIS();
        String schema = server.getDefaultSchemaName();
        DDLFunctions ddlFunctions = server.getDXL().ddlFunctions();
        Session session = server.getSession();

        switch (ddl.getNodeType()) {
        case NodeTypes.CREATE_SCHEMA_NODE:
            SchemaDDL.createSchema(ais, schema, (CreateSchemaNode)ddl);
            break;
        case NodeTypes.DROP_SCHEMA_NODE:
            SchemaDDL.dropSchema(ddlFunctions, session, (DropSchemaNode)ddl);
            break;
        case NodeTypes.CREATE_TABLE_NODE:
            TableDDL.createTable(ddlFunctions, session, schema, (CreateTableNode)ddl);
            break;
        case NodeTypes.DROP_TABLE_NODE:
            TableDDL.dropTable(ddlFunctions, session, schema, (DropTableNode)ddl);
            break;
        case NodeTypes.CREATE_VIEW_NODE:
            // TODO: Need to store persistently in AIS (or its extension).
            ((AISBinder)server.getAttribute("aisBinder")).addView(new ViewDefinition(ddl, server.getParser()));
            break;
        case NodeTypes.DROP_VIEW_NODE:
            ((AISBinder)server.getAttribute("aisBinder")).removeView(((DropViewNode)ddl).getObjectName());
            break;
        case NodeTypes.CREATE_INDEX_NODE:
            IndexDDL.createIndex(ais, schema, (CreateIndexNode)ddl);
            break;
        case NodeTypes.RENAME_NODE:
        case NodeTypes.ALTER_TABLE_NODE:
        case NodeTypes.REVOKE_NODE:
        default:
            throw new StandardException(ddl.statementToString() + " not supported yet");
        }

        {        
            PostgresMessenger messenger = server.getMessenger();
            messenger.beginMessage(PostgresMessenger.COMMAND_COMPLETE_TYPE);
            messenger.writeString(ddl.statementToString());
            messenger.sendMessage();
        }
        return 0;
    }
}
