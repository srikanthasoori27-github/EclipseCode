/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class AccountGroupMembershipCertificationReportDataSource extends BaseCertificationDataSource {
	private static final Log log = LogFactory.getLog(AccountGroupMembershipCertificationReportDataSource.class);
    private static final int PAGE_SIZE = 100;

    private List<String> applications;
    private Set<String> aGroupNames = new HashSet<String>();

    private List<Attributes<String,String>> excludedItems;
    private Iterator<Attributes<String,String>> items;

    private int page;
    private Attributes currentRow;


    /**
     * Default constructor
     *
     */
    public AccountGroupMembershipCertificationReportDataSource(List<String> applications,
            Locale locale, TimeZone timezone) {
        super(locale, timezone);
        if (applications==null || applications.isEmpty())
            throw new IllegalArgumentException("At least one application must be selected.");

        this.applications = applications;
    }

    /**
     * Initialize the report. Queries all excluded certifications and
     * stores them for later merge into the report.
     *
     * @throws sailpoint.tools.GeneralException
     */
    @Override
    public void internalPrepare() throws GeneralException {
        //Use entity count logic (see BaseCertificationDataSource)
        super.internalPrepare();

        // jsl - this will not scale in practice!  Since you only
        // appear to need the name do a projection search
        List<ManagedAttribute> accountGroups = getContext().getObjects(ManagedAttribute.class, new QueryOptions(Filter.in("application.id", applications)));
        if (accountGroups != null){
            for (ManagedAttribute accountGroup : accountGroups){
                aGroupNames.add(accountGroup.getName());
            }
        }

        page = 0;
        items = null;
        excludedItems = new ArrayList<Attributes<String,String>>();

        updateProgress("Computing Exclusions...");
        List<Certification> certs = getContext().getObjects(Certification.class, getExclusionQueryOptions());
        if (certs != null){
            for(Certification cert : certs){
                for (ArchivedCertificationEntity archived : cert.fetchArchivedEntities(getContext())) {
                    CertificationEntity entity = archived.getEntity();
                    List<CertificationItem> items = getRelevantItems(entity);
                    if (items!=null && items.size() > 0){
                        //Total entity count will not include archived entity, so add it here.
                        setTotalEntityCount(getTotalEntityCount()+1);
                        for(CertificationItem item : items){
                            excludedItems.add(createRow(archived.getCertification(), item, archived.getReason(), archived.getExplanation()));
                        }
                    }
                }
            }
        }
        
        getContext().decache();
        Collections.sort(excludedItems, ROW_SORTER);
        updateProgress("Exclusions Computed...");
    }

    /**
     * Gets the given field from the current report row.
     *
     * @param jrField The field from the jasper template to retrieve
     * @return Field value
     * @throws net.sf.jasperreports.engine.JRException
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
    private Attributes<String,String> createRow(Certification cert, CertificationItem item,
                                                ArchivedCertificationEntity.Reason exclusionReason,
                                                String exclusionExplanation)
        throws GeneralException {

        Attributes<String,String> row = new Attributes<String,String>();
        putEntityIdForPercent(row, item);

        row.put("nativeIdentity", item.getExceptionEntitlements().getDisplayableName());
        row.put("application", item.getExceptionEntitlements().getApplication());

        String idOrName = item.getTargetId() != null ? item.getTargetId() : item.getTargetName();
        boolean useId = item.getTargetId() != null;
        if (idOrName != null){
            Identity identity = useId ? getContext().getObjectById(Identity.class, idOrName) : getContext().getObjectByName(Identity.class, idOrName);
            if (identity != null){
                row.put("identity", identity.getName());
                row.put("firstName", identity.getFirstname());
                row.put("lastName", identity.getLastname());
                if(identity.getManager()!=null)
                    row.put("manager", identity.getManager().getName());
            }
        }

        if (exclusionReason != null)
           row.put("status", getMessage(exclusionReason.getMessageKey()));
        else
           row.put("status", item.getSummaryStatus() != null ?
                   getMessage(item.getSummaryStatus().getMessageKey()) : null);

        row.put("exclusionExplanation", exclusionExplanation);

        if (item.getAction() != null){
            row.put("decisionMaker", item.getAction() != null ? item.getAction().getActorName() : null);
            CertificationAction.Status stat =  item.getAction().getStatus();
            if (stat != null) {
                if (DataSourceUtil.isRemediationModified(item.getAction())) {
                    row.put("decision", getMessage(CertificationAction.Status.Modified.getMessageKey(), ""));
                    row.put("newValue", Util.otos(DataSourceUtil.getRemediationModifiableNewValue(item.getAction())));
                } else {
                    row.put("decision", getMessage(stat.getMessageKey()));
                }
            }

            row.put("comments", item.getAction().getComments());
            row.put("remediationCompleted", getRemediationStatus(item,
                    MessageKeys.REPT_ACCOUNT_GRP_MEMB_STATUS_REMED_COMPLETED));
        }

        row.put("accountGroup", item.getParent().getAccountGroup());

        // the is now referred to as 'Access Review' leaving it as
        // 'certName' for backwards compatibility with customized reports
        row.put("certName", cert.getName());

        String certGrp = cert.getCertificationName();
        row.put("certGroupName", certGrp != null ? certGrp : "");

        return row;
    }


    /**
     * Gets the next item on the list.
     *
     * @return True if there are more items in the datasource
     * @throws JRException
     */
    public boolean internalNext() throws JRException {

        if (items == null || !items.hasNext()){
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
     * internalInit() method ran. We merge, rather than adding these to the
     * front or end of the report so that we can maintain the
     * lastname, firstname sort order.
     *
     * @throws GeneralException
     */
    private void getNextPage() throws GeneralException{

        page++;

        qo.setResultLimit(PAGE_SIZE);
        qo.setFirstRow((page-1) * PAGE_SIZE);

        List<Attributes<String,String>> page = new ArrayList<Attributes<String,String>>();
        List<CertificationEntity> entities = getContext().getObjects(CertificationEntity.class, qo);

        for(CertificationEntity entity : entities){
            List<CertificationItem> relevantItems = getRelevantItems(entity);
            if (relevantItems == null || relevantItems.size() == 0) {
                //If there are no items, this entity has nothing to do later, so just increment processed count 
                incrementProcessedEntityCount();
            }
            else {
                for(CertificationItem item : relevantItems){
                    Attributes<String,String> row = createRow(entity.getCertification(), item, null, null);
                    List<Attributes<String,String>> excludedRowsToMerge = getExludedItemsToMerge(row);
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
    private List<Attributes<String,String>> getExludedItemsToMerge(Attributes row){

        if (excludedItems == null || excludedItems.isEmpty())
            return null;

        List<Attributes<String,String>> mergeRows = new ArrayList<Attributes<String,String>>();
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

        List<CertificationItem> foundItems = new ArrayList<CertificationItem>();

        if (entity.getItems() == null || entity.getItems().isEmpty())
            return foundItems;

        
        return entity.getItems();
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

        QueryOptions queryOps = new QueryOptions();
        queryOps.setOrderings(ordering);

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("certification.type", Certification.Type.AccountGroupMembership));
        filters.add(Filter.in("application", this.applications));
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

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("type", Certification.Type.AccountGroupMembership));

        Filter userFilters = getUserSpecifiedFilters("");
        if (userFilters!=null)
            filters.add(userFilters);

        queryOps.add(Filter.and(filters));

        return queryOps;
    }

    /**
     * @param item CertificationItem to retrieve native identity from.
     * @return Native identity, or comma delimited list of native identities
     * if more than one distinct native id is found.
     */
    private String getAccount(CertificationItem item){

        if (item.getExceptionEntitlements() != null)
            return item.getExceptionEntitlements().getDisplayName();

        Set<String> nativeIds = new HashSet<String>();
        if (item.getBundleEntitlements() != null){
            List<EntitlementSnapshot> snaps = item.getBundleEntitlements();
            for(EntitlementSnapshot snap : snaps){
                nativeIds.add(snap.getDisplayableName());
            }

            if (!nativeIds.isEmpty()){
                if (nativeIds.size()==1){
                    return nativeIds.iterator().next();
                }else{
                    return "[" + Util.join(nativeIds, ",") + "]";
                }
            }
        }


        return null;
    }

    /**
     * Comparator for sorting rows in this report. sorts on user lastname, 
     * firstname
     */
    private static Comparator<Attributes> ROW_SORTER = 
      new Comparator<Attributes>(){
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
