/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.QueryOptions;
import sailpoint.policy.AccountPolicyExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class CertificationReportDataSource extends BaseCertificationDataSource {

    private List<Attributes> excludedItems;
    private Iterator<Attributes> items;

    private Attributes currentRow;

    // Query parameters
    private Certification.Type certType;
    private List<String> objectIds;

    private Locale locale;
    private TimeZone timezone;

    private IncrementalObjectIterator<CertificationEntity> iterator;
    private int cacheTracker;

    /**
     * Default constructor
     *
     */
    public CertificationReportDataSource(Certification.Type certType, List<String> objectIds, Attributes inputs,
                                         Locale locale, TimeZone tz) {
        super(locale, tz);
        this.certType = certType;
        this.objectIds = objectIds;

        locale = inputs.get(JRParameter.REPORT_LOCALE) != null ? (Locale)inputs.get(JRParameter.REPORT_LOCALE) :
                Locale.getDefault();
        timezone = inputs.get(JRParameter.REPORT_TIME_ZONE) != null ? (TimeZone)inputs.get(JRParameter.REPORT_TIME_ZONE) :
                TimeZone.getDefault();
    }

    private String getMessage(String key, String emptyMsg, Object... params){
       if (key == null)
           return emptyMsg;

       Message msg = new Message(key, params);
       return msg.getLocalizedMessage(locale, timezone);
    }

    /**
     * Initialize the report. Queries all excluded certifications and
     * stores them for later merge into the report.
     */
    @Override
    public void internalPrepare() throws GeneralException {
        //Use entity count logic (see BaseCertificationDataSource)
        super.internalPrepare();

        items = null;
        excludedItems = new ArrayList<Attributes>();

        Message msg = new Message(MessageKeys.REPT_CERTIFICATION_PROGRESS_EXCLUSIONS_COMPUTED);

        updateProgress(msg.getLocalizedMessage());

        IncrementalObjectIterator<Certification> iter = new IncrementalObjectIterator<Certification>(getContext(), 
                Certification.class, getExclusionQueryOptions());
        if (iter != null && iter.hasNext()) {
            int cnt =0;
            while(iter.hasNext()){
                Certification cert = iter.next();
                for (ArchivedCertificationEntity archived : cert.fetchArchivedEntities(getContext())) {
                    CertificationEntity entity = archived.getEntity();
                    List<CertificationItem> items = entity.getItems();
                    if (items != null && items.size() > 0) {
                        //Total entity count will not include archived entity, so add it here.
                        setTotalEntityCount(getTotalEntityCount() + 1);
                        for (CertificationItem item : items) {
                            excludedItems.add(createRow(archived.getCertification(), item, archived.getReason(), archived.getExplanation()));
                        }
                    }
                }

                cnt++;
                if (cnt % 100 == 0){
                    getContext().decache();
                }
            }
        }

        getContext().decache();
        Collections.sort(excludedItems, ROW_SORTER);
        updateProgress(msg.getLocalizedMessage());

        iterator = new IncrementalObjectIterator<CertificationEntity>(getContext(),
                CertificationEntity.class, qo);


    }

    /**
     * Gets the given field from the current report row.
     *
     * @param jrField The field from the jasper template to retrieve
     * @return Field value
     * @throws net.sf.jasperreports.engine.JRException
     *
     */
    public Object getFieldValue(JRField jrField) throws JRException {

        String fieldName = jrField.getName();

        if (fieldName == null)
            throw new RuntimeException("No field specified");

        if (DataSourceUtil.CURRENT_BEAN_FIELD_NAME.equals(fieldName))
            return currentRow;

        Object val = currentRow.get(fieldName);

        // Add some spaces ifnull so we don't get any blank cells. This seems to
        // happen randonly on the first row on the page. This hack 'fixes' the problem.
        return val == null || val.toString().length() == 0 ? "  " : val;
    }

    /**
     * Creates a report row. Builds a map with all the datapoints we're interested
     * in. The keys in the report match the field names coming from the jasper
     * template.
     *
     * @param cert The Certification. It's passed in b/c ArchivedCertificationEntity
     * items will not have a reference to the cert.
     * @param item CertificationItem to convert into a report row
     * @return Map of properties for the given certification item
     * @throws GeneralException
     */
    private Attributes<String, String> createRow(Certification cert, CertificationItem item,
                                                 ArchivedCertificationEntity.Reason exclusionReason,
                                                 String exclusionExplanation)
        throws GeneralException {

        Attributes<String, String> row = new Attributes<String, String>();
        putEntityIdForPercent(row, item);
        row.put("identity", item.getParent().getIdentity());
        row.put("firstName", item.getParent().getFirstname());
        row.put("lastName", item.getParent().getLastname());

        Set<String> nativeIds = getAccounts(item);
        // this will join the accounts using the locale specific delimiter
        Message msg = new Message(MessageKeys.MSG_PLAIN_TEXT, nativeIds);
        row.put("nativeIdentity", nativeIds.isEmpty() ? "" :  msg.getLocalizedMessage(locale, timezone));

        row.put("entitlementType", getMessage(item.getType().getMessageKey(), ""));

        if (exclusionReason != null)
            row.put("status", getMessage(exclusionReason.getMessageKey()));
        else
            row.put("status", item.getSummaryStatus() != null ?
                    getMessage(item.getSummaryStatus().getMessageKey(), "") : "");

        row.put("exclusionExplanation", exclusionExplanation);

        if (item.getAction() != null) {
            Identity actor = item.getAction().getActor(getContext());
            row.put("decisionMaker", actor != null ? actor.getDisplayableName()
                    : item.getAction().getActorName());
            CertificationAction.Status stat = item.getAction().getStatus();
            if (CertificationAction.Status.Remediated.equals(stat) && item.getAction().isRevokeAccount()) {
                row.put("decision", getMessage(CertificationAction.Status.RevokeAccount.getMessageKey(), ""));
            } else if (DataSourceUtil.isRemediationModified(item.getAction())) {
                row.put("decision", getMessage(CertificationAction.Status.Modified.getMessageKey(), ""));
                row.put("newValue", Util.otos(DataSourceUtil.getRemediationModifiableNewValue(item.getAction())));
            } else if (stat != null){
                row.put("decision", getMessage(stat.getMessageKey(), ""));
            }

            row.put("comments", item.getAction().getComments());
            row.put("remediationCompleted", getRemediationStatus(item,
                    MessageKeys.REPT_CERTIFICATION_REMEDIATION_COMPLETED));
        }

        row.put("entitlement", getEntitlementDescription(item));

        // the is now referred to as 'Access Review' leaving it as
        // 'certName' for backwards compatibility with customized reports
        row.put("certName", cert.getName());

        String certGrp = cert.getCertificationName();
        row.put("certGroupName", certGrp != null ? certGrp : "");

        /** Get application and instance from exception entitlements **/
        if(item.getExceptionEntitlements()!=null) {
        	EntitlementSnapshot snapshot = item.getExceptionEntitlements();
        	row.put("application",snapshot.getApplication());
        	row.put("instance", snapshot.getInstance());
        }

        row.put("tags", WebUtil.objectListToNameString(cert.getTags()));
        
        IdentitySnapshot snapshot = item.getParent().getIdentitySnapshot(getContext());

        if (snapshot != null && snapshot.getAttributes() != null)
            row.put("manager", snapshot.getAttributes().getString("manager"));

        return row;
    }


    /**
     * Gets the next item on the list.
     *
     * @return True if there are more items in the datasource
     * @throws JRException
     */
    public boolean internalNext() throws JRException {

        while (iterator.hasNext() && (items == null || !items.hasNext())) {
            try {
                getNextPage();
            } catch (GeneralException e) {
                throw new JRException(e);
            }
        }

        if ((items == null || !items.hasNext()) && excludedItems != null) {
            items = excludedItems.iterator();
            excludedItems = null;
        }

        if (items != null && items.hasNext()) {
            currentRow = items.next();
            checkProcessedEntity(currentRow, true);
            return true;
        }
        return false;
    }

    /**
     * Gets the next page of report data. Once the page is retrieved
     * we merge in any excluded certifications items we found when the
     * internalPrepare() method ran. We merge, rather than adding these to the
     * front or end of the report so that we can maintain the
     * lastname, firstname sort order.
     *
     * @throws GeneralException
     */
    private void getNextPage() throws GeneralException {

        List<Attributes> page = new ArrayList<Attributes>();

        if (iterator.hasNext()){
            CertificationEntity entity = iterator.next();
            if (entity.getItems() == null || entity.getItems().size() == 0) {
                //If there are no items, this entity has nothing to do later, so just increment processed count 
                incrementProcessedEntityCount();
            }
            else {
                for (CertificationItem item : entity.getItems()) {
                    Attributes row = createRow(entity.getCertification(), item, null, null);
                    List<Attributes> excludedRowsToMerge = getExludedItemsToMerge(row);
                    if (excludedRowsToMerge != null && !excludedRowsToMerge.isEmpty())
                        page.addAll(excludedRowsToMerge);
                    page.add(row);
                }
            }
            getContext().decache();
            items = page.iterator();
        }

        cacheTracker++;
        if (cacheTracker % 100 == 0)
            getContext().decache();
    }

    /**
     * Looks for any excluded items we found in the internalPrepare() method that
     * should be returned before the given row. This is based on
     * lastname, firstname sort.
     *
     * @param row The current row
     * @return List of rows that should be inserted before the given row.
     */
    private List<Attributes> getExludedItemsToMerge(Attributes row) {

        if (excludedItems == null || excludedItems.isEmpty())
            return null;

        List<Attributes> mergeRows = new ArrayList<Attributes>();
        while (!excludedItems.isEmpty() && ROW_SORTER.compare(excludedItems.get(0), row) < 1) {
            mergeRows.add(excludedItems.get(0));
            excludedItems.remove(excludedItems.get(0));
        }

        return mergeRows;
    }


    /**
     * @return Base query options for retrieving all CertificationEntity objects
     *         from identity certifications.
     */
    @Override
    protected QueryOptions getQueryOptions() throws GeneralException {

        List<QueryOptions.Ordering> ordering = new ArrayList<QueryOptions.Ordering>();
        ordering.add(new QueryOptions.Ordering("lastname", true));
        ordering.add(new QueryOptions.Ordering("firstname", true));
        ordering.add(new QueryOptions.Ordering("id", true));

        QueryOptions queryOps = new QueryOptions();
        queryOps.add(Filter.ne("certification.phase", Certification.Phase.Staged));
        queryOps.setOrderings(ordering);

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("certification.type", certType));

        Filter userFilters = getUserSpecifiedFilters("certification.");
        if (userFilters != null)
            filters.add(userFilters);

        Filter f = getObjectFilter("certification.");
        if (f!= null)
            filters.add(f);
        
        if(filters.size()>1)
        	queryOps.add(Filter.and(filters));
        else if(filters.size()>0) 
        	queryOps.add(filters.get(0));

        return queryOps;
    }

    private Filter getObjectFilter(String propertyPrefix) throws GeneralException{
        Filter filter = null;
        switch(certType){
            case Manager:
                if (objectIds != null && !objectIds.isEmpty())
                    filter = Filter.in(propertyPrefix + "manager", objectIds);
                break;
            case ApplicationOwner:
                if (objectIds != null && !objectIds.isEmpty())
                    filter = Filter.in(propertyPrefix + "applicationId", objectIds);
                break;
            case Group:
                if (objectIds != null && !objectIds.isEmpty())
                    filter = Filter.in(propertyPrefix + "groupDefinitionId", objectIds);
                break;
            default:
                throw new RuntimeException("Unhandled certification type");
        }

        return filter;
    }

    /**
     * @return Base query options for retrieving all excluded CertificationEntity objects
     *         from identity certifications.
     */
    private QueryOptions getExclusionQueryOptions() {

        QueryOptions queryOps = new QueryOptions();
        queryOps.add(Filter.not(Filter.isempty("archivedEntities")));

        // Non-null phase if certification has been started.
        queryOps.add(Filter.notnull("phase"));
        queryOps.add(Filter.ne("phase", Certification.Phase.Staged));

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("type", certType));

        Filter userFilters = getUserSpecifiedFilters("");
        if (userFilters != null)
            filters.add(userFilters);
        
        try { 
            Filter f = getObjectFilter("");
            if (f!= null)
                filters.add(f);        
        } catch (GeneralException ge) {}

        queryOps.add(Filter.and(filters));

        return queryOps;
    }

    /**
     *
     * @param item The certification item containing the entitlement to describe
     * @return Friendly text description of the entitlement in the given certification item.
     */
    private String getEntitlementDescription(CertificationItem item) {

        if (item.getBundle() != null)
            return item.getBundle();
        else if (item.getExceptionEntitlements() != null){
            Message msg = item.getExceptionEntitlementsDescription();
            return msg.getLocalizedMessage(locale, timezone);
        } else if (item.getViolationSummary() != null)
            return item.getViolationSummary();

        return null;
    }

    /**
     * @param item CertificationItem to retrieve native identity from.
     * @return Native identity, or set of native identities
     *         if more than one distinct native id is found.
     */
    private Set<String> getAccounts(CertificationItem item) {

        Set<String> nativeIds = new HashSet<String>();
        if (item.getExceptionEntitlements() != null)
            nativeIds.add(item.getExceptionEntitlements().getDisplayableName());

        if (item.getPolicyViolation() != null) {
            Object accountsArg = item.getPolicyViolation().getArgument(AccountPolicyExecutor.VIOLATION_ACCOUNTS);
            if (accountsArg != null) {
                List accounts = (List) accountsArg;
                if (accounts.isEmpty())
                    nativeIds.addAll(accounts);
            }
        } else if (item.getBundleEntitlements() != null) {
            List<EntitlementSnapshot> snaps = item.getBundleEntitlements();
            if (snaps != null && !snaps.isEmpty()) {
                for(EntitlementSnapshot snap : snaps){   
                    nativeIds.add(snap.getDisplayableName());
                }
            }
        }

        return nativeIds;
    }

    /**
     * Comparator for sorting rows in this report. sorts on user lastname, firstname
     */
    private static Comparator<Attributes> ROW_SORTER = new Comparator<Attributes>() {

        public int compare(Attributes o1, Attributes o2) {
            String lName1 = o1.getString("lastName") != null ? o1.getString("lastName") : "";
            String lName2 = o2.getString("lastName") != null ? o2.getString("lastName") : "";

            if (lName1.compareToIgnoreCase(lName2) != 0)
                return lName1.compareToIgnoreCase(lName2);

            String fName1 = o1.getString("firstName") != null ? o1.getString("firstName") : "";
            String fName2 = o2.getString("firstName") != null ? o2.getString("firstName") : "";

            return fName1.compareToIgnoreCase(fName2);
        }
    };
}
