/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 * A class to help in finding Link and Identity Objects.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.CorrelationConfig;
import sailpoint.object.DirectAssignment;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.search.ExternalAttributeFilterBuilder;
import sailpoint.search.ResourceObjectMatcher;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

public class Correlator {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Correlator.class);

    // correlation rule return values
    public static final String RULE_RETURN_CORRELATION_ATTRIBUTE = "correlationAttribute";
    public static final String RULE_RETURN_APPLICATIONS = "applications";

    // Identity correlation
    public static final String RULE_RETURN_IDENTITY = "identity";
    public static final String RULE_RETURN_IDENTITY_NAME = "identityName";
    public static final String RULE_RETURN_IDENTITY_ATTRIBUTE = "identityAttributeName";
    public static final String RULE_RETURN_IDENTITY_ATTRIBUTE_VALUE = "identityAttributeValue";

    // Link correlation
    public static final String RULE_RETURN_LINK_INSTANCE = "linkInstance";
    public static final String RULE_RETURN_LINK_IDENTITY = "linkIdentity";
    public static final String RULE_RETURN_LINK_DISPLAYNAME = "linkDisplayName";
    public static final String RULE_RETURN_LINK_ATTRIBUTE = "linkAttributeName";
    public static final String RULE_RETURN_LINK_ATTRIBUTE_VALUE = "linkAttributeValue";

    // allowed in both link and identity correlation
    public static final String MULTI_OPERATOR = "multiValuedOperator";
    public static final String MULTI_MATCHMODE = "multiValuedMatchMode";

    // AccountGroup correlation
    public static final String RULE_RETURN_GROUP = "group";
    public static final String RULE_RETURN_GROUP_ATTRIBUTE = "groupAttributeName";
    public static final String RULE_RETURN_GROUP_ATTRIBUTE_VALUE = "groupAttributeValue";
    public static final String RULE_RETURN_GROUP_TYPE = "groupType";

    // attribute names used in common queries
    private static final String APP_ATTRIBUTE = "application";
    private static final String INSTANCE_ATTRIBUTE = "instance";
    private static final String NATIVE_IDENTITY_ATTRIBUTE = "nativeIdentity";
    private static final String DISPLAY_NAME_ATTRIBUTE = "displayName";
    private static final String UUID_ATTRIBUTE = "uuid";

    /**
     * Context given to us by the creator. For correlation queries.
     */
    SailPointContext _context;

    /**
     * Description set by the methods when looking up identities
     * so it can give some detail back to the aggregator about
     * what the rule returned to help us correlate.
     */
    String _correlationAttribute;

    /**
     * Flag indiciating that we should lock what we correlate.
     * This used to be the presence of a non-null lock name
     * but that was removed in 7.3 in favor of universally using
     * the thread-local generated lock name.
     */
    boolean _doLocking;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    private Correlator() {
        _context = null;
    }

    public Correlator(SailPointContext context) {
        this();
        setContext(context);
    }

    /**
     * If we ever have to cache Hibernate objects, this is the
     * place to pre-load them.
     */
    public void prepare() {
    }

    public void setDoLocking(boolean b) {
        _doLocking = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Field setters/getters
    //
    //////////////////////////////////////////////////////////////////////

    public void setContext(SailPointContext context) {
        _context = context;
    }

    /**
     * String left around after a rule is executed for identity
     * correlation that gives a hint back to how we correlated
     * to the identity.
     */
    public String getCorrelationAttribute() {
        return _correlationAttribute;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Link Correlation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run each of the given rules attempting to correlate
     * back to one of our Link objects.  Bail once we find
     * a match.
     */
    public SailPointObject runLinkCorrelationRules(Application app,
                                          Map<String,Object> inputs,
                                          List<Rule> rules)
        throws GeneralException{

        SailPointObject linkOrIdentity = null;
        if (rules != null) {
            for (Rule rule : rules) {
                Object result = _context.runRule(rule, inputs);
                if  ( result != null ) {
                    linkOrIdentity = processLinkRuleResult(app, result);
                    // bail after we get a non-null value
                    if ( linkOrIdentity != null ) break;
                }
            }
        }
        return linkOrIdentity;
    }

    /**
     *
     * Every rule that is executed for correlation must return
     * a Map object which can contain any of the following attributes
     * and return types.
     * <table>
     *   <tr>
     *      <td><b>KeyName</b></td> <td><b>Type</b></td> <td><b>Description</b></td>
     *   </th>
     *   <tr>
     *     <td>linkIdentity</td><td>String</td><td>A string that represents the nativeIdentity attribute on a Link object.</td>
     *   </tr>
     *   <tr>
     *     <td>linkDisplayName</td><td>String</td><td>A string that represents the displayName attribute on the Link object.</td>
     *   </tr>
     *   <tr>
     *     <td>The next two keys are used together and both values must be supplied.</td>
     *     <td>linkAttributeName</td><td>String</td><td>The attributes name</td>
     *     <td>linkAttributeValue</td><td>String</td><td>The attributes value.</td>
     *   </tr>
     * </table>
     */
    protected SailPointObject processLinkRuleResult(Application app, Object result)
        throws GeneralException {

        Link link = null;

        if ( result instanceof Map ) {
            Map map = (Map)result;

            String instance = Util.getString(map, RULE_RETURN_LINK_INSTANCE);

            Object linkId = map.get(RULE_RETURN_LINK_IDENTITY);
            if ( linkId != null ) {
                if ( linkId instanceof String ) {
                    String id = (String)linkId;
                    link = findLinkByNativeIdentity(app, instance, id);
                    return link;
                }
            }

            Object linkName = map.get(RULE_RETURN_LINK_DISPLAYNAME);
            if ( linkName != null ) {
                if ( linkName instanceof String ) {
                    String name = (String)linkName;
                    link = findLinkByDisplayName(app, instance, name);
                    return link;
                }
            }

            Object attrName = map.get(RULE_RETURN_LINK_ATTRIBUTE);
            Object attrValue = map.get(RULE_RETURN_LINK_ATTRIBUTE_VALUE);
            if ( ( attrName != null ) &&
                 ( attrValue != null ) ) {
                if ( attrName instanceof String )  {
                    String name = (String)attrName;
                    ObjectConfig objConfig = Link.getObjectConfig();
                    ObjectAttribute attr = null;
                    if ( objConfig != null ) {
                        Map<String,ObjectAttribute> attrMap = objConfig.getObjectAttributeMap();
                        attr = attrMap.get(name);
                    }

                    if ( ( attr != null ) && ( attr.isMulti() ) ) {
                        List value = null;
                        if ( attrValue instanceof List )  {
                            value = (List)attrValue;
                        } else
                        if ( attrValue instanceof String )  {
                            value = Util.asList(attrValue);
                        }
                        Filter filter = buildMultiValuedAttributeFilter(Link.class, getOperator(map),
                                                                        getMatchMode(map), app, name, value);
                        if ( filter != null ) {
                            link = findOneObject(Link.class, filter);
                        }
                    } else
                    if ( attrValue instanceof String )  {
                        String value = (String)attrValue;
                        link = findLinkByAttribute(app, instance, name, value);
                    }
                    return link;
                }
            }

            // Give a hook here, just in case...
            Identity identity = processIdentityRuleResult(result);
            if ( identity != null ) {
                return identity;
            }

        } else if( result instanceof Link )  {
            link = ( Link ) result;
        } else {
            throw new GeneralException("Correlation rule did not"
                + " return the correct object type.  All correlation"
                + " rules must to return a Map object.");
        }
        return link;
    }

    /**
     * Attempt to find a Link object given the native identity. The
     * correlator will attempt to find a Link wiht the given native identity
     * relative to the application supplied.
     */
    public Link findLinkByNativeIdentity(Application app, String instance,
                                         String nativeIdentity)
        throws GeneralException {
        return findLink(app, instance, nativeIdentity, null);
    }

    /**
     * Attempt to find a Link object given the link's displayName. The
     * correlator will attempt to find a Link wiht the given native identity
     * relative to the application supplied.
     */
    public Link findLinkByDisplayName(Application app, String instance,
                                      String displayName)
        throws GeneralException {
        return findLink(app, instance, null, displayName);
    }

    /**
     * Attempt to find a Link object given the link's identity and/or
     * displayName.
     * If just byNativeIdentity is non-null query Link by "nativeIdentity"
     * If just byDisplayName is non-null query Link by "displayName"
     * If both are non-null, preform an AND of both conditions
     */
    public Link findLink(Application app, String instance,
                         String byNativeIdentity,  String byDisplayName)
        throws GeneralException {

        if ( ( byNativeIdentity == null ) && ( byDisplayName == null ) ) {
            return null;
        }

        Filter filter = null;

        if ( instance != null ) {
            filter = Filter.eq(INSTANCE_ATTRIBUTE, instance);
        }

        if ( byNativeIdentity != null ) {
            Filter nativeIdFilter = Filter.ignoreCase(Filter.eq(NATIVE_IDENTITY_ATTRIBUTE, byNativeIdentity));
            if (filter == null)
                filter = nativeIdFilter;
            else
                filter = Filter.and(filter, nativeIdFilter);
        }

        if ( byDisplayName != null ) {
            Filter displayNameFilter = Filter.eq(DISPLAY_NAME_ATTRIBUTE, byDisplayName);
            if ( filter == null ) {
                filter = displayNameFilter;
            } else {
                filter = Filter.and(filter, displayNameFilter);
            }
        }

        // !! The Application we're using here may no longer be in the
        // Hibernate session, is this an issue or is Hibernate smart
        // enough just to get the id out of it?
        filter = addAppFilter(filter, app);

        return findOneObject(Link.class, filter);
    }

    /**
     * Use the extended attribute facility to find a Link based on
     * a named Link attribute and value.
     *
     * Looks the attribute name up from the account schema and
     * check to see which correlation key the attribute is being
     * stored under.  Then using the correlation key number build
     * a filter that will query the correct key value.
     *
     * This method will throw an exception if the attribute name or
     * value is passed in null. It will also throw an exception if
     * the attribute is not marked as a correlation key in the account
     * schema.
     *
     */
    public Link findLinkByAttribute(Application app,
                                    String instance,
                                    String attrName,
                                    String attrValue)
        throws GeneralException {

        Filter filter =  buildExtendedAttributeFilter(app,Connector.TYPE_ACCOUNT,
                                                      attrName,attrValue);

        if (instance != null) {
            Filter insFilter = Filter.eq(INSTANCE_ATTRIBUTE, instance);
            if (filter == null)
                filter = insFilter;
            else
                filter = Filter.and(insFilter, filter);
        }

        return findOneObject(Link.class, filter);

    }

    /**
     * Find a line using the attributeValue as either the native identiy
     * string or the displayName.
     */
    public Link findLinkByIdentityOrDisplayName(Application app,
                                                String instance,
                                                String attrValue)
        throws GeneralException {

        Filter identityFilter = Filter.eq(NATIVE_IDENTITY_ATTRIBUTE, attrValue);
        Filter displayNameFilter = Filter.eq(DISPLAY_NAME_ATTRIBUTE, attrValue);

        Filter filter = Filter.or(identityFilter, displayNameFilter);
        filter = addAppFilter(filter, app);

        if (instance != null) {
            Filter insFilter = Filter.eq(INSTANCE_ATTRIBUTE, instance);
            if (filter == null)
                filter = insFilter;
            else
                filter = Filter.and(insFilter, filter);
        }

        return findOneObject(Link.class, filter);
    }

    /**
     * Find a link by uuid.
     */
    public Link findLinkByUuid(Application app, String instance, String uuid)
        throws GeneralException {

        Link link = null;
        if (app != null && uuid != null) {

            Filter filter = null;
            if (instance != null)
                filter = Filter.eq(INSTANCE_ATTRIBUTE, instance);

            // not sure if these should be case insensitive, the index currently isn't
            Filter uuidFilter = Filter.eq(UUID_ATTRIBUTE, uuid);
            if ( filter == null )
                filter = uuidFilter;
            else
                filter = Filter.and(filter, uuidFilter);

            filter = addAppFilter(filter, app);

            link = findOneObject(Link.class, filter);
        }
        return link;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Correlation
    //
    //////////////////////////////////////////////////////////////////////


    /**
     * Use the applications correaltion config to find the identity
     * assignment.
     *
     * There are two types of stored configuration.
     *
     * 1) Condition based correlation
     *
     *    Check one or more conditions expressed as LeafFilter obejects
     *    to match incomming accounts.  If there is a match of the
     *    conditions ( anded together ) then assign the account to
     *    the configured Identity object.
     *
     *    The condition is expressed as a Filter where the Filter's
     *    property is the name of the account attribute and the
     *    value is the String version of the attribute's value that
     *    will be used to match against.
     *
     *    These "Static" type definitions need to be processed
     *    first before the attribugte based assignments.
     *
     * 2) Attribute based correlation
     *
     *    Correlate based on matching account attributes with
     *    identity attributes. This will be the typicall case
     *    and is also expressed as a LeafFilter object.
     *
     *    The filters will be tryed in order until a single
     *    match is found or the list is exhausted.
     *
     *    The filter's property maps to the name of the identity
     *    attribute and the value (Stringified) is the name of
     *    the application object that should be used when
     *    resolving a value ( from the ResourceObject ) that will
     *    be used during the matching.
     */
    public Identity correlateIdentity(CorrelationConfig config,
                                      ResourceObject account)
        throws GeneralException{

        if ( account == null ) {
            log.warn("Null account encounterred during correlation.");
            return null;
        }

        if ( config != null ) {
            if ( log.isDebugEnabled() ) 
                 log.debug("CorrelationConfig XML: " + config.toXml());
            
            List<DirectAssignment> directs = config.getDirectAssignments();
            if ( Util.size(directs) > 0 ) {
                for ( DirectAssignment direct : directs ) {
                    List<Filter> _filters = direct.getFilters();
                    if ( Util.size(_filters) == 0 ) {
                        log.warn("Correlation: Direct assignment included a null fillter list.");
                        continue;
                    }
                    Filter andedFilter = Filter.and(_filters);
                    ResourceObjectMatcher matcher =  new ResourceObjectMatcher(andedFilter);
                    if ( matcher.matches(account) ) {
                        Identity id = direct.getIdentity();
                        if ( id != null ) {
                            _correlationAttribute = "Condition Based";
                            // can't trust the reference from the config object
                            // and Identitizer normally requires that the returned object
                            // be locked
                            if (_doLocking)
                                return ObjectUtil.lockIdentityById(_context, id.getId());
                            else
                                return _context.getObjectById(Identity.class, id.getId());
                        }
                    }
                }
            }
            List<Filter> dynamics = config.getAttributeAssignments();
            if ( Util.size(dynamics) > 0 ) {
                ObjectConfig identityConfig = Identity.getObjectConfig();
                for ( Filter dynamic : dynamics ) {
                    LeafFilter filter = (LeafFilter)dynamic;
                    if ( filter != null ) {
                        Identity id = executeDynamic(filter, account, identityConfig);
                        if ( id != null )
                            return id;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Using the store filter map it back to an IdentityAttribute from
     * the object config and build the appropriate query.
     */
    private Identity executeDynamic(LeafFilter filter, ResourceObject account, ObjectConfig identityConfig)
        throws GeneralException {

        String applicationAttribute = Util.getString((String)filter.getValue());
        if ( applicationAttribute == null ) {
            log.warn("CorrelationConfig has a null application Attribute.");
            return null;
        }

        String identityAttributeName = filter.getProperty();
        if ( log.isDebugEnabled() ) 
            log.debug("Trying to correlate on identity attribute [" + identityAttributeName + 
                      "] and application attribute [" + applicationAttribute + "]");
        
        ObjectAttribute oattr = identityConfig.getObjectAttribute(identityAttributeName);
        String resolvedValue = Util.getString(account.getStringAttribute(applicationAttribute));
        if ( ( resolvedValue == null ) || ( oattr == null ) ) {
            return null;
        }
        
        if ( log.isDebugEnabled() ) 
            log.debug("Attribute [" + applicationAttribute + "] resolved to [" +
                      resolvedValue + "]");
        

        Filter identityFilter = null;
        if ( oattr.isMulti() ) {
            List<String> values = Util.asList(resolvedValue);
            identityFilter = ExternalAttributeFilterBuilder.buildAndFilter(
                                 ExternalAttributeFilterBuilder.IDENTITY_EXTERNAL,
                                 ExternalAttributeFilterBuilder.IDENTITY_JOIN,
                                 identityAttributeName,
                                 values,
                                 filter.getOperation().toString());
        } else {
            identityFilter = Filter.eq(identityAttributeName, resolvedValue);
        }

        QueryOptions ops = getQueryOptions();
        ops.add(identityFilter);
        int count = _context.countObjects(Identity.class, ops);
        if ( log.isDebugEnabled() ) 
            log.debug("Found [" + count + "] matches.");
        
        if ( count == 1 ) {
            Identity id = findOneObject(Identity.class, identityFilter);
            if ( id != null ) {
                _correlationAttribute = applicationAttribute + " = " + resolvedValue;
            }
            return id;
        }
        return null;
    }

    /**
     * Run each of the given rules attempting to correlate
     * back to one of our Link objects.  Bail once we find
     * a match.
     */
    public Identity runIdentityCorrelationRules(Map<String,Object> inputs,
                                                List<Rule> rules)

        throws GeneralException{

        Identity identity = null;
        _correlationAttribute = null;
        if (rules != null) {
            for (Rule rule : rules) {
                Object result = _context.runRule(rule, inputs);
                if  ( result != null ) {
                    identity = processIdentityRuleResult(result);
                    // bail after we get a non-null value
                    break;
                }
            }
        }
        return identity;
    }

    /**
     *
     * Every rule that is executed for correlation must return
     * a Map object which can contain any of the following attributes
     * and return types.
     * <table>
     *   <tr>
     *      <td><b>KeyName</b></td> <td><b>Type</b></td> <td><b>Discription</b></td>
     *   </th>
     *   <tr>
     *     <td>identity</td><td>Identity</td><td>An identity object.</td>
     *   </tr>
     *   <tr>
     *     <td>identityName</td><td>String</td><td>A string that represents the attribute of that identity.</td>
     *   </tr>
     *   <tr>
     *     <td>The next two keys are used together and both values must be supplied.</td>
     *   </tr>
     *   <tr>
     *     <td>identityAttributeName</td><td>String</td><td>The attributes name.</td>
     *   </tr>
     *   <tr>
     *     <td>identityAttributeValue</td><td>String</td><td>The attributes value.</td>
     *   </tr>
     * </table>
     */
    protected Identity processIdentityRuleResult(Object result)
        throws GeneralException {

        Identity identity = null;
        if ( result instanceof Map ) {
            Map map = (Map)result;
            if ( log.isDebugEnabled()) 
                log.debug("Correlation Rule result: " +
                           XMLObjectFactory.getInstance().toXml(map));
            
            Object attrName = map.get(RULE_RETURN_IDENTITY_ATTRIBUTE);
            Object attrValue = map.get(RULE_RETURN_IDENTITY_ATTRIBUTE_VALUE);
            if ( ( attrName != null ) &&
                 ( attrValue != null ) ) {
                if ( attrName instanceof String ) {
                    String name = (String)attrName;
                    ObjectConfig objConfig = Identity.getObjectConfig();
                    ObjectAttribute attr = null;
                    if ( objConfig != null ) {
                        Map<String,ObjectAttribute> attrMap = objConfig.getObjectAttributeMap();
                        attr = attrMap.get(name);
                    }
                    if ( ( attr != null ) && ( attr.isMulti() ) ) {
                        List value = null;
                        if ( attrValue instanceof List )  {
                            value = (List)attrValue;
                        } else
                        if ( attrValue instanceof String )  {
                            value = Util.asList(attrValue);
                        }
                        Filter filter = buildMultiValuedAttributeFilter(Identity.class, getOperator(map),
                                                                        getMatchMode(map), null, name, value);
                        if ( filter != null ) {
                            identity= findOneObject(Identity.class, filter);
                        }
                    } else
                    if ( attrValue instanceof String )  {
                        String value = (String)attrValue;
                        identity = findIdentityByAttribute(name, value);
                        _correlationAttribute = name + " = " + value;
                    }
                }
            }

            Object identityName = map.get(RULE_RETURN_IDENTITY_NAME);
            if ( identityName != null ) {
                if ( identityName instanceof String ) {
                    String name = (String)identityName;
                    // in order to be case-insensitive  we have to
                    // make sure we do a query on the name and not
                    // just attempt to bind to the name given to use
                    // by the rule.
                    Filter nameFilter = Filter.eq("name", name);
                    identity = this.findOneObject(Identity.class, nameFilter);
                    _correlationAttribute = "identityName = " + name;
                }
            }

            Object ident = map.get(RULE_RETURN_IDENTITY);
            if ( ident != null ) {
                if ( ident instanceof Identity ) {
                    identity = (Identity)ident;
                    // Don't trust the identity object -- re-fetch it to assure
                    // its in the hibernate cache and locked. see bug#2026 for details
                    if (_doLocking) {
                        String identifier = identity.getId();
                        if (identifier == null)
                            identifier = identity.getName();
                        identity = ObjectUtil.lockIdentity(_context, identifier);
                    }
                    if (identity != null)
                        _correlationAttribute = "identity = " + identity.getName();
                }
            }
            // check to see if the rule left us a crumb
            String ruleDescription = (String)map.get(RULE_RETURN_CORRELATION_ATTRIBUTE);
            if ( ruleDescription != null ) {
                _correlationAttribute = ruleDescription;
            }

        } else {
            throw new GeneralException("Correlation rule did not"
                + " return the correct object type.  All correlation"
                + " rules must to return a Map object.");
        }

        if ( log.isDebugEnabled()) {
            if ( identity != null ) {
                log.debug("returning identity: " +
                           XMLObjectFactory.getInstance().toXml(identity));
            } else {
                log.debug("Identity null, Not correlated.");
            }
        }
        return identity;
    }

    /**
     * This ends up using the extended attribute facility to find a
     * Identity based on an identity attribute and value.
     */
    public Identity findIdentityByAttribute(String attrName,
                                            String attrValue)
        throws GeneralException {

        return findOneObject(Identity.class, Filter.eq(attrName,attrValue));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // AccountGroup Correlation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run each of the given rules attempting to correlate
     * back to one of our Link objects.  Bail once we find
     * a match.
     */
    public SailPointObject runTargetCorrelationRule(List<Application> apps,
                                                    Map<String,Object> inputs,
                                                    Rule rule)
        throws GeneralException {

        SailPointObject groupOrLink = null;
        if (rule != null) {
            Object result = _context.runRule(rule, inputs);
            if  ( result != null ) {
                groupOrLink = processTargetRuleResult(apps, result);
            }
        }
        return groupOrLink;
    }

    /**
     *
     * Every rule that is executed for correlation must return
     * a Map object which can contain any of the following attributes
     * and return types.
     * <table>
     *   <th>
     *      <td><b>KeyName</b></td> <td><b>Type</b></td> <td><b>Description</b></td>
     *   </th>
     *   <tr>
     *     <td>accountGroup</td><td>AccountGroup</td><td>An Account Group</td>
     *   </tr>
     *   <tr>
     *     <td>applications</td><td>Application</td><td>A Collection of filtered down Applications</td>
     *   </tr>
     *   <tr>
     *     <td>The next two keys are used together and both values must be supplied.</td>
     *   </tr>
     *   <tr>
     *     <td>groupAttributeName</td><td>String</td><td>The attributes name.</td>
     *   </tr>
     *   <tr>
     *     <td>groupAttributeValue</td><td>String</td><td>The attributes value.</td>
     *   </tr>
     *   <tr>
     *     <td>groupType</td><td>String</td><td>The objectType of the group</td>
     *   </tr>
     * </table>
     */
    protected SailPointObject processTargetRuleResult(List<Application> apps, Object result)
        throws GeneralException {

        List<Application> appCandidates = apps;
        //If rule returned a list of applications, use these (rule can have some context as to what app to use)
        if (result instanceof Map) {
            Object o = ((Map)result).get(RULE_RETURN_APPLICATIONS);
            if (o != null) {
                appCandidates = Util.asList(o);
            }
        }

        for (Application app : Util.safeIterable(appCandidates)) {
            // First see if we got a link back
            SailPointObject object = (SailPointObject)processLinkRuleResult(app,result);
            if ( object != null ) {
                return object;
            }

            // TODO: Do we need to support instances of a template app here?
            // In theory each instance could have a different set of
            // account groups, though it could be better to model account groups
            // as a merger of all groups in the template?

            if ( result instanceof Map ) {
                Map map = (Map)result;
                if ( log.isDebugEnabled()) {
                    log.debug("Correlation Rule result: " +
                               XMLObjectFactory.getInstance().toXml(map));
                }

                Object attrName = map.get(RULE_RETURN_GROUP_ATTRIBUTE);
                Object attrValue = map.get(RULE_RETURN_GROUP_ATTRIBUTE_VALUE);
                Object groupType = map.get(RULE_RETURN_GROUP_TYPE);
                if ( ( attrName != null ) &&
                     ( attrValue != null ) ) {
                    if ( ( attrName instanceof String ) &&
                         ( attrValue instanceof String ) ) {
                        String name = (String)attrName;
                        String value = (String)attrValue;
                        String type = (String)groupType;

                        // Allow "nativeIdentity" to indicate the identity of the object
                        if ( Util.nullSafeCompareTo(NATIVE_IDENTITY_ATTRIBUTE, name) == 0 ) {
                            // Technically these don't have to be unique by "value" but in practice
                            // they are unique..If there is more then one group type
                            // callers must use RULE_RETURN_GROUP
                            Schema groupSchema = Util.isNotNullOrEmpty(type) ? app.getSchema(type) : app.getGroupSchema();
                            if ( groupSchema != null ) {

                                List<Filter> filters = new ArrayList<Filter>();
                                filters.add(Filter.eq("application", app));
                                filters.add(Filter.ignoreCase(Filter.eq("value", value)));
                                if (Util.isNotNullOrEmpty(type)) {
                                    filters.add(Filter.eq("type", groupType));
                                }

                                QueryOptions ops = getQueryOptions();
                                ops.add(Filter.and(filters));
                                int count = _context.countObjects(ManagedAttribute.class, ops);
                                if ( count == 0 ) {
                                   if ( log.isDebugEnabled())
                                       log.debug("Unable to find any objects during nativeIdentity matching, using these options."+ ops);
                                } else
                                if ( count > 1) {
                                    if ( log.isDebugEnabled())
                                        log.debug("Found more then one object during nativeIdentity matching, using these options."+ ops + "numFound: " + count);
                                } else
                                if ( count == 1 )  {
                                    object = _context.getUniqueObject(ManagedAttribute.class, Filter.and(filters));
                                    if ( object == null ) {
                                        if ( log.isDebugEnabled()) {
                                            log.debug("ManageAttribute was null during getUniqueObject using these filters" + Filter.and(filters));
                                        }
                                    }
                                }
                            }
                        } else {
                            object = findAccountGroupByAttribute(app,name,value, Util.isNotNullOrEmpty(type) ? type : Connector.TYPE_GROUP);
                        }
                    }
                }

                Object group = map.get(RULE_RETURN_GROUP);
                if ( group != null ) {
                    if ( group instanceof ManagedAttribute ) {
                        object = (ManagedAttribute)group;
                    }
                }

            } else {
                throw new GeneralException("Correlation rule did not"
                    + " return the correct object type.  All correlation"
                    + " rules must to return a Map object.");
            }

            if ( object != null ) {
                if ( log.isDebugEnabled()) {
                    log.debug("Returning object: " +
                            XMLObjectFactory.getInstance().toXml(object));
                }
                return object;
            }

        }

        if ( log.isDebugEnabled()) {
            log.debug("Object null, target was not correlated.");
        }
        //Could not correlate for any app
        return null;
    }

    public ManagedAttribute findAccountGroupByAttribute(Application app,
                                                        String attrName,
                                                        String attrValue)
        throws GeneralException {

        return findAccountGroupByAttribute(app, attrName, attrValue, Connector.TYPE_GROUP);
    }

    public ManagedAttribute findAccountGroupByAttribute(Application app,
                                                        String attrName,
                                                        String attrValue,
                                                        String objectType)
        throws GeneralException {

        Filter filter =  buildExtendedAttributeFilter(app,objectType,
                attrName,attrValue);
        return findOneObject(ManagedAttribute.class, filter);

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Util
    //
    //////////////////////////////////////////////////////////////////////

    private String getOperator(Map map) {
        String op = (String)map.get(MULTI_OPERATOR);
        if ( op == null ) {
           op = "AND";
        }
        return op;
    }

    private String getMatchMode(Map map) {
        String mode = (String)map.get(MULTI_MATCHMODE);
        if ( mode == null ) {
           mode = "EQ";
        }
        return mode;
    }

    /**
     * Since multivalued attributes are stored in a separate table we have to build
     * a special filter.
     *
     * NOTE : at some point I'll try and moving the filtering handling down
     *        into the persistence layer. djs
     */
    @SuppressWarnings("unchecked")
    private Filter buildMultiValuedAttributeFilter(Class clazz, String operator, String matchMode,
                                                   Application app, String attrName,
                                                   List values)
        throws GeneralException {

        if ( attrName == null ) {
            throw new GeneralException("Attribute name cannot be specified"
                                      + " null.");
        }
        if ( Util.size(values) == 0 ) {
            throw new GeneralException("Atribute values cannot be null or empty.");
        }

        String table = ExternalAttributeFilterBuilder.IDENTITY_EXTERNAL;
        if ( Link.class.equals(clazz) ) {
            table = ExternalAttributeFilterBuilder.LINK_EXTERNAL;
        }

        String join = ExternalAttributeFilterBuilder.IDENTITY_JOIN;
        if ( Link.class.equals(clazz) ) {
            join = ExternalAttributeFilterBuilder.LINK_JOIN;
        }

        Filter filter = null;
        if ( "AND".compareTo(operator) == 0 ) {
            filter = ExternalAttributeFilterBuilder.buildAndFilter(table, join, attrName, values, matchMode);
        } else
        if ( "OR".compareTo(operator) == 0 ) {
            filter = ExternalAttributeFilterBuilder.buildOrFilter(table, join, attrName, values, matchMode);
        }

        if ( app != null ) {
            filter = addAppFilter(filter, app);
        }
        return filter;
    }

    /**
     * Build a Filter using the schema on the app to find the attribute's
     * key column.
     */
    private Filter buildExtendedAttributeFilter(Application app,String schemaType,
                                                String attrName, String attrValue)
        throws GeneralException {

        if ( attrName == null ) {
            throw new GeneralException("Attribute name cannot be specified"
                                      + " null.");
        }

        if ( attrValue == null ) {
            throw new GeneralException("Atribute value cannot be null");
        }

        Schema schema = app.getSchema(schemaType);
        if ( schema == null ) {
            throw new GeneralException("Schema for type "
                                       + "[" + schemaType +"]"
                                       + " could not be found.");
        }

        AttributeDefinition def = schema.getAttributeDefinition(attrName);
        if ( def == null ) {
            throw new GeneralException("Attribute definition for attribute ["+attrName+"] was not found.");
        }
        int correlationKey = def.getCorrelationKey();
        if ( correlationKey == 0 ) {
            throw new GeneralException("Attribute " + attrName + " is not"
                + " configured in the application schema["+schemaType+"] as a correlation key.");

        }
        String keyName = "key" + correlationKey;
        // jsl - these usually need ignoreCase but we can't
        // know from here if they the indexes were actually declared.
        // Starting in 7.1 we can punt and let HQLFilterVisitor figure it out
        Filter filter = Filter.eq(keyName, attrValue);
        filter = addAppFilter(filter, app);
        return filter;
    }

    /**
     * If the app into the filter, which should be non-null in most
     * cases. This method will add an AND condition to the
     * an existing filter with the application included.
     */
    private Filter addAppFilter(Filter filter, Application app ) {

        Filter newFilter = filter;
        if (app != null) {
            Filter appFilter = Filter.eq(APP_ATTRIBUTE, app);
            if ( filter == null ) {
                newFilter = appFilter;
            } else {
                newFilter = Filter.and(appFilter, filter);
            }
        } else {
            log.warn("Application not specified in link query");
        }
        return newFilter;
    }

    /**
     * This is different then _context.getUniqueObject so
     * I can log an error when there is more then one
     * object found that matches the filter.
     */
    private <T extends SailPointObject> T
        findOneObject(Class<T> cls, Filter filter) throws GeneralException {

        T object = null;
        try {
            QueryOptions qo = getQueryOptions();
            qo.add(filter);

            // kludge: do locking only for the Identity class to
            // try and avoid deadlocks.  This is also important for persistent
            // locks since we don't try to clean up locks on Links.
            // Also do an id projection
            // search first then fetch because search() doesn't support
            // locking yet.
            // UPDATE: 
            if (_doLocking && cls == Identity.class) {
                List<String> props = new ArrayList<String>();
                props.add("id");
                Iterator<Object[]> ids = _context.search(cls, qo, props);
                while (ids.hasNext()) {
                    String id = (String)(ids.next())[0];
                    if (ids.hasNext()) {
                        String msg = "Found more than one object of type " + cls.getName() +
                                     " using Filter [" + filter.toString() + "]";  
                        log.error(msg, new GeneralException(msg));
                    } 
                    else {
                        // Try the lock once, if it fails
                        // it is likely to be a cache conflict
                        //We should now have the secondary owner/remediator via cache.
                            object = (T)ObjectUtil.lockIdentityById(_context, id);
                    }
                }
            }
            else {
                // the old way, could handle it the same as above
                // with an id search first, but I'm not sure what
                // the performance impact will be, it might be
                // better actually...- jsl
                Iterator<T> objs = _context.search(cls, qo);
                if ( objs != null ) {
                    while ( objs.hasNext() ) {
                        if (object == null ) {
                            object = objs.next();
                        } else {
                            String msg = "Found more than one object of type " + cls.getName() +
                                         " using Filter [" + filter.toString() + "]";  
                            log.error(msg, new GeneralException(msg));

                            object = null;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            String msg = "Unable to correlate using filter [" + filter.toString() + 
                         "]: " + e.getMessage();
            GeneralException ge = new GeneralException(msg);
            log.error(msg, ge);
            
            throw ge;
        }
        return object;
    }

    private QueryOptions getQueryOptions() {
        QueryOptions ops = new QueryOptions();
        // formerly set ops.setIgnoreCase(true) here but that
        // doesn't always apply to everything that can be passed
        // in the Filter.  In 7.1 we can let HQLFilterVisitor figure it out
        return ops;
    }
}
