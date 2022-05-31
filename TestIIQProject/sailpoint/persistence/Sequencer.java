/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.model.naming.ObjectNameNormalizer;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionImplementor;

import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.StringType;
import sailpoint.api.SailPointContext;
import sailpoint.object.Alert;
import sailpoint.object.IdentityRequest;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.SyslogEvent;
import sailpoint.object.WorkItem;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;


/**
 * The Sequencer can return IDs in a sequence for certain object types.  This
 * should only be used when the sequence is being used for a non-ID field.
 * Otherwise, the SPSequenceStyleGenerator should be used in the hibernate
 * mapping for the ID's generator.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class Sequencer {

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * The default amount of left padding to use.  Consider allowing this to be
     * configurable.  Should keep this in sync with hibernate IDs that use this
     * generator.  Ten digits will give us just under 10 billion IDs before we
     * overflow.
     */
    public static final int DEFAULT_LEFT_PADDING = 10;
    
    // Store one of these to avoid creating them every time.  This is stateless,
    // so we're cool.
    private static final PhysicalNamingStrategy NAMING_STRATEGY = new SPNamingStrategy();
    
    // Ideally we could get this from the hibernate configuration, but it doesn't
    // seem easy to retrieve so we'll create our own.
    private static final ObjectNameNormalizer NORMALIZER = new ObjectNameNormalizer() {
        @Override
        protected MetadataBuildingContext getBuildingContext() {
            //TODO: Update this
            return null;
        }

        public boolean isUseQuotedIdentifiersGlobally() {
            return false;
        }

        public PhysicalNamingStrategy getNamingStrategy() {
            return NAMING_STRATEGY;
        }
    };

    // Any classes that need non-ID sequences need to be registered here.
    // This will cause additional create/drop DDL, so upgrade scripts will need
    // to be modified if new classes are added.  Kind of ugly to keep a registry
    // here.  We could consider creating a marker annotation that we add to
    // classes that need this and build the registry on the fly.
    // This property has changed to use a Map where the key is the class itself and 
    // the value is the name for generating the table name in configureGenerator().
    // The change was made to allow long table names to fit when appending _sequence
    // in case the generated name would cause a table name that would be too long.
    private static final Map<Class<?>, String> CLASSES = new LinkedHashMap<Class<?>, String>();
    static {
        CLASSES.put(IdentityRequest.class, IdentityRequest.class.getSimpleName());
        CLASSES.put(WorkItem.class, WorkItem.class.getSimpleName());
        CLASSES.put(SyslogEvent.class, SyslogEvent.class.getSimpleName());
        CLASSES.put(ProvisioningTransaction.class, "PrvTrans");
        CLASSES.put(Alert.class, Alert.class.getSimpleName());
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private Dialect dialect;
    private String schema;
    private ServiceRegistry serviceReg;
    private Metadata meta;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor - assumes that the HibernatePersistenceManager has been
     * initialized.  Use this constructor if generating IDs.
     */
    public Sequencer() {

        this.dialect = HibernatePersistenceManager.getDialect();
        if (Environment.getEnvironment() != null) {
            //Set serviceReg from Environment
            this.serviceReg = Environment.getEnvironment().getServiceRegistry();
            if (this.serviceReg != null) {
                this.meta = HibernateMetadataIntegrator.INSTANCE.getMetadata();
            }
        }
    }

    /**
     * Constructor that accepts a dialect and schema.  This does not require the 
     * full HibernatePersistenceManager to be initialized.  You may choose to
     * use this if just getting the create and drop SQL.  If generating IDs,
     * use {@link #Sequencer()} instead.
     */
    public Sequencer(Dialect dialect, String schema, ServiceRegistry reg) {
        this.dialect = dialect;
        this.schema = schema;
        this.serviceReg = reg;
        if (this.serviceReg != null) {
            //Have to build it since Integrator not created
            this.meta = new MetadataSources(this.serviceReg).getMetadataBuilder().build();
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Generate the next ID for the given object.
     * 
     * @throws IllegalArgumentException  If the object's class is not registered.
     */
    public String generateId(SailPointContext context, Object object)
        throws GeneralException, IllegalArgumentException {
        
        SPSequenceStyleGenerator gen = configureGenerator(object);
        SessionImplementor session =
            (SessionImplementor) HibernatePersistenceManager.getSession(context);
        return gen.generate((SessionImplementor) session, object).toString();
    }

    /**
     * Return a list of the SQL statements required to create the appropriate
     * schema and data required by the generators for all registered classes.
     */
    public List<String> getCreateSql() throws GeneralException {
        return getSql(true);
    }

    /**
     * Return a list of the SQL statements required to drop the schema and data
     * required by the generators for all registered classes.
     */
    public List<String> getDropSql() throws GeneralException {
        return getSql(false);
    }

    /**
     * Return create or drop SQL statements for all registered classes.
     */
    private List<String> getSql(boolean create) throws GeneralException {

        List<String> sql = new ArrayList<String>();

        try {
            for (Class<?> clazz : CLASSES.keySet()) {
                Object object = clazz.newInstance();
                SPSequenceStyleGenerator gen = configureGenerator(object);
                String[] stmts =
                    (create) ? gen.sqlCreateStrings(this.dialect)
                             : gen.sqlDropStrings(this.dialect);
                if (null != stmts) {
                    sql.addAll(Arrays.asList(stmts));
                }
            }
        }
        catch (Exception e) {
            throw new GeneralException(e);
        }

        return sql;
    }
    
    /**
     * Create and configure an SPSequenceStyleGenerator to use to generate IDs
     * for the given object.
     */
    private SPSequenceStyleGenerator configureGenerator(Object object) {

        if (null == object) {
            throw new NullPointerException("Cannot generate ID for null object.");
        }
        
        if (!CLASSES.containsKey(object.getClass())) {
            throw new IllegalArgumentException("ID generation only supported for: " + CLASSES + ". " +
                                               "Either register with " + getClass() + " or use " +
                                               "a hibernate <generator> for an ID.");
        }
        
        Properties params = new Properties();

        String tableName = CLASSES.get(object.getClass()) + "_sequence";

        Identifier ident = NAMING_STRATEGY.toPhysicalTableName(Identifier.toIdentifier(tableName), null);
        tableName = ident.render();

        // These are required.
        params.put(SPSequenceStyleGenerator.IDENTIFIER_NORMALIZER, NORMALIZER);
        params.put(SPSequenceStyleGenerator.SEQUENCE_PARAM, tableName);

        // Set this to our default.  Consider making this configurable.
        params.put(SPSequenceStyleGenerator.LEFT_PADDING_PARAM, DEFAULT_LEFT_PADDING);

        // Use the default value - the column is called "next_val".
        //params.put(SequenceStyleGenerator.VALUE_COLUMN_PARAM, SequenceStyleGenerator.DEF_VALUE_COLUMN);

        // Set the schema if we have it.
        if (null != this.schema) {
            params.put(SPSequenceStyleGenerator.SCHEMA, this.schema);
        }
        
        // Hibernate supplies this automatically.
        //params.put(SequenceStyleGenerator.CATALOG, null);
        
        SPSequenceStyleGenerator gen = new SPSequenceStyleGenerator();

        gen.configure(StringType.INSTANCE, params, this.serviceReg);

        if (gen.getDatabaseStructure() != null && this.meta != null) {
            //TODO: Why is this needed? TableStructure not initializing correct vars unless registerExportables is called -rap
            gen.getDatabaseStructure().registerExportables(this.meta.getDatabase());
        }

        return gen;
    }
}
