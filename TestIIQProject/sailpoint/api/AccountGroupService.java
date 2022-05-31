/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityEntitlement.AggregationState;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.Schema;
import sailpoint.service.LCMConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.group.AccountGroupDTO;
import sailpoint.web.messages.MessageKeys;

/**
 * A service class for dealing with AccountGroups.
 *
 * @author Kelly Grizzle, Dan Smith
 */
public class AccountGroupService {

    private SailPointContext context;

    private static final Log log = LogFactory.getLog(AccountGroupService.class);

    /**
     * Constructor.
     */
    public AccountGroupService(SailPointContext context) {
        this.context = context;
    }

    /**
     * Is the given attribute a group attribute on the given application.
     *
     * Note: This will only test for directly assignable group attributes. (attributes on the account schema)
     * It is possible to have a group attribute on a group schema instead of the account schema.
     *
     * @param  appName   The application on which the attribute lives.
     * @param  attrName     The name of the attribute to check.
     *
     * @return True if the given attribute on the given application is a "group"
     *         attribute, false otherwise.
     *
     */
    public boolean isGroupAttribute(String appName, String attrName) throws GeneralException {
        return isGroupAttribute(appName, attrName, Connector.TYPE_ACCOUNT);
    }

    /**
     * Is the given attribute a group attribute on the given application and schema.
     *
     * @param  appName   The application on which the attribute lives.
     * @param  attrName     The name of the attribute to check.
     * @param  schemaName The optional schema name, this method will look here if the group is not found in Account
     *                    schema.
     *
     * @return True if the given attribute on the given application is a "group"
     *         attribute, false otherwise.
     *
     */
    public boolean isGroupAttribute(String appName, String attrName, String schemaName) throws GeneralException {

        boolean isGroup = false;

        // This is a little hard to read because of all the null checks, but essentially what we are doing is looking
        // for the attribute first in the Account schema, and if it's not found there, look in the schema named
        // in schemaName.

        Application app = this.context.getObjectByName(Application.class, appName);
        if (app != null) {
            Schema schema = app.getSchema(Connector.TYPE_ACCOUNT);
            if (schema != null) {
                AttributeDefinition attr = schema.getAttributeDefinition(attrName);
                if (attr == null) {
                    schema = app.getSchema(schemaName);
                    if (schema != null) {
                        attr = schema.getAttributeDefinition(attrName);
                    }
                }
                if (attr != null) {
                    //If the AttributeDefinition has an objectType, we will assume it is a group.
                    isGroup = Util.isNotNullOrEmpty(attr.getSchemaObjectType());
                }
            }
        }

        return isGroup;
    }

    /**
     * Return the ManagedAttribute (if one exists) for a given group on an
     * application.
     *
     * @param  appName    The name of the Application on which the group lives.
     * @param  groupAttr  The name of the attribute on the Application that is
     *                    a "group" attribute (eg - memberOf).
     * @param  groupVal   The system name of the group (eg - cn=MyGroup,dc=blah).
     *
     * @return The ManagedAttribute for a given group on an application, or null if
     *         an ManagedAttribute does not exist with the given attributes.
     */
    public ManagedAttribute getAccountGroup(String appName, String groupAttr,
                                        String groupVal)
        throws GeneralException {

        return getAccountGroup(appName, groupAttr, groupVal, null);
    }

    public ManagedAttribute getAccountGroup(String appName, String groupAttr, String groupVal, String objectType)
        throws GeneralException {
        // jsl - this is similar to ManagedAttributer.getAll except it uses the
        // application name and it has ignoreCase around value.

        Filter f = Filter.and(Filter.eq("application.name", appName),
                Filter.ignoreCase(Filter.eq("value", groupVal)));
        QueryOptions qo = new QueryOptions();
        qo.add(f);
        if (Util.isNotNullOrEmpty(groupAttr)) {
            qo.add(Filter.ignoreCase(Filter.eq("attribute", groupAttr)));
        } else {
            qo.add(Filter.eq("type", objectType));
        }

        // setting this to true causes some funky class cast exceptions
        // figure this out for 5.2
        // Update 1/3/2010 : It seems this is related to hibernate bug: http://opensource.atlassian.com/projects/hibernate/browse/HHH-2463
        // The issue doesn't seem to occur when using mysql db.
        // Hibernate issue is fixed in version 3.5.5 or 3.6.1
        qo.setCacheResults(false);
        ManagedAttribute acctGroup = null;
        List<ManagedAttribute> groups = this.context.getObjects(ManagedAttribute.class, qo);
        if (groups.size() == 1) {
            acctGroup = groups.get(0);
        }
        else if (groups.size() > 1) { // duplicate found?
            if (log.isWarnEnabled())
                log.warn("Duplicate account group found for : " + appName + groupAttr + groupVal);
        }
        return acctGroup;
    }

    /**
     * Return the displayable name of the ManagedAttribute (if one exists)
     * for a given group on an application.
     *
     * @param  appName The name of the Application on which the group lives.
     * @param  attr    The name of the attribute on the Application that is
     *                      a "group" attribute (eg: memberOf).
     * @param  val     The system name of the group (eg: cn=MyGroup,dc=blah).
     *
     * @return The displayable name of the AccountGroup, or the attr value if
     *         an AccountGroup does not exist with the given attributes.
     */
    public String getGroupDisplayableName(String appName, String attr, String val)
        throws GeneralException {

        return Explanator.getDisplayValue(appName, attr, val);
    }

    /**
     * Return the displayable name(s) of the AccountGroup (if one exists)
     * for a given group on an application.  The incoming values might be
     * a single String or a List of Strings, depending on the attribute.
     *
     * @param  appName The name of the Application on which the group lives.
     * @param  attr    The name of the attribute on the Application that is
     *                      a "group" attribute (eg: memberOf).
     * @param  vals    The system name(s) of the group (eg: cn=MyGroup,dc=blah).
     *
     * @return A list of the  displayable names of the AccountGroup, or the attr
     *         values if an AccountGroup does not exist with the given attributes.
     */
    @SuppressWarnings("unchecked")
    public List<String> getGroupDisplayableNames(String appName, String attr, Object vals)
        throws GeneralException {

        // we wouldn't need this if <c:forEach> could recognize the object
        // being returned from Attributes as a List...
        List<String> displayableNames = new ArrayList<String>();
        if (vals == null) {
            displayableNames.add(""); // or should it be "(null)"?
        } else if (vals instanceof String || vals instanceof Boolean) {
            //IIQETN-4327 :- Adding boolean to the condition and changing to a more general way
            //of casting the value.
            String value = String.valueOf(vals);
            displayableNames.add(getGroupDisplayableName(appName, attr, value));
        } else if (vals instanceof List) {
            List<Object> values = (List<Object>)vals;
            for (Object valObj : values) {
                if(valObj instanceof String) {
                    String val = (String)valObj;
                    ManagedAttribute group = getAccountGroup(appName, attr, val);
                    if (group != null) {
                        displayableNames.add(group.getDisplayableName());
                    }
                    else {
                        displayableNames.add(val);
                    }
                } else {
                    if(log.isDebugEnabled()) {
                        log.debug("Coercing call to getGroupDisplayableNames for class of type " + valObj.getClass() + " to String");
                    }
                    displayableNames.add(valObj.toString());
                }
            }
        } else {
            throw new GeneralException("Invalid argument: vals is a " + vals.getClass());
        }

        return displayableNames;
    }



    /**
     * Query LinkExternalAttributes for this attribute and value
     * to compute the membership list.
     * 
     * This was deprecated in 6.0 in favor of IdentityEntitlement
     * connections.
     * 
     * @param app
     * @param attrName
     * @param groupIdentity
     * @return
     * @throws GeneralException
     */
    @Deprecated
    public Filter getGroupMemberFilter(Application app, String attrName,
                                       String groupIdentity)
        throws GeneralException {

        // jsl - shouldn't these be case insensitive for consistency with 
        Filter filter = Filter.and(Filter.join("id", "LinkExternalAttribute.objectId"),
                                   Filter.ignoreCase(Filter.eq("LinkExternalAttribute.attributeName", attrName)),
                                   Filter.ignoreCase(Filter.eq("LinkExternalAttribute.value", groupIdentity)),
                                   Filter.eq("application", app));
        return filter;
    }  // getGroupMemberFilter(Application, String, String)
    
    
    /**
     * Added in 6.0, retrieve a filter that can be used to query IdentityEntitlement
     * objects to find the members of group.
     * 
     * @param app
     * @param attrName
     * @param groupIdentity
     * @return
     * @throws GeneralException
     */
    public Filter getMembershipfilter(Application app, String attrName,
                                       String groupIdentity)
        throws GeneralException {

        Filter filter = Filter.and(Filter.eq("application", app),
                                   Filter.ignoreCase(Filter.eq("name", attrName)),
                                   Filter.ignoreCase(Filter.eq("value", groupIdentity)),
                                   Filter.eq("aggregationState", AggregationState.Connected));
        return filter;
    }  // getMembershipfilter(Application, String, String)

    /**
     * Return a list of members for a given account group. This method uses
     * extended multivalued attributes to resolve group membership if the
     * memberAttribute is not null on the account group.
     * 
     * Otherwise starting in 6.0, it uses the IdentityEntitlement relationship 
     * to compute the relationships.
     *
     * Note: Indirectly Assignable Acount groups will not have an attribute set.
     * 
     */
    public List<String> getMemberNames(ManagedAttribute group, String memberNameAttribute)
        throws GeneralException {

        List<String> memberNames = new ArrayList<String>();
        Application app = group.getApplication();
        String attrValue = group.getValue();
        String attrName = group.getAttribute();
        String groupName = group.getName();

        QueryOptions ops = new QueryOptions();

        ops.add(getMembershipfilter(app, attrName, attrValue));
        memberNames = resolveUsingEntitlements(ops, memberNameAttribute);

        if ( attrName == null ) {
            Message msg = new Message(Message.Type.Warn,
                MessageKeys.ERR_GROUP_CANT_FIND_MEMBERATTR, groupName, app.getName());
            throw new GeneralException(msg);
        }
        return memberNames;
    }
    
    /**
     * This was deprecated in 6.0, the preferred way is to 
     * use the IdentityEntitlement relationship to compute
     * membership. 
     * 
     * @param ops
     * @param memberNameAttribute
     * @return
     * @throws GeneralException
     */
    @Deprecated
    private List<String> resolveUsingLinks(QueryOptions ops, String memberNameAttribute)
        throws GeneralException {
        
        List<String> memberNames = new ArrayList<String>();
        Iterator<Link> it = context.search(Link.class, ops);
        while ( it.hasNext() ) {
            Link link = it.next();
            String memberName = null;
            if ( ( memberNameAttribute == null ) || ( "identityName".equals(memberNameAttribute) ) ) {
                Identity id = link.getIdentity();
                if ( id != null ) {
                    memberName = id.getName();
                } else {
                    if (log.isWarnEnabled())
                        log.warn("Link [" + link.getNativeIdentity() + "] has no identity.");
                    
                    memberName = link.getNativeIdentity();
                }
            } else  {
                String val = (String)link.getAttribute(memberNameAttribute);
                if ( val != null ) {
                    memberName = val;
                } else {
                    if (log.isWarnEnabled())
                        log.warn("Attribute [" + val + "] was not found on the link." +
                                 "Defaulting to the native identity.");
                    
                    memberName = link.getNativeIdentity();
                }
            }
            memberNames.add(memberName);
        }
        return memberNames;
    }

    /**
     * Get the query options for IdentityEntitlements to limit to the given ManagedAttribute
     * @return QueryOptions that will find the connected Links associated with this AccountGroup
     */
    public QueryOptions getMembersQueryOptions(ManagedAttribute group) throws GeneralException {
        return getMembersQueryOptions(group, true);
    }

    /**
    *
    * @return QueryOptions that will find the Links associated with this AccountGroup
    * @ignore
    * jsl - huh?  What class is this filter applied to, it certainly can't be Link
    * because Links don't have name and value properties.  It appears to be used only
    * by getMemberCount below andAccountGroupBean  which apply it to IdentityEntitlement.class.
    */
   public QueryOptions getMembersQueryOptions(ManagedAttribute group, boolean connectedOnly) throws GeneralException {
       QueryOptions qo = new QueryOptions();

       if ( group == null ) return null;

       List<Filter> filters = new ArrayList<Filter>();
       String type = group.getType();
       Application app = group.getApplication();
       if ( app == null )
           throw new GeneralException("Entitlement's application is null, unable to query membership.");
       filters.add(Filter.eq("application", app));

       //Note, AccountGroups that are only indirectly assigned will not have an attribute.
       String attributeOrTarget = group.getAttribute();       
       if ( attributeOrTarget == null )
           throw new GeneralException("Entitlement's attribute is null, unable to query membership.");
       filters.add(Filter.ignoreCase(Filter.eq("name", attributeOrTarget)));
       
       if (!ManagedAttribute.Type.Permission.name().equals(type)) {
           String value = group.getValue();
           if ( value == null )
               throw new GeneralException("Entitlement's value identity is null, unable to query membership.");
           filters.add(Filter.ignoreCase(Filter.eq("value", value)));
       }

       if (connectedOnly) {
           filters.add(Filter.eq("aggregationState", AggregationState.Connected));
       }
       
       Filter filter = Filter.and(filters);
       qo.add(filter);

       // SQLServer returns a non-unique list without this
       qo.setDistinct(true);

       return qo;
   }  // getMembersQueryOptions()
   
   public int getMemberCount(ManagedAttribute entitlement) {
       int size = 0;
       try {
           if (entitlement != null) {
               QueryOptions qo = getMembersQueryOptions(entitlement);
               if (qo != null) {
                   Iterator<Object[]> countResultIterator = context.search(IdentityEntitlement.class, qo, "count(distinct identity)");
                   if (countResultIterator != null && countResultIterator.hasNext()) {
                       Object[] countResult = countResultIterator.next();
                       if (countResult != null && countResult.length == 1) {
                           size = Util.otoi(countResult[0]);
                       }
                   }
               }                
           }
       } catch(GeneralException e) {
           log.error(e);
           size = 0;
       }
       
       return size;
   }
   
   public static WorkflowSession launchWorkflow(AccountGroupDTO groupDTO, ProvisioningPlan plan, BaseBean launchingPage) 
       throws GeneralException {
       AccountGroupLifecycler lifecycler = new AccountGroupLifecycler(launchingPage.getContext(), launchingPage.getSessionScope());
       String workflowName = (String) Configuration.getSystemConfig().get(Configuration.WORKFLOW_MANAGED_ATTRIBUTE);
       WorkflowSession session = lifecycler.launchUpdate(launchingPage.getLoggedInUserName(), groupDTO, plan, workflowName);
       
       return session;
   }
   
   /**
    * Determine whether or not the user with the given rights and capabilities can provision ManagedAttributes
    * @param capabilities List of Capabilities that the current user has 
    * @param rights Collection of rights that the current user has
    * @return true if ManagedAttribute provisioning is permitted for the user with the specified capabilities and rights; false otherwise
    */
   public static boolean isProvisioningEnabled(List<Capability> capabilities, Collection<String> rights) {
       Set<String> rightSet = new HashSet<String>(rights);
       boolean hasProvisioningRight = rightSet.contains(SPRight.ManagedAttributeProvisioningAdministrator) || Capability.hasSystemAdministrator(capabilities);
       return hasProvisioningRight && LCMConfigService.isLCMEnabled();

   }
   
   /**
    * Resolve the memebership based on an Identities connected Entitlements.
    * 
    * @param ops
    * @param memberNameAttribute
    * @return
    * @throws GeneralException
    */
   private List<String> resolveUsingEntitlements(QueryOptions ops, String memberNameAttribute)
       throws GeneralException {
       
       List<String> memberNames = new ArrayList<String>();
       Iterator<IdentityEntitlement> it = context.search(IdentityEntitlement.class, ops);
       while ( it.hasNext() ) {
           IdentityEntitlement ent = it.next();
           String memberName = null;
           if ( ( memberNameAttribute == null ) || ( "identityName".equals(memberNameAttribute) ) ) {
               Identity id = ent.getIdentity();
               if ( id != null ) {
                   memberName = id.getName();
               } else {
                   if (log.isWarnEnabled())
                       log.warn("ent [" + ent.getNativeIdentity() + "] has no identity.");
                   
                   memberName = ent.getNativeIdentity();
               }
           } else  {
               String val = (String)ent.getAttribute(memberNameAttribute);
               if ( val != null ) {
                   memberName = val;
               } else {
                   if (log.isWarnEnabled())
                       log.warn("Attribute [" + val + "] was not found on the ent. " + 
                                "Defaulting to the native identity.");
                   
                   memberName = ent.getNativeIdentity();
               }
           }
           memberNames.add(memberName);
       }
       return memberNames;
   }
}
