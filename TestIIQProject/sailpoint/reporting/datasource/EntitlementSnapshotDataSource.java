/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Permission;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLReferenceResolver;

import java.util.Iterator;
import java.util.List;

/**
 * @author peter.holcomb
 *
 */

public class EntitlementSnapshotDataSource implements JRDataSource, 
XMLReferenceResolver {

	/**
	 *
	 */
	Iterator<EntitlementSnapshot> _entries;

	/**
	 *
	 */
	EntitlementSnapshot _current;

	/**
	 *
	 */

	@SuppressWarnings("unchecked")
	public EntitlementSnapshotDataSource(List<EntitlementSnapshot> snapshots) {
		if (snapshots != null ) {
			_entries = snapshots.iterator();
		}   
	}


    /* (non-Javadoc)
	 * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
	 */
	public Object getFieldValue(JRField field) throws JRException {
		Object value = null;

		String fieldName = field.getName();
		if("application".equals(fieldName))
			value = _current.getApplication();
		if("permissions".equals(fieldName)) {
			List<Permission> permissions = (List<Permission>)_current.getPermissions();
			if(permissions!=null && permissions.size() > 0) {
				StringBuffer sb = new StringBuffer();
				for(Iterator<Permission> iter = permissions.iterator(); iter.hasNext(); ) {
					Permission permission = iter.next();
					String permString = new String("Target: [" + permission.getTarget() +
							"]   Rights: [" + permission.getRights() + "]\n");
					sb.append(permString);
				}
				value = sb.toString();
			}
		}
		if("attributes".equals(fieldName) && _current.getAttributes()!=null && _current.getAttributes().size() > 0) {
			XMLObjectFactory factory = XMLObjectFactory.getInstance();
			value = factory.toXml(_current.getAttributes(), false);
			//System.out.println(value);
		}

		return value;
	}

	/* (non-Javadoc)
	 * @see net.sf.jasperreports.engine.JRDataSource#next()
	 */
	public boolean next() throws JRException {
		boolean hasMore = false;
		if ( _entries != null ) {
			hasMore = _entries.hasNext();
			if ( hasMore ) {
				_current = _entries.next();
			} else {
				_current = null;
			}
		}
		return hasMore;
	}

	/* (non-Javadoc)
	 * @see sailpoint.tools.xml.XMLReferenceResolver#getReferencedObject(java.lang.String, java.lang.String, java.lang.String)
	 */
	public Object getReferencedObject(String className, String id, String name)
	throws GeneralException {
		// TODO Auto-generated method stub
		return "";
	}

}
