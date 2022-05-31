/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;


import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.beanutils.BeanUtils;


/**
 * A Jasper data source that can load an object from XML and uses reflection to
 * retrieve the field value based on the name of the JRField.
 * 
 * @author Kelly Grizzle
 */
public class XmlObjectDataSource implements JRDataSource {

    private Object object;
    private boolean hasBeenRetrieved;

    /**
     * Constructor.
     * 
     * @param  object   The XML of the object.
     */
    public XmlObjectDataSource(Object object) {
        super();
        this.object = object; 
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField field) throws JRException {

        String value = null;
        String fieldName = field.getName();

        if (null != this.object) {
            try {
                value = BeanUtils.getNestedProperty(this.object, fieldName);
            }
            catch (Exception e) {
                throw new JRException(e);
            }
        }

        return value;
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     */
    public boolean next() throws JRException {

        boolean hadBeenRetrieved = this.hasBeenRetrieved;

        this.hasBeenRetrieved = true;

        return !hadBeenRetrieved;
    }
}
