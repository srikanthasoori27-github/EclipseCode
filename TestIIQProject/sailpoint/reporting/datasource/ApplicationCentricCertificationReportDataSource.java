/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Arrays;
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
import sailpoint.object.Application;
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationAction.Status;
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

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ApplicationCentricCertificationReportDataSource
    extends BaseCertificationDataSource {

    private static final int PAGE_SIZE = 100;

    private Application application;

    private List<Attributes> excludedItems;
    private Iterator<Attributes> items;

    private int page;
    private int totalPages;
    private Attributes currentRow;

    private Locale locale;
    private TimeZone timezone;

    /**
     * Default constructor
     *
     * @param application The application to report on. Must be non-null
     */
    public ApplicationCentricCertificationReportDataSource(Application application, Attributes inputs,
                                                           Locale locale, TimeZone tz) {
        super(locale, tz);
        if (application==null)
            throw new IllegalArgumentException("Application may not be null.");

        this.application = application;
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

        page = 0;
        items = null;
        excludedItems = new ArrayList<Attributes>();
        
        totalPages = (getTotalEntityCount() / PAGE_SIZE) + 1;
        updateProgress("Computing the exclusions...");
        List<Certification> certs = getContext().getObjects(Certification.class, getExclusionQueryOptions());
        if (certs != null){
            for(Certification cert : certs) {
                for (ArchivedCertificationEntity archived : cert.fetchArchivedEntities(getContext())) {
                    CertificationEntity entity = archived.getEntity();
                    List<CertificationItem> items = getRelevantItems(entity);
                    if (items!=null && items.size() > 0){
                        //Total entity count will not include archived entity, so add it here.
                        setTotalEntityCount(getTotalEntityCount()+1);
                        for(CertificationItem item : items){
                            excludedItems.add(createRow(item, archived.getReason(), archived.getExplanation()));
                        }
                    }
                }
            }
        }
        getContext().decache();
        Collections.sort(excludedItems, ROW_SORTER);
        updateProgress("Exclusions computed...");
    }

    /**
     * Gets the given field from the current report row.
     *
     * @param jrField The field from the jasper template to retrieve
     * @return Field value
     * @throws JRException
     */
    public Object getFieldValue(JRField jrField) throws JRException {

        String fieldName = jrField.getName();

        if (fieldName==null)
            throw new RuntimeException("No field specified");

        if (DataSourceUtil.CURRENT_BEAN_FIELD_NAME.equals(fieldName))
            return currentRow;

        Object val = currentRow.get(fieldName);
       
        // Add some spaces ifnull so we don't get any blank cells. This seems to
        // happen randonly on the first row on the page. This hack 'fixes' the problem.
        return val==null || val.toString().length() == 0 ? "  " : val;
    }

    /**
     * Creates a report row. Builds a map with all the datapoints we're interested
     * in. The keys in the report match the field names coming from the jasper
     * template.
     *
     * @param item CertificationItem to convert into a report row
     * @return Map of properties for the given certification item
     * @throws GeneralException
     */
    protected Attributes<String,String> createRow(CertificationItem item, ArchivedCertificationEntity.Reason exclusionReason,
                                                  String exclusionExplanation)
        throws GeneralException {

        Attributes<String,String> row = new Attributes<String,String>();

        putEntityIdForPercent(row, item);
        row.put("nativeIdentity", getAccount(item));

        Identity identity = null;
        if (CertificationItem.Type.AccountGroupMembership.equals(item.getType()) 
                || CertificationItem.Type.DataOwner.equals(item.getType())){
            if (item.getTargetId() != null)
                identity = getContext().getObjectById(Identity.class, item.getTargetId());
        } else {
            identity = item.getParent().getIdentity(getContext());
        }

        if(identity != null){
            row.put("identity", identity.getName());
            row.put("firstName", identity.getFirstname());
            row.put("lastName", identity.getLastname());

            IdentitySnapshot snapshot = item.getParent() != null ?
                    item.getParent().getIdentitySnapshot(getContext()) : null;
           
            // We always want to use the manager from the time of the certification,
            // not the current manager.
            if ( snapshot != null && snapshot.getAttributes() != null ){
            	String managerId = snapshot.getAttributes().getString( "manager" );
                row.put( "manager", managerId );
                /* BUG #6635: Retrieve manager Identity to display actual name in report  */ 
                if( managerId != null ) {
                	Identity manager = getContext().getObjectByName( Identity.class, managerId );
                	if( manager != null )
                		row.put( "managerName", manager.getDisplayableName() );
                }
            }
        }

        row.put("entitlementType", entitlementTypeFromCertItem(item));

        if (exclusionReason != null){
           row.put("status",getMessage(exclusionReason.getMessageKey()));
        } else {
           String key = item.getSummaryStatus() != null ? item.getSummaryStatus().getMessageKey() : null;
           row.put("status", getMessage(key, ""));
        }

        row.put("exclusionExplanation", exclusionExplanation);

        row.put("remediationCompleted", getRemediationStatus(item,
                    MessageKeys.REPT_CERT_ACTIVITY_VAL_REMED_COMPLETED));

        if (item.getAction() != null){
            row.put("comments", item.getAction().getComments());
            Identity actor = item.getAction().getActor(getContext());
            row.put("decisionMaker", actor != null ? actor.getDisplayableName() 
                    : item.getAction().getActorName());
            if (item.getAction().getStatus() != null) {
                if (Status.Remediated.equals(item.getAction().getStatus()) && item.getAction().isRevokeAccount()) {
                    //special handling for revoke account
                    row.put("decision", getMessage(Status.RevokeAccount.getMessageKey(), ""));
                } else if (DataSourceUtil.isRemediationModified(item.getAction())) {
                    row.put("decision", getMessage(CertificationAction.Status.Modified.getMessageKey(), ""));
                    row.put("newValue", Util.otos(DataSourceUtil.getRemediationModifiableNewValue(item.getAction())));
                } else {
                    row.put("decision", getMessage(item.getAction().getStatus().getMessageKey(), ""));
                }
            }
            else
                row.put("", getMessage(item.getAction().getStatus().getMessageKey(), ""));
        } else {
            row.put("decisionMaker", "");
            row.put("comments", "");
        }

        row.put("entitlement", getEntitlementDescription(item));


        return row;
    }

    private String entitlementTypeFromCertItem(CertificationItem item) {
        
        if (CertificationItem.Type.DataOwner == item.getType()) {
            return item.getSubType() != null ? getMessage(item.getSubType().getMessageKey(), "") : "";
        } else {
            return item.getType() != null ? getMessage(item.getType().getMessageKey(), "") : "";
        }
    }

    

    /**
     * Gets the next item on the list.
     *
     * @return True if there are more items in the datasource
     * @throws JRException
     */
    public boolean internalNext() throws JRException {

        while(page < totalPages && (items == null || !items.hasNext())){
            try {
                getNextPage();
            } catch (GeneralException e) {
                throw new JRException(e);
            }
        }

        if (!items.hasNext() && excludedItems != null){
            items = excludedItems.iterator();
            excludedItems = null;
        }

        if (items.hasNext()){
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
    private void getNextPage() throws GeneralException{

        page++;

        qo.setResultLimit(PAGE_SIZE);
        qo.setFirstRow((page-1) * PAGE_SIZE);

        List<Attributes> page = new ArrayList<Attributes>();
        List<CertificationEntity> entities = getContext().getObjects(CertificationEntity.class, qo);

        for(CertificationEntity entity : entities){
            List<CertificationItem> relevantItems = getRelevantItems(entity);
            if (relevantItems == null || relevantItems.size() == 0){
                //If there are no items, this entity has nothing to do later, so just increment processed count 
                incrementProcessedEntityCount();
            }
            else {
                for(CertificationItem item : relevantItems){
                    Attributes row = createRow(item, null, null);
                    List<Attributes> excludedRowsToMerge = getExludedItemsToMerge(row);
                    if (excludedRowsToMerge != null && !excludedRowsToMerge.isEmpty())
                        page.addAll(excludedRowsToMerge);
                    page.add(row);
                }
            }
        }

        if (!entities.isEmpty())
            getContext().decache();

        items = page.iterator();
    }

    /**
     * Looks for any excluded items we found in the internalPrepare() method that
     * should be returned before the given row. This is based on
     * lastname, firstname sort.
     *
     * @param row The current row
     * @return List of rows that should be inserted before the given row.
     */
    private List<Attributes> getExludedItemsToMerge(Attributes row){

        if (excludedItems == null || excludedItems.isEmpty())
            return null;

        List<Attributes> mergeRows = new ArrayList<Attributes>();
        while(!excludedItems.isEmpty() && ROW_SORTER.compare(excludedItems.get(0), row) < 1){
            mergeRows.add(excludedItems.get(0));
            excludedItems.remove(excludedItems.get(0));
        }

        return mergeRows;
    }

    /**
     * Returns any relevant certification items from the given certification
     * entity. A certification item is relevant if it includes an entitlement
     * on the application we're reporting on.
     *
     * @param entity The entity to retrieve relevant certification items from.
     * @return List of CertificationItem which included an entitlement for the
     * application we're reporting on.
     * @throws GeneralException
     */
    public List<CertificationItem> getRelevantItems(CertificationEntity entity)
            throws GeneralException {
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("parent.id", entity.getId()));
        options.add(Filter.containsAll("applicationNames", Arrays.asList(application.getName())));
        
        Iterator<CertificationItem> relevantItems = getContext().search(CertificationItem.class, options);
        return Util.iteratorToList(relevantItems);  
    }

    /**
     * @return Base query options for retrieving all CertificationEntity objects
     * from identity certifications.
     */
    @Override
    protected QueryOptions getQueryOptions(){

        List<QueryOptions.Ordering> ordering = new ArrayList<QueryOptions.Ordering>();
        ordering.add(new QueryOptions.Ordering("lastname", true));
        ordering.add(new QueryOptions.Ordering("firstname", true));
        ordering.add(new QueryOptions.Ordering("identity", true));

        QueryOptions queryOps = new QueryOptions();
        queryOps.setOrderings(ordering);

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.ne("type", Certification.Type.AccountGroupPermissions));
        filters.add(Filter.ne("type", Certification.Type.BusinessRoleComposition));

        //todo could these be included?
        filters.add(Filter.ne("type", Certification.Type.AccountGroupMembership));
        
        filters.add(Filter.ne("certification.phase", Certification.Phase.Staged));

        Filter userFilters = getUserSpecifiedFilters("certification.");
        if (userFilters!=null)
            filters.add(userFilters);

        queryOps.add(Filter.and(filters));

        return queryOps;
    }

    /**
     * @return Base query options for retrieving all excluded CertificationEntity objects
     *  from identity certifications.
     */
    private QueryOptions getExclusionQueryOptions(){

        QueryOptions queryOps = new QueryOptions();
        queryOps.add(Filter.not(Filter.isempty("archivedEntities")));

        // Non-null phase if certification has been started.
        queryOps.add(Filter.notnull("phase"));
        queryOps.add(Filter.ne("phase", Certification.Phase.Staged));
        
        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.ne("type", Certification.Type.AccountGroupPermissions));
        filters.add(Filter.ne("type", Certification.Type.BusinessRoleComposition));
      
        Filter userFilters = getUserSpecifiedFilters("");
        if (userFilters!=null)
            filters.add(userFilters);

        queryOps.add(Filter.and(filters));

        return queryOps;
    }

    /**
     *
     * @param item The certification item containing the entitlement to describe
     * @return Friendly text description of the entitlement in the given certification item.
     */
    private String getEntitlementDescription(CertificationItem item){

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
     * @return Native identity, or comma delimited list of native identities
     * if more than one distinct native id is found.
     */
    private String getAccount(CertificationItem item){

        if (item.getExceptionEntitlements() != null)
            return item.getExceptionEntitlements().getDisplayableName();

        Set<String> nativeIds = new HashSet<String>();
        if (item.getPolicyViolation() != null){
            Object accountsArg = item.getPolicyViolation().getArgument(AccountPolicyExecutor.VIOLATION_ACCOUNTS);
            if (accountsArg != null){
                List accounts = (List)accountsArg;
                if (accounts.isEmpty())
                    return null;

                // this will join the accounts using the locale specific delimiter
                Message msg = new Message(MessageKeys.MSG_PLAIN_TEXT, accounts);
                return msg.getLocalizedMessage(locale, timezone);
            }
        }else if (item.getBundleEntitlements() != null){
            List<EntitlementSnapshot> snaps = item.getBundleEntitlements();
            for(EntitlementSnapshot snap : snaps){
                if (application.getName().equals(snap.getApplication())){
                    nativeIds.add(snap.getDisplayableName());
                }
            }

            if (!nativeIds.isEmpty()){
                if (nativeIds.size()==1){
                    return nativeIds.iterator().next();
                }else{
                    // this will join the nativeIds using the locale specific delimiter
                    Message msg = new Message(MessageKeys.MSG_PLAIN_TEXT, nativeIds);
                    return msg.getLocalizedMessage(locale, timezone);
                }
            }
        }


        return null;
    }

    /**
     * Comparator for sorting rows in this report. sorts on user lastname, firstname, identity id
     */
    private static Comparator<Attributes> ROW_SORTER = new Comparator<Attributes>(){

        public int compare(Attributes o1, Attributes o2) {
            String lName1 = o1.getString("lastName") != null ? o1.getString("lastName") : "";
            String lName2 = o2.getString("lastName") != null ? o2.getString("lastName") : "";

            if (lName1.compareToIgnoreCase(lName2) != 0)
                return lName1.compareToIgnoreCase(lName2);

            String fName1 = o1.getString("firstName") != null ? o1.getString("firstName") : "";
            String fName2 = o2.getString("firstName") != null ? o2.getString("firstName") : "";

            if (fName1.compareToIgnoreCase(fName2) != 0)
                return fName1.compareToIgnoreCase(fName2);
            
            String identityId1 = o1.getString("identity") != null ? o1.getString("identity") : "";
            String identityId2 = o2.getString("identity") != null ? o2.getString("identity") : "";
            
            return identityId1.compareToIgnoreCase(identityId2);
        }
    };
}
