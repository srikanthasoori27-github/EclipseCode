/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.api.certification.CertificationStatCounter;
import sailpoint.api.SailPointContext;
import sailpoint.web.messages.MessageKeys;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Represents a grouping of Certification objects. Most commonly, this
 * is used to group together all the access reviews generated
 * for a given certification schedule execution.
 *
 * @author jonathan.bryant@sailpoint.com
 */
@XMLClass
public class CertificationGroup extends SailPointObject{

    @XMLClass(xmlname="CertificationGroupType")
    public static enum Type {

        Certification(MessageKeys.CERT_GRP_TYPE_CERTIFICATION);

        private String messageKey;

        private Type(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    };

    @XMLClass(xmlname="CertificationGroupStatus")
    public static enum Status{

        Pending(MessageKeys.CERT_GRP_STATUS_PENDING),
        Staged(MessageKeys.CERT_GRP_STATUS_STAGED),
        Canceling(MessageKeys.CERT_GRP_STATUS_CANCELING),
        Activating(MessageKeys.CERT_GRP_STATUS_ACTIVATING),
        Active(MessageKeys.CERT_GRP_STATUS_ACTIVE),
        Complete(MessageKeys.CERT_GRP_STATUS_COMPLETED),
        Error(MessageKeys.CERT_GRP_STATUS_ERROR),
        Archived(MessageKeys.CERT_GRP_STATUS_ARCHIVED);

        private String messageKey;

        private Status(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    }
    
    public static final String SCHEDULE_FREQUENCY = "scheduleFrequency";
    
    /**
     * Extended attributes.
     */
    Attributes<String, Object> attributes;

    /**
     * Type of CertificationGroup.
     */
    private Type type;

    /**
     * Status of CertificationGroup
     */
    private Status status;


    /**
     * CertificationDefinition used to create
     * this CertificationGroup.
     */
    private CertificationDefinition definition;


    /**
     * Warnings or errors attached to this
     * CertificationGroup when the Certification
     * was generated.
     */
    private List<Message> messages;

    /**
     * Total count of access reviews that make up this
     * group.
     */
    private int totalCertifications;

    /**
     * Total count of completed access reviews that make up this
     * group.
     */
    private int completedCertifications;

    /**
     * Percentage complete access reviews in
     * this CertificationGroup.
     */
    private int percentComplete;


    public CertificationGroup() {
        super();
    }

    public CertificationGroup(Type type) {
        this();
        this.type = type;
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitCertificationGroup(this);
    }

    /**
     * Calculate aggregate statistics for all certifications included in this group.
     *
     * @param context SailPointContext
     * @return The statistics for all certifications included in this group.
     * @throws GeneralException
     */
    public Certification.CertificationStatistics calculateStatistics(SailPointContext context)
            throws GeneralException {

        Certification.CertificationStatistics statistics = new Certification.CertificationStatistics();

        if (getId() == null)
            return statistics;

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("certificationGroups.id", getId()));

        List<String> fields = new ArrayList<String>();
        fields.add("sum(statistics.totalEntities)");
        fields.add("sum(statistics.completedEntities)");
        fields.add("sum(statistics.totalItems)");
        fields.add("sum(statistics.completedItems)");

        fields.add("sum(statistics.violationsAcknowledged)");
        fields.add("sum(statistics.violationsAllowed)");
        fields.add("sum(statistics.violationsRemediated)");
        fields.add("sum(statistics.totalViolations)");

        fields.add("sum(statistics.rolesApproved)");
        fields.add("sum(statistics.rolesRemediated)");
        fields.add("sum(statistics.totalRoles)");

        fields.add("sum(statistics.exceptionsApproved)");
        fields.add("sum(statistics.exceptionsRemediated)");
        fields.add("sum(statistics.totalExceptions)");

        fields.add("sum(statistics.accountGroupMembershipsApproved)");
        fields.add("sum(statistics.accountGroupMembershipsRemediated)");
        fields.add("sum(statistics.totalAccountGroupMemberships)");

        fields.add("sum(statistics.accountGroupPermissionsApproved)");
        fields.add("sum(statistics.accountGroupPermissionsRemediated)");
        fields.add("sum(statistics.totalAccountGroupPermissions)");

        fields.add("sum(statistics.totalRoleHierarchies)");
        fields.add("sum(statistics.roleHierarchiesApproved)");
        fields.add("sum(statistics.roleHierarchiesRemediated)");

        fields.add("sum(statistics.totalRequirements)");
        fields.add("sum(statistics.requirementsApproved)");
        fields.add("sum(statistics.requirementsRemediated)");

        fields.add("sum(statistics.totalPermits)");
        fields.add("sum(statistics.permitsApproved)");
        fields.add("sum(statistics.permitsRemediated)");

        fields.add("sum(statistics.totalCapabilities)");
        fields.add("sum(statistics.capabilitiesApproved)");
        fields.add("sum(statistics.capabilitiesRemediated)");

        fields.add("sum(statistics.totalScopes)");
        fields.add("sum(statistics.scopesApproved)");
        fields.add("sum(statistics.scopesRemediated)");

        fields.add("sum(statistics.totalProfiles)");
        fields.add("sum(statistics.profilesApproved)");
        fields.add("sum(statistics.profilesRemediated)");

        fields.add("sum(statistics.excludedEntities)");
        fields.add("sum(statistics.excludedItems)");

        fields.add("sum(statistics.rolesAllowed)");
        fields.add("sum(statistics.exceptionsAllowed)");

        fields.add("sum(statistics.accountsApproved)");
        fields.add("sum(statistics.accountsRemediated)");
        fields.add("sum(statistics.accountsAllowed)");
        fields.add("sum(statistics.totalAccounts)");

        Iterator<Object[]> iter = context.search(Certification.class, ops, fields);
        if (iter != null && iter.hasNext()){
            Object[] result = iter.next();
            statistics.setTotalEntities(Util.otoi(result[0]));
            statistics.setCompletedEntities(Util.otoi(result[1]));
            statistics.setPercentComplete(CertificationStatCounter.calculatePercentComplete(
                    statistics.getCompletedEntities(), statistics.getTotalEntities()));

            statistics.setTotalItems(Util.otoi(result[2]));
            statistics.setCompletedItems(Util.otoi(result[3]));
            statistics.setItemPercentComplete(CertificationStatCounter.calculatePercentComplete(
                    statistics.getCompletedItems(), statistics.getTotalItems()));

            statistics.setViolationsAcknowledged(Util.otoi(result[4]));
            statistics.setViolationsAllowed(Util.otoi(result[5]));
            statistics.setViolationsRemediated(Util.otoi(result[6]));
            statistics.setTotalViolations(Util.otoi(result[7]));

            statistics.setRolesApproved(Util.otoi(result[8]));
            statistics.setRolesRemediated(Util.otoi(result[9]));
            statistics.setTotalRoles(Util.otoi(result[10]));

            statistics.setExceptionsApproved(Util.otoi(result[11]));
            statistics.setExceptionsRemediated(Util.otoi(result[12]));
            statistics.setTotalExceptions(Util.otoi(result[13]));

            statistics.setAccountGroupMembershipsApproved(Util.otoi(result[14]));
            statistics.setAccountGroupMembershipsRemediated(Util.otoi(result[15]));
            statistics.setTotalAccountGroupMemberships(Util.otoi(result[16]));

            statistics.setAccountGroupPermissionsApproved(Util.otoi(result[17]));
            statistics.setAccountGroupPermissionsRemediated(Util.otoi(result[18]));
            statistics.setTotalAccountGroupPermissions(Util.otoi(result[19]));

            statistics.setTotalRoleHierarchies(Util.otoi(result[20]));
            statistics.setRoleHierarchiesApproved(Util.otoi(result[21]));
            statistics.setRoleHierarchiesRemediated(Util.otoi(result[22]));

            statistics.setTotalRequirements(Util.otoi(result[23]));
            statistics.setRequirementsApproved(Util.otoi(result[24]));
            statistics.setRequirementsRemediated(Util.otoi(result[25]));

            statistics.setTotalPermits(Util.otoi(result[26]));
            statistics.setPermitsApproved(Util.otoi(result[27]));
            statistics.setPermitsRemediated(Util.otoi(result[28]));

            statistics.setTotalCapabilities(Util.otoi(result[29]));
            statistics.setCapabilitiesApproved(Util.otoi(result[30]));
            statistics.setCapabilitiesRemediated(Util.otoi(result[31]));

            statistics.setTotalScopes(Util.otoi(result[32]));
            statistics.setScopesApproved(Util.otoi(result[33]));
            statistics.setScopesRemediated(Util.otoi(result[34]));

            statistics.setTotalProfiles(Util.otoi(result[35]));
            statistics.setProfilesApproved(Util.otoi(result[36]));
            statistics.setProfilesRemediated(Util.otoi(result[37]));

            statistics.setExcludedEntities(Util.otoi(result[38]));
            statistics.setExcludedItems(Util.otoi(result[39]));

            statistics.setRolesAllowed(Util.otoi(result[40]));
            statistics.setExceptionsAllowed(Util.otoi(result[41]));

            statistics.setAccountsApproved(Util.otoi(result[42]));
            statistics.setAccountsRemediated(Util.otoi(result[43]));
            statistics.setAccountsAllowed(Util.otoi(result[44]));
            statistics.setTotalAccounts(Util.otoi(result[45]));
        }

        return statistics;
    }

    @XMLProperty
    public int getTotalCertifications() {
        return totalCertifications;
    }

    public void setTotalCertifications(int totalCertifications) {
        this.totalCertifications = totalCertifications;
    }

    @XMLProperty
    public int getCompletedCertifications() {
        return completedCertifications;
    }                                                                                 

    public void setCompletedCertifications(int completedCertifications) {
        this.completedCertifications = completedCertifications;
    }

    @XMLProperty
    public int getPercentComplete() {
        return percentComplete;
    }

    public void setPercentComplete(int percentComplete) {
        this.percentComplete = percentComplete;
    }

    @XMLProperty
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @XMLProperty    
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public CertificationDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(CertificationDefinition certificationDefinition) {
        this.definition = certificationDefinition;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Message> getMessages() {
        return messages;
    }
    
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String,Object> a) {
        attributes = a;
    }
    
    public void setAttribute(String name, Object value) {
        if (value == null) {
            if (attributes != null)
                attributes.remove(name);
        }
        else {
            if (attributes == null)
                attributes = new Attributes<String,Object>();
            attributes.put(name, value);
        }
    }

    public Object getAttribute(String name) {
        return (attributes != null) ? attributes.get(name) : null;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public void addMessage(Message msg){
        if (messages == null)
            messages = new ArrayList<Message>();
        messages.add(msg);
    }

    public void addMessages(List<Message> msgs){
        if (msgs != null){
            for(Message msg : msgs){
                addMessage(msg);
            }
        }
    }
}
