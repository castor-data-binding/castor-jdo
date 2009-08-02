/*
 * Copyright 2009 Ahmad Hassan, Ralf Joachim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.castor.cpa.persistence.sql.keygen;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.castor.core.util.Messages;
import org.castor.cpa.persistence.sql.engine.SQLStatementInsertCheck;
import org.castor.jdo.engine.SQLTypeInfos;
import org.castor.persist.ProposedEntity;
import org.exolab.castor.jdo.Database;
import org.exolab.castor.jdo.DuplicateIdentityException;
import org.exolab.castor.jdo.PersistenceException;
import org.exolab.castor.jdo.engine.SQLColumnInfo;
import org.exolab.castor.jdo.engine.SQLEngine;
import org.exolab.castor.jdo.engine.SQLFieldInfo;
import org.exolab.castor.jdo.engine.nature.ClassDescriptorJDONature;
import org.exolab.castor.mapping.ClassDescriptor;
import org.exolab.castor.mapping.MappingException;
import org.exolab.castor.persist.spi.Identity;
import org.exolab.castor.persist.spi.PersistenceFactory;


/**
 * Key generator implementation that does not generate key.
 * 
 * @author <a href="mailto:ahmad DOT hassan AT gmail DOT com">Ahmad Hassan</a>
 * @author <a href="mailto:ralf DOT joachim AT syscon DOT eu">Ralf Joachim</a>
 * @version $Revision: 8241 $ $Date: 2006-04-13 06:47:36 -0600 (Thu, 13 Apr 2006) $
 */
public final class NoKeyGenerator extends AbstractNoKeyGenerator {
    //-----------------------------------------------------------------------------------
    /** The <a href="http://jakarta.apache.org/commons/logging/">Jakarta
     *  Commons Logging</a> instance used for all logging. */
    private static final Log LOG = LogFactory.getLog(NoKeyGenerator.class);
    
    /** Persistence factory for the database engine the entity is persisted in.
     * Used to format the SQL statement. */
    private final PersistenceFactory _factory;
    
    /** SQL engine for all persistence operations at entities of the type this
     * class is responsible for. Holds all required information of the entity type. */
    private SQLEngine _engine;

    /**
     * Constructor. 
     * 
     * @param factory Persistence factory for the database engine the entity is persisted in.
     *        Used to format the SQL statement.
     */
    public NoKeyGenerator(final PersistenceFactory factory) { 
        _factory = factory;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isInSameConnection() {
        return true;
    }
    
    //-----------------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public Object generateKey(final Connection conn, final String tableName,
            final String primKeyName, final Properties props) throws PersistenceException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String patchSQL(final String insert, final String primKeyName)
            throws MappingException {
        return null;
    }
    
    /**
     * {@inheritDoc}
     */
    public Object executeStatement(final SQLEngine engine, final String statement, 
            final Database database, final Connection conn, final Identity identity, 
            final ProposedEntity entity) throws PersistenceException {
        _engine = engine;
        
        ClassDescriptor clsDesc = _engine.getDescriptor();
        String type = clsDesc.getJavaClass().getName();
        String mapTo = new ClassDescriptorJDONature(clsDesc).getTableName();
        SQLStatementInsertCheck lookupStatement = new SQLStatementInsertCheck(_engine, _factory);
        Identity internalIdentity = identity;
        SQLEngine extended = _engine.getExtends();
        
        if ((extended == null) && (internalIdentity == null)) {
            throw new PersistenceException(Messages.format("persist.noIdentity", type));
        }

        PreparedStatement stmt = null;
        try {
            // must create record in the parent table first. all other dependents
            // are created afterwards. quick and very dirty hack to try to make
            // multiple class on the same table work.
            if (extended != null) {
                ClassDescriptor extDesc = extended.getDescriptor();
                if (!new ClassDescriptorJDONature(extDesc).getTableName().equals(mapTo)) {
                    internalIdentity = extended.create(database, conn, entity, internalIdentity);
                }
            }
            
            // we only need to care on JDBC 3.0 at after INSERT.
            stmt = conn.prepareStatement(statement);
             
            if (LOG.isTraceEnabled()) {
                LOG.trace(Messages.format("jdo.creating", type, stmt.toString()));
            }
            
            SQLColumnInfo[] ids = _engine.getColumnInfoForIdentities();
            if (internalIdentity.size() != ids.length) {
                throw new PersistenceException("Size of identity field mismatched!");
            }

            // must remember that SQL column index is base one.
            int count = 1;
            for (int i = 0; i < ids.length; i++) {
                stmt.setObject(count++, ids[i].toSQL(internalIdentity.get(i)));
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace(Messages.format("jdo.creating", type, stmt.toString()));
            }

            count = bindFields(entity, stmt, count);

            if (LOG.isDebugEnabled()) {
                LOG.debug(Messages.format("jdo.creating", type, stmt.toString()));
            }

            stmt.executeUpdate();

            stmt.close();

            return internalIdentity;
        } catch (SQLException except) {
            LOG.fatal(Messages.format("jdo.storeFatal",  type,  statement), except);

            Boolean isDupKey = _factory.isDuplicateKeyException(except);
            if (Boolean.TRUE.equals(isDupKey)) {
                throw new DuplicateIdentityException(Messages.format(
                        "persist.duplicateIdentity", type, internalIdentity), except);
            } else if (Boolean.FALSE.equals(isDupKey)) {
                throw new PersistenceException(Messages.format("persist.nested", except), except);
            }

            // without an identity we can not check for duplicate key
            if (internalIdentity == null) {
                throw new PersistenceException(Messages.format("persist.nested", except), except);
            }

            // check for duplicate key the old fashioned way, after the INSERT
            // failed to prevent race conditions and optimize INSERT times.
            lookupStatement.insertDuplicateKeyCheck(conn, internalIdentity);

            try {
                if (stmt != null) { stmt.close(); }
            } catch (SQLException except2) {
                LOG.warn("Problem closing JDBC statement", except2);
            }
            
            throw new PersistenceException(Messages.format("persist.nested", except), except);
        }
    }
    
    /**
     * Binds parameters values to the PreparedStatement.
     * 
     * @param entity
     * @param stmt PreparedStatement object containing sql staatement.
     * @param count Offset.
     * @return final Offset
     * @throws SQLException If a database access error occurs.
     * @throws PersistenceException If identity size mismatches.
     */
    private int bindFields(final ProposedEntity entity, final PreparedStatement stmt,
            final int count) throws SQLException, PersistenceException {
        int internalCount = count;
        SQLFieldInfo[] fields = _engine.getInfo();
        for (int i = 0; i < fields.length; ++i) {
            SQLColumnInfo[] columns = fields[i].getColumnInfo();
            if (fields[i].isStore()) {
                Object value = entity.getField(i);
                if (value == null) {
                    for (int j = 0; j < columns.length; j++) {
                        stmt.setNull(internalCount++, columns[j].getSqlType());
                    }
                } else if (value instanceof Identity) {
                    Identity identity = (Identity) value;
                    if (identity.size() != columns.length) {
                        throw new PersistenceException("Size of identity field mismatch!");
                    }
                    for (int j = 0; j < columns.length; j++) {
                        SQLTypeInfos.setValue(stmt, internalCount++,
                                columns[j].toSQL(identity.get(j)), columns[j].getSqlType());
                    }
                } else {
                    if (columns.length != 1) {
                        throw new PersistenceException("Complex field expected!");
                    }
                    SQLTypeInfos.setValue(stmt, internalCount++, columns[0].toSQL(value),
                            columns[0].getSqlType());
                }
            }
        }
        return internalCount;
    }
    
    //-----------------------------------------------------------------------------------
}