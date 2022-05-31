/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.ExternalContext;
import javax.faces.model.SelectItem;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.ApplicationEntitlementWeights;
import sailpoint.object.EntitlementWeight;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Right;
import sailpoint.object.RightConfig;
import sailpoint.object.Schema;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.score.EntitlementScorer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class EntitlementsBarConfigBean extends BaseBean {
    private static Log log = LogFactory.getLog(EntitlementsBarConfigBean.class);

    private Map<String, ApplicationEntitlement> applicationEntitlements;
    private List<String> existingApps;
    private Integer existingAppsCount;
    private String instructions;

    public EntitlementsBarConfigBean() {
        super();
        
        try {
            ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
            instructions = config.getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT).getDescription();
            
            initializeEntitlements(config);
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE), null);
            log.error("The database is not accessible right now.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<ApplicationEntitlement> getApplicationEntitlements() { 
    	List<ApplicationEntitlement> entitlements = 
    		new ArrayList<ApplicationEntitlement>(applicationEntitlements.values());
    	Collections.sort(entitlements);
        return entitlements;
    }
    
    public String getInstructions() {
        return instructions;
    }
    
    public int getAvailableApplicationsCount() throws GeneralException {
		if (null == existingAppsCount)
			existingAppsCount = getContext().countObjects(Application.class, getQueryOptions());

		return existingAppsCount;
    }
    
    /**
     * Returns JSON string with applications available for entitlement configuration
     * @return
     */
    public String getAvailableApplicationsJSON() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("totalCount", 0);
        List<Map<String, Object>> appRows = new ArrayList<Map<String, Object>>();
        response.put("objects", appRows);
        
        try {
            QueryOptions qo = getQueryOptions();
            
            // filter query
            String query = getRequestParameter("query");
            if (query != null && !query.equals("")) 
    			qo.add(Filter.ignoreCase(Filter.like("name", query, Filter.MatchMode.START)));

    		// paging options
    		int start = Util.atoi(getRequestParameter("start"));
    		int limit = getResultLimit();
    		
    		if(start>0) 
    			qo.setFirstRow(start);
    		
    		if(limit>0) 
    			qo.setResultLimit(limit);

            Iterator<Object[]> appResults = getContext().search(Application.class, qo, "id,name");
            response.put("totalCount", getAvailableApplicationsCount());
            while (appResults != null && appResults.hasNext()) {

                Object[] appResult = appResults.next();
                if (appResult != null) {
                    Map<String, Object> appRow = new HashMap<String, Object>();
                    appRow.put("id", appResult[0]);
                    appRow.put("displayName", appResult[1]);
                    appRows.add(appRow);
                }

            }
            
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE), null);
            log.error("The database is not accessible right now.", e);
        }

        return JsonHelper.toJson(response);
    }

	private QueryOptions getQueryOptions() throws GeneralException {
		QueryOptions qo = new QueryOptions();
		
		if (!existingApps.isEmpty()) 
		    qo.add(Filter.not(Filter.in("name", existingApps)));
		
		qo.setOrderBy("name");
		
		// Only show apps that are in scope or owned by the logged in user.
		qo.setScopeResults(true);
		qo.addOwnerScope(super.getLoggedInUser());
		return qo;
	}

    /**
     * Note that we have to call saveEntitlementBARS() to make sure
     * that the recently added default account weight is persisted.
     * This has historically called saveAction to commit the changes.
     * 
     * Note that this makes add/remove application different from
     * most form actions because it does actually commit the changes.
     * Once you do add/remove the Cancel button is ineffective.  We should
     * consider fixing that but it would require a complex DTO model
     * (don't we already have one?) - jsl
     */
    @SuppressWarnings("unchecked")
    public String addApplication() {
        final String newAppName = getRequestParameter("editForm:applicationEntitlements:appSelectionDropDown");
        
        if (newAppName != null && !"".equals(newAppName)) {
            try {
                ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
                ScoreDefinition entitlementsDef = config.getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);
                List<ApplicationEntitlementWeights> appEntitlementWeights = (List<ApplicationEntitlementWeights>)entitlementsDef.getArgument("applicationEntitlementWeights");
                
                if (appEntitlementWeights == null) {
                    appEntitlementWeights = new ArrayList<ApplicationEntitlementWeights>();
                    entitlementsDef.setArgument("applicationEntitlementWeights", appEntitlementWeights);
                }
                
                List<EntitlementWeight> newEntitlementWeights = createDefaultEntitlementWeights();
                ApplicationEntitlementWeights newAppWeights = new ApplicationEntitlementWeights(newAppName, newEntitlementWeights, getContext());
                appEntitlementWeights.add(newAppWeights);
                
                ApplicationEntitlement newDto = createAppEntitlementDTO(newAppWeights);
                applicationEntitlements.put(newAppName, newDto);
                existingApps.add(newAppName);
                saveEntitlementBARs(config);
                getContext().saveObject(config);
                getContext().commitTransaction();
            } catch (GeneralException e) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE), null);
                log.error("The database is not accessible right now.", e);
            }
        }
        
        return "add";
    }
    
    @SuppressWarnings("unchecked")
    public String removeApplications() {
        final Set<String> oldAppNames = new HashSet<String>();
        
        for (ApplicationEntitlement entitlement: applicationEntitlements.values()) {
            if (entitlement.isChecked) {
                oldAppNames.add(entitlement.getName());
            }
        }
        
        if (!oldAppNames.isEmpty()) {
            try {
                // this has to be done before we start modifying the list or else 
                // the application element is added back
                ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
                saveEntitlementBARs(config);
                ScoreDefinition entitlementsDef = config.getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);
                List<ApplicationEntitlementWeights> appEntitlementWeights = (List<ApplicationEntitlementWeights>)entitlementsDef.getArgument("applicationEntitlementWeights");
                Set<ApplicationEntitlementWeights> weightsToRemove = new HashSet<ApplicationEntitlementWeights>();
                
                for (int i = 0; i < appEntitlementWeights.size(); i++) {
                    ApplicationEntitlementWeights currentWeights = appEntitlementWeights.get(i);
                    if (oldAppNames.contains(currentWeights.getApplication().getName())) {
                        weightsToRemove.add(currentWeights);
                    }
                }
                
                if (!weightsToRemove.isEmpty()) {
                    appEntitlementWeights.removeAll(weightsToRemove);
                    List<ApplicationEntitlementWeights> updatedEntitlementWeights = new ArrayList(appEntitlementWeights);
                    entitlementsDef.setArgument("applicationEntitlementWeights", updatedEntitlementWeights);
                }                
                getContext().saveObject(config);
                getContext().commitTransaction();
            } catch (GeneralException e) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE),
                        e.getMessageInstance());
                log.error("The database is not accessible right now.", e);
            }
        }
        
        return "remove";
    }
    
    public String saveChanges() throws GeneralException {    
        try {
            ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
            saveEntitlementBARs(config);
            getContext().saveObject(config);
            getContext().commitTransaction();
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE),
                    e.getMessageInstance());
            log.error("No changes can be saved right now because the system is offline.", e);
        }
        
        return "save";
    }
    
    public String cancelChanges() {
        return "cancel";
    }
    
    public String configurePermission() {
        ExternalContext ctx = getFacesContext().getExternalContext();
        String appName = ctx.getRequestParameterMap().get("editedApp").toString();
        EditedEntitlementAppBean editedEntitlement = (EditedEntitlementAppBean) getFacesContext().getApplication().createValueBinding("#{editedEntitlementApp}").getValue(getFacesContext());
        ApplicationEntitlement appEntitlement = applicationEntitlements.get(appName);
        editedEntitlement.setApplication(appEntitlement);
        
        return "configurePermission";
    }
    
    public String configureAttribute() {
        ExternalContext ctx = getFacesContext().getExternalContext();
        String appName = ctx.getRequestParameterMap().get("editedApp").toString();
        EditedEntitlementAppBean editedEntitlement = (EditedEntitlementAppBean) getFacesContext().getApplication().createValueBinding("#{editedEntitlementApp}").getValue(getFacesContext());
        ApplicationEntitlement appEntitlement = applicationEntitlements.get(appName);
        editedEntitlement.setApplication(appEntitlement);

        return "configureAttribute";
    }
    
    public String backToRiskConfig() throws GeneralException {
        // have to save here too now that we have editable account weights in the table
        saveChanges();
        return "backToRiskConfig";
    }

    @SuppressWarnings("unchecked")
    private void initializeEntitlements(ScoreConfig config) throws GeneralException {
        // Reconcile changes from the RightConfig before we edit anything
        EntitlementScorer scorer = new EntitlementScorer();
        SailPointContext context = getContext();
        scorer.update(context, config);
        context.saveObject(config);
        context.commitTransaction();

        ScoreDefinition entitlementsDef = config.getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);
        List<ApplicationEntitlementWeights> entitlements = (List<ApplicationEntitlementWeights>)entitlementsDef.getArgument("applicationEntitlementWeights");
        existingApps = new ArrayList<String>();
        applicationEntitlements = new HashMap<String, ApplicationEntitlement>();
        
        // Should we filter these by scope and owner of the application?
        // Punting right now because saveEntitlementBARs() depends on
        // applicationEntitlements having ALL apps.  We'll need to make the
        // save method merge instead of replace if filter these.
        if (entitlements != null) {
            for (ApplicationEntitlementWeights weights : entitlements) {
                ApplicationEntitlement dto = createAppEntitlementDTO(weights);
                applicationEntitlements.put(weights.getApplication().getName(), dto);
                existingApps.add(weights.getApplication().getName());
            }
        }
    }
    
    
    private ApplicationEntitlement createAppEntitlementDTO(ApplicationEntitlementWeights weights) {
        List<AppPermission> permissions = new ArrayList<AppPermission>();
        List<AppAttribute> attributes = new ArrayList<AppAttribute>(); 
        
        if (weights.getWeights() != null) {
            for (EntitlementWeight weight : weights.getWeights()) {
                if (weight.getType() == EntitlementWeight.EntitlementType.permission) {
                    permissions.add(new AppPermission(weight.getTarget(), weight.getValue(), String.valueOf(weight.getWeight())));
                } else if (weight.getType() == EntitlementWeight.EntitlementType.attribute) {
                    attributes.add(new AppAttribute(weight.getTarget(), weight.getValue(), String.valueOf(weight.getWeight())));                    
                }
            }
        }
        
        String accountWeight = Util.itoa(weights.getAccountWeight()); 
        return new ApplicationEntitlement(weights.getApplication().getName(), accountWeight, permissions, attributes);
    }
    
    private void saveEntitlementBARs(ScoreConfig config) throws GeneralException {        
        ScoreDefinition entitlementsDef = config.getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);

        
        List<ApplicationEntitlementWeights> updatedEntitlements = new ArrayList<ApplicationEntitlementWeights>();

        for (ApplicationEntitlement entitlement : applicationEntitlements.values()) {
            List<AppPermission> permissions = entitlement.getPermissions();
            List<EntitlementWeight> updatedWeights = new ArrayList<EntitlementWeight>();

            for (AppPermission permission : permissions) {
                EntitlementWeight updatedWeight = new EntitlementWeight(EntitlementWeight.EntitlementType.permission, permission.getTarget(), permission.getName(), permission.getWeight());
                updatedWeights.add(updatedWeight);
            }
            
            List<AppAttribute> attributes = entitlement.getAttributes(); 
            for (AppAttribute attribute : attributes) {
                EntitlementWeight updatedWeight = new EntitlementWeight(EntitlementWeight.EntitlementType.attribute, attribute.getAttribute(), attribute.getValue(), attribute.getWeight());
                updatedWeights.add(updatedWeight);
            }

            ApplicationEntitlementWeights aew = new ApplicationEntitlementWeights(entitlement.getName(), updatedWeights, getContext());
            aew.setAccountWeight(Util.atoi(entitlement.getAccountWeight()));
            updatedEntitlements.add(aew);
        }
                
        entitlementsDef.setArgument("applicationEntitlementWeights", updatedEntitlements);
    }
    
    private List<EntitlementWeight> createDefaultEntitlementWeights() {
        final List<EntitlementWeight> retval = new ArrayList<EntitlementWeight>();
        
        EntitlementWeight newWeight;
        try {
            RightConfig rightConfig = getContext().getObjectByName(RightConfig.class, RightConfig.OBJ_NAME);
            
            List<Right> rights = rightConfig.getRights();
            for (Right right : rights) {
                String name = right.getDisplayableName(getLocale());
                String weight = Integer.toString(right.getWeight());
                newWeight = new EntitlementWeight(EntitlementWeight.EntitlementType.permission, null, name, weight);
                retval.add(newWeight);                
            }
        } catch (GeneralException e) {
            log.error("Entitlement BAR permission values could not be initialized because the RightConfig was not accessible", e);
        }
        
        return retval;
    }


    public class ApplicationEntitlement implements Comparable<ApplicationEntitlement> {
        private String id;
        private String name;
        private String maxValue;
        private String increment;
        private String accountWeight;
        private List<AppPermission> permissions;
        private List<AppAttribute> attributes;
        private List<SelectItem> availableAttributes;
        private boolean isChecked;
        private String applicationId;
        
        public ApplicationEntitlement(final String name, String accountWeight, final List<AppPermission> permissions, final List<AppAttribute> attributes) {
            this.id = StringUtils.deleteWhitespace(name);
            this.name = name;
            this.accountWeight = accountWeight;
            this.permissions = permissions;
            this.attributes = attributes;
            this.maxValue = "10";
            this.increment = "0.1";
            this.availableAttributes = new ArrayList<SelectItem>();

            try {
                Application app = getContext().getObjectByName(Application.class, name);
                this.applicationId = app.getId();
                
                Schema schema = app.getSchema(Connector.TYPE_ACCOUNT);
                
                // cya here - everything SHOULD have a schema defined, but it's possible
                // that someone is trying to play with the entitlement bar first
                if (null != schema) {
	                List<String> attributeNames = schema.getEntitlementAttributeNames();
	                for (String attributeName : attributeNames) {
	                    availableAttributes.add(new SelectItem(attributeName));
	                }
	                availableAttributes.add(0, new SelectItem("", getMessage(MessageKeys.SELECT_ATTRIBUTE)));
                }
            } catch (GeneralException e) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE),
                        e.getMessageInstance());
                log.error("The database is not accessible right now.", e);
            }
            
            if (availableAttributes.isEmpty()) {
                availableAttributes.add(new SelectItem("", getMessage(MessageKeys.NO_ATTRS_FOUND_FOR_APP, name)));
            }
        }
        
        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
        
        public String getMaxValue() {
            return maxValue;
        }
        
        public String getIncrement() {
            return increment;
        }
        
        public String getAccountWeight() {
            return accountWeight;
        }

        public void setAccountWeight(String s) {
            accountWeight = s;
        }

        public List<AppPermission> getPermissions() {
            return permissions;
        }
        
        public List<AppAttribute> getAttributes() {
            return attributes;
        }

        public void setAttributes(List<AppAttribute> attributes) {
            this.attributes = attributes;
        }

        public boolean isChecked() {
            return isChecked;
        }

        public void setChecked(boolean isChecked) {
            this.isChecked = isChecked;
        }
        
        public List<SelectItem> getAvailableAttributes() {
            return availableAttributes;
        }
        
        public String getApplicationId() {
        	return applicationId;
        }
                
        public int compareTo(ApplicationEntitlement ae) {
            return this.getName().compareTo(ae.getName());
        }
    }
    
    public class AppPermission {
        // Target is unused in 1.0
        private String target;
        private String name;
        private String weight;
        
        public AppPermission(final String target, final String name, final String weight) {
            this.target = target;
            this.name = name;
            this.weight = weight;
        }
        
        public String getTarget() {
            return target;
        }
        
        public String getName() {
            return name;
        }
        
        public void setWeight(final String weight) {
            this.weight = weight;
        }
        
        public String getWeight() {
            return weight;
        }
    }
    
    public static class AppAttribute {
        private String attribute;
        private String value;
        private String weight;
        private boolean isChecked;
        
        public AppAttribute() {
        }
        
        public AppAttribute(final String attribute, final String value, final String weight) {
            this.attribute = attribute;
            this.value = value;
            this.weight = weight;
        }
        
        public void setAttribute(final String attribute) {
            this.attribute = attribute;
        }
        
        public String getAttribute() {            
            return attribute;
        }

        public void setValue(final String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public void setWeight(final String weight) {
            this.weight = weight;
        }

        public String getWeight() {
            return weight;
        }

        public void setChecked(boolean isChecked) {
            this.isChecked = isChecked;
        }

        public boolean isChecked() {
            return isChecked;
        }
        
        public String getHashCode() {
            return Integer.toString( hashCode() );
        }
        
        @Override
        public int hashCode() {
            int hashCode = super.hashCode();
            if( attribute != null )
                hashCode += attribute.hashCode();
            if( value != null ) 
                hashCode += value.hashCode();
            return hashCode;
        }
    }
    
    public class EntitlementRight {
        private String name;
        private String value;
        
        public EntitlementRight(final String name, final String value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() {
            return name;
        }
        
        public void setValue(String val) {
            value = val;
        }
        
        public String getValue() {
            return value;
        }
    }    
}
