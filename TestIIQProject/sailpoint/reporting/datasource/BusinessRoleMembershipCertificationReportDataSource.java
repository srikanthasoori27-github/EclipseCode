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
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class BusinessRoleMembershipCertificationReportDataSource
    extends BaseCertificationDataSource {

    private static final int PAGE_SIZE = 100;

    private List<String> roleIds;
    private Set<String> roleNames = new HashSet<String>();

    private List<Attributes<String,String>> excludedItems;
    private Iterator<Attributes<String,String>> items;

    private int page;
    private Attributes currentRow;

    /**
     * Default constructor
     *
     * @param roleIds List of role IDs to report on. Must be non-null and non-empty
     * @param roleIds 
     */
    public BusinessRoleMembershipCertificationReportDataSource(List<String> roleIds,  Locale locale, TimeZone timezone) {
        super(locale, timezone);
        this.roleIds = roleIds;
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
        
        if (roleIds != null && !roleIds.isEmpty()){
            List<Bundle> bundles = getContext().getObjects(Bundle.class, new QueryOptions(Filter.in("id", roleIds)));
            if (bundles != null){
                for (Bundle bundle : bundles){
                    roleNames.add(bundle.getName());
                }
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
                    if (items!=null && items.size() > 0) {
                        //Total entity count will not include archived entity, so add it here.
                        setTotalEntityCount(getTotalEntityCount()+1);
                        for(CertificationItem item : items){
                            excludedItems.add(createRow(cert, item, archived.getReason(), archived.getExplanation()));
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
    private Attributes<String,String> createRow(Certification cert,
                                                CertificationItem item,
                                                ArchivedCertificationEntity.Reason exclusionReason,
                                                String exclusionExplanation)
        throws GeneralException {

        Attributes<String,String> row = new Attributes<String,String>();
        putEntityIdForPercent(row, item);
        
        row.put("nativeIdentity", getAccount(item));
        row.put("identity", item.getParent().getIdentity());
        row.put("firstName", item.getParent().getFirstname());
        row.put("lastName", item.getParent().getLastname());

        row.put("certName", cert.getName());
        
        String certGrp = cert.getCertificationName();
        row.put("certGroupName", certGrp != null ? certGrp : "");

        row.put("tags", WebUtil.objectListToNameString(cert.getTags()));

        String entitlementTypeName = null;
        switch(item.getType()){
            case Bundle:
                entitlementTypeName = MessageKeys.REPT_MEMBER_CERT_ENTITLEMENT_TYPE_ROLE;
                break;
            case Exception:
                entitlementTypeName = MessageKeys.REPT_MEMBER_CERT_ENTITLEMENT_TYPE_EXCEPTION;
                break;
            case PolicyViolation:
                entitlementTypeName = MessageKeys.REPT_MEMBER_CERT_ENTITLEMENT_TYPE_VIOLATION;
                break;
        }
        row.put("entitlementType", entitlementTypeName);

        if (exclusionReason != null)
           row.put("status", exclusionReason.getMessageKey());
        else
           row.put("status", item.getSummaryStatus() != null ?
                   item.getSummaryStatus().getMessageKey() : null);

        row.put("exclusionExplanation", exclusionExplanation);

        if (item.getAction() != null){
            row.put("decisionMaker", item.getAction() != null ? item.getAction().getActorName() : null);
            CertificationAction.Status stat =  item.getAction().getStatus();
            if (stat != null){
                String decision = null;
                switch(stat){
                    case Remediated:
                        decision = MessageKeys.REPT_MEMBER_CERT_REVOKED;
                        break;
                    case Mitigated:
                        decision = MessageKeys.REPT_MEMBER_CERT_ALLOWED;
                        break;
                    default:
                        decision = stat.toString();
                }
                row.put("decision", decision);
            }

            row.put("comments", item.getAction().getComments());
            row.put("remediationCompleted", getRemediationStatus(item,
                    MessageKeys.REPT_MEMBER_CERT_COL_REVOKED));
        }

        //row.put("bundle", item.getBundle());
        row.put("entitlement", getEntitlementDescription(item));

        IdentitySnapshot snapshot = item.getParent().getIdentitySnapshot(getContext());

        if (snapshot != null && snapshot.getAttributes()!=null){
            row.put("manager", snapshot.getAttributes().getString("manager"));
        }

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
                    Attributes<String,String> row = createRow(item.getCertification(), item, null, null);
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

        // Iterate over thie certifiables created for this identity and check if
        // the certifiable refererences one of the bundles we're reporting on
        for(CertificationItem item : entity.getItems()){
            if (item.getBundle() != null){
                if (roleNames == null || roleNames.isEmpty() || roleNames.contains(item.getBundle())){
                    foundItems.add(item);
                }else{
                    // if we don't see the name in the name list, it may mean that
                    // the bundle name has changed since the cert was started,
                    // so we need to compare the ID to our list of role IDs.
                    Bundle b = item.getBundle(getContext());
                    if (b != null && roleIds.contains(b.getId())){
                        foundItems.add(item);
                    }
                }
            }
        }

        return foundItems;
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
        filters.add(Filter.eq("certification.type", Certification.Type.BusinessRoleMembership));
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
        filters.add(Filter.eq("type", Certification.Type.BusinessRoleMembership));

        Filter userFilters = getUserSpecifiedFilters("");
        if (userFilters!=null)
            filters.add(userFilters);

        queryOps.add(Filter.and(filters));

        return queryOps;
    }

    /**
     *
     *
     * @param item The certification item containing the entitlement to describe
     * @return Friendly text description of the entitlement in the given certification item.
     */
    private String getEntitlementDescription(CertificationItem item){

        if (item.getBundle() != null)
            return item.getBundle();
        else if (item.getExceptionEntitlements() != null) {
            return item.getExceptionEntitlementsDescription().getLocalizedMessage(getLocale(), getTimezone());
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
