/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.persistence;

import java.io.Serializable;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.QualifiedName;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.Oracle12cDialect;
import org.hibernate.dialect.SQLServer2012Dialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.enhanced.DatabaseStructure;
import org.hibernate.id.enhanced.SequenceStructure;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.id.enhanced.TableStructure;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import sailpoint.tools.Util;

/**
 * An extension of the hibernate SequenceStyleGenerator that returns IDs as
 * strings (since this is the convention used everywhere in SailPointContext).
 * Additionally, this adds the ability to left-pad the returned IDs with zeros
 * to help with sorting and searching.
 * 
 * As with the SequenceStyleGenerator, this uses a database sequence if
 * supported by the database, and otherwise uses a table-per-sequence to store
 * the next value.  If we start getting many of these, we might consider
 * changing this to use a single table for all sequences.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class SPSequenceStyleGenerator extends SequenceStyleGenerator {

    private static final Log log = LogFactory.getLog(SPSequenceStyleGenerator.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Parameter used to specify the width of the string to left pad.  Defaults
     * to zero (no padding) if not specified.
     */
    public static final String LEFT_PADDING_PARAM = "leftPadding";
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private int leftPad;


    ////////////////////////////////////////////////////////////////////////////
    //
    // SequenceStyleGenerator overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry)
        throws MappingException {

        super.configure(type, params, serviceRegistry);

        // This defaults to zero if not found.
        this.leftPad = Util.otoi(params.get(LEFT_PADDING_PARAM));
    }
    
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object)
        throws HibernateException {

        Serializable id = super.generate(session, object);

        // ID should be a long.  Left pad (if configured) and convert to a string.
        if (id != null) {
            if (this.leftPad > 0) {
                id = String.format("%0" + this.leftPad + "d", id);

                if (((String) id).length() > this.leftPad) {
                    if (log.isWarnEnabled())
                        log.warn("Left padding has overflowed for " + object.getClass());
                }
            }
            else {
                id = id.toString();
            }
        }

        return id;
    }

    /*
     * This is essentially a copy from SequenceStyleGenerator except that
     * Long.class is hardcoded as the last parameter for the constructors rather
     * than using the type's returned class.  This is necessary because the type
     * is a string but the structures expect numbers.
     */
    @Override
    protected DatabaseStructure buildDatabaseStructure(
            Type type,
            Properties params,
            JdbcEnvironment jdbcEnvironment,
            boolean forceTableUse,
            QualifiedName sequenceName,
            int initialValue,
            int incrementSize) {

        boolean useSequence = jdbcEnvironment.getDialect().supportsSequences() && !forceTableUse;
        if ( useSequence ) {
            return new SequenceStructure( jdbcEnvironment, sequenceName, initialValue, incrementSize, Long.class );
        }
        else {
            Identifier valueColumnName = determineValueColumnName( params, jdbcEnvironment);
            return new TableStructure( jdbcEnvironment, sequenceName, valueColumnName, initialValue, incrementSize, Long.class );
        }

    }

    /*
     * Overridden to prevent sequence caching when sequences are supported.
     */
    @Override
    public String[] sqlCreateStrings(Dialect dialect) throws HibernateException {
        String[] sql = super.sqlCreateStrings(dialect);

        // If sequences are supported, add the "nocache" directive.
        if (dialect.supportsSequences()) {
            String[] newSql = new String[sql.length];

            // Note: I considered putting this in the custom dialects, but there
            // are two problems.  The first is that I'm not sure that all legacy
            // iiq.properties are using the custom sailpoint dialects.  The
            // other is that Dialect.getCreateSequenceString() is actually just
            // the "create sequence <name>" part, and is suffixed with the start
            // and limit.  Since nocache needs to be added to the end, this would
            // not have worked.
            String suffix = "";
            if (dialect instanceof Oracle12cDialect) {
                suffix = " nocache";
            }
            else if (dialect instanceof org.hibernate.dialect.DB2Dialect) {
                suffix = " nocache order";
            }
            else if (dialect instanceof SQLServer2012Dialect) {
                //no cache by default
            }
            else {
                throw new HibernateException("Unhandled dialect that supports sequences '" + dialect.getClass() + "' - sequences may be cached.");
            }
            
            for (int i=0; i<sql.length; i++) {
                newSql[i] = sql[i] + suffix;
            }
            
            sql = newSql;
        }
        
        return sql;
    }
}
