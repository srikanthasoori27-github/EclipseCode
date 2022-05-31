/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Datasource for the certification detail report. Returns a
 * list of CertificationEntity records.
 * list of CertificationEntity records.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class CertificationDetailDataSource extends BaseCertificationDataSource {

    private static final Log log = LogFactory.getLog(CertificationDetailDataSource.class);

    public static final String FIELD_EXCLUDED = "excluded";

    // Names of the object properties we will query on
    public static final String PROPERTY_EXPIRATION = "expiration";
    public static final String PROPERTY_CREATED = "created";
    public static final String PROPERTY_SIGNED = "signed";
    public static final String PROPERTY_MANAGER = "manager";
    public static final String PROPERTY_APPLICATION = "applicationId";

    // Report data objects
    private Iterator<CertificationEntity> entities;
    private CertificationEntity currentItem;
    private Certification currentCertification;
    private Iterator<Certification> certifications;

    // Query parameters
    private List<String> managers;
    private List<String> applications;
    private List<String> applicationNames;

    public CertificationDetailDataSource(Locale locale, TimeZone timezone) {
        super(locale, timezone);
    }

    /**
     * Returns the datasource's current CertificationEntity.
     *
     * @return Returns the datasource's current CertificationEntity, or null.
     * @throws JRException
     */
    public Object getCurrentItem() throws JRException {
        return currentItem;
    }

    
    /**
     * Moves the next CertificationEntity to the current item.
     *
     * @return False if the datasource has reached the end of the list.
     * @throws JRException
     */
    public boolean internalNext() throws JRException {

        if (certifications == null)
            return false;

        if (currentCertification == null)
            getNextCertification();

        if(entities != null && entities.hasNext()){
            currentItem = entities.next();
            return true;
        }

        if (currentCertification != null){
            try {
                List<ArchivedCertificationEntity> archivedEntities = currentCertification.fetchArchivedEntities(getContext());

                if (currentCertification!= null && !isExcludedEntity() && archivedEntities != null && !archivedEntities.isEmpty()) {
                    entities = new ArchivedEntityIterator(currentCertification, archivedEntities);
                    this.currentItem = entities.next();
                    return true;
                }else if (getNextCertification()){
                    return next();
                }

            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        return false;
    }

    
    private static class ArchivedEntityIterator
        implements Iterator<CertificationEntity> {

        private Certification cert;
        private Iterator<ArchivedCertificationEntity> it;
        private ArchivedCertificationEntity previous;
        
        public ArchivedEntityIterator(Certification cert, List<ArchivedCertificationEntity> archivedEntities) {
            
            this.cert = cert;

            if (null != archivedEntities) {
                this.it = archivedEntities.iterator();
            }
        }
        
        public boolean hasNext() {
            return ((null != this.it) && it.hasNext());
        }
        
        public CertificationEntity next() {

            ArchivedCertificationEntity archived = this.it.next();
            CertificationEntity entity = archived.getEntity();

            // This is somewhat of a hack since we don't have a ref to the cert on an excluded entity. We
            // need this reference for all the summary counts on the title page.
            // Todo CertificationDetailReportStatistics needs to be deleted and the jrxml should do the counting.
            // Once that's done we can remove this hack.
            entity.setCertification(this.cert);
            
            this.previous = archived;

            return entity;
        }
        
        public void remove() {
            this.it.remove();
        }

        /**
         * The iterator must always return a CertificationEntity with next, but
         * we're also interested in the exclusion information.  This method will
         * return the ArchivedCertificationEntity that hold the
         * CertificationEntity from the last call to next().  This throws an
         * exception if next() has not yet been called.  Ugly, but it gets the
         * job done.
         */
        public ArchivedCertificationEntity getArchivedCertificationEntity() {
            if (null == this.previous) {
                throw new RuntimeException("next() must be called before the archived entity can be retrieved.");
            }
            return this.previous;
        }
    }

    /**
     * Rather than creating a new class which wraps the entity and adds a
     * property 'excluded' we'll just intercept calls to that property and
     * return whether or not the current entity is an excluded entity.
     *
     * @param field
     * @return
     * @throws JRException
     */
    public Object getFieldValue(JRField field) throws JRException {

        // rather than letting the jasper pull the cert from the entity, lets return the cert
        // we already have stored here. This lets us skip the reflection and any lazy hibernate
        // calls to get the cert
        if ("certification".equals(field.getName())){
            return currentCertification;
        }

        if ("certificationItems".equals(field.getName())){
            return getCertificationItems();
        }

        if (FIELD_EXCLUDED.equals(field.getName())){
            return isExcludedEntity();
        }

        if ("exclusionExplanation".equals(field.getName())) {
            return getExclusionExplanation();
        }

        if ("fullname".equals(field.getName())){
            if (Util.isNullOrEmpty(currentItem.getFullname()) && currentItem.getIdentity()!= null){
                try {
                    Identity id = currentItem.getIdentity(getContext());
                    String name = null;
                    if (id != null)
                        name = id.getDisplayableName();
                    return name;
                } catch (GeneralException e) {
                    log.error(e);
                    throw new JRException(e);
                }
            } else {
                return this.currentItem.getFullname();
            }
        }
        
        if ("identityType".equals(field.getName())) {
            return isIdentityType();
        }

        if (field.getName().startsWith("dataOwner")) {
            return handleDataOwnerFields(field.getName());
        }
        // We used to extend BaseDataSource but now extend BaseCertificationDataSource.
        // To allow for fetching properties off the current item, use DataSourceUtil.
        return DataSourceUtil.getFieldValue(field, this.currentItem);
    }
    
    private boolean isIdentityType() {
        
        if (this.currentItem.getType() == CertificationEntity.Type.DataOwner) {
            return false;
        }
        
        return !Util.isNullOrEmpty(this.currentItem.getIdentity());     
    }
    
    private Object handleDataOwnerFields(String name) 
        throws JRException {

        if (name.equals("dataOwnerType")) {
            return Boolean.TRUE;
        }
        
        return null;
    }

    /**
     * Queries for the certifications and gets the sets the current
     * certification and populates the certification entities.
     *
     * @return True if a certification was found
     * @throws GeneralException
     */
    @Override
    public void internalPrepare() throws GeneralException {
        certifications = getCertifications();
    }

    /**
     * Gets the next certification in the list and sets it as the
     * currentCertification. Also pulls the CertificationEntities
     * from the cert and populates the entities list.
     *
     * @return True if a another certification is in the list.
     */
    private boolean getNextCertification(){

        if (!certifications.hasNext())
            return false;

        currentCertification = certifications.next();

        entities = getEntities(currentCertification);

        return true;
    }
    
    private Iterator<CertificationEntity> getEntities(Certification certification) 
    { 
        assert(certification != null);
        
        if (Util.isEmpty(applicationNames)) {
            return certification.getEntities().iterator();
        }
        
        QueryOptions options = new QueryOptions();
        options.setDistinct(true);
        options.add(Filter.eq("certification.id", certification.getId()));
        
        List<Filter> appFilters = new ArrayList<Filter>();       
        for (String applicationName : applicationNames) {
            appFilters.add(Filter.containsAll("applicationNames", Arrays.asList(applicationName)));
        }       

        options.add(Filter.collectionCondition("items", Filter.or(appFilters)));
            
        try {
            return getContext().search(CertificationEntity.class, options);
        } catch (GeneralException ex) {
            throw new RuntimeException(ex);
        }
    }

    private List<CertificationItem> getCertificationItems()
    {
        if (currentItem == null) {
            return new ArrayList<CertificationItem>();
        } else if (Util.isEmpty(applicationNames)) {
            return currentItem.getItems();
        }        
        
        QueryOptions options = new QueryOptions();
        options.setDistinct(true);
        options.add(Filter.eq("parent.id", currentItem.getId()));       
       
        List<Filter> appFilters = new ArrayList<Filter>();       
        for (String applicationName : applicationNames) {
            appFilters.add(Filter.containsAll("applicationNames", Arrays.asList(applicationName)));
        }       
       
        options.add(Filter.or(appFilters));
        
        try {
            Iterator<CertificationItem> items =  getContext().search(CertificationItem.class, options);
            
            List<CertificationItem> result = new ArrayList<CertificationItem>();
            while (items.hasNext()) {
                result.add(items.next());
            }
            
            return result;
        } catch (GeneralException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Get certifications filtered by report parameters.
     *
     * @return List of query results. If no results are found the list will be non-null and empty
     * @throws GeneralException
     */
    private Iterator<Certification> getCertifications() throws GeneralException{

        Iterator<Certification> items = new ArrayList<Certification>().iterator();

        Filter filter = getCertificationsFilter();

        // If filter is null then it has already been determined that no results will
        // be returned
        if (filter == null)
            return items;

        QueryOptions ops = new QueryOptions();
        ops.add(filter);
        ops.setOrderBy(PROPERTY_CREATED);

        Iterator<Certification> queryResults = getContext().search(Certification.class, ops);

        if (queryResults != null) {
            items = queryResults;
        }
        
        return items;
    }

     /**
     * Creates the primary query for this report. Returns all certification which match
      * report parameters.
     *
     * @return Filter based on report attributes. If no filters are needed, null is returned.
     */
   private Filter getCertificationsFilter() {
        List<Filter> filters = new ArrayList<Filter>();

        if (managers != null && !managers.isEmpty() )
            filters.add(Filter.in(PROPERTY_MANAGER, managers));
        
        // Non-null phase if cert has been started.S
        filters.add(Filter.notnull("phase"));
        filters.add(Filter.ne("phase", Certification.Phase.Staged));

        Filter userSpecified = super.getUserSpecifiedFilters("");
        if (null != userSpecified) {
            filters.add(userSpecified);
        }

        return Filter.and(filters);
    }

    private boolean isExcludedEntity(){
        return (this.entities != null && this.entities instanceof ArchivedEntityIterator);
    }

    private String getExclusionExplanation() {
        String explanation = null;
        
        if (isExcludedEntity()) {
            ArchivedCertificationEntity archived =
                ((ArchivedEntityIterator) this.entities).getArchivedCertificationEntity();
            explanation = archived.getExplanation();
        }

        return explanation;
    }
    
    // **************************************************************
    //  Report Parameters
    // **************************************************************

    public List<String> getApplications() {
        return applications;
    }

    public void setApplications(List<String> applications) {
        this.applications = applications;

        // iiqtc-159
        // In IIQETN-3001 the TaskDefinitionBean.mergeArgs() converts the list of application ids
        // into a list of names so we don't need to do that conversion if the list already contains
        // names. This change eliminates the conversion and also gets rid of the getApplicationNames()
        // method since this was the only place where it was called.
        List<String> appNames = new ArrayList<String>();
        if (applications != null && !applications.isEmpty()) {
            appNames.addAll(applications);
        }

        this.applicationNames = appNames;
    }

    public List<String> getManagers() {
        return managers;
    }

    public void setManagers(List<String> managers) {
        this.managers = managers;
    }
}
