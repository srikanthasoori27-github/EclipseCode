/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EntitlementDescriber;
import sailpoint.api.ProvisioningChecker;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.RemediationCalculator;
import sailpoint.object.Application;
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.Argument;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.Link;
import sailpoint.object.LiveReport;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Recommendation;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.ReportDataSource;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Scope;
import sailpoint.recommender.ReasonsLocalizer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.extjs.Component;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterConverter;

/**
 * Library of utility functions used in reports to
 * simplify the amount of beanshel required.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class ReportingLibrary {
    
    private static Log log = LogFactory.getLog(ReportingLibrary.class);

    public static String getManagerByName(SailPointContext context, String name) throws GeneralException{
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("name", name));

        Iterator<Object[]> iter = context.search(Identity.class, ops, Arrays.asList("manager.name"));
        if (iter.hasNext())
            return (String)iter.next()[0];

        return null;
    }

    public static String getManagerNameById(SailPointContext context, String id) throws GeneralException{
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("id", id));

        Iterator<Object[]> iter = context.search(Identity.class, ops, Arrays.asList("manager.name"));
        if (iter.hasNext())
            return (String)iter.next()[0];

        return null;
    }

    public static String getIdentityDisplayName(SailPointContext context, String name) throws GeneralException{
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("name", name));

        Iterator<Object[]> iter = context.search(Identity.class, ops, Arrays.asList("displayName"));
        if (iter.hasNext())
            return (String)iter.next()[0];

        return null;
    }

    public static String getCertificationItemManagerName(SailPointContext context, Object value) throws GeneralException{

        IdentitySnapshot snap = null;
        if (value != null){
            CertificationEntity entity = null;
            if (value instanceof ArchivedCertificationEntity){
                entity = ((ArchivedCertificationEntity)value).getEntity();
            } else {
                entity = (CertificationEntity)value;
            }

            if (entity != null){
                snap = entity.getIdentitySnapshot(context);
            }
        }

        if (snap != null && snap.getAttributes() != null){
            String mgrName = snap.getAttributes().getString("manager");
            String displayName = mgrName != null ? getIdentityDisplayName(context, mgrName) : null;
            return displayName != null ? displayName : mgrName;
        }

        return null;
    }

    public static Object describeCertificationEntitlement(Object entitlements, String role, String violationSummary,
                                                          String targetName){

        if (role != null){
            return role;
        } else if (violationSummary != null){
            return violationSummary;
        }else if (entitlements != null){
            EntitlementSnapshot snapshot = null;
            if (List.class.isAssignableFrom(entitlements.getClass())){
                List<EntitlementSnapshot> snaps = (List<EntitlementSnapshot>)entitlements;
                snapshot = !snaps.isEmpty() ? snaps.get(0) : null;
            } else {
                snapshot = (EntitlementSnapshot)entitlements;
            }
            if (snapshot != null)
                return EntitlementDescriber.summarize(snapshot);
        } else if (targetName != null){
            return targetName;
        }

        return null;
    }

    /**
     * Creates a description based on item type. Used in role composition
     * certification report.
     */
     public static String describeCompositionItem(SailPointContext context, Locale locale,
                                                          CertificationItem.Type type,
                                                          String id, String name) throws GeneralException{
         boolean useId = (id != null);
        String identifier = id != null ?id : name;
        SailPointObject target = null;
        switch(type){
        case BusinessRolePermit:
        case BusinessRoleRequirement:
        case BusinessRoleHierarchy:
          target = useId ? context.getObjectById(Bundle.class, identifier) : context.getObjectByName(Bundle.class, identifier);
          break;
        case BusinessRoleGrantedCapability:
          target = useId ? context.getObjectById(Bundle.class, identifier) : context.getObjectByName(Capability.class, identifier);
          break;
        case BusinessRoleGrantedScope:
          target = useId ? context.getObjectById(Bundle.class, identifier) : context.getObjectByName(Scope.class, identifier);
          break;
        case BusinessRoleProfile:
          target = useId ? context.getObjectById(Bundle.class, identifier) : context.getObjectByName(Profile.class, identifier);
        }

        if (target != null){
            String localized = Internationalizer.getMessage(target.getDescription(), locale);
            return localized != null ? localized : target.getDescription();
        }

        return "";
    }

    public static Object getColumnValue(SailPointContext ctx, Class<? extends SailPointObject> clazz,
                                 String id, String column) throws GeneralException{
        if (id != null){
            QueryOptions ops = new QueryOptions(Filter.eq("id", id));
            Iterator<Object[]> iter = ctx.search(clazz, ops, Arrays.asList(column));
            if (iter.hasNext()){
                return iter.next()[0];
            }
        }
        return null;
    }

    public static String buildHqlFilter(String argument, String property,  Map args){

        String clause = null;

        if (argument == null || property == null || args == null || args.isEmpty())
            return clause;

        Object value = args.get(argument);

        // handle both null values and the string 'null' since this is used
        // in some UI components.
        if (value != null && !"null".equals(value)){
            if (value instanceof List){
                clause = " " + property + " in(:"+argument+")";
            } else {
                clause = " " + property + "=:"+argument + " ";
            }
        }

        return clause;
    }

    
    public static void addAttributes(SailPointContext context, LiveReport report, Class objectClass,
            List<ObjectAttribute> attributes, String queryProperty,
            String sectionName,  Locale locale) throws GeneralException{
        addAttributes(context, report, objectClass, attributes, queryProperty, sectionName, locale, "name");
    }

    public static void addAttributes(SailPointContext context, LiveReport report, Class objectClass,
                                     List<ObjectAttribute> attributes, String queryProperty,
                                     String sectionName,  Locale locale, String identityProperty) throws GeneralException{

        if (attributes != null && !attributes.isEmpty()){

            Form form = report.getForm();
            Form.Section section = null;
            if (sectionName != null){
                section = form.getSection(sectionName);
                if (section == null){
                    section = new Form.Section(sectionName);
                    form.add(section);
                }
            } else {
                // default to the second form section if
                // no section was specified
                section = form.getSection(1);
            }

            for (ObjectAttribute att : attributes) {
                Argument arg = new Argument();
                arg.setName(createFieldName(att, queryProperty));
                arg.setType(getEffectiveType(objectClass, att, queryProperty));
                arg.setMulti(att.isMulti());
                report.addExtendedArgument(arg);

                // If a property is specified we have to build the parameter
                // property using dot notations, eg owner.department. If no
                // property is specified then we can just search on the attribute name
                String propertyName = queryProperty != null ? queryProperty + "." + att.getName() : att.getName();

                ReportDataSource.Parameter param = new ReportDataSource.Parameter();
                param.setArgument(arg.getName());
                param.setValueClass(arg.getType());
                param.setMulti(arg.isMulti());

                // Identity extended attributes must be treated as identity objects when filtering
                if (ObjectAttribute.TYPE_IDENTITY.equals(arg.getType())){
                    param.setProperty(propertyName + "." + identityProperty);
                    param.setValueClass(null); // since this is just a string value, we can null out the type
                } else {
                    param.setProperty(propertyName);
                }

                if (ObjectAttribute.TYPE_DATE.equals(arg.getType())){
                    param.setValueClass(ReportDataSource.Parameter.PARAM_TYPE_DATE_RANGE);
                }
                // multivalued identity extended attributes cannot be filtered on
                else if (att.isMulti() && "identity".equals(queryProperty)) {
                    break;
                } else if (att.isMulti()){
                    param.setExternalAttributeClass(objectClass.getName());
                }

                // Default to ignoring case on all attribute searches
                if (ObjectAttribute.TYPE_STRING.equals(arg.getType())){
                    param.setIgnoreCase(true);
                }

                ReportDataSource ds = report.getDataSource();
                ds.addParameter(param);

                Field field = constructAttributeField(objectClass, att, queryProperty, locale);
                section.add(field);
            }
        }
    }

    private static String getEffectiveType(Class clazz, ObjectAttribute att, String property){

        String effectiveType = att.getType();

        // Boolean attributes are stored as strings, with the exception
        // of the identity inactive attribute
        if (Identity.ATT_INACTIVE.equals(att.getName()) &&
                (Identity.class.equals(clazz) || IdentityEntitlement.class.equals(clazz) &&
                        IdentityEntitlement.IDENTITY_COLUMN.equals(property))){
            effectiveType = ObjectAttribute.TYPE_BOOLEAN;
        } else if (ObjectAttribute.TYPE_BOOLEAN.equals(effectiveType)){
            effectiveType = ObjectAttribute.TYPE_STRING;
        }

        if (effectiveType == null)
            effectiveType = ObjectAttribute.TYPE_STRING;

        return effectiveType;
    }

    private static String createFieldName(ObjectAttribute att, String queryProperty){
        return queryProperty != null ? "_attr_" + queryProperty + "_" + att.getName() :
                "_attr_" + att.getName();
    }

    private static Field constructAttributeField(Class objectClass, ObjectAttribute att, String queryProperty, Locale locale){

        Field field = new Field();
        field.setName(createFieldName(att, queryProperty));
        field.setType(getEffectiveType(objectClass, att, queryProperty));
        field.setPrompt(att.getDisplayableName(locale));
        field.setValue("ref:" + field.getName());

        if (att.isStandard() && Identity.ATT_INACTIVE.equals(att.getName())){
            field.addAttribute("xtype", "boolradio");
            field.setType(ObjectAttribute.TYPE_BOOLEAN);
        } else if (ObjectAttribute.TYPE_IDENTITY.equals(att.getType()) && Identity.ATT_MANAGER.equals(att.getName())){
            field.setFilterString("managerStatus==true");
            field.setMulti(true);
        } else if (ObjectAttribute.TYPE_IDENTITY.equals(att.getType())){
            field.setMulti(true);
        } else if (ObjectAttribute.TYPE_DATE.equals(att.getType())){
            field.addAttribute("xtype", Component.XTYPE_DATERANGE);
            field.setType(ReportDataSource.Parameter.PARAM_TYPE_DATE_RANGE);
        } else if (ObjectAttribute.TYPE_BOOLEAN.equals(att.getType())){
            field.addAttribute("xtype", "boolradio");
        } else if (att.isMulti()){
            field.addAttribute("xtype", "extattrfilter");
        }

        return field;
    }

    public static List<ReportColumnConfig> createApplicationAttributeColumns(SailPointContext context,
                                                                   String applicationId) throws GeneralException{

        List<ObjectAttribute> linkAttributes = new ArrayList<ObjectAttribute>();
        List<ReportColumnConfig> newCols = new ArrayList();

        Application app = context.getObjectById(Application.class, applicationId);
        if (app != null){
            Schema schema = app.getSchema(Application.SCHEMA_ACCOUNT);
            List<AttributeDefinition>  attributes = schema.getAttributes();
            ObjectConfig linkConf = ObjectConfig.getObjectConfig(Link.class);
            for (AttributeDefinition attribute : attributes) {
                ObjectAttribute objectAttribute = linkConf.getObjectAttributeWithSource(app, attribute.getName());
                if (objectAttribute != null)
                    linkAttributes.add(objectAttribute);
            }

            for(ObjectAttribute objectAttribute : linkAttributes){
                boolean isSearchable = objectAttribute != null && objectAttribute.isSearchable();
                ReportColumnConfig conf = new ReportColumnConfig(applicationId + "_" + objectAttribute.getName(),
                      objectAttribute.getName(), "java.lang.String");
                conf.setProperty(!isSearchable ? "attributes." + objectAttribute.getName() : objectAttribute.getName());
                conf.setSortable(isSearchable);
                conf.setExtendedColumn(true);
                newCols.add(conf);
            }
        }

        return newCols;
    }

    public static Filter getGroupDefinitionFilter(SailPointContext context, List groupDefinitionIds,
                                                  boolean useJoin)
            throws GeneralException{
        return getGroupDefinitionFilter(context, groupDefinitionIds, useJoin, null);
    }

    public static Filter getGroupDefinitionFilter(SailPointContext context, List groupDefinitionIds,
                                                  boolean useJoin, String property)
            throws GeneralException{

        Filter filter = null;
        if (groupDefinitionIds != null && !groupDefinitionIds.isEmpty()){
            QueryOptions ops = new QueryOptions(Filter.in("id", groupDefinitionIds));
            List<String> props = Arrays.asList(new String[]{"filter"});
            Iterator<Object[]> groups = context.search(GroupDefinition.class, ops, props);
            List<Filter> groupFilters = new ArrayList<Filter>();
            while(groups.hasNext()){
                Object[] next = groups.next();
                Filter groupFilter = (Filter)next[0];
                if (groupFilter != null){
                    if (useJoin){
                        groupFilters.add(FilterConverter.convertFilter(groupFilter, Identity.class, property));
                    } else {
                        groupFilters.add(groupFilter);
                    }
                }
            }

            if(!groupFilters.isEmpty()) {
                filter = Filter.or(groupFilters);
            }
        }

        return filter;
    }
    
    /*
     * Utility method to infer the remediation status if the action doesn't already have that value
     */
    public static String getRemediationStatus(SailPointContext context, CertificationAction action)
        throws GeneralException {

        if (action == null || 
                !CertificationAction.Status.Remediated.equals(action.getStatus()) || 
                !action.isRemediationKickedOff())
            return "";

        boolean remediated = false;
        String completionKey = MessageKeys.REPT_CERT_ACTIVITY_VAL_REMED_COMPLETED; // default value
        if (action.isRemediationCompleted()){
            remediated = true;
        } else {
            ProvisioningChecker scanner = new ProvisioningChecker(context);
            QueryOptions opts = new QueryOptions();
            opts.setResultLimit(1); // we need only one value
            opts.add(Filter.eq("action", action));
            Iterator<Object[]> results = context.search(CertificationItem.class, opts, "id");
            String itemId = null;
            if (results.hasNext()) {
                itemId = (String)results.next()[0];
            }
            if (itemId == null) { // seems unlikely, but don't let it surprise anybody
                return "";
            }
            CertificationItem item = context.getObjectById(CertificationItem.class, itemId);
            CertificationEntity parent = item.getParent();
            if (parent != null){
                switch(parent.getType()){
                    case DataOwner:
                        // for dataowner type the identity is per cert item not cert entity
                        Identity dataOwnerIdentity = item.getIdentity(context);
                        completionKey = MessageKeys.REPT_ACCOUNT_GRP_MEMB_STATUS_REMED_COMPLETED;
                        return getRemediationStatusForIdentity(context, parent, item, dataOwnerIdentity, scanner,  completionKey);
                    case Identity:
                        Identity identity = parent.getIdentity(context);
                        return getRemediationStatusForIdentity(context, parent, item, identity, scanner,  completionKey);
                    case AccountGroup:
                        completionKey = MessageKeys.REPT_ACCOUNT_GRP_MEMB_STATUS_REMED_COMPLETED;
                        remediated = isAccountGroupRemediated(context, item, scanner, parent);
                        break;
                    case BusinessRole:
                        completionKey = MessageKeys.REPT_COMP_CERT_REMED_COMPLETED;
                        Bundle role = context.getObjectById(Bundle.class, parent.getTargetId());
                        remediated = role == null ||
                                scanner.hasBeenExecuted(item.getAction().getRemediationDetails(), role);
                        break;
                    default:
                        throw new GeneralException("Unhandled CertificationEntity type:" + parent.getType());
                }
            }
        }

        return remediated ? getMessage(completionKey,"") : "";
    }
    
    private static String getRemediationStatusForIdentity(SailPointContext ctx, CertificationEntity parent, 
            CertificationItem item, 
            Identity identity,
            ProvisioningChecker scanner,
            String completionKey) throws GeneralException {

        boolean remediated;

        try {
            remediated = identity == null ||
                    scanner.hasBeenExecuted(item.getAction().getRemediationDetails(), identity);

            // if this is an account revoke which has not been remediated, check to
            // see if just the entitlement has been remediated.
            if (!remediated && item.getAction().isRevokeAccount() &&
                    !CertificationItem.Type.Account.equals(item.getType())){
                RemediationCalculator calc = new RemediationCalculator(ctx);
                ProvisioningPlan remedPlan =
                        calc.calculateProvisioningPlan(item, CertificationAction.Status.Remediated);
                if (remedPlan != null && scanner.hasBeenExecuted(remedPlan, identity)){
                    return getMessage(MessageKeys.REPT_CERT_ENTITLEMENT_REMOVED,"");          
                }
            }
        } catch (ProvisioningChecker.ProvisioningCheckerException pce) {

            String errorMsg = "ProvisioningChecker failed on item: " + item.getId() +
                    " in entity: " + parent.getId() +
                    " on certification " + parent.getCertification().getId();

            // why?
            if (pce.requestAttrValueIsNull && null != pce.attributeRequest) {
                errorMsg += " : Attribute Request value is null: " + pce.attributeRequest.toXml();
            } else if (pce.requestPermValueIsNull && null != pce.permissionRequest) {
                errorMsg += " : Permission Request value is null: " + pce.permissionRequest.toXml();
            }

            log.error(errorMsg + " : " + pce);
            return getMessage(MessageKeys.UNKNOWN);
        }

        return remediated ? getMessage(completionKey,"") : "";
    }
    
    private static String getMessage(String key, Object... args) {
        if (key == null)
            return null;

        Message msg = new Message(key, args);
        return msg.getLocalizedMessage();
    }
    
    private static boolean isAccountGroupRemediated(SailPointContext ctx, CertificationItem item,
            ProvisioningChecker scanner, 
            CertificationEntity parent)
                    throws GeneralException {
        
        //IIQETN-4579: We should be taking our queues from RemediationScanner, which is getting this right.
        //Referring to the refreshRemediationCompletions(CertificationEntity, ManagedAttribute) method.
        //If the CertificationItem.Type is AccountGroupMembership,
        //we check the hasBeenExecuted for plan, identity. Otherwise check hasBeenExecuted for plan, group.
        
        //We are defaulting to true.
        boolean isRemediated = true;
        
        if (CertificationItem.Type.AccountGroupMembership.equals(item.getType())) {
            Identity identity = item.getIdentity(ctx);
            isRemediated = scanner.hasBeenExecuted(item.getAction().getRemediationDetails(), identity);
        } else if (CertificationItem.Type.Exception.equals(item.getType())) {
            ManagedAttribute accountGroup = parent.getAccountGroup(ctx);
            if (accountGroup == null)
                isRemediated = true;
            else if (item.getAction() == null || item.getAction().getRemediationDetails() == null)
                isRemediated = true;
            else
                isRemediated = scanner.hasBeenExecuted(item.getAction().getRemediationDetails(), accountGroup);
        }

        return isRemediated;
    }

    public static String getRoleTypeDisplayName(String roleType){
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        if (roleConfig != null){
            Map<String,RoleTypeDefinition> types = null;
            Object value = roleConfig.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);
            if (value instanceof Map) {
                types = (Map<String,RoleTypeDefinition>)value;
            }
            else if (value instanceof List) {
                types = new HashMap<String,RoleTypeDefinition>();
                for (Object o : (List)value) {
                    RoleTypeDefinition type = (RoleTypeDefinition)o;
                    types.put(type.getName(), type);
                }
            }

            if (types != null){
                RoleTypeDefinition def = types.get(roleType);
                if (def != null){
                    return def.getDisplayName();
                }
            }
        }

        return null;
    }
    
    public static String getLocalizedRoleTypeDisplayName(String roleType, Locale locale, TimeZone timezone) {
        String displayName = getRoleTypeDisplayName(roleType);
        return new Message(displayName).getLocalizedMessage(locale, timezone);
    }

    /**
     * Retrieve the localized recommendation text for the given recommendation enum value.
     * Recommendations must be enabled for the cert in order for the value to be returned.
     *
     * @param context SailPointContext
     * @param recommendation Recommendation value. Must be a value that exists in RecommendedDecision enum.
     * @param itemId The CertificationItem Id to retrieve
     * @return The localized recommendation string
     * @throws GeneralException
     */
    public static String getRecommendedDecision(SailPointContext context, String recommendation, String itemId)
        throws GeneralException {

        CertificationItem item = context.getObjectById(CertificationItem.class, itemId);
        String result = "";
        try {
            if (item != null && getCertificationShowRecommendations(context, item)) {
                Recommendation.RecommendedDecision decision = Recommendation.RecommendedDecision.valueOf(recommendation);
                result = getMessage(decision.getMessageKey());
            }
        }
        catch (Exception e) {
            log.debug("Unable to retrieve the localized recommendation value for certification item.", e);
        }

        return result;
    }

    /**
     * Retrieve the timestamp of when the recommendation for a particular certification item was retrieved.
     * Recommendations must be enabled for the cert in order for the value to be returned.
     *
     * @param context SailPointContext
     * @param itemId The CertificationItem Id to retrieve
     * @return The date & time of retrieval as a Date object
     * @throws GeneralException
     */
    public static Date getRecommendationTimestamp(SailPointContext context, String itemId) throws GeneralException {
        CertificationItem item = context.getObjectById(CertificationItem.class, itemId);
        Recommendation recommendation = null;
        Date result = null;

        if (item != null && getCertificationShowRecommendations(context, item)) {
            recommendation = item.getRecommendation();
        }

        if (recommendation != null) {
            result = recommendation.getTimeStamp();
        }

        return result;
    }

    /**
     * Retrieve the reasons for why a recommendation was given for a particular certification item.
     * Recommendations must be enabled for the cert in order for the value to be returned.
     *
     * @param context SailPointContext
     * @param itemId The CertificationItem Id to retrieve
     * @return A comma-seperated list of reasons as a string
     * @throws GeneralException
     */
    public static String getRecommendationReasons(SailPointContext context, String itemId) throws GeneralException {
        CertificationItem item = context.getObjectById(CertificationItem.class, itemId);
        Recommendation recommendation = null;
        String result = "";

        if (item != null && getCertificationShowRecommendations(context, item)) {
            recommendation = item.getRecommendation();
        }

        if (recommendation != null) {
            result = Util.listToQuotedCsv((new ReasonsLocalizer(context, recommendation)).getReasons(), '"', true);
        }

        return result;
    }

    /**
     * Retrieve the autoDecisionGenerated flag on the certification item with the given id.
     *
     * @param context SailPointContext
     * @param itemId The CertificationItem Id to retrieve
     * @return true if a decision was automatically generated, otherwise false
     * @throws GeneralException
     */
    public static boolean getAutoDecisionGenerated(SailPointContext context, String itemId) throws GeneralException {
        CertificationItem item = context.getObjectById(CertificationItem.class, itemId);
        return item != null ? item.isAutoDecisionGenerated() : false;
    }

    /**
     * Retrieve the autoDecisionAccepted flag on the action attached to the certification item with the given id.
     *
     * @param context SailPointContext
     * @param itemId The CertificationItem Id to retrieve
     * @return true if the automatic decision was accepted, otherwise false
     * @throws GeneralException
     */
    public static boolean getAutoDecisionAccepted(SailPointContext context, String itemId) throws GeneralException {
        boolean accepted = false;
        CertificationItem item = context.getObjectById(CertificationItem.class, itemId);
        if (item != null && item.isAutoDecisionGenerated() && item.getAction() != null) {
            accepted = item.getAction().isAutoDecision();
        }
        return accepted;
    }

    private static boolean getCertificationShowRecommendations(SailPointContext context, CertificationItem item)
            throws GeneralException {

        boolean showRecommendations = false;

        if (item != null) {
            Certification cert = item.getCertification();
            CertificationDefinition def = cert.getCertificationDefinition(context);

            if (def != null) {
                showRecommendations = def.getShowRecommendations();
            }
        }

        return showRecommendations;
    }
}
