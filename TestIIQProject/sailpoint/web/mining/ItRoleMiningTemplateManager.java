package sailpoint.web.mining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.task.ITRoleMiningTask;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.mining.ItRoleMiningTemplate.FilterType;
import sailpoint.web.task.TaskDefinitionBean;

public class ItRoleMiningTemplateManager {

    public ItRoleMiningTemplateManager( final SailPointContext context, final Locale locale, final String userId ) {
        this.context = context;
        this.locale = locale;
        this.userId = userId;
    }
    
    public void validate( final ItRoleMiningTemplate template ) throws GeneralException {
        validateTemplateName( template );
        validateOwner( template );
        validateApplications( template );
    }
    
    public void saveTemplate( final ItRoleMiningTemplate template ) throws GeneralException {
        
        validate( template );
        
        String templateId = template.getId();
        TaskDefinition definition;
        /* If there is an existing template load that */
        if( !Util.isNullOrEmpty( templateId ) ) {
            try {
                definition = context.getObjectById( TaskDefinition.class, templateId );
            } catch ( GeneralException e ) {
                //TODO: Localize
                /* We were not able to get the existing object, something is wrong */
                throw new GeneralException( "Unable to fetch template with id: " + templateId, e );
            }
        } else {
            /* Create a new template */
            TaskDefinition parent;
            try {
                parent = context.getObjectByName( TaskDefinition.class, IT_ROLE_MINING_TEMPLATE_PARENT_TASK_NAME );
            } catch ( GeneralException e ) {
                /* Problem getting attributes for new task */
                throw new GeneralException( "Unable to fetch role mining parent template", e );
            }
            definition = TaskDefinitionBean.assimilateDef( parent );
            definition.setName( template.getName() );
        }
        
        definition.setName(template.getName());
        
        /* Get the specified owner */
        Identity owner = null;
        String ownerId = template.getOwnerId();
        if( !Util.isNullOrEmpty( ownerId ) ) {
            try {
                owner = context.getObjectById( Identity.class, ownerId );
            } catch ( GeneralException e ) {
                throw new GeneralException( "Unable to get Identity with id: " + ownerId, e );
            } 
        } else {
            throw new GeneralException( "No owner set" );
        }
        definition.setOwner( owner );
        
        /* Update the task definition */
        Attributes<String, Object> itRoleMiningArguments = new Attributes<String, Object>( getITRoleMiningArguments( template ) );
        definition.setArguments( itRoleMiningArguments );
        /* Save and commit the new/updated object */
        try {
            context.saveObject( definition );
            context.commitTransaction();
        } catch ( GeneralException e ) {
            /* Unable to save template... */
            // TODO Localize
            throw new GeneralException( "Unable to save template" , e );
        }
        
    }

    public ItRoleMiningTemplate getTemplate( String id ) throws GeneralException {
        if( id == null ) {
            throw new GeneralException( "Template Id must not be null" );
        }
        TaskDefinition taskDefinition = context.getObjectById( TaskDefinition.class, id );
        ItRoleMiningTemplate response = new ItRoleMiningTemplate();
        
        response.setId( taskDefinition.getId() );
        response.setName( taskDefinition.getName() );
        String ownerId = taskDefinition.getOwner().getId();
        if( !Util.isNullOrEmpty( ownerId ) ) {
            try {
                Identity owner = context.getObjectById( Identity.class, ownerId );
                response.setOwnerId( ownerId );
                response.setOwnerName( owner.getDisplayableName() );
            } catch ( GeneralException e ) {
                throw new GeneralException( "Unable to get Identity with id: " + ownerId, e );
            } 
        }
        Attributes<String,Object> arguments = taskDefinition.getArguments();
        /* Get Applications */
        String applicationIdsString = (String)arguments.get( ITRoleMiningTask.APPLICATIONS );
        if( !Util.isNullOrEmpty( applicationIdsString ) ) {
            response.setApplicationIds( Util.csvToList( applicationIdsString ) );
        }
        /* Minimum Identities per role */
        Integer minIdentities = (Integer)arguments.get( ITRoleMiningTask.MIN_IDENTITIES_PER_ROLE );
        if( minIdentities != null ) {
            response.setMinimumIdentitiesPerRole( minIdentities.intValue() );
        }
        /* Minimum Entitlements Per Role */
        Integer minEntitlements = (Integer)arguments.get( ITRoleMiningTask.MIN_ENTITLEMENTS_PER_ROLE );
        if( minEntitlements != null ) {
            response.setMinimumEntitlementsPerRole( minEntitlements.intValue() );
        }
        /* Maximum number of roles */
        Integer maxRoles = (Integer)arguments.get( ITRoleMiningTask.MAX_CANDIDATE_ROLES );
        if( maxRoles != null ) {
            response.setMaximumRoles( maxRoles.intValue() );
        }
        /* Filter Type */
        String filterTypeString = (String)arguments.get( ItRoleMiningTemplateManager.IT_ROLE_MINING_FILTER_TYPE );
        if( !Util.isNullOrEmpty( filterTypeString ) ) {
            response.setFilterType( filterTypeString.equals( POP_FILTER_TYPE_BY_IPOP ) ? FilterType.POPULATION : FilterType.ATTRIBUTE );
        }
        /* Population name, if that is used for filtering */
        String populationName = (String)arguments.get( ITRoleMiningTask.POPULATION_NAME );
        response.setPopulationName( populationName );
        
        /* IdentityItems to ItRoleMiningEntitlements */
        // This is necessary because we changed included entitlements into excluded entitlements.  To figure out which method this task is using 
        // we look to the task argument called IS_EXCLUDED
        boolean isExcluded = Util.otob(arguments.get(ITRoleMiningTask.IS_EXCLUDED));
        String serializedEntitlements = (String) arguments.get( ITRoleMiningTask.INCLUDED_ENTITLEMENTS );
        Set<ITRoleMiningEntitlement> convertedEntitlements = getConvertedEntitlements(serializedEntitlements);
        if (isExcluded) {
            if( !Util.isNullOrEmpty( serializedEntitlements ) ) {
                response.addEntitlements(convertedEntitlements);
            }
        } else {
            List<String> appIds = Util.csvToList( applicationIdsString );
            Set<ITRoleMiningEntitlement> excludedEntitlements = new HashSet<ITRoleMiningEntitlement>();
            // Add attribute entitlements
            List<ManagedAttribute> managedAttributes = context.getObjects(ManagedAttribute.class, new QueryOptions(Filter.and(Filter.in("application.id", appIds), Filter.ne("type", ManagedAttribute.Type.Permission.name()))));
            if (managedAttributes != null && !managedAttributes.isEmpty()) {
                for (ManagedAttribute managedAttribute : managedAttributes) {
                    ITRoleMiningEntitlement entitlement = 
                        new ITRoleMiningEntitlement(ITRoleMiningEntitlement.Type.Attribute, managedAttribute.getApplicationId(), managedAttribute.getAttribute(), managedAttribute.getDescription(locale), managedAttribute.getValue(), managedAttribute.getDisplayableName());
                    if (!convertedEntitlements.contains(entitlement)) {
                        excludedEntitlements.add(entitlement);
                    }
                }
            }
            // Add permission entitlements
            managedAttributes = context.getObjects(ManagedAttribute.class, new QueryOptions(Filter.and(Filter.in("application.id", appIds), Filter.eq("type", ManagedAttribute.Type.Permission.name()))));
            if (managedAttributes != null && !managedAttributes.isEmpty()) {
                for (ManagedAttribute managedAttribute : managedAttributes) {
                    ITRoleMiningEntitlement entitlement = 
                        new ITRoleMiningEntitlement(ITRoleMiningEntitlement.Type.Permission, managedAttribute.getApplicationId(), managedAttribute.getAttribute(), managedAttribute.getDescription(locale), managedAttribute.getValue(), managedAttribute.getDisplayableName());
                    if (!convertedEntitlements.contains(entitlement)) {
                        excludedEntitlements.add(entitlement);
                    }
                }
            }
            if (!excludedEntitlements.isEmpty()) {
                response.addEntitlements(excludedEntitlements);
            }
        }
        
        String serializedFilters = (String)arguments.get( ITRoleMiningTask.IDENTITY_FILTER );
        if( !Util.isNullOrEmpty( serializedFilters ) ) { 
            XMLObjectFactory xmlDeserializer = XMLObjectFactory.getInstance();
            List<Filter> filters = (List<Filter>)xmlDeserializer.parseXml( context, serializedFilters, false );
            if( filters != null ) {
                response.setIdentityFilters( filters );
            }
        }
        return response;
    }
    
    public void scheduleTemplate( final ItRoleMiningTemplate template ) throws GeneralException {
        TaskManager tm = new TaskManager( context );
        // We want to queue the task to run, but our TaskManager doesn't seem to support queueing right now.
        // Ask Jeff about this.  Just run now for now.
        String templateName = template.getName();
        if( Util.isNullOrEmpty( templateName ) ) {
            templateName = IT_ROLE_MINING_TEMPLATE_PARENT_TASK_NAME;
        }
        TaskDefinition miningTask;
        try {
            miningTask = context.getObjectByName( TaskDefinition.class, templateName );
            if( miningTask == null ) {
                miningTask = context.getObjectByName( TaskDefinition.class, IT_ROLE_MINING_TEMPLATE_PARENT_TASK_NAME );
            }
        } catch( GeneralException e ) {
            throw new GeneralException( "Failed loading template with name: " + templateName, e );
        }
        TaskSchedule ts = new TaskSchedule();
        Map<String, Object> arguments = getITRoleMiningArguments( template );
        ts.setArguments( arguments );
        ts.setTaskDefinition( miningTask );
        ts.setLauncher( userId );
        ts.setName( (String)arguments.get( ITRoleMiningTask.RESULT_NAME ) );
        try { 
            tm.runNow( ts );
        } catch( GeneralException e ) {
            throw new GeneralException( "Failed running task", e );
        }
    }

    /* Private Properties */
    private final SailPointContext context;
    private final Locale locale;
    private final String userId;

    /* Private Methods */
    private Map<String, Object> getITRoleMiningArguments( final ItRoleMiningTemplate template ) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        XMLObjectFactory xmlSerializer = XMLObjectFactory.getInstance(); 
        List<Filter> identityFilters = template.getIdentityFilters();
        String serializedFilters = xmlSerializer.toXml( identityFilters, false );
        arguments.put( ITRoleMiningTask.IDENTITY_FILTER, serializedFilters );
        arguments.put( ITRoleMiningTask.APPLICATIONS, Util.listToCsv( template.getApplicationIds() ) );
        // IdentityItems are not ideal for our purposes, but they exist and they're serializeable.
        // For these reasons we'll use them as a transport between the scheduler and executor.
        List<IdentityItem> entitlements = getIdentityItems( template.getEntitlements() );
        String serializedIncludedEntitlements = xmlSerializer.toXml( entitlements, false );
        arguments.put( ITRoleMiningTask.INCLUDED_ENTITLEMENTS, serializedIncludedEntitlements );
        arguments.put( ITRoleMiningTask.MIN_IDENTITIES_PER_ROLE, template.getMinimumIdentitiesPerRole() );
        arguments.put( ITRoleMiningTask.MIN_ENTITLEMENTS_PER_ROLE, template.getMinimumEntitlementsPerRole());
        arguments.put( ITRoleMiningTask.MAX_CANDIDATE_ROLES, template.getMaximumRoles() );
        arguments.put( IT_ROLE_MINING_FILTER_TYPE, ( template.getFilterType() == ItRoleMiningTemplate.FilterType.ATTRIBUTE ? POP_FILTER_TYPE_BY_ATTRIBUTES : POP_FILTER_TYPE_BY_IPOP ) );
        arguments.put( ITRoleMiningTask.POPULATION_NAME, template.getPopulationName() );
        String name = template.getName();
        if (name == null || name.trim().length() == 0) {
            name = "IT Role Mining";
        }
        arguments.put( ITRoleMiningTask.RESULT_NAME, name);
        arguments.put(ITRoleMiningTask.IS_EXCLUDED, true);

        return arguments;
    }
    
    /**
     * Create List<IdentityItem> from List<ItRoleMiningEntitlement> 
     * @param entitlements List of ItRoleMiningEntitlements to translate to IdentityItems
     * @return List of IdentityItems representing entitlements
     */
    private List<IdentityItem> getIdentityItems(
        Set<ITRoleMiningEntitlement> entitlements ) {
        List<IdentityItem> response = new ArrayList<IdentityItem>( entitlements.size() );
        for( ITRoleMiningEntitlement entitlement : entitlements ) {
            response.add( getIdentityItem( entitlement ) );
        }
        return response;
    }

    /**
     * Creates a new IdentityItem from entitlement
     * @param entitlement The ItRoleMiningEntitlement to translate
     * @return A new IdentityItem representing entitlement
     */
    private IdentityItem getIdentityItem( ITRoleMiningEntitlement entitlement ) {
      IdentityItem response = new IdentityItem();
      response.setApplication( entitlement.getApplication() );
      if( entitlement.getType().equals(  ITRoleMiningEntitlement.Type.Attribute ) ) {
          response.setPermission( false );
          response.setName( entitlement.getName() );
          response.setValue( entitlement.getValue() );
          response.setDisplayName( entitlement.getDisplayName());
      } else {
          response.setPermission( true );
          response.setName( entitlement.getTarget() );
      }
      return response;
    }

    private void validateTemplateName( final ItRoleMiningTemplate template ) throws GeneralException {
        /* If there is no template name that is a problem */
        String templateName = template.getName();
        if ( Util.isNullOrEmpty( templateName ) ) {
            /* Template name is required */
            throw new GeneralException( new Message( MessageKeys.IT_ROLE_MINING_TEMPLATE_VALIDATION_BLANK_NAME ) );
        }
        String templateId = template.getId();
        /* Conflicting task names are also a problem */
        try {
            TaskDefinition possibleConflictingTaskDef = context.getObjectByName( TaskDefinition.class, templateName );
            if ( possibleConflictingTaskDef != null && !possibleConflictingTaskDef.getId().equals( templateId ) ) {
                // TODO: Localize
                throw new GeneralException( "Template already exists with name: " + templateName );
            }
        } catch ( GeneralException e ) {
            /* We failed fetching a task with a matching name... */
            throw new GeneralException( new Message( MessageKeys.IT_ROLE_MINING_TEMPLATE_VALIDATION_EXISTING_NAME ) );
        }
    }
    
    private void validateApplications( ItRoleMiningTemplate template ) throws GeneralException {
        if( template.getApplicationIds().size() == 0 ) {
            throw new GeneralException( new Message( MessageKeys.IT_ROLE_MINING_TEMPLATE_VALIDATION_NO_APPLICATIONS ) );
        }
    }

    private void validateOwner( ItRoleMiningTemplate template ) throws GeneralException {
        if( Util.isNullOrEmpty( template.getOwnerId() ) ) {
            throw new GeneralException( new Message( MessageKeys.IT_ROLE_MINING_TEMPLATE_VALIDATION_NO_OWNER ) );
        }
    }
    
    private Set<ITRoleMiningEntitlement> getConvertedEntitlements(String serializedEntitlements) {
        Set<ITRoleMiningEntitlement> convertedEntitlements = new HashSet<ITRoleMiningEntitlement>();
        
        if( !Util.isNullOrEmpty( serializedEntitlements ) ) {
            XMLObjectFactory xmlDeserializer = XMLObjectFactory.getInstance();
            List<IdentityItem> identityItems = (List<IdentityItem>)xmlDeserializer.parseXml( context, serializedEntitlements, false );
            for( IdentityItem item : identityItems ) {
                ITRoleMiningEntitlement entitlement;
                if( item.isPermission() ) {
                    entitlement = new ITRoleMiningEntitlement( ITRoleMiningEntitlement.Type.Permission, item.getApplication(), item.getName().toString() );
                } else {
                    entitlement = new ITRoleMiningEntitlement( ITRoleMiningEntitlement.Type.Attribute, item.getApplication(), item.getName(), "", item.getValue().toString(), item.getDisplayName() );
                }
                convertedEntitlements.add( entitlement );
            }
        }

        return convertedEntitlements;
    }

    /* Private constants */
    private static final String IT_ROLE_MINING_TEMPLATE_PARENT_TASK_NAME = "New IT Role Mining";
    private static final String IT_ROLE_MINING_FILTER_TYPE = "roleMiningFilterType";
    private static final String POP_FILTER_TYPE_BY_ATTRIBUTES = "searchByAttributes";
    private static final String POP_FILTER_TYPE_BY_IPOP = "searchByIpop";
}
