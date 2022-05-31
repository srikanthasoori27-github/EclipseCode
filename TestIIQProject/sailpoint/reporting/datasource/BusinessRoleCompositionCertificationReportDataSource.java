/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class BusinessRoleCompositionCertificationReportDataSource 
       extends BaseCertificationDataSource {

    private static final int PAGE_SIZE = 100;

    private List<String> businessRoleIds;

    private Iterator<Attributes> items;

    private int page;
    private Attributes currentRow;

    /**
     * Default constructor
     *
     * @param businessRoleIds The role IDs to report on. Must be non-null
     */
    public BusinessRoleCompositionCertificationReportDataSource(List<String> businessRoleIds,
            Locale locale, TimeZone timezone) {

        super(locale, timezone);
        this.businessRoleIds = businessRoleIds;
        items = null;
        page = 0;
    }

    /**
     * Initialize the report.
     *
     * @throws sailpoint.tools.GeneralException
     */
    @Override
    public void internalPrepare() throws GeneralException {
        //Use entity count logic (see BaseCertificationDataSource)
        super.internalPrepare();
        page = 0;
        items = null;
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
    private Attributes<String,String> createRow(Certification cert, CertificationItem item) throws GeneralException {

        Attributes<String,String> row = new Attributes<String,String>();
        putEntityIdForPercent(row, item);
        SailPointObject targetObj = item.getTargetObject(getContext());
        if (targetObj != null){
            String localized = Internationalizer.getMessage(targetObj.getDescription(), getLocale());
            row.put("description", localized != null ? localized : targetObj.getDescription());
        }

        row.put("name", item.getParent().getTargetName());

        if (item.getTargetName() != null){
            row.put("childName", item.getTargetName());
        } else if (CertificationItem.Type.BusinessRoleProfile.equals(item.getType()) && item.getParent() != null){
            RoleSnapshot snap = item.getParent().getRoleSnapshot();
            if (snap != null){
                RoleSnapshot.ProfileSnapshot profileSnap = snap.getProfileSnapshot(item.getTargetId());
                if (profileSnap != null){
                    Message msg = new Message(MessageKeys.TEXT_ENTITLEMENTS_ON_APP, profileSnap.getApplication());
                    String name = msg.getLocalizedMessage(getLocale(), null);
                    if (name != null)
                        row.put("childName", name);
                }
            }
        }
        row.put("type", Internationalizer.getMessage(item.getType().getMessageKey(), getLocale()));

        row.put("status", item.getSummaryStatus() != null ?
                item.getSummaryStatus().getMessageKey() : null);

        if (item.getAction() != null){
            row.put("decisionMaker", item.getAction() != null ? item.getAction().getActorName() : null);
            CertificationAction.Status stat =  item.getAction().getStatus();
            if (stat != null){
                String decision = null;
                switch(stat){
                    case Remediated:
                        decision = MessageKeys.REPT_COMP_CERT_REVOKED;
                        break;
                    case Mitigated:
                        decision = MessageKeys.REPT_COMP_CERT_ALLOWED;
                        break;
                    default:
                        decision = stat.toString();
                }
                row.put("decision", decision);
            }

            row.put("comments", item.getAction().getComments());

            row.put("remediationCompleted", getRemediationStatus(item, MessageKeys.REPT_COMP_CERT_REMED_COMPLETED));
        }

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

        if (items.hasNext()){
            currentRow = items.next();
            checkProcessedEntity(currentRow, true);
            return true;
        }

        return false;
    }

    /**
     * Gets the next page of report data.
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
        if (entity.getItems() == null || entity.getItems().size() == 0) {
                //If there are no items, this entity has nothing to do later, so just increment processed count 
                incrementProcessedEntityCount();
            }
            else {
                for(CertificationItem item : entity.getItems()){
                    Attributes<String,String> row = createRow(entity.getCertification(), item);
                    page.add(row);
                }
            }
        }

        if (!entities.isEmpty())
            getContext().decache();

        items = page.iterator();
    }

    /**
     * @return Base query options for retrieving all CertificationEntity objects
     * from identity certifications.
     */
    @Override
    protected QueryOptions getQueryOptions() {
        List<QueryOptions.Ordering> ordering = new ArrayList<QueryOptions.Ordering>();
        ordering.add(new QueryOptions.Ordering("targetName", true));

        QueryOptions queryOps = new QueryOptions();
        queryOps.setOrderings(ordering);

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("certification.type", Certification.Type.BusinessRoleComposition));
        filters.add(Filter.ne("certification.phase", Certification.Phase.Staged));
        
        if (businessRoleIds != null && !businessRoleIds.isEmpty())
            filters.add(Filter.in("targetId", this.businessRoleIds));

        Filter userFilters = getUserSpecifiedFilters("certification.");
        if (userFilters!=null)
            filters.add(userFilters);

        queryOps.add(Filter.and(filters));

        return queryOps;
    }

}