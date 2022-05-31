/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Used to record information about role assignment in an Identity.
 * This is similar to AttributeMetadata but different in that
 * it is recording changes to individual values of a single multi-valued
 * attribute.  There might be other things wanted here.
 *
 * Author: Jeff/Dan
 *
 * Derived from the original RoleAssignment.
 * 
 */

package sailpoint.object;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import sailpoint.object.Application.Feature;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 *
 * List of assignments that are assigned to an identity, typically
 * through the Lifecycle Manager interface.
 *
 * A list of these will be stored in the preferences area of
 * the identity cube.
 * 
 * When comparing or using the application stored on this object
 * be careful as these applications can fail to be resolved.
 * 
 * Either call resolveApplication or use the ID when trying
 * to resolve the application instance. 
 * 
 * Since these are stored in an XML blob on the identity
 * these can get stale so consumers make sure they are
 * valid. IdentityIQ does not go through them during refresh to make
 * sure they contain valid attributes and applications.
 *     
 * @see Identity#validateAttributeAssignments(Resolver)
 * 
 */
@XMLClass
public class AttributeAssignment extends Assignment
{

    //////////////////////////////////////////////////////////////////////
    // 
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * You must have a serialVersionUID!
     */
    private static final long serialVersionUID = 1L;

    /**
     * Application name or null for identity-level attributes.
     */
    String applicationName;
    
    /**
     * Application ID, to help with the cases when the application has been renamed.
     */
    String applicationId;

    /**
     * Instance identifier for template applications.
     */
    String instance;

    /**
     * Native identity of the application account link.
     */
    String nativeIdentity;

    /**
     * Name of an attribute or target of a permission.
     */
    String name;

    /**
     * Value of an attribute or rights of a permission.
     * Rights are represented as a CVS string.
     */
    Object value;
    
    /**
     * Annotation of the permission. Undefined for attributes.
     */
    String annotation;

    /**
     * The collector that created these assignments
     */
    String targetCollector;
    
    /**
     * Indicate the type of the assignment to help
     * distinguish between permissions and normal entitlements.
     * TODO: Should we change this to string as well?
     */
    ManagedAttribute.Type type;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public AttributeAssignment() {
       super();
    }

    public AttributeAssignment(Application app,
                               String nativeIdentity,
                               String instance,
                               String name,
                               Object value,
                               String assigner,
                               Source source,
                               ManagedAttribute.Type type) {
        this(app, nativeIdentity, instance, name, value, assigner, source, type, null);

    }
    
    public AttributeAssignment(Application app,                               
                               String nativeIdentity,
                               String instance, 
                               String name,
                               Object value, 
                               String assigner, 
                               Source source,
                               ManagedAttribute.Type type,
                               String assignmentId) {
        
        this();        
        
        if ( app != null ) {
            setApplicationId(app.getId());
            setApplicationName(app.getName());
        }
        setNativeIdentity(nativeIdentity);
        setInstance(instance);
        setName(name);
        setValue(value);
        setAssigner(assigner);
        setSource((source != null) ? source.toString() : null);
        setType(type);
        setAssignmentId(assignmentId);
   }
    
   public AttributeAssignment(Application app,
            String nativeIdentity,
            String instance,
            String name,
            Object value,
            String assigner,
            Source source ) {

       this(app, nativeIdentity, instance, name, value, assigner, source, ManagedAttribute.Type.Entitlement);
   }

    public AttributeAssignment(Application app,
                               String nativeIdentity,
                               String instance,
                               String name,
                               Object value,
                               String assigner,
                               Source source,
                               String assignmentId) {

        this(app, nativeIdentity, instance, name, value, assigner, source, ManagedAttribute.Type.Entitlement, assignmentId);
    }
   
   public AttributeAssignment(IdentityEntitlement entitlement) {
       this(entitlement.getApplication(), 
            entitlement.getNativeIdentity(), 
            entitlement.getInstance(), 
            entitlement.getName(), 
            entitlement.getValue(), 
            entitlement.getAssigner(), 
            entitlement.getSourceObject(), 
            entitlement.getType(),
            entitlement.getAssignmentId());
       
       setAnnotation(entitlement.getAnnotation());
   }
    
   public AccountRequest toAccountRequest(AccountRequest.Operation acctReqOp, ProvisioningPlan.Operation attrReqOp) {
    
        AccountRequest request = new AccountRequest();
        request.setNativeIdentity(getNativeIdentity());
        request.setInstance(getInstance());
        request.setApplication(getApplicationName());
        request.setOperation(acctReqOp);
        
        GenericRequest attr = (isPermission()) ? new PermissionRequest() : new AttributeRequest();
        attr.setName(getName());
        attr.setValue(getValue());
        attr.setOperation(attrReqOp);
        if (getStartDate() != null) {
            attr.setAddDate(getStartDate());
        }
        if (getEndDate() != null) {
            attr.setRemoveDate(getEndDate());
        }
        request.add(attr);
        
        return request;
    }
   

    @XMLProperty
    public void setName(String name) {
        this.name = name;
    }

    /**
     * The name of the attribute that is assigned.
     */
    public String getName() {
        return name;
    }

    /**
     * Resolves the application object, first by trying by Id, then
     * if that fails to resolve anything, trying to find
     * it by name.
     * 
     * @param resolver Resolver
     * @return Application that is referenced by the assignment
     * 
     * @throws GeneralException
     */
    public Application resolveApplication(Resolver resolver) 
        throws GeneralException {
        
        Application app = null;        
        String applicationId = getApplicationId();
        if ( applicationId != null ) {
            app = resolver.getObjectById(Application.class, applicationId);
        } else {
            String applicationName = getApplicationName();
            if ( applicationName != null ) {
                app = resolver.getObjectByName(Application.class, applicationName);
            }
        }
        return app;
    }

    /**
     * Note that this object stores both the application
     * name and id. In the case of application renames,
     * the application name can be problematic.
     * 
     * If you want to resolve to a full application object
     * it is recommended that you call the resolveApplication
     * method that will try using the id then fall-back
     * to the application name.
     * 
     * @see #resolveApplication(Resolver)
     * 
     * @return Name of the Application
     */
    @XMLProperty
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    @XMLProperty
    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    @XMLProperty
    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    @XMLProperty
    public ManagedAttribute.Type getType() {
        return type;
    }

    public void setType(ManagedAttribute.Type type) {
        this.type = type;
    }
    
    /**
     * @return true if the assignment is type permission
     */
    public boolean isPermission() {
        return ( Util.nullSafeEq(type, ManagedAttribute.Type.Permission ) ) ? true : false;
    }
    
    @XMLProperty
    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    public void setValue(Object value) {
        this.value = value;
    }
    
    public Object getValue() {
        return value;
    }
    
    public String getStringValue() {
        String str = null;
        if ( value != null ) {
            //IIQSAW-1518: Keep consistent with IdentityEntitlement.getStringValue()
            if ( value instanceof String ) {
                str = (String)value;    
            } else {
                str = Util.listToCsv(getListValue());
            }
        }
        return str;
    }
    
    @SuppressWarnings("unchecked")
    public List<Object> getListValue() {
        List<Object> list = null;
        if ( value != null ) {
            list = Util.asList(value);
        }
        return list;        
    }
    
    @XMLProperty
    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    @XMLProperty
    public String getTargetCollector() {
        return targetCollector;
    }

    public void setTargetCollector(String targetCollector) {
        this.targetCollector = targetCollector;
    }

    /**
     * Make sure the assignment is valid and it has
     * a valid application and attribute name. 
     * 
     * If not valid remove it from the assigned list 
     * and prevent it from being provisioned.
     * 
     * @param resolver Resolver
     * @return True when the application and all schema attributes
     *         are valid.
     *         
     * @throws GeneralException
     */
    public boolean isValid(Resolver resolver) 
        throws GeneralException {
        
        Application app = resolveApplication(resolver);
        if ( app != null && getName() != null ) {
            Schema schema = app.getAccountSchema();
            // Can't tell the difference between a target and direct permission.  If this is a permission
            // and the app supports permissions or targets, we'll assume that this is valid.
            if ( schema != null ) {
                if (( schema.getAttributeDefinition(getName()) != null ) ||
                    ( schema.getIncludePermissions() && isPermission() ) ||
                    ( app.supportsFeature(Feature.UNSTRUCTURED_TARGETS) && this.isPermission() )) {
                    if (getStartDate() != null && getStartDate().after(new Date())) {
                        //Sunrise set. Treat as valid. May not have NativeIdentity yet because prov policy won't be presented
                        //until sunrise date happens.
                        return true;
                    }
                    if ( getNativeIdentity() != null && getValue() != null )
                        return true;
                }
            }
        }   
        return false;
    }
    
    /**
     * Compares all of the fields at the AttributeAssignment level 
     * excluding the annotation.
     * 
     * Use the XML serialized version when comparing since these
     * values can be Collections of things.
     *  
     * @param obj AttributeAssignment to compare
     *            
     * @return True when matches passed in assignment
     * 
     * @ignore
     * TODO: at some point probably need to consider start/end 
     * date when matching

     */
    public boolean matches(AttributeAssignment obj) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null ) {
            return false;
        }
        AttributeAssignment assignment = (AttributeAssignment)obj;
        if ( Util.nullSafeCompareTo(name, assignment.getName()) == 0  &&
             Util.nullSafeCompareTo(getStringValue(), obj.getStringValue()) == 0  &&
             Util.nullSafeCompareTo(instance, assignment.getInstance()) == 0 &&
             Util.nullSafeCompareTo(applicationId, assignment.getApplicationId()) == 0  )  {
            //Test nativeId now.
            if (Util.nullSafeEq(nativeIdentity, assignment.getNativeIdentity())) {
                return true;
            } else {
                //Fall back to assignmentId.
                if (Util.nullSafeEq(_assignmentId, assignment.getAssignmentId(), Util.isNullOrEmpty(nativeIdentity))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    //
    // Usual XML hackery for terseness in the common case
    //

    /**
     * @exclude
     * @deprecated use {@link #getValue()}
     */
    @Deprecated
    @XMLProperty(xmlname="value")
    public String getXmlValueAttribute() {
        String value = null;
        if (this.value instanceof String)
            value = (String)this.value;
        else if ( this.value instanceof Collection) {
            Collection col = (Collection)this.value;
            if (col.size() == 1) {
                // ugh, Collection doesn't have get(int)
                Object[] elements = col.toArray();
                Object el = elements[0];
                if (el instanceof String) 
                    value = (String)el;
            }
        }
        return value;
    }

    /**
     * @exclude
     * @deprecated use {@link #setValue(Object)}
     */
    @Deprecated
    public void setXmlValueAttribute(String s) {
        value = s;
    }

    /**
     * @exclude
     * @deprecated use {@link #getValue()}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    @XMLProperty(mode=SerializationMode.ELEMENT, xmlname="value")
    public Object getXmlValueElement() {
        Object value = null;
        String svalue = getXmlValueAttribute();
        if (svalue == null)
            value = value;
        return value;
    }

    /**
     * @exclude
     * @deprecated use {@link #setValue(Object)}
     */
    public void setXmlValueElement(Object o) {
        value = o;
    }    
}
