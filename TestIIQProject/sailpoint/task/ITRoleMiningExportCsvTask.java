/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.ITRoleMiningTaskResult;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlementsKey;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.role.MiningService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;


public class ITRoleMiningExportCsvTask extends AbstractTaskExecutor {
    private static final Log log = LogFactory.getLog(ITRoleMiningExportCsvTask.class);
    public static final String ARG_EXPORT_TASK_TO_EXPORT_ID = "taskToExportId";
    public static final String ARG_EXPORT_IDENTITY_ATTRIBUTES = "identityAttributes";
    public static final String ARG_EXPORT_IDENTITY_ATTRIBUTE_DIPLAY_NAMES = "identityAttributesDisplayNames";
    public static final String ARG_EXPORT_CSV_RESULTS = "exportCsvResult";
    public static final String ARG_EXPORT_MONITOR = "exportMonitor";
    
    public void execute( SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args ) throws Exception {
        /* Process Arguments */
        /* Fetch the task result to export */
        String taskToExportResultId = ( String )args.get( ARG_EXPORT_TASK_TO_EXPORT_ID );
        TaskResult taskResultToExport = getTaskToExportResult( context, taskToExportResultId );
        /* Peel the Identity Attributes we want from the passed args */
        List<String> identityAttributeNames = Util.csvToList( ( String ) args.get( ARG_EXPORT_IDENTITY_ATTRIBUTES ) );
        List<String> identityAttributeDisplayNames = Util.csvToList( ( String ) args.get( ARG_EXPORT_IDENTITY_ATTRIBUTE_DIPLAY_NAMES ) );
        /* Entitlements/Applications */
        List<String> applicationIds = getApplications( args, taskResultToExport );
        
        groupMap = new HashMap<SimplifiedEntitlementsKey, String>();
        
        boolean isEntitlementSetExclusion;
        Object exclusionArg = taskResultToExport.getAttribute(ITRoleMiningTask.IS_EXCLUDED);
        if (exclusionArg == null) {
            isEntitlementSetExclusion = false;
        } else {
            isEntitlementSetExclusion = Util.otob(exclusionArg);
        }
        Set<SimplifiedEntitlement> entitlements = getEntitlements( context, args, taskResultToExport );

        /* Build query options */
        QueryOptions identityQuery = buildQueryOption( taskResultToExport );
        identityQuery.setCloneResults(true);
        identityAttributeNames.add( 0, Identity.ATT_USERNAME );
        identityAttributeDisplayNames.add( 0, "" );
        identityAttributeNames.add( 0, "id" );
        identityAttributeDisplayNames.add( 0, "" );
        Iterator<Object[]> iterator = context.search( Identity.class, identityQuery, identityAttributeNames);
        
        Map<String,CsvFieldConverter> attrConverterMap = new HashMap<String, CsvFieldConverter>( 1 );
        attrConverterMap.put( Identity.ATT_MANAGER, new IdentityToNameCsvFieldConverter() );
        
        IdentitySimplifiedEntitlementManager identityEntitlementManager = new IdentitySimplifiedEntitlementManager();
        IdentityAttributeManger identityAttributeManger = new IdentityAttributeManger( identityAttributeNames, identityAttributeDisplayNames, attrConverterMap );
        Monitor m = getMonitor();
        int total = getTotalIdentities( taskResultToExport, context );
        String results = new Message( MessageKeys.NO_RESULTS_FOUND ).getLocalizedMessage();
        if( total != 0 ) {
            mapGroups(taskResultToExport, context);
            int processed = 0;
            /* For each identity process each link to requested application */
            while( iterator.hasNext() && !terminated ) {
                Object[] identityAttrs = iterator.next();
                String identityId = identityAttrs[ 0 ].toString();
                /* Process Identity attributes */
                identityAttributeManger.add( identityId, identityAttrs );
                for (String applicationId : applicationIds) {
                    if( terminated ) {
                        break;
                    }
                    /* Get the links for this application and id  */
                    QueryOptions linkQuery = new QueryOptions();
                    linkQuery.add( Filter.eq("application.id", applicationId ) );
                    linkQuery.add( Filter.eq("identity.id", identityId ) );
                    List<Link> links = context.getObjects( Link.class, linkQuery );
    
                    /* For each link process the links attributes */
                    for( Link link : links ) {
                        processLink( identityEntitlementManager, link, entitlements, isEntitlementSetExclusion );
                    }
                }
                processed++;
                int progress = ( processed * 100 ) / total; 
                m.updateProgress( Integer.toString( progress ), progress );
            }
            if( !terminated ) {
                results = buildCsv( identityEntitlementManager, identityAttributeManger, getTaskResults( context, taskResultToExport ) );
            }
        }
        m.completed();
        
        result.setAttribute( ARG_EXPORT_CSV_RESULTS, results );
        result.setTerminated(terminated);
    }
    
    /**
     * Terminate does not stop progress immediately
     */
    public boolean terminate() {
        terminated = true;
        return terminated;
    }

    /**
     * Gets the total number of Identities will be in the result set
     * @return The total number of Identities that will be in the result set
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private int getTotalIdentities( TaskResult taskResultToExport, SailPointContext context ) {
        List<Filter> identityFilters = (List<Filter>)taskResultToExport.getAttribute( ITRoleMiningTask.IDENTITY_FILTER );
        QueryOptions identityQuery = new QueryOptions(identityFilters.toArray(new Filter[identityFilters.size()]));
        identityQuery.setDistinct(true);
        int response = 0;
        try {
            response = context.countObjects(Identity.class, identityQuery);
        } catch ( GeneralException e ) {
            log.error("The ITRoleMiningExportCsvTask failed to count the identities used in mining.", e);
        }
        return response;
    }
    
    private void mapGroups(TaskResult taskResultToExport, SailPointContext context) {
        List<ITRoleMiningTaskResult> roleMiningTaskResults = getTaskResults( context, taskResultToExport );
        for( ITRoleMiningTaskResult itResult : roleMiningTaskResults ) {
            if (terminated) {
                break;
            } else {
                groupMap.put(itResult.getEntitlementSet(), itResult.getIdentifier());
            }
        }
        
    }
    
    /**
     * Populates the manager with values the attributeDifferentiator determined to be important
     * 
     * @param identityEntitlementManager This will be populated with information about the important links
     * @param link The Link to process attributes of
     * @param inclusionsOrExclusions The entitlements
     * @param isExclusions is 'excluded' set to true/false
     */
    private void processLink( IdentitySimplifiedEntitlementManager identityEntitlementManager, 
                              Link link,
                              Set<SimplifiedEntitlement> inclusionsOrExclusions,
                              boolean isExclusions) {
        identityEntitlementManager.add( link, inclusionsOrExclusions, isExclusions );
    }

    private String buildCsv( IdentitySimplifiedEntitlementManager identityEntitlementManager, IdentityAttributeManger identityAttributeManager, Collection<ITRoleMiningTaskResult> taskResults ) {
        StringBuilder response = new StringBuilder();
        
        /* build header */
        response.append( getCsvString( "UserName" ) ).append( "," );
        response.append( getCsvString( "Group" ) ).append( "," );
        response.append( getCsvString( "UserAttributes") ).append( "," );
        for( String attributeKey : identityAttributeManager.getAttributeKeys() ) {
            if( !attributeKey.equals( Identity.ATT_USERNAME ) && !attributeKey.equals( "id" ) ) { 
                response.append( getCsvString( identityAttributeManager.getAttributeDisplayName( attributeKey ) ) ).append( "," );
            }
        }
        for( String application : identityEntitlementManager.getApplications() ) {
            response.append( application ).append( "," );
            for( SimplifiedEntitlement entitlement : identityEntitlementManager.getEntitlementsForApplication( application ) ) {
                response.append( getCsvString( entitlement.getDisplayName() ) ).append( "," );
            }
        }
        response.replace( response.length() - 1, response.length(), "\n" );
        /* build rows */
        for( String identityId : identityEntitlementManager.getIdentities() ) {
            SimplifiedEntitlementsKey entitlementSet = identityEntitlementManager.getEntitlementsForIdentity( identityId );
            // Filter out identities whose entitlements were all excluded
            if (!entitlementSet.getSimplifiedEntitlements().isEmpty()) {
                response.append( getCsvString( identityAttributeManager.getIdentityAttributeForIdentity( identityId, Identity.ATT_USERNAME ) ) ).append( "," );
                response.append( getGroup( entitlementSet ) ).append( "," );
                response.append( "," ); // Blank Field
                for( String attributeKey : identityAttributeManager.getAttributeKeys() ) {
                    if( !attributeKey.equals( Identity.ATT_USERNAME ) && !attributeKey.equals( "id" ) ) { 
                        response.append( getCsvString( identityAttributeManager.getIdentityAttributeForIdentity( identityId, attributeKey ) ) ).append( "," );
                    } 
                }
                for( String applcationId : identityEntitlementManager.getApplications() ) {
                    response.append( "," ); // Blank Field
                    for( SimplifiedEntitlement entitlement : identityEntitlementManager.getEntitlementsForApplication( applcationId ) ) {
                        if( identityEntitlementManager.hasEntitlement( identityId, entitlement ) ) {;
                            response.append( getCsvString( "1" ) );
                        }
                        response.append( "," );
                    }
                }
                response.replace( response.length() - 1, response.length(), "\n" );
            }
        }
        return response.substring( 0, response.length() - 1 );
    }
    
    private String getGroup( SimplifiedEntitlementsKey entitlementsForIdentity) {
        if (groupMap.containsKey(entitlementsForIdentity)) {
            return groupMap.get(entitlementsForIdentity);
        }
        return "No Group";
    }

    private String getCsvString( String string ) {
        StringBuilder response = new StringBuilder( "\"" );
        response.append( string.replace( "\"", "\"\"" ) );
        response.append( "\"" );
        return response.toString();
    }

    private List<String> getApplications( Map<String, Object> args, TaskResult taskResultToExport ) {
        List<String> response = Util.csvToList( (String)taskResultToExport.getAttribute( ITRoleMiningTask.APPLICATIONS ) );
        if( response == null ) {
            response = new ArrayList<String>( 0 );
        }
        return response;
    }
    
    @SuppressWarnings("unchecked")
    private Set<SimplifiedEntitlement> getEntitlements( SailPointContext context, Map<String, Object> args, TaskResult taskResultToExport ) {
        Set<SimplifiedEntitlement> response = (Set<SimplifiedEntitlement>)taskResultToExport.getAttribute( ITRoleMiningTask.INCLUDED_ENTITLEMENTS );
        if( response == null ) {
            response = new HashSet<SimplifiedEntitlement>();
        }
        return response;
    }
    
    @SuppressWarnings("unchecked")
    private List<ITRoleMiningTaskResult> getTaskResults( SailPointContext context, TaskResult taskResultToExport ) {

        List<ITRoleMiningTaskResult>response = new ArrayList<ITRoleMiningTaskResult>();
        Object attrITRoleMiningResults = taskResultToExport.getAttribute(ITRoleMiningTask.IT_ROLE_MINING_RESULTS);
        
        // Accommodate the "old" way of using a serialized String
        if (attrITRoleMiningResults instanceof String) {
            String serializedResults = (String) attrITRoleMiningResults;
            XMLObjectFactory xmlDeserializer = XMLObjectFactory.getInstance();
            // Previously, toXml was called for itRoleMiningResults data before it was set on the TaskResult.
            // That effectively lost any ampersand literals, so accommodate those before parsing the xml.
            serializedResults = serializedResults.replace("&", "&amp;");
            response = (List<ITRoleMiningTaskResult>)xmlDeserializer.parseXml(context, serializedResults, false);
        } else if (attrITRoleMiningResults instanceof List) {
            response = (List<ITRoleMiningTaskResult>)attrITRoleMiningResults;
        }
        
        if( response == null ) {
            response = new ArrayList<ITRoleMiningTaskResult>( 0 );
        }
        return response;
    }

    private static interface CsvFieldConverter {
        public String convertToCsvField( Object object );
    }
        
    private static class IdentityToNameCsvFieldConverter implements CsvFieldConverter {
        public String convertToCsvField( Object object ) {
            String response = "";
            if( object instanceof Identity ) {
                Identity identity = (Identity)object;
                response = identity.getDisplayName();
            }
            return response;
        }
    }

    /**
     * Build query options using filters from taskRestultToExport
     * @param taskResultToExport The TaskResult to extract filters from 
     * @return QueryOption with filters from taskResultToExport
     */
    @SuppressWarnings("unchecked")
    private QueryOptions buildQueryOption( TaskResult taskResultToExport ) {
        QueryOptions response = new QueryOptions();  
        response.setResultLimit( 0 );
        response.setFirstRow( 0 );
        response.setDistinct( true );
        List<Filter> filters = ( List<Filter> ) taskResultToExport.getAttribute( ITRoleMiningTask.IDENTITY_FILTER );
        response.add(Filter.and(filters));
        return response;
    }

    private TaskResult getTaskToExportResult( SailPointContext context, String id ) throws GeneralException {
        TaskResult resposne = context.getObjectById( TaskResult.class, id );
        return resposne;
    }

    private boolean terminated = false;
    private Map<SimplifiedEntitlementsKey, String> groupMap;
    
    /**
     * Class that manages the fields to be exported from IT Role Mining  
     * 
     *  author justin.williams
     */
    private static class IdentitySimplifiedEntitlementManager {
        
        /**
         * Constructed with a map of Identity Attribute Strings to Display Friendly Attribute Converters
         */
        public IdentitySimplifiedEntitlementManager() {
            identityEntitlementMap = new HashMap<String, SimplifiedEntitlementsKey>();
            applicationEntitlementMap = new HashMap<String, SortedSet<SimplifiedEntitlement>>();
            entitlements = new HashSet<SimplifiedEntitlement>();
        }
        
        /**
         * Adds the specified entitlement to the specified identity
         * @param link The Link to process attributes of
         * @param inclusionsOrExclusions The entitlements
         * @param isExclusions is 'excluded' set to true/false
         */
        public void add( Link link, Set<SimplifiedEntitlement> inclusionsOrExclusions, boolean isExclusions ) {
            String identityId = link.getIdentity().getId();
            SimplifiedEntitlementsKey entitlementsKey = identityEntitlementMap.get( identityId );
            if( entitlementsKey == null ) {
                entitlementsKey = new SimplifiedEntitlementsKey(link, inclusionsOrExclusions, isExclusions);
                identityEntitlementMap.put( identityId, entitlementsKey );
            } else {
                entitlementsKey.addLink(link, inclusionsOrExclusions, isExclusions);
            }
            
            Set<SimplifiedEntitlement> simplifiedEntitlements = entitlementsKey.getSimplifiedEntitlementsByApplication(link.getApplication().getId());
            entitlements.addAll( simplifiedEntitlements );
            String appName = link.getApplication().getName();
            SortedSet<SimplifiedEntitlement> applicationEntitlements = applicationEntitlementMap.get(appName);
            if (applicationEntitlements == null) {
                applicationEntitlements = new TreeSet<SimplifiedEntitlement>(MiningService.SIMPLIFIED_ENTITLEMENT_COMPARATOR);
                applicationEntitlementMap.put(appName, applicationEntitlements);
            }
            applicationEntitlements.addAll(simplifiedEntitlements);
        }

        /**
         * Returns a list of all entitlements added to the Identity
         * @param identityId The ID of the Identity to get Entitlements for 
         * @return List of entitlement for the user
         */
        public SimplifiedEntitlementsKey getEntitlementsForIdentity( String identityId ) {
            return identityEntitlementMap.get( identityId );
        }
        
        public boolean hasEntitlement( String identityId, SimplifiedEntitlement entitlement ) {
            boolean response = false;
            if( identityEntitlementMap.containsKey( identityId ) ) {
                SimplifiedEntitlementsKey entitlements = identityEntitlementMap.get( identityId );
                response = entitlements.getSimplifiedEntitlements().contains(entitlement);
            }
            return response;
        }
        
        public Collection<String> getIdentities() {
            return identityEntitlementMap.keySet();
        }
        
        public Collection<String> getApplications() {
            Set<String> response = new HashSet<String>();
            for( SimplifiedEntitlement entitlement : entitlements ) {
                response.add( entitlement.getApplicationName() );
            }
            return response;
        }
        
        public SortedSet<SimplifiedEntitlement> getEntitlementsForApplication( String applicationName ) {
            return applicationEntitlementMap.get(applicationName);
        }
        
        private final Map<String, SimplifiedEntitlementsKey> identityEntitlementMap;
        private final Map<String, SortedSet<SimplifiedEntitlement>> applicationEntitlementMap;
        private final Set<SimplifiedEntitlement> entitlements;
    }
    
    private static final class IdentityAttributeManger {
        public IdentityAttributeManger( final List<String> attributeKeys, final List<String> attributeDisplayNames, Map<String, CsvFieldConverter> attributeConverters ) {
            this.attributeKeys = attributeKeys;
            this.attributeDisplayNames = attributeDisplayNames;
            this.attributeConverters = attributeConverters;
            identityAttributeMap = new HashMap<String, Map<String,Object>>();
        }
        
        public void add( String identityId, Object[] attributeValues ) {
            Map<String, Object> identityAttributes = identityAttributeMap.get( identityId );
            if( identityAttributes == null ) {
                identityAttributes = new HashMap<String, Object>( attributeKeys.size() );
                identityAttributeMap.put( identityId, buildIdentityAttributeMap( attributeValues ) );
            }
        }
        
        public List<String> getAttributeKeys() {
            return attributeKeys;
        }
        
        public String getAttributeDisplayName( String attributeKey ) {
            int size = attributeKeys.size();
            for( int i = 0; i < size; i++ ) {
                String key = attributeKeys.get( i );
                if( key.equals( attributeKey ) ) {
                    return attributeDisplayNames.get( i );
                }
            }
            return attributeKey;
        }

        public String getIdentityAttributeForIdentity( String identityId, String attributeKey ) {
            Map<String, Object> map = identityAttributeMap.get( identityId );
            Object attributeValue = map.get( attributeKey );
            CsvFieldConverter converter = attributeConverters.get( attributeKey );
            String response = null;
            if( converter != null ) {
                response = converter.convertToCsvField( attributeValue );
            } else {
                if( attributeValue != null ) {
                    response = attributeValue.toString();
                }
            }
            return response;
        }
        
        private Map<String, Object> buildIdentityAttributeMap( Object[] attributeValues ) {
            int size = attributeValues.length;
            HashMap<String, Object> response = new HashMap<String, Object>( size );
            for( int i = 0; i < size; i++ ) {
                String key = attributeKeys.get( i );
                Object value = attributeValues[ i ] == null ? "" : attributeValues[ i ];
                response.put( key, value );
            }
            return response;
        }

        /* List of key/value pairs being attribute/displayName */
        private final List<String> attributeKeys;
        private final List<String> attributeDisplayNames;
        /* Map Identity Id to attribute to attribute value */
        private final Map<String, Map<String, Object>> identityAttributeMap;
        private final Map<String, CsvFieldConverter> attributeConverters;
    }
    
}
