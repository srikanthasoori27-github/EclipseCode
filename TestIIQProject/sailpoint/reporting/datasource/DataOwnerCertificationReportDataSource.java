/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.certification.DataOwnerCertifiableEntity;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

/**
 * @author <a href="mailto:tpox.mozambo@sailpoint.com">Tpox Mozambo</a>
 */
public class DataOwnerCertificationReportDataSource extends BaseCertificationDataSource {
	private static final Log log = LogFactory.getLog(DataOwnerCertificationReportDataSource.class);
    private static final int PAGE_SIZE = 100;

    private List<String> applicationNames;

    private Iterator<Attributes<String, Object>> items;

    private int page;
    private Attributes<String, Object> currentRow;


    public DataOwnerCertificationReportDataSource(List<String> applicationNames,
            Locale locale, TimeZone timezone) {
        
        super(locale, timezone);
        
        if (applicationNames==null || applicationNames.isEmpty()) {throw new IllegalArgumentException("At least one application must be selected.");}

        this.applicationNames = applicationNames;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void internalPrepare() throws GeneralException {
        super.internalPrepare();

        this.page = 0;
        this.items = null;

    }
    
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

    public Object getFieldValue(JRField jrField) throws JRException {

        String fieldName = jrField.getName();

        if (fieldName==null) {throw new RuntimeException("No field specified");}

        if (DataSourceUtil.CURRENT_BEAN_FIELD_NAME.equals(fieldName)) {return currentRow;}

        Object val = currentRow.get(fieldName);

        return val==null || val.toString().length() == 0 ? "  " : val;
    }

    @Override
    protected QueryOptions getQueryOptions(){

        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("type", CertificationItem.Type.DataOwner));
        options.add(Filter.in("application", this.applicationNames));
        options.add(getUserSpecifiedFilters("certification."));
        
        options.addOrdering("application", true);
        options.addOrdering("referenceAttribute", true);
        options.addOrdering("nativeIdentity", true);
        options.addOrdering("targetName", true);
        
        return options;
    }
    
    private void getNextPage() throws GeneralException{

        this.page++;
        if (log.isInfoEnabled()) {log.info("fetching page: " + this.page);}

        this.qo.setResultLimit(PAGE_SIZE);
        this.qo.setFirstRow((this.page-1) * PAGE_SIZE);

        List<Attributes<String, Object>> page = new ArrayList<Attributes<String,Object>>();
        List<CertificationEntity> certEntities = getContext().getObjects(CertificationEntity.class, this.qo);

        for (CertificationEntity certEntity : certEntities) {
            if (certEntity.getItems() == null || certEntity.getItems().size() == 0) {
                incrementProcessedEntityCount();
            }
            else {
                for(CertificationItem certItem : certEntity.getItems()){
                    Attributes<String, Object> row = createRow(certItem.getCertification(), certItem);
                    page.add(row);
                }
            }
        }

        if (!certEntities.isEmpty()) {getContext().decache();}

        this.items = page.iterator();
    }
    
    private Attributes<String, Object> createRow(Certification cert, CertificationItem item)
        throws GeneralException {

        Attributes<String, Object> row = new Attributes<String, Object>();
        putEntityIdForPercent(row, item);

        row.put("description", DataOwnerCertifiableEntity.createFromCertificationEntity(item.getParent()).getDisplayName(getContext(), getLocale()));
        row.put("nativeIdentity", item.getExceptionEntitlements().getDisplayableName());
        row.put("application", item.getExceptionEntitlements().getApplication());

        String id = item.getTargetId();
        if (id != null){
            Identity identity = getContext().getObjectById(Identity.class, id);
            if (identity != null){
                row.put("identity", identity.getName());
                row.put("firstName", identity.getFirstname());
                row.put("lastName", identity.getLastname());
                if(identity.getManager() != null) {row.put("manager", identity.getManager().getName());}
            }
        }

        row.put("status", item.getSummaryStatus() != null ?
                   getMessage(item.getSummaryStatus().getMessageKey()) : null);

        if (item.getAction() != null){
            row.put("decisionMaker", item.getAction() != null ? item.getAction().getActorName() : null);
            CertificationAction.Status stat =  item.getAction().getStatus();
            if (stat != null){
                row.put("decision", getMessage(stat.getMessageKey()));
            }

            row.put("comments", item.getAction().getComments());
            row.put("remediationCompleted", getRemediationStatus(item, MessageKeys.REPT_ACCOUNT_GRP_MEMB_STATUS_REMED_COMPLETED));
        }

        // the is now referred to as 'Access Review' leaving it as
        // 'certName' for backwards compatibility with customized reports
        row.put("certName", cert.getName());

        String certGrp = cert.getCertificationName();
        row.put("certGroupName", certGrp != null ? certGrp : "");

        return row;
    }
}
