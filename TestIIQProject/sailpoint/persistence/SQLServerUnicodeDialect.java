/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Extends org.hibernate.dialect.SQLServerDialect
 * default dialect for Unicode. 
 */

package sailpoint.persistence;

import java.sql.Types;

import org.hibernate.mapping.Column;
import org.hibernate.type.StringType;

public class SQLServerUnicodeDialect extends org.hibernate.dialect.SQLServer2012Dialect {

	public SQLServerUnicodeDialect() {
		super();
        registerColumnType(Types.CHAR, "nchar(1)");
        registerColumnType(Types.CHAR, Column.DEFAULT_LENGTH-1, "char($l)");
		registerColumnType(Types.VARCHAR, "nvarchar($l)" );
		registerColumnType(Types.VARCHAR, 4000, "nvarchar($l)");
		//using nvarchar(max) or ntext? ntext is probably deprecated
		//registerColumnType(Types.CLOB, "nvarchar(max)");
		//nvarchar(max) also works from ALTER TABLE ALTER COLUMN viewpoint
		//If we get into issue with this, we can revert back.
		registerColumnType(Types.CLOB, "nvarchar(max)");

		registerColumnType( Types.BIGINT, "numeric(19,0)" );
		registerColumnType( Types.BOOLEAN, "tinyint" );

        registerHibernateType(Types.NVARCHAR, new StringType().getName());
   }

}
