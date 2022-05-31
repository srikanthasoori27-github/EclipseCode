/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.AccountGroupService;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.SailPointContext;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.LiveReport;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.Schema;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Copied this from the old IdentityEffectiveAccessDataSource.
 * Made some small modifications to make it work with the new framework
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class IdentityEffectiveEntitlementsDataSource extends ProjectionDataSource implements JavaDataSource {

    private static final Log log = LogFactory.getLog(IdentityEffectiveEntitlementsDataSource.class);

    private static String COL_ID = "__id";

    private static final String UNRESOLVABLE = "UNRESOLVABLE";

    private static final String DISPLAY_NAME = "displayName";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String APPLICATION = "application";
    private static final String ACCOUNT = "account";
    private static final String ENITLEMENT = "entitlement";
    private static final String VALUE = "value";
    private static final String ACCOUNT_GROUP = "accountGroup";
    private static final String DETECTED_ROLE = "detectedRole";
    private static final String ASSIGNED_ROLE = "assignedRole";


    private static List<String> ENTRY_FIELDS = Arrays.asList(
        APPLICATION,
        ACCOUNT,
        ENITLEMENT,
        VALUE,
        ACCOUNT_GROUP,
        DETECTED_ROLE,
        ASSIGNED_ROLE
    );

    /* private data members */
    private SailPointContext context;
    private AccountGroupService accountGroupService;
    private final Collection<Entry> collection = new LinkedHashSet<Entry>();
    private boolean initialized = false;
    private Entry currentItem = new Entry();
    private Iterator<Entry> iterator;
    private Identity currentIdentity;


    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
                           String groupBy, List<Sort> sort) throws GeneralException {

        this.context = context;
        super.setTimezone((TimeZone) arguments.get(JRParameter.REPORT_TIME_ZONE));
        super.setLocale((Locale) arguments.get(JRParameter.REPORT_LOCALE));

        accountGroupService = new AccountGroupService(context);

        ReportHelper helper = new ReportHelper(context, getLocale(), getTimezone());
        QueryOptions ops = helper.getFilterQueryOps(report, arguments);

        List<ReportColumnConfig> cols = new ArrayList<ReportColumnConfig>();
        cols.add(new ReportColumnConfig(COL_ID, "id"));
        cols.addAll(report.getGridColumns());

        init(Identity.class, ops, cols, getLocale(), getTimezone());
    }

    @Override
    public boolean next() throws JRException {

        currentItem = null;

        if (iterator == null || !iterator.hasNext()) {

            boolean hasEntitlements = false;
            try {
                while (!hasEntitlements && super.next()) {
                    String id = (String) super.getFieldValue(COL_ID);
                    currentIdentity = context.getObjectById(Identity.class, id);
                    if (currentIdentity != null) {
                        loadCurrentIdentityEntitlements();
                    }

                    hasEntitlements = iterator != null && iterator.hasNext();

                    context.decache();
                }
            } catch (GeneralException e) {
                throw new JRException(e);
            }
        }

        if (iterator != null && iterator.hasNext()) {
            currentItem = iterator.next();
        }

        return currentItem != null;
    }

    @Override
    public Object getFieldValue(String fieldName) throws GeneralException {


        if (ENTRY_FIELDS.contains(fieldName))
            return currentItem.get(fieldName);

        return super.getFieldValue(fieldName);
    }

    public void setLimit(int startPage, int pageSize) {
        // not supported
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Code taken from IdentityEffectiveAccessDataSource
    //
    //////////////////////////////////////////////////////////////////////

    private void loadCurrentIdentityEntitlements() throws GeneralException {

        if (currentIdentity == null) {
            return;
        }
        collection.clear();
        addAdditionalEntitlements(currentIdentity);

        EntitlementCorrelator correlator = new EntitlementCorrelator(getContext());
        correlator.analyzeContributingEntitlements(currentIdentity);

        List<RoleAssignment> roleAssignments = currentIdentity.getRoleAssignments();
        for (RoleAssignment roleAssignment : Util.iterate(roleAssignments)) {
            addAssignedEntitlements(currentIdentity, correlator, roleAssignment, (Bundle)null);
        }

        List<RoleDetection> detections = currentIdentity.getRoleDetections();
        for (RoleDetection det : Util.iterate(detections)) {
            // only the uncovered
            if (det.getAssignmentIds() == null) {
                addDetectedEntitlements(currentIdentity, correlator, det, (Bundle)null);
            }
        }

        iterator = collection.iterator();

    }

    private Set<Bundle> getAssignedRoles() {
        Set<Bundle> response = new LinkedHashSet<Bundle>();
        List<Bundle> assignedRoles = currentIdentity.getAssignedRoles();
        getAssignedRolesRec(response, assignedRoles);
        return response;
    }

    private void getAssignedRolesRec(Set<Bundle> response, Collection<Bundle> assignedRoles) {
        for (Bundle assignedRole : assignedRoles) {
            if (!response.contains(assignedRole)) {
                response.add(assignedRole);
                response.addAll(assignedRole.getPermits());
                response.addAll(assignedRole.getRequirements());
                Collection<Bundle> inheritance = assignedRole.getFlattenedInheritance();
                getAssignedRolesRec(response, inheritance);
            }
        }
    }

    private void addAdditionalEntitlements(Identity identity) throws GeneralException {
        for (EntitlementGroup exception : identity.getExceptions()) {
            addEntriesForEntitlementGroup(identity, exception, "", "");
        }
    }

    // Add assigned entitlements for a single role or role assignment.
    private void addAssignedEntitlements(Identity identity, EntitlementCorrelator ec, RoleAssignment roleAssignment, Bundle role) throws GeneralException {

        if (role == null) {
            role = roleAssignment.getRoleObject(getContext());
        }
        if (identity.hasRole(role, true)) {
            
            // Get the entitlements for the role in the context of the assignment.
            List<EntitlementGroup> entitlementGroups = ec.getContributingEntitlements(roleAssignment, role, /*flattened*/false);
            if (null != entitlementGroups) {
                for (EntitlementGroup group : entitlementGroups) {
                    addEntriesForEntitlementGroup(identity, group, role.getName(), roleAssignment.getName());
                }
            }
            addAssignedEntitlements(identity, ec, roleAssignment, role.getRequirements());
            addAssignedEntitlements(identity, ec, roleAssignment, role.getPermits());
            addAssignedEntitlements(identity, ec, roleAssignment, role.getInheritance());
        }
    }

    // Add assigned entitlements for a list of roles
    private void addAssignedEntitlements(Identity identity, EntitlementCorrelator ec, RoleAssignment roleAssignment, 
                                         List<Bundle> roles) throws GeneralException {
        for (Bundle role : Util.iterate(roles)) {
            addAssignedEntitlements(identity, ec, roleAssignment, role);
        }
    }

    // Add detected entitlements for a single role or role detection.
    private void addDetectedEntitlements(Identity identity, EntitlementCorrelator ec, RoleDetection roleDetection, 
                                        Bundle role) throws GeneralException {
        if (role == null) {
            role = roleDetection.getRoleObject(getContext());
        }
        if (identity.hasRole(role, true)) {

            // get the entitlements for this role without assignment context
            List<EntitlementGroup> entitlements = ec.getContributingEntitlements(/*roleAssignment*/null, role, /*flattened*/false);

            for (EntitlementGroup group : Util.iterate(entitlements)) {
                addEntriesForEntitlementGroup(identity, group, role.getName(), "");
            }
            addDetectedEntitlements(identity, ec, roleDetection, role.getRequirements());
            addDetectedEntitlements(identity, ec, roleDetection, role.getPermits());
            addDetectedEntitlements(identity, ec, roleDetection, role.getInheritance());
        }
    }

    // Add detected entitlements for a list of roles
    private void addDetectedEntitlements(Identity identity, EntitlementCorrelator ec, RoleDetection roleDetection,
                                         List<Bundle> roles) throws GeneralException {
        for (Bundle role : Util.iterate(roles)) {
            addDetectedEntitlements(identity, ec, roleDetection, role);
        }
    }

    private void addEntriesForEntitlementGroup(Identity identity, EntitlementGroup entitlementGroup, String detectedBy, String assignedRole) throws GeneralException {
        Link link = identity.getLink(entitlementGroup.getApplication(),
                entitlementGroup.getInstance(),
                entitlementGroup.getNativeIdentity());

        if (link != null){
            String account = link.getDisplayableName();
            if (account == null) {
                account = "";
            }
            String instance = link.getInstance();
            if (instance == null) {
                instance = "";
            }
            if (entitlementGroup.getAttributeNames() != null) {
                for (String attributeName : entitlementGroup.getAttributeNames()) {
                    collection.addAll(createEntryForEntitlementGroup(entitlementGroup, attributeName, instance, account, detectedBy, assignedRole));
                }
            }
            if (entitlementGroup.getPermissions() != null) {
                String applicationName = entitlementGroup.getApplicationName();
                for (Permission permission : entitlementGroup.getPermissions()) {
                    Collection<Entry> entry = createEntryForPermission(applicationName, instance, account, permission, detectedBy, "", assignedRole);
                    collection.addAll(entry);
                }
            }
        } else {
            log.warn("No link found, skipping to next entry");
        }
        
    }

    private Collection<Entry> getAccountGroupEntitlements(ManagedAttribute accountGroup, String instance, String account, String detectedBy, String assignedRole) throws GeneralException {
        return getAccountGroupEntitlements(accountGroup, instance, account, new ArrayList<String>(), detectedBy, assignedRole);
    }

    private Collection<Entry> getAccountGroupEntitlements(ManagedAttribute accountGroup, String instance, String account, List<String> inheritance, String role, String assignedRole) throws GeneralException {
        Set<Entry> response = new LinkedHashSet<Entry>();
        ArrayList<String> localInheritance = new ArrayList<String>(inheritance);
        /* The Account Group itself*/
        String applicationName = accountGroup.getApplication().getName();
        String referenceAttribute = accountGroup.getReferenceAttribute();
        String accountGroupName = accountGroup.getDisplayableName();
        if (inheritance.contains(accountGroupName)) {
            return response;
        }
        response.add(createEntryMap(applicationName, instance, account, referenceAttribute, role, getInheritanceString(inheritance), accountGroupName, assignedRole));
        localInheritance.add(accountGroupName);
        String inheritanceString = getInheritanceString(localInheritance);
        /* Account group permissions */
        if (accountGroup.getAllPermissions() != null) {
            for (Permission permission : accountGroup.getAllPermissions()) {
                Collection<Entry> entry = createEntryForPermission(applicationName, instance, account, permission, role, inheritanceString, assignedRole);
                response.addAll(entry);
            }
        }

        /* Account group entitlements */
        if (accountGroup.getAttributes() != null) {
            for (Map.Entry<String, Object> entry : accountGroup.getAttributes().entrySet()) {
                Schema schema = accountGroup.getApplication().getSchema(accountGroup.getType());
                if (schema == null) {
                    Message msg = new Message(Message.Type.Error, MessageKeys.ERR_GROUP_SCHEMA_NOT_FOUND, accountGroup.getType());
                    throw new GeneralException(msg);
                }

                String entitlement = entry.getKey();
                AttributeDefinition attributeDefinition = schema.getAttributeDefinition(entitlement);
                if (entry.getValue() != null && attributeDefinition != null && attributeDefinition.isEntitlement()) {
                    String value = entry.getValue().toString();
                    response.add(createEntryMap(applicationName, instance, account, entitlement, role, inheritanceString, value, assignedRole));
                }
            }
        }
        /* Inherited account groups */
        if (accountGroup.getInheritance() != null) {
            for (ManagedAttribute parent : accountGroup.getInheritance()) {
                if (parent != null) {
                    response.addAll(getAccountGroupEntitlements(parent, instance, account, localInheritance, role, assignedRole));
                }
            }
        }
        return response;
    }

    private String getInheritanceString(List<String> inheritance) {
        StringBuilder response = new StringBuilder();
        for (String string : inheritance) {
            response.append(string);
            response.append("->");
        }
        if (response.length() > 2) {
            response.setLength(response.length() - 2);
        }

        return response.toString();
    }

    private Collection<Entry> createEntryForEntitlementGroup(EntitlementGroup entitlementGroup, String attributeName, String instance, String account, String role, String assignedRole) throws GeneralException {
        String applicationName = entitlementGroup.getApplicationName();
        Object rawValue = entitlementGroup.getAttributes().get(attributeName);
        List<String> values = listifyRawValue(rawValue);
        Set<Entry> response = new LinkedHashSet<Entry>();
        for (String value : values) {
            ManagedAttribute accountGroup = accountGroupService.getAccountGroup(applicationName, attributeName, value);
            if (accountGroup != null && accountGroup.isGroupType()) {
                response.addAll(getAccountGroupEntitlements(accountGroup, instance, account, role, assignedRole));
            } else {
                response.add(createEntryMap(applicationName, instance, account, attributeName, role, "", value, assignedRole));
            }
        }
        return response;
    }


    private List<String> listifyRawValue(Object rawValue) throws GeneralException {
        List<String> values;
        if(rawValue == null){
            return new ArrayList<String>(1);
        }
        if (rawValue instanceof String) {
            values = new ArrayList<String>(1);
            values.add((String) rawValue);
        } else if (rawValue instanceof Collection<?>) {
            Collection<?> rawList = (Collection<?>) rawValue;
            values = new ArrayList<String>(rawList.size());
            for (Object value : rawList) {
                if (value != null) {
                    values.add(value.toString());
                } else {
                    values.add("null");
                }
            }
        } else if (rawValue instanceof Boolean) {  //This same issue could exist for long and int types, but I could not get them to fail
            values = new ArrayList<String>(1);
            values.add(rawValue.toString());
        } else {
            throw new GeneralException("Value is not a String, Boolean, or a Collection, is a " + rawValue.getClass().getSimpleName());
        }
        return values;
    }

    private Collection<Entry> createEntryForPermission(String applicationName, String instance, String account, Permission permission, String role, String accountGroup, String type) {
        Set<Entry> response = new LinkedHashSet<Entry>();
        if (permission.getRightsList() != null) {
            for (String right : permission.getRightsList()) {
                Entry entry = createEntryMap(applicationName, instance, account, permission.getTarget(), role, accountGroup, right, type);
                response.add(entry);
            }
        } else {
            Entry entry = createEntryMap(applicationName, instance, account, permission.getTarget(), role, accountGroup, permission.getRights(), type);
            response.add(entry);
        }
        return response;
    }

    private String getApplicationAndInstance(String applicationName, String instance) {
        if (Util.isNullOrEmpty(instance))
            return applicationName;
        return applicationName + " (" + instance + ")";
    }

    private Entry createEntryMap(String applicationName,
                                 String instance,
                                 String account,
                                 String target,
                                 String detectedRole,
                                 String accountGroup,
                                 String right,
                                 String assignedRole) {
        Entry entry = new Entry();
        entry.put(APPLICATION, getApplicationAndInstance(applicationName, instance));
        entry.put(ACCOUNT, account);
        entry.put(ENITLEMENT, target);
        entry.put(VALUE, right);
        entry.put(ACCOUNT_GROUP, accountGroup);
        entry.put(DETECTED_ROLE, detectedRole);
        entry.put(ASSIGNED_ROLE, assignedRole);
        return entry;
    }

    private static final class Entry {

        private static final String[] keys = new String[]{
                DISPLAY_NAME,
                FIRST_NAME,
                LAST_NAME,
                APPLICATION,
                ACCOUNT,
                ENITLEMENT,
                VALUE,
                ACCOUNT_GROUP,
                DETECTED_ROLE,
                ASSIGNED_ROLE
        };

        Map<String, String> entry = new HashMap<String, String>();

        public void put(String key, String value) {
            entry.put(key, value);
        }

        public String get(String key) {
            String value = entry.get(key);
            if (value == null) {
                value = "";
            }
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry that = (Entry) obj;
            int length = keys.length;
            for (int i = 0; i < length; i++) {
                String key = keys[i];
                String thisValue = this.get(key);
                String thatValue = that.get(key);
                if (!(thisValue.equals(thatValue))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int length = keys.length;
            int hashCode = 0;
            for (int i = 0; i < length; i++) {
                hashCode += this.get(keys[i]).hashCode();
            }
            return hashCode;
        }
    }


}