/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.EntitlementMiningIdentityBean.EntitlementMiningIdentityAttrBean;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridResponse;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.extjs.GridResponseSortInfo;
import sailpoint.web.extjs.Response;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.IdentitySearchBean;
import sailpoint.web.util.FilterConverter;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class EntitlementMiningBean extends BaseBean implements Serializable {
    private static final Log log = LogFactory.getLog(EntitlementMiningBean.class);

    public static final String ATT_ENT_MINE_APPLICATION_ID = "EntitlementMiningApplicationId";
    public static final String ATT_ENT_MINE_ENT_BUCKETS = "EntitlementBuckets";
    public static final String ATT_ENT_MINE_ENT_IDENTITIES = "EntitlementIdentities";
    public static final String ATT_ENT_MINE_ENT_GROUP_BUCKETS = "EntitlementGroupBuckets";
    public static final String ATT_ENT_MINE_MAP_APP_BUCKETS = "appBuckets";
    public static final String ATT_ENT_MINE_MAP_APP_NAME = "appName";
    public static final String ATT_ENT_MINE_MAP_APP_ID = "appId";
    public static final String ATT_ENT_MINE_MAP_PAGE = "page";
    public static final String ATT_ENT_MINE_MAP_PAGE_CNT = "pages";
    public static final String ATT_ENT_MINE_MAP_RES_CNT = "size";


    int maxAppBuckets;
    boolean limitedMaxAppBuckets = false;

    /**
     * The advanced search item that stores all of the necessary filters.  All filters are pulled off
     * of this search item and used to retrieve the list of entitlements
     */
    SearchItem searchItem;
    /**
     * The ids of the applications that are being analyzed for their list of entitlements 
     */    
    List<String> applicationIds;
    List<Application> applications;
    /**
     * When two or more entitlementbuckets are chosen and the "group and analyze" button is clicked,
     * they are added to this list 
     */
    List<EntitlementMiningBucketBean> bucketGroups;
    private HashMap<String, HashMap<String, EntitlementMiningIdentityBean>> entitlementIdentitiesByApplication;
    /**
     * A list of Maps that congregate the entitlementbuckets into groups by application
     */
    List<Map<String, Object>> appEntitlementBuckets;
    List<Filter> linkFilters;
    int populationSize;
    
    /** The id of a bucket we'd like to retrieve via a request parameter on an ajax request. **/
    String bucketId;
    
    /** The id of a application we'd like to retrieve a bucket for 
     * via a request parameter on an ajax request. **/
    String applicationId;
    
    private int filterThreshold;

    /**
     * Controls whether or not we show matching or nonmatching
     * identities in the nested expando identity grid
     */
    private boolean showNonMatchingIdentities;

    /**
     * Updated page number for the bucket for specified by pagingApp.
     * This is submitted when the user pages one of the application
     * bucket grids.
     */
    private int page;

    /**
     * ID of the application whose paging status is being updated.
     * This is submitted when the user pages one of the application
     * bucket grids.
     */
    private String pagingApp;


	//////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////
    public EntitlementMiningBean() {
        super();
        init();
    }

    private void init() {
        try {            
            Configuration sysConfig = SailPointFactory.getCurrentContext().getConfiguration();
            maxAppBuckets = Integer.parseInt((String)sysConfig.get(Configuration.ENTITLEMENT_MINING_MAX_APP_BUCKETS));
        } catch( GeneralException ge) {
            log.error("Unable to get Sysconfig from context. Exception " + ge.getMessage());
            maxAppBuckets = 25;
        }
        
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map request = ctx.getExternalContext().getRequestParameterMap();
    	bucketId = Util.getString((String) request.get("bucketId"));
    	applicationId = Util.getString((String) request.get("applicationId"));
    	showNonMatchingIdentities = Util.getBoolean(request, "showNonMatched");
        filterThreshold = 0;
    }
    
    public int getFilterThreshold() {
        return filterThreshold;
    }
    
    public void setFilterThreshold(final int filterThreshold) {
        this.filterThreshold = filterThreshold;
        this.resetPaging();
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////


    /**
     * Used by the Modeler to take a list of selected entitlement buckets and their percentage of population,
     * group them, and then redisplay them to the user showing how many identities have all of the grouped
     * entitlements
     */
    public String groupBuckets() {

        /**
         *  Cycle through the list of attribute select beans and get the list of attributes that were chosen
         */
        EntitlementMiningBucketBean bucket = new EntitlementMiningBucketBean();
        List<EntitlementMiningBucketBean> chosenBuckets = new ArrayList<EntitlementMiningBucketBean>();
        try {
            if(appEntitlementBuckets!=null 
                    && getSelectedEntitlementBuckets(false)!=null 
                    && !getSelectedEntitlementBuckets(false).isEmpty()) {            
                chosenBuckets.addAll(getSelectedEntitlementBuckets(false));
            }
        } catch (GeneralException ge) {
            log.debug("Cannot group entitlements from different applications");
            //addErrorMessage("Cannot group entitlements from different applications.", null);
            return null;
        }
        /**
         * Now check the bucket groups to see if any of them were selected
         */
        if(bucketGroups!=null && getSelectedBucketGroups()!=null) {
            for(EntitlementMiningBucketBean bkt : getSelectedBucketGroups()) {
                boolean duplicate = false;
                for(EntitlementMiningBucketBean chosenBucket : chosenBuckets) {
                    AttrSelectBean chosenAttr = chosenBucket.getAttr();
                    AttrSelectBean bktAttr = bkt.getAttr();
                    if(bktAttr.getName().equals(chosenAttr.getName()) 
                            && bktAttr.getValue().equals(chosenAttr.getValue())) {
                        duplicate = true;
                    }
                }
                if(!duplicate){
                	log.debug("Chosen bucket: " + bkt.name);
                    chosenBuckets.add(bkt);
                }
            }            
        }
        if(chosenBuckets.isEmpty()) {
            log.debug("Chosen buckets is empty.");
            return null;
        }
        bucket.setChildBuckets(chosenBuckets);

        /**
         * Go through the list of EntitlementIdentities and see if they have ALL of the attributes
         */
        if(!(bucket.getChildBuckets()==null) && !(bucket.getChildBuckets().isEmpty())) {
            List<EntitlementMiningIdentityBean> childIdentities = new ArrayList<EntitlementMiningIdentityBean>();

            /** Go through the child buckets and get all of their identities.  We are going to see
             * which of these identities have all of the entitlements selected **/
            for(EntitlementMiningBucketBean childBucket : bucket.getChildBuckets()) {
                for(EntitlementMiningIdentityBean childBucketIdentity : childBucket.getIdentities()) {
                    if(!childIdentities.contains(childBucketIdentity)) {                    	
                    	childIdentities.add(childBucketIdentity);
                    }
                }
                log.debug("ChildBucket: " + childBucket.name + " Attr: " + childBucket.attr + " Size: " + childIdentities.size());
            }

            bucket.setTotal(getTotalNumberOfIdentities());

            /** Loop through the entitlement mining identities **/
            for(EntitlementMiningIdentityBean eIdent : childIdentities){
                Set<String> applicationIds = eIdent.getAttrMap().keySet();
                log.debug("Application Ids: " + applicationIds);
                boolean identMatched = false;

                /** For each child bucket, check if this identity has the matching attribute **/
                for(EntitlementMiningBucketBean childBucket : bucket.getChildBuckets()) {
                    boolean thisMatched = false;

                    if(childBucket.getAttr()!=null) {
                        String bName = childBucket.getAttr().getName();
                        String bValue = childBucket.getAttr().getValue();
                        String bAppId = childBucket.getApplicationId();
                        //log.warn("Identity: " + eIdent.getDisplayName() + " Bucket Attributes: " + bName + " " + bValue);

                        /** For each entitlement mining identity, 
                         * get the attribute that goes with their native identity
                         */ 
                        for(String applicationId : applicationIds) {
                            log.debug("Application Id: " + applicationId);
                            List<EntitlementMiningIdentityAttrBean> identAttrBeans = eIdent.getAttrMap().get(applicationId);
                            if (Util.isEmpty(identAttrBeans)) {
                                continue;
                            }
                            for (EntitlementMiningIdentityAttrBean identAttrBean: identAttrBeans) {
                                String nativeIdent = identAttrBean.getNativeIdentity();
                                Attributes attr = identAttrBean.getAttributes();
                                if(attr!=null) {
                                    List<String> names = attr.getKeys();
                                    log.debug("Identity: " + eIdent.getDisplayName() + 
                                            " Bucket Attributes: " + bName + " " + bValue + 
                                            "\nNative Ident: " + nativeIdent + " Attributes: " + names);

                                    //For each attribute, get the name and value
                                    for(String iName : names) {
                                        Object iValue = attr.get(iName);
                                        if(iValue instanceof java.util.List) {
                                            for(Object iVal : (List<Object>)iValue) {
                                                if(iName != null && iName.equals(bName) &&
                                                        iVal != null && iVal.toString().equals(bValue) &&
                                                        applicationId != null && applicationId.equals(bAppId)) {
                                                    log.debug("Identity: " + eIdent.getDisplayName() + " Native Ident: " + nativeIdent +
                                                            "\nBucket Attributes: " + bName + " " + bValue + " " + bAppId +
                                                            "\nIdent Attributes: " + iName + " " + iValue + " " + applicationId);
                                                    thisMatched = true;
                                                    break;
                                                }
                                            }
                                        } else {
                                            if(iName != null && iName.equals(bName) &&
                                                    iValue != null && iValue.equals(bValue) &&
                                                    applicationId != null && applicationId.equals(bAppId)) {
                                                log.debug("Identity: " + eIdent.getDisplayName() + " Native Ident: " + nativeIdent +
                                                        "\nBucket Attributes: " + bName + " " + bValue  + " " + bAppId + 
                                                        "\nIdent Attributes: " + iName + " " + iValue + " " + applicationId);
                                                thisMatched = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if(thisMatched)
                                    break;
                            }
                        }
                    }
                    //If we haven't found the attribute in this identity, continue the loop
                    if(!thisMatched) {
                        identMatched = false;
                        break;
                    }
                    else
                        identMatched = true;

                }

                if(identMatched) {
                	log.debug("Match: " + eIdent.getDisplayName());
                    bucket.addIdentity(eIdent);
                }
            }
        }

        if(bucketGroups==null) {
            bucketGroups = new ArrayList<EntitlementMiningBucketBean>();
        }
        bucket.setName("Grouping " + (bucketGroups.size() + 1));
        bucket.setAttr(new AttrSelectBean(bucket.getName(), "", "", bucket.getApplication(), false));
        bucket.setId(bucketGroups.size()+1);
        bucketGroups.add(bucket);
        
        FacesContext.getCurrentInstance().getExternalContext().getSessionMap().put(ATT_ENT_MINE_ENT_GROUP_BUCKETS, bucketGroups);
        
        return "ok";
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters/Helpers
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @return the appEntitlementBuckets
     */
    public List<Map<String, Object>> getAppEntitlementBuckets() {

        if(appEntitlementBuckets==null) {
            if(searchItem!=null) {

                appEntitlementBuckets = new ArrayList<Map<String, Object>>();
                entitlementIdentitiesByApplication = new HashMap<String, HashMap<String, EntitlementMiningIdentityBean>>();

                /** Get the filters that are provided from the search criteria and
                 * create a user-friendly display of them for the ui **/
                getDisplayableFilters();

                /**
                 * We are getting identity-related filters from the searchItem, need to
                 * convert them to Link filters
                 */
                linkFilters = FilterConverter.convertFilters(searchItem.getCalculatedFilters(), Identity.class, "identity");

                /** If the list of applications is empty, we will need to build it from the supplied filters
                 */
                if(applicationIds==null || applicationIds.isEmpty()) {
                    getApplicationsFromFilters(linkFilters);
                }

                /** Fill the list of entitlement identities based on which identities
                 * have links to this application */
                if(getApplications()!=null) {
                    loadEntitlementIdentities();
                }
                
                if(entitlementIdentitiesByApplication==null || entitlementIdentitiesByApplication.isEmpty())
                    return null;

                if(getApplications()!=null) {
	                for(Application application : getApplications()) {
	                    log.debug("Application: " + application.getName());
	                    
	                    Map<String, Object> appBucket = new HashMap<String, Object>();
	                    appBucket.put(ATT_ENT_MINE_MAP_APP_NAME, application.getName());
	                    appBucket.put(ATT_ENT_MINE_MAP_APP_ID, application.getId());

	                    /**Get the attributes from the application that are considered 
	                     * "Entitlement Attributes" */
	                    List<String> entitlementAttributeNames = getEntitlementAttributeNames(application);
	                    
	                    log.debug("Entitlement Attributes: " + entitlementAttributeNames);
	
	                    /** Fill the list of entitlement mining buckets based on what entitlements
	                     * are selected in the Application schema */
	                    List<EntitlementMiningBucketBean> buckets = 
	                        buildBucketList(entitlementAttributeNames, linkFilters, application);
	
	
	                    /** Sort the entitlement buckets so that the highest percentage
	                     * entitlements are on top */
	                    if(buckets!=null) {
	                        Collections.sort(buckets, new Comparator<EntitlementMiningBucketBean>() {
	                            public int compare(EntitlementMiningBucketBean a, EntitlementMiningBucketBean b) {                    
	                                if(a.getCount() > b.getCount()) {
	                                    return -1;
	                                } else if (a.getCount() == b.getCount()) {
	                                    String aName = a.getAttr().getName();
	                                    if (aName == null) {
	                                        aName = "";
	                                    }
	                                    String aValue = a.getAttr().getValue();
	                                    if (aValue == null) {
	                                        aValue = "";
	                                    }
	                                    String bName = b.getAttr().getName();
	                                    if (bName == null) {
	                                        bName = "";
	                                    }
	                                    String bValue = b.getAttr().getValue();
	                                    if (bValue == null) {
	                                        bValue = "";
	                                    }
	                                    
	                                    if ( aName.equals(bName) ) {
                                            return aValue.compareToIgnoreCase(bValue);
	                                    } else {
                                            return aName.compareToIgnoreCase(bName);
	                                    }
	                                } else {
	                                    return 1;
	                                }
	                            }
	                        });
	                    }
	
	//                  Set the ids of the buckets in ascending order so that they look good on the ui
	                    //in a striped table.
	                    for(int i=0; i<buckets.size(); i++) {
	                        buckets.get(i).setId(i+1);
	                        buckets.get(i).setApplicationId(application.getId());
	                    }
	
	                    appBucket.put(ATT_ENT_MINE_MAP_APP_BUCKETS, buckets);
	                    log.debug("Putting Bucket: " + appBucket.get(ATT_ENT_MINE_MAP_APP_NAME) + " Size: " + buckets.size());
	                    
	                    if(buckets.size()>0)
	                    	appEntitlementBuckets.add(appBucket);
	                }
                }
            }
            /** Sort the app entitlement buckets based on which app entitlement buckets
             * have the most entitlement buckets */
            if(appEntitlementBuckets!=null) {
                Collections.sort(appEntitlementBuckets, new Comparator<Map<String, Object>>() {
                    public int compare(Map<String, Object> a, Map<String, Object> b) {
                        List<EntitlementMiningBucketBean> aBuckets = (List)a.get(ATT_ENT_MINE_MAP_APP_BUCKETS);
                        List<EntitlementMiningBucketBean> bBuckets = (List)b.get(ATT_ENT_MINE_MAP_APP_BUCKETS);
                        if(aBuckets.size() > bBuckets.size()) return -1;
                        else if(aBuckets.size() == bBuckets.size()) return 0;
                        else return 1;
                    }

                });

                /** Need to trim the size of the appEntitlement buckets if the list is larger
                 * than the configured limit. **/
                if(appEntitlementBuckets.size() > maxAppBuckets) {
                    appEntitlementBuckets = appEntitlementBuckets.subList(0, maxAppBuckets);
                    limitedMaxAppBuckets = true;
                    log.debug("Trimming app bucket list to " + maxAppBuckets + " items. Original Size: " + appEntitlementBuckets.size());
                    
                }

                initPaging(appEntitlementBuckets);
            }

            FacesContext.getCurrentInstance().getExternalContext().getSessionMap().put(ATT_ENT_MINE_ENT_BUCKETS, appEntitlementBuckets);
            FacesContext.getCurrentInstance().getExternalContext().getSessionMap().put(ATT_ENT_MINE_ENT_IDENTITIES, entitlementIdentitiesByApplication);
        }

        // Add the message about limited app buckets every time, ensuring it is only present once 
        // by removing previous instances. 
        if (limitedMaxAppBuckets) {
            FacesContext ctx = FacesContext.getCurrentInstance();
            Iterator existingMessages = ctx.getMessages("maxAppBuckets"); 
            while (existingMessages.hasNext()) {
                existingMessages.next();
                existingMessages.remove();
            }
            FacesContext.getCurrentInstance().addMessage("maxAppBuckets", 
                    getFacesMessage(Message.warn(MessageKeys.ENTITLEMENT_MINING_APP_BUCKET_RESULTS_LIMIT, maxAppBuckets), null));
        }

        List<Map<String, Object>> appEntitlementBucketsView;
        
        if (filterThreshold > 0 && appEntitlementBuckets != null) {
            appEntitlementBucketsView = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> entitlementBucket : appEntitlementBuckets) {
                Map<String, Object> entitlementBucketView = new HashMap<String, Object>();
                List<EntitlementMiningBucketBean> buckets = (List<EntitlementMiningBucketBean>) entitlementBucket.get(ATT_ENT_MINE_MAP_APP_BUCKETS);
                List<EntitlementMiningBucketBean> bucketsView = new ArrayList<EntitlementMiningBucketBean>();
                
                for (EntitlementMiningBucketBean bucketBean : buckets) {
                    if (bucketBean.getPercent() > filterThreshold) {
                        bucketsView.add(bucketBean);
                    }
                }
                
                if (!bucketsView.isEmpty()) {
                    entitlementBucketView.put(ATT_ENT_MINE_MAP_APP_BUCKETS, bucketsView);
                    entitlementBucketView.put(ATT_ENT_MINE_MAP_APP_NAME, entitlementBucket.get(ATT_ENT_MINE_MAP_APP_NAME));
                    entitlementBucketView.put(ATT_ENT_MINE_MAP_APP_ID, entitlementBucket.get(ATT_ENT_MINE_MAP_APP_ID));
                    entitlementBucketView.put(ATT_ENT_MINE_MAP_PAGE, entitlementBucket.get(ATT_ENT_MINE_MAP_PAGE));
                    appEntitlementBucketsView.add(entitlementBucketView);
                }
            }
        } else {
            appEntitlementBucketsView = appEntitlementBuckets;
        }

        initPaging(appEntitlementBucketsView);

        return appEntitlementBucketsView;
    }


    private void initPaging(List<Map<String, Object>> appEntBuckets){
        if (appEntBuckets != null){
            for(Map<String, Object> appBucket : appEntBuckets){
                List buckets = (List)appBucket.get(ATT_ENT_MINE_MAP_APP_BUCKETS);
                int cnt = buckets != null ? buckets.size() : 0;
                int pages = (cnt / 20) + (cnt % 20 > 0 ? 1 : 0) ;

                appBucket.put(ATT_ENT_MINE_MAP_RES_CNT, cnt);
                appBucket.put(ATT_ENT_MINE_MAP_PAGE_CNT, pages);
                if (!appBucket.containsKey(ATT_ENT_MINE_MAP_PAGE))
                    appBucket.put(ATT_ENT_MINE_MAP_PAGE, 1);
            }
        }
    }

    private void resetPaging(){
        if (appEntitlementBuckets != null){
            for(Map<String, Object> appBucket : appEntitlementBuckets){
                List buckets = (List)appBucket.get(ATT_ENT_MINE_MAP_APP_BUCKETS);
                int cnt = buckets != null ? buckets.size() : 0;
                int pages = (cnt / 20) + (cnt % 20 > 0 ? 1 : 0) ;

                appBucket.put(ATT_ENT_MINE_MAP_RES_CNT, cnt);
                appBucket.put(ATT_ENT_MINE_MAP_PAGE_CNT, pages);
                appBucket.put(ATT_ENT_MINE_MAP_PAGE, 1);
            }
        }
    }

    private List<String> getEntitlementAttributeNames(Application application) {
        List<String> attrs = new ArrayList<String>();
        List<Schema> schemas = application.getSchemas();
        if(schemas!=null) {
            attrs = new ArrayList<String>();
            for(Schema schema:schemas) {
                // Filtering out group attributes -- Bug 2586
                if (Connector.TYPE_ACCOUNT.equals(schema.getObjectType())) {                   
                    
                    List<String> entAttrs = schema.getMinableAttributeNames();
                    log.debug("Application: [" + application.getName() + "] Attrs: [" + entAttrs + "]");
                    if(entAttrs != null) 
                        attrs.addAll(entAttrs);
                }
            }
        }
        return attrs;
    }
    
    /** Cycles through the list of filters and combines any filters on the links.application.id
     * into a collection condition filter
     */
    private List<Filter> convertCollectionFilters(List<Filter> filters, BooleanOperation op) {
        List<LeafFilter> linkFilters = new ArrayList();
        for(Iterator<Filter> fIter = filters.iterator(); fIter.hasNext();) {
            Filter f = fIter.next();
            if(f instanceof LeafFilter && ((LeafFilter)f).getProperty().equals("links.application.id")) {
                fIter.remove();
                linkFilters.add((LeafFilter)f);
            } else if(f instanceof CompositeFilter) {
                convertCollectionFilters(((CompositeFilter)f).getChildren(), ((CompositeFilter)f).getOperation());
            }
        }
        
        if(linkFilters.size()>1) {
            List<Filter> collectionFilters = new ArrayList<Filter>();
            for(LeafFilter f : linkFilters) {
                f.setProperty("application.id");
                collectionFilters.add(f);
            }
            
            if(op!=null && op.equals(BooleanOperation.OR)) {
                filters.add(Filter.collectionCondition("links", Filter.or(collectionFilters)));
            } else {
                filters.add(Filter.collectionCondition("links", Filter.and(collectionFilters)));
            }
        } else if(!linkFilters.isEmpty()){
            filters.add(linkFilters.get(0));
        }
        return filters;
    }

    public void loadEntitlementIdentities() {
        
        for ( Application app : getApplications() ) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.join("id", "Link.identity"));
                // only get the links for this application
            qo.add(Filter.eq("Link.application.id", app.getId()));
            
            /** First, get the filters from the search item **/
            List<Filter> filters = searchItem.getCalculatedFilters();
            if(filters!=null) {
                /** Convert any links.application.id filters to collection condition filters */
                filters = convertCollectionFilters(filters, BooleanOperation.valueOf(searchItem.getOperation()));
                for(Filter f : filters) {
                    qo.add(f);
                }
            }
            if ( log.isDebugEnabled() ) {
                log.debug("Query Options for Entitlements Analysis query: " + qo.toString());
            }
    
            
            List<String> props = new ArrayList<String>();
            props.add("id");
            props.add("name");
            props.add("lastname");
            props.add("firstname");
            props.add("Link.application.id");
            props.add("Link.nativeIdentity");
            props.add("Link.attributes");
            props.add("Link.displayName");
            props.add("displayName");

            try {
                Iterator<Object[]> it = SailPointFactory.getCurrentContext().search(Identity.class, qo, props);            
                
                while(it.hasNext()) {
                    Object[] row = it.next();
                    String identityId = (String)row[0];
    
                    String name = (String)row[1];
                    String lastname = (String)row[2];
                    String firstname = (String)row[3];
                    String applicationId = (String)row[4];
                    String nativeIdentity = (String)row[5];
                    Attributes attributes = (Attributes)row[6];
                    String accountDisplayName = (String)row[7];
                    String displayName = (String)row[8];
                    
                    /** Loop through the list of identities and add the identity to the 
                     * list of entitlement identities.
                     */
                    HashMap<String, EntitlementMiningIdentityBean> entitlementIdentitiesMap = entitlementIdentitiesByApplication.get(applicationId);
                    if(entitlementIdentitiesMap == null) {
                        entitlementIdentitiesMap = new HashMap<String, EntitlementMiningIdentityBean>();
                    }
                    
                    EntitlementMiningIdentityBean emIdentity = entitlementIdentitiesMap.get(identityId);
                    if(emIdentity==null) {                
                        emIdentity = new EntitlementMiningIdentityBean(identityId, name, displayName, lastname, firstname);
                    }
                    
                    log.debug("Entitlement Identity: [" + emIdentity.getName() + "] Native Identity: [" + nativeIdentity + "] ");
                    Map<String, List<EntitlementMiningIdentityAttrBean>> attrMap = emIdentity.getAttrMap();
                    if(attrMap==null) {
                        attrMap = new HashMap<String, List<EntitlementMiningIdentityAttrBean>>();
                    }
    
                    EntitlementMiningIdentityAttrBean identAttrBean = 
                        new EntitlementMiningIdentityAttrBean();
    
                    identAttrBean.setNativeIdentity(nativeIdentity);
                    //IIQETN-6460 :- Since link.display_name could be null for some accounts, we will use link.native_identity
                    //in those cases and avoid a null pointer exception.
                    if (!Util.isNullOrEmpty(accountDisplayName)) {
                        identAttrBean.setAccountName(accountDisplayName);
                    } else {
                        identAttrBean.setAccountName(nativeIdentity);
                    }
                    identAttrBean.setAttributes(attributes);
                    if (attrMap.get(applicationId) == null) {
                        attrMap.put(applicationId, new ArrayList<EntitlementMiningIdentityAttrBean>());
                    }
                    attrMap.get(applicationId).add(identAttrBean);
                    
                    emIdentity.setAttrMap(attrMap);
                    entitlementIdentitiesMap.put(identityId, emIdentity);
                    entitlementIdentitiesByApplication.put(applicationId, entitlementIdentitiesMap);
                }
            } catch(GeneralException ge) {
                if (log.isErrorEnabled())
                    log.error("Unable to get identities from search item filters." + ge.getMessage(), ge);
            }
        }
    }

    private List<EntitlementMiningBucketBean> buildBucketList(List<String> entitlementAttributeNames, 
            List<Filter> linkFilters, Application application) {
        List<EntitlementMiningBucketBean> entitlementBuckets = null;
        int x=0;
        
        if(entitlementIdentitiesByApplication!=null){
            entitlementBuckets = new ArrayList<EntitlementMiningBucketBean>();
            for(String appIdString : entitlementIdentitiesByApplication.keySet()) {
                HashMap<String, EntitlementMiningIdentityBean> entitlementIdentitiesMap = entitlementIdentitiesByApplication.get(appIdString);
                if(entitlementIdentitiesMap != null) {
                    for(String identityId : entitlementIdentitiesMap.keySet()) {
                        EntitlementMiningIdentityBean eIdent = entitlementIdentitiesMap.get(identityId);
        
                        //log.debug("Identity: " + eIdent.getDisplayName());
        
                        List<EntitlementMiningIdentityAttrBean> identAttrBeans = eIdent.getAttrMap().get(application.getId());
                        //log.debug("Application: " + application.getName() + " Identity Attr Bean: " + identAttrBean);
                        if (!Util.isEmpty(identAttrBeans)) {
                            for (EntitlementMiningIdentityAttrBean identAttrBean : identAttrBeans) {
                                Attributes attr = identAttrBean.getAttributes();
        
                                //log.debug("Attr: " + attr);
        
                                if(attr!=null) {
                                    List<String> keys = attr.getKeys();
        
                                    //log.debug("Keys: " + keys);
                                    //log.debug("EntitlementAttributeNames: " + entitlementAttributeNames);
        
                                    for(String key : keys) {
                                        if(entitlementAttributeNames.contains(key)) {
                                            //log.debug("Application: " + application.getName() + " Attribute: " + key + " " + attr.get(key));
                                            Object value = attr.get(key);
                                            if(value!=null) {
                                                if ( value instanceof List ) {
                                                    for ( Object o : (List)value ) {
                                                        if(o!=null) {
                                                            String attrName = key;
                                                            String attrVal = o.toString();
        
                                                            // Use display name if possible
                                                            String displayVal = attrVal;
                                                            String description = null;
        
                                                            Explanator.Explanation expl = Explanator.get(application, attrName, attrVal);
        
                                                            if (expl != null) {
                                                                displayVal = expl.getDisplayValue();
                                                                description = expl.getDescription(getLocale());
                                                            }
        
                                                            addToBuckets(eIdent, entitlementBuckets, "bucket" + x++, 
                                                                    attrName, attrVal, displayVal, application.getName(), true, description, entitlementIdentitiesMap.size());
                                                        }
                                                    }
                                                } else {
                                                    String attrName = key;
                                                    String attrVal = value.toString();
                                                    String description = null;
                                                    Explanator.Explanation expl = Explanator.get(application, attrName, attrVal);
                                                    if (expl != null) {
                                                        attrVal = expl.getDisplayValue();
                                                        description = expl.getDescription(getLocale());
                                                    }
                                                    addToBuckets(eIdent, entitlementBuckets, "bucket" + x++, 
                                                            attrName, attrVal, attrVal, application.getName(), false, description, entitlementIdentitiesMap.size());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return entitlementBuckets;
    }


    private void addToBuckets(EntitlementMiningIdentityBean eIdent, List<EntitlementMiningBucketBean> buckets,
            String name, String key, String value, String displayValue, String application, boolean multiValue, String description, int popSize) {

        //log.debug("Name: " + name + " Key: " + key + " Value: " + value + " MultiValue? " + multiValue);
        Iterator<EntitlementMiningBucketBean> bktIter = buckets.iterator();
        AttrSelectBean attr = new AttrSelectBean(key, value, displayValue, application, multiValue, description);

        /**If we've already seen these attributes before, just increment the count of the bucket,
         * add the identity, and return **/
        while(bktIter.hasNext()) {
            EntitlementMiningBucketBean bkt = bktIter.next();
            if((bkt.getAttr().getName().equals(attr.getName()))
                    && bkt.getAttr().getValue().equals(attr.getValue())){
                bkt.addIdentity(eIdent);
                return;
            }
        }

        EntitlementMiningBucketBean bucket = new EntitlementMiningBucketBean(popSize);        
        bucket.setAttr(attr);
        bucket.setName(name);
        bucket.setId(buckets.size()+1);
        bucket.addIdentity(eIdent);
        buckets.add(bucket);
    }

    public List<EntitlementMiningBucketBean> getSelectedEntitlementBuckets(boolean applicationCheck) throws GeneralException{
        List<EntitlementMiningBucketBean> buckets = null;
        String appId = null;

        if(appEntitlementBuckets!=null){
            buckets = new ArrayList<EntitlementMiningBucketBean>();

            /** Cycle through the list of applications in this search and get their bucket lists **/
            for(Map<String,Object> bucketMap : appEntitlementBuckets) {
                List<EntitlementMiningBucketBean> entitlementBuckets = (List)bucketMap.get(ATT_ENT_MINE_MAP_APP_BUCKETS);
                if(entitlementBuckets!=null) {
                    for(EntitlementMiningBucketBean bckt : entitlementBuckets) {

                        AttrSelectBean attrBean = bckt.getAttr();
                        if(attrBean.isSelected()) { 
                            if(applicationCheck) {
                                String thisAppId = bucketMap.get(ATT_ENT_MINE_MAP_APP_ID).toString();
                                if(appId == null) {
                                    appId = thisAppId;
                                } else if(!appId.equals(thisAppId)) {
                                    /** They have selected entitlements from two different applications **/
                                    throw new GeneralException("Selected Entitlements from different applications.");
                                }
                            }
                            buckets.add(bckt);
                        }
                    }
                }
            }
        }
        return buckets;
    }

    public List<EntitlementMiningBucketBean> getSelectedBucketGroups() {
        List<EntitlementMiningBucketBean> buckets = null;
        if(bucketGroups!=null){
            buckets = new ArrayList<EntitlementMiningBucketBean>();
            for(EntitlementMiningBucketBean bckt : bucketGroups) {
                AttrSelectBean attrBean = bckt.getAttr();
                if(attrBean.isSelected()) {                               
                    for(EntitlementMiningBucketBean child : bckt.getChildBuckets()) {
                        buckets.add(child);
                    }
                }
            }
        }
        return buckets;
    }


    /**
     * @return the application
     */
    public List<Application> getApplications() {
        if(applications==null && !(applicationIds==null)) {
            try {
                List<Filter> filters = new ArrayList<Filter>();
                QueryOptions qo = new QueryOptions();
                for(String applicationId : applicationIds) {
                    log.debug("getApplications(): Application id Filter: " + applicationId);
                    filters.add(Filter.eq("id", applicationId));
                }
                qo.add(Filter.or(filters));
                applications = SailPointFactory.getCurrentContext().getObjects(Application.class, qo);
            } catch(GeneralException ge) {
                log.error("Unable to load Application from provided Application Id. " + ge.getMessage());
                applications = null;
            }
        }
        return applications;
    }

    public List<Application> getApplicationsFromFilters(List<Filter> filters) {
        if(applications==null && filters!=null) {
            QueryOptions qo = new QueryOptions();
            for(Filter filter : filters) {
                qo.add(filter);
            }
            qo.setDistinct(true);
            log.info("Filters: " + filters);
            List<String> props = new ArrayList<String>();
            props.add("application");

            try {
                Iterator<Object[]> it = SailPointFactory.getCurrentContext().search(Link.class, qo, props);
                if(it.hasNext())
                    applications = new ArrayList<Application>();
                while(it.hasNext()) {
                    Application application = (Application)it.next()[0];
                    applications.add(application);
                }
            } catch (Exception e) {
                log.warn("Unable to load the applications with provided filters. Exception: " + e.getMessage());
            }
        }
        log.debug("Applications From Filters: " + applications);
        return applications;
    }

    /**
     * @return the applicationId
     */
    public List<String> getApplicationIds() {
        return applicationIds;
    }


    /**
     * @return the searchItem
     */
    public SearchItem getSearchItem() {
        return searchItem;
    }

    /**
     * @param searchItem the searchItem to set
     */
    public void setSearchItem(SearchItem searchItem) {
        this.searchItem = searchItem;
    }

    /**
     * @param applicationIds the applicationIds to set
     */
    public void setApplicationIds(List<String> applicationIds) {
        this.applicationIds = applicationIds;
    }

    /**
     * @return the bucketGroups
     */
    public List<EntitlementMiningBucketBean> getBucketGroups() {
        return bucketGroups;
    }

    /**
     * @return the groups that were selected for inclusion in a newly created profile
     */
    public List<EntitlementMiningBucketBean> getSelectedBuckets() {
        List<EntitlementMiningBucketBean> selectedBuckets = new ArrayList<EntitlementMiningBucketBean>();
        
        if (bucketGroups != null && !bucketGroups.isEmpty()) {
            for (EntitlementMiningBucketBean bucketGroup : bucketGroups) {
                AttrSelectBean attr = bucketGroup.getAttr();
                if(attr != null && attr.isSelected()) {
                    selectedBuckets.add(bucketGroup);
                }
            }
        }
        
        return selectedBuckets;
    }
    
    /**
     * @param bucketGroups the bucketGroups to set
     */
    public void setBucketGroups(List<EntitlementMiningBucketBean> bucketGroups) {
        this.bucketGroups = bucketGroups;
    }

    public SelectItem[] getApplicationSelectItems() {
        SelectItem[] apps = null;
        //log.debug("Get Application Select Items: " + applications);
        if(getApplications()!=null && !getApplications().isEmpty()) {
            apps = new SelectItem[applications.size()];
            for(int i=0; i<applications.size(); i++) {
                Application app = applications.get(i);
                apps[i] = new SelectItem(app.getId(), app.getName());
            }
        }
        return apps;
    }

    /** Takes the list of incoming filters and builds a list of filters for displaying on the UI **/
    public List<Map<String, String>> getDisplayableFilters() {
        List<Map<String, String>> displayableFilters = null;
        if(searchItem!=null && searchItem.getInputDefinitions()!=null) {
            displayableFilters = new ArrayList<Map<String, String>>();
            for(SearchInputDefinition def : searchItem.getInputDefinitions()) {
                if(def!=null && def.getValue()!=null && !def.getValue().toString().equals("")) {
                    //log.debug("Key: " + def.getDescription() + " Value: " + def.getValue() + " Type: " + def.getInputTypeValue() + "Property: " + def.getPropertyName());
                    Object value = def.getValue();
                    Map<String, String> filterMap = new HashMap<String, String>();
                    filterMap.put("key", def.getDescription());
                    filterMap.put("type", getMessage(def.getInputType().getMessageKey()));
                    filterMap.put("value", buildValueString(def, value));
                    if(!displayableFilters.contains(filterMap))
                        displayableFilters.add(filterMap);
                }
            }

        }

        return displayableFilters;
    }

    /** Builds the string representation of the filter **/
    public String buildValueString(SearchInputDefinition def, Object value) {
        
        String valString = null;
        if(value instanceof List) {
            List<String> values = (ArrayList<String>)value;
            StringBuffer sb = new StringBuffer();
            for(String val : values) {
                if(def.getName().equals(IdentitySearchBean.ATT_IDT_SEARCH_APPLICATION_NAME)) {
                    //Need to get the name of the object from the id
                    try {
                        // Need a fresh context every time because this is sometimes invoked
                        // by ajax calls and the BaseObjectBean context will be stale in that case
                        SailPointContext context = SailPointFactory.getCurrentContext();
                        Application app = context.getObjectById(Application.class, val);
                        if(app != null) {
                            val = app.getName();
                        }
                    } catch (GeneralException ge) {
                        log.error("Unable to get object by id: " + val + ". Exception: " + ge.getMessage());
                    }
                }

                String propertyType = "AND";
                // iiqtc-132 - several search input definitions used in identity searches have lists as
                // values and do not define a listOperation. The default for a null listOperation should
                // be OR.
                if(def.getListOperation() == null || def.getListOperation().equals("OR")) {
                    propertyType = "OR";
                }

                if(sb.length()>1) {
                    sb.append("\n"+propertyType +" "+ val);
                } else {
                    sb.append(val);
                }
            }
            valString = sb.toString();

        } else if (value instanceof String) {
            valString = (String)value;
        }

        return valString;
    }

    /**
     * @param appEntitlementBuckets the appEntitlementBuckets to set
     */
    public void setAppEntitlementBuckets(
            List<Map<String, Object>> appEntitlementBuckets) {
        this.appEntitlementBuckets = appEntitlementBuckets;
    }

    /**
     * @return the maxAppBuckets
     */
    public int getMaxAppBuckets() {
        return maxAppBuckets;
    }

    /**
     * @param maxAppBuckets the maxAppBuckets to set
     */
    public void setMaxAppBuckets(int maxAppBuckets) {
        this.maxAppBuckets = maxAppBuckets;
    }

    public boolean isShowNonMatchingIdentities() {
        return showNonMatchingIdentities;
    }

    public void setShowNonMatchingIdentities(boolean showNonMatchingIdentities) {
        this.showNonMatchingIdentities = showNonMatchingIdentities;
    }
    
    public List<EntitlementMiningIdentityBean> getNonMatchingIdentities() {
        List<EntitlementMiningIdentityBean> identities = new ArrayList<EntitlementMiningIdentityBean>();
        
        EntitlementMiningBucketBean chosenBucket = getChosenBucket();
        HashMap<String, HashMap<String,EntitlementMiningIdentityBean>> entitlementIdentitiesApplicationMap = (HashMap<String,HashMap<String, EntitlementMiningIdentityBean>>)(getSessionScope().get(ATT_ENT_MINE_ENT_IDENTITIES));
        
        if(chosenBucket!=null && entitlementIdentitiesApplicationMap!=null) {
            for (Map.Entry<String, HashMap<String,EntitlementMiningIdentityBean>> appEntry : entitlementIdentitiesApplicationMap.entrySet()) {
                HashMap<String, EntitlementMiningIdentityBean> entitlementIdentitiesHashMap = appEntry.getValue();
                for (Map.Entry<String,EntitlementMiningIdentityBean> entry : entitlementIdentitiesHashMap.entrySet()) {
                    if(!chosenBucket.getIdentities().contains(entry.getValue()) && !identities.contains(entry.getValue())) {
                        identities.add(entry.getValue());
                    }
                }
            }
        }
        
        if(identities!=null)
            Collections.sort(identities, EntitlementMiningIdentityBean.COMPARATOR);
        return identities;
    }

    public String getPage() {
        return String.valueOf(page);
    }

    public void setPage(String page) {
        try{
            this.page = Integer.parseInt(page);
            if (this.page < 1)
                this.page = 1;
        } catch(NumberFormatException e){
            this.page = 1;
        }
    }

    public String getPagingApp() {
        return pagingApp;
    }

    public void setPagingApp(String pagingApp) {
        this.pagingApp = pagingApp;
    }

    private void incrementPage(int amount){
        if (getAppEntitlementBuckets() != null){
            for(Map<String, Object> appBucket : appEntitlementBuckets){
                String appId = (String)appBucket.get(ATT_ENT_MINE_MAP_APP_ID);
                if (appId.equals(pagingApp)){
                    Integer pg = null;
                    if (appBucket.get(ATT_ENT_MINE_MAP_PAGE) instanceof String) {
                        pg = Integer.valueOf((String)appBucket.get(ATT_ENT_MINE_MAP_PAGE));
                    }
                    else if (appBucket.get(ATT_ENT_MINE_MAP_PAGE) instanceof Integer) {
                        pg = (Integer)appBucket.get(ATT_ENT_MINE_MAP_PAGE);
                    }

                    if (pg == null)
                        appBucket.put(ATT_ENT_MINE_MAP_PAGE, 1);
                    else {
                        int val = pg + amount;
                        appBucket.put(ATT_ENT_MINE_MAP_PAGE,  val);
                    }
                }
            }
        }
    }

    public String updateBucketPage(){
        if (getAppEntitlementBuckets() != null){
            for(Map<String, Object> appBucket : appEntitlementBuckets){
                String appId = (String)appBucket.get(ATT_ENT_MINE_MAP_APP_ID);
                if (appId.equals(pagingApp)){
                    Integer pageSize = (Integer)appBucket.get(ATT_ENT_MINE_MAP_PAGE_CNT);
                    if (pageSize != null && page <= pageSize)
                        appBucket.put(ATT_ENT_MINE_MAP_PAGE,  page);
                    else if (pageSize != null && page > pageSize)
                        appBucket.put(ATT_ENT_MINE_MAP_PAGE,  pageSize);
                    else
                        appBucket.put(ATT_ENT_MINE_MAP_PAGE,  1);
                }
            }
        }
        return "";
    }

    /**
     * Increment the page number ofr the applicatin bucket
     * specified by pagingApp
     * @return
     */
    public String nextPageBucket(){
        incrementPage(1);
        return "";
    }

    /**
     * Decrement the page number ofr the applicatin bucket
     * specified by pagingApp
     * @return
     */
    public String prevPageBucket(){
        incrementPage(-1);
        return "";
    }

    /**
     * Gets the matched or unmatched identities for a given bucket.
     * This populates the matching/unmatching identities nested expando grid.
     *
     * @return Json grid response string
     */
    public String getIdentitiesJson(){
        Object response;
        
        try {
            List<EntitlementMiningIdentityBean> identities;
            if (showNonMatchingIdentities) {
                identities = getNonMatchingIdentities();
            } else {
                EntitlementMiningBucketBean chosenBucket = getChosenBucket();
                if (chosenBucket != null) {
                    identities = chosenBucket.getIdentities();
                } else {
                    identities = new ArrayList<EntitlementMiningIdentityBean>();
                }
            }

            response = getGridResponseForIdentities(identities);

        } catch (Throwable e) {
            log.error("Could not fetch identities", e);
            response = new Response(false, Response.SYS_ERROR, e.getMessage() != null ?
                    e.getMessage() : "Unknown Error, check logs.");
        }


        return JsonHelper.toJson(response);
    }

    
    /** This method is called through ajax to provide a certain bucket
     * to the ui.  This is used so that we don't have to list all of the identities
     * to the user unless they click to show them.
     * @return
     */
    public EntitlementMiningBucketBean getChosenBucket() {
    	EntitlementMiningBucketBean bucket = null;
    	
    	if(bucketId!=null && applicationId!=null) {
    		if(appEntitlementBuckets==null) {
    			appEntitlementBuckets = (List<Map<String,Object>>)(getSessionScope().get(ATT_ENT_MINE_ENT_BUCKETS));
    		}
    		if(appEntitlementBuckets!=null) {
    			for(Map<String,Object> appMap : appEntitlementBuckets) {
    				String id = (String)appMap.get(ATT_ENT_MINE_MAP_APP_ID);
    				if(id.equals(applicationId)) {
    					List<EntitlementMiningBucketBean> buckets = (List)appMap.get(ATT_ENT_MINE_MAP_APP_BUCKETS);
    					for(EntitlementMiningBucketBean thisBucket : buckets) {
    						if(thisBucket.getId()==(Integer.parseInt(bucketId))) {
    							bucket = thisBucket;
    							return bucket;
    						}
    					}
    				}
    			}
    		}
    	}
    	return bucket;
    }
    
    /**
     * Gets the identities for a given bucket group.
     * This populates the grouped identities nested expando grid.
     *
     * @return Json grid response string
     */
    public String getBucketGroupIdentitiesJson() {
        Object response;
        
        try{
            List<EntitlementMiningIdentityBean> currIdentities = null;

            EntitlementMiningBucketBean groupBucket = getChosenBucketGroup();
            if (groupBucket != null) {
                currIdentities = groupBucket.getIdentities();
            } else {
                currIdentities = new ArrayList<EntitlementMiningIdentityBean>();
            }
            
            response = getGridResponseForIdentities(currIdentities);
        } catch (Throwable e) {
            log.error("Could not fetch identities", e);
            response = new Response(false, Response.SYS_ERROR, e.getMessage() != null ?
                    e.getMessage() : "Unknown Error, check logs.");
        }
        
        return JsonHelper.toJson(response);
    }
    
    /** This method is called through ajax to provide a certain bucket group
     * to the ui.  This is used so that we don't have to list all of the identities
     * to the user unless they click to show them.
     * @return
     */
    public EntitlementMiningBucketBean getChosenBucketGroup(){

        int bucketGroupId = Integer.parseInt(getRequestParameter("bucketGroupId"));
        //ID starts at 1 for bucket groups
        if (bucketGroupId > 0) {
            if (bucketGroups == null){
                bucketGroups = (List<EntitlementMiningBucketBean>)(getSessionScope().get(ATT_ENT_MINE_ENT_GROUP_BUCKETS));
            }
            if(bucketGroups != null) {
                for (EntitlementMiningBucketBean bucketBean: bucketGroups) {
                    if (bucketBean.getId() == bucketGroupId) 
                        return bucketBean;
                }
            }
        }

        return null;
    }
    
    /***
     * Shared method to return common format GridResponse for identity grids for both buckets 
     * and bucket groups
     * 
     * @param identities List of identities to use for GridResponse
     * @return GridResponse with the correct set of paged identities
     * @throws Exception
     */
    private GridResponse getGridResponseForIdentities(List<EntitlementMiningIdentityBean> identities) 
    throws Exception{
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();

        int start = getRequestParameter("start") == null || "".equals(getRequestParameter("start")) ? 0 :
            Integer.parseInt((getRequestParameter("start")));
        int limit = getRequestParameter("limit") == null || "".equals(getRequestParameter("limit")) ? 20 :
            getResultLimit();

        for (int i = start; i < identities.size() && i < start + limit; i++){
            EntitlementMiningIdentityBean identity = identities.get(i);
            Map<String, String> row = new HashMap<String, String>();
            row.put("id", identity.getIdentityId());
            //IIQTC-140 :- Escaping name to avoid XSS in entitlement analysis
            row.put("name", WebUtil.escapeHTML(identity.getName(),false));
            //IIQTC-125 :- Escaping display name to avoid XSS in entitlement analysis
            row.put("displayName", WebUtil.escapeHTML(identity.getDisplayName(), false));
            rows.add(row);
        }

        GridResponseMetaData meta = new GridResponseMetaData(new GridResponseSortInfo("ASC","id"),
                Arrays.asList(new GridColumn("id", "", 0, false, true, false),
                        new GridColumn("displayName",
                                getMessage(MessageKeys.DISPLAYABLE_NAME), "auto", false, false, false),
                                new GridColumn("name",
                                        getMessage(MessageKeys.USER_NAME), 350, false, false, false)));

        return new GridResponse(meta, rows, identities.size());
    }
    
    private int getTotalNumberOfIdentities() {
        int numberOfIdentities = 0;
        if(entitlementIdentitiesByApplication != null) {
            for(String appIdString : entitlementIdentitiesByApplication.keySet()) {
                HashMap<String, EntitlementMiningIdentityBean> entitlementIdentiesMap = entitlementIdentitiesByApplication.get(appIdString);
                if(entitlementIdentiesMap != null) {
                    numberOfIdentities += entitlementIdentiesMap.size();
                }
            }
        }
        
        return numberOfIdentities;
    }

}
