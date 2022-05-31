/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A Hibernate plugin class that gives us control over the table
 * and column names generated from the persistent classes.
 * Based on ImprovedNamingStrategy which handles conversion
 * of camel case to embedded underscores.
 *
 * We're adding a prefix to all table names which seem to cause
 * the most problems, but for now we let the column names map directly.
 *
 * Author: Jeff
 */

package sailpoint.persistence;
 
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.cfg.ImprovedNamingStrategy;

import org.hibernate.cfg.NamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import sailpoint.tools.BrandingServiceFactory;

public class SPNamingStrategy extends PhysicalNamingStrategyStandardImpl {

    public static final String PREFIX = "spt_";

    protected static NamingStrategy STRATEGY = new ImprovedNamingStrategy();


	public SPNamingStrategy() {
	}

    /**
     * Add a prefix to the underscored name produced by
     * ImprovedNamingStrategy.
     */
	public String classToTableName(String className) {
        String tableName = STRATEGY.classToTableName(className);
        return BrandingServiceFactory.getService().brandTableName( tableName );
	}
	
	/**
	/**
     * Add a prefix to table names explicitly defined in the mapping files.
	 */
	public String tableName(String tableName) {
        String qualifiedName = STRATEGY.tableName(tableName);
        return BrandingServiceFactory.getService().brandTableName( qualifiedName );
	}

	@Override
	public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment jdbcEnvironment) {
		return super.toPhysicalCatalogName(name, jdbcEnvironment);
	}

	@Override
	public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment jdbcEnvironment) {
		return super.toPhysicalSchemaName(name, jdbcEnvironment);
	}

	@Override
	public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment jdbcEnvironment) {
		Identifier ident = super.toPhysicalTableName(name, jdbcEnvironment);
		return Identifier.toIdentifier(classToTableName(ident.render()));
	}

	@Override
	public Identifier toPhysicalSequenceName(Identifier name, JdbcEnvironment jdbcEnvironment) {
		//TODO: Not sure we use these? -rap
		return super.toPhysicalSequenceName(name, jdbcEnvironment);
	}

	@Override
	public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment jdbcEnvironment) {
		Identifier ident = super.toPhysicalColumnName(name, jdbcEnvironment);
		return Identifier.toIdentifier(STRATEGY.propertyToColumnName(ident.render()));
	}
}

