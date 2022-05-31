/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.flatfile;
public class ConnectorService {
	// An enum that has utility methods around pre-defined connectors
	public static enum Connector {
		LDAP("sailpoint.connector.LDAPConnector"), JDBC(
				"sailpoint.connector.JDBCConnector"), DELIMITED_FILE(
				"sailpoint.connector.DelimitedFileConnector");
		String className; // The connector class name
		Connector(String className) {
			this.className = className;
		}
		/**
		 * Returns the fully qualified name of the underlying class file that
		 * implements this connector. For example, for JDBC, it returns
		 * sailpoint.connector.JDBCConnector
		 * 
		 * @return
		 */
		public String getConnectorClassName() {
			return className;
		}
	}
}
