/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.Map;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.beanutils.PropertyUtils;

import sailpoint.object.CertificationAction;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.tools.Util;


/**
 * Utility to retrieve field values on a special "_THIS" property, from a map,
 * or using introspection to get the value from a property.  Pulled this out
 * of BaseDataSource so that we can get this behavior without having to extend
 * the BaseDataSource.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class DataSourceUtil {

    interface PropertyNameProvider {
        public String getPropertyName(JRField field);
    }

    protected static final PropertyNameProvider FIELD_NAME_PROPERTY_NAME_PROVIDER =
            new PropertyNameProvider() {
                public String getPropertyName(JRField field) {
                    return field.getName();
                }
            };

    protected static PropertyNameProvider propertyNameProvider =
            FIELD_NAME_PROPERTY_NAME_PROVIDER;

    // Field name clients can pass to recieve the current bean
    public static final String CURRENT_BEAN_FIELD_NAME = "_THIS";


    /**
     * Creates a datasource for with the given context and list.
     */
    private DataSourceUtil() {
        propertyNameProvider = FIELD_NAME_PROPERTY_NAME_PROVIDER;
    }

    /**
     * Returns value for the given field.
     * <p/>
     * Pass in the string value of CURRENT_BEAN_FIELD_NAME and retrieve the
     * current item, instead of just a field. Note that this is planned to be
     * included in the next release of jasper.
     *
     * @param field Field to retrieve.
     * @return Value of the given field.
     * @throws net.sf.jasperreports.engine.JRException
     *
     */
    public static Object getFieldValue(JRField field, Object item) throws JRException {

        if (CURRENT_BEAN_FIELD_NAME.equals(field.getName()))
            return item;

        if (item != null && item instanceof Map) {
            Map map = (Map) item;
            if (map.containsKey(field.getName()))
                return map.get(field.getName());
        }

        return getFieldValue(item, field);
    }

    protected static Object getFieldValue(Object bean, JRField field)
            throws JRException {
        return getBeanProperty(bean, getPropertyName(field));
    }

    protected static Object getBeanProperty(Object bean, String propertyName)
            throws JRException {

        Object value = null;
        if (isCurrentBeanMapping(propertyName)) {
            value = bean;
        } else if (bean != null) {
            try {
                value = PropertyUtils.getProperty(bean, propertyName);
            } catch (java.lang.IllegalAccessException e) {
                throw new JRException("Error retrieving field value from bean : " + propertyName, e);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new JRException("Error retrieving field value from bean : " + propertyName, e);
            } catch (java.lang.NoSuchMethodException e) {
                throw new JRException("Error retrieving field value from bean : " + propertyName, e);
            } catch (IllegalArgumentException e) {
                //FIXME replace with NestedNullException
                //when upgrading to BeanUtils 1.7
                if (!e.getMessage().startsWith("Null property value for ")) {
                    throw e;
                }
            }
        }
        return value;
    }

    protected static boolean isCurrentBeanMapping(String propertyName) {
        return CURRENT_BEAN_FIELD_NAME.equals(propertyName);
    }

    protected static String getPropertyName(JRField field) {
        return propertyNameProvider.getPropertyName(field);
    }

    /**
     * True if action is Remediation and the Remediation Modifiable option was used to
     * "change" the value. This will be indicated by provisioning plan with both add/remove
     * attribute
     *
     * @return
     */
    public static boolean isRemediationModified(CertificationAction action) {
        return (getRemediationModifiableNewValue(action) != null);
    }

    /**
     * Get the "Add" operation value for a remediation modification
     */
    public static Object getRemediationModifiableNewValue(CertificationAction action) {

        if (action != null &&
                CertificationAction.Status.Remediated.equals(action.getStatus()) &&
                action.getRemediationDetails() != null &&
                !Util.isEmpty(action.getRemediationDetails().getAccountRequests())) {
            AccountRequest accReq = action.getRemediationDetails().getAccountRequests().get(0);
            if (accReq != null && !Util.isEmpty(accReq.getAttributeRequests())
                    && AccountRequest.Operation.Modify.equals(accReq.getOperation())) {
                boolean removeFound = false;
                boolean addFound = false;
                Object newValue = null;
                for (AttributeRequest attReq : accReq.getAttributeRequests()) {
                    if (attReq != null) {
                        if (addFound == false &&
                                ProvisioningPlan.Operation.Add.equals(attReq.getOperation())) {
                            addFound = true;
                            newValue = attReq.getValue();
                        } else if (removeFound == false &&
                                ProvisioningPlan.Operation.Remove.equals(attReq.getOperation())) {
                            removeFound = true;
                        }
                    }
                }

                if (removeFound && addFound) {
                    return newValue;
                }
            }
        }

        return null;
    }
}
