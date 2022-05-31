/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.risk;

import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Localizer;
import sailpoint.object.Bundle;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.web.BaseEditBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleConfig;
import sailpoint.web.modeler.RoleUtil;
import sailpoint.web.util.WebUtil;

public class BusinessRoleBarConfigBean extends BaseEditBean<ScoreConfig> implements Serializable {
    private static final long serialVersionUID = 180495847738326538L;
    private static Log log = LogFactory.getLog(BusinessRoleBarConfigBean.class);
    private String instructions;
    private List<RoleRiskScore> roles;
    
    private static final String ROLE_LIST = "roleList";
    
    
    @SuppressWarnings("unchecked")
    public BusinessRoleBarConfigBean() {
        super();

        try {
            if (getObject() == null) {
                ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
                setObject(config);
                setObjectId(config.getId());    
                ScoreDefinition score = config.getIdentityScore(ScoreConfig.SCORE_RAW_ROLE);
                if (score != null)
                    instructions = score.getDescription();
            }
            
            roles = (List<RoleRiskScore>) getEditState(ROLE_LIST);
            if (roles == null) {
                initializeRoles();
            }
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE),
                    e.getMessageInstance());
            log.error("The database is not accessible right now.", e);
        }
    }
    
    public boolean isStoredOnSession() {
        return true;
    }
    
    protected Class<ScoreConfig> getScope() {
        return ScoreConfig.class;
    }
    
    @SuppressWarnings("unchecked")
    public List<SelectItem> getRoleTypeChoices() {
        List<SelectItem> roleTypeChoices = new ArrayList<SelectItem>();
        String selectMessage = getMessage(MessageKeys.SELECT_ROLE_TYPE);
        roleTypeChoices.add(new SelectItem("", selectMessage));

        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        List<RoleTypeDefinition> roleTypeDefs = (List<RoleTypeDefinition>) roleConfig.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);
        List<String> roleTypes = new ArrayList<String>();
        if (roleTypeDefs != null && !roleTypeDefs.isEmpty()) {
            for (RoleTypeDefinition roleTypeDef : roleTypeDefs) {
                roleTypes.add(roleTypeDef.getDisplayableName());
            }
        }
        Collections.sort(roleTypes, Internationalizer.INTERNATIONALIZED_STRING_COMPARATOR);
        for (String roleType : roleTypes) {
            String label = roleType;
            roleTypeChoices.add(new SelectItem(roleType, label));
        }
        
        return roleTypeChoices;
    }
    
    public String getInstructions() {
        return instructions;
    }
    
    public String getRolesByQuery() {
        Map requestParams = getRequestParam();
        Comparator<RoleRiskScore> sortingComparator;
        
        String orderBy = (String) requestParams.get("sort");

        if ("roleType".equals(orderBy)) {
            sortingComparator = ROLE_RISK_SCORE_TYPE_COMPARATOR;
        } else if ("riskScore".equals(orderBy)){
            sortingComparator = ROLE_RISK_SCORE_SCORE_COMPARATOR;
        } else {
            sortingComparator = ROLE_RISK_SCORE_NAME_COMPARATOR;
        }
        
        String sortDir = (String) requestParams.get("dir");
        int start = -1;
        String startString = (String) requestParams.get("start");
        if (startString != null) {
            start = Integer.parseInt(startString);
        }
        int limit = getResultLimit();
        
        String typeFilter = (String) requestParams.get("type");
        String nameFilter = (String) requestParams.get("name");
        String riskRangeFilter = (String) requestParams.get("riskRangeFilter");
        
        List<RoleRiskScore> filteredRoles;
        if ((null == typeFilter || typeFilter.equals("")) 
                && (null == nameFilter || nameFilter.equals(""))
                && (null == riskRangeFilter || riskRangeFilter.equals(""))) {
            filteredRoles = roles;
        } else {
            filteredRoles = new ArrayList<RoleRiskScore>();
            if (roles != null && roles.size() > 0) {
                for (RoleRiskScore roleRiskScore : roles) {
                    boolean shouldBeAdded = true;
                    
                    if ((typeFilter != null && !typeFilter.equals(""))
                            && (roleRiskScore.getRoleType() == null || !roleRiskScore.getRoleType().equals(typeFilter))) {
                        shouldBeAdded = false;
                    }
                
                    if ((nameFilter != null && !nameFilter.equals("")) 
                            && (roleRiskScore.getName() == null || !roleRiskScore.getName().toUpperCase().startsWith(nameFilter.toUpperCase()))) {
                        shouldBeAdded = false;
                    }
                    
                    // TODO: add a risk range if needed
                    
                    if (shouldBeAdded)
                        filteredRoles.add(roleRiskScore);
                }
            }
        }
        
        Collections.sort(filteredRoles, sortingComparator);
        if ("DESC".equals(sortDir))
            Collections.reverse(filteredRoles);
        
        int numRoleResults = filteredRoles.size();
        List<RoleRiskScore> rolesToReturn = new ArrayList<RoleRiskScore>(limit);
        int endOfSubList = start + limit;
        if (endOfSubList > numRoleResults) {
            endOfSubList = numRoleResults;
        }
        
        rolesToReturn.addAll(filteredRoles.subList(start, endOfSubList));
        String jsonString = RoleUtil.getRiskGridJsonForRoles(rolesToReturn, numRoleResults);

        log.debug("Returning " + jsonString);
        return jsonString;
    }
    
    /**
     * Ext.Ajax-based action
     * @return
     */
    public String getUpdateRiskScore() {
        final String result;
        String roleId = getRequestParameter("id");
        String newRiskScore = getRequestParameter("value");
        RoleRiskScore roleRiskScore = getRoleRiskScore(roleId);
        if (roleRiskScore == null) {
            result = "failure";
        } else {
            roleRiskScore.setRiskScore(newRiskScore);
            log.debug("Updated roleRiskScore: " + roleRiskScore + " with ID " + roleRiskScore.getRoleId() + " and name " + roleRiskScore.getName() + " with new value of " + roleRiskScore.getRiskScore());
            result = "success";
        }
        
        return result;
    }

    /**
     * @return the updateRiskScore result in a JSON string.
     */
    public String getUpdateRiskScoreJSON() {
        String result = getUpdateRiskScore();
        return WebUtil.simpleJSONKeyValue("result", result);
    }

    public String saveChanges() throws GeneralException {    
        try {
            // Dump all our business roles into an id/score map
            Map<String, Integer> businessRoleIdToScore = new HashMap<String, Integer>();
    
            for (RoleRiskScore roleRiskScore : roles) {
                businessRoleIdToScore.put(roleRiskScore.getRoleId(), Integer.valueOf(roleRiskScore.getRiskScore()));
            }
            
            // Fetch all the business roles
            QueryOptions query = new QueryOptions();
            query.setScopeResults(true);
            query.addOwnerScope(super.getLoggedInUser());
            
            List<Bundle> businessRoles = getContext().getObjects(Bundle.class, query);
            
            for (Bundle businessRole : businessRoles) {
                Integer newScore = businessRoleIdToScore.get(businessRole.getId());
                if (newScore != null) {
                    businessRole.setRiskScoreWeight(newScore);
                }
            }
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE),
                    e.getMessageInstance());
            log.error("No changes can be saved right now because the system is offline.", e);
        }
        
        saveAction();
        return "save";
    }
    
    public String cancelChanges() {
        clearHttpSession();
        return "cancel";
    }
    
    private void initializeRoles() {
        roles = new ArrayList<RoleRiskScore>();

        try {
            QueryOptions opts = new QueryOptions();
            opts.setScopeResults(true);
            opts.addOwnerScope(super.getLoggedInUser());
            List<Bundle> spRoles = getContext().getObjects(Bundle.class, opts);
            
            for (Bundle role : spRoles) {
                roles.add(new RoleRiskScore(role));
            }
        } catch (GeneralException e) {
            log.error("The RiskScoreConfigBean could not fetch the business roles", e);
        }
        
        addEditState(ROLE_LIST, roles);
    }
    
    private RoleRiskScore getRoleRiskScore(String roleId) {
        RoleRiskScore retval = null;
        
        for (RoleRiskScore riskScore : roles) {
            if (riskScore.getRoleId().equals(roleId)) {
                retval = riskScore;
                break;
            }
        }
        
        return retval;
    }
    
    protected void initObjectId() {
        try {
            _objectId = getLoggedInUser().getId();
        } catch (GeneralException e) {
            log.error("No one is logged in right now.", e);
        }
    }


    public class RoleRiskScore implements Serializable {
        private static final long serialVersionUID = -7683873367725117358L;
        private String roleId;
        private String roleType;
        private String name;
        private String description;
        private String riskScore;
        
        public RoleRiskScore() {
            roleType = "";
            name = "";
            riskScore = "0";
        }
        
        public RoleRiskScore(Bundle role) {
            this.roleId = role.getId();
            String typeDisplayName;
            String type = role.getType();
            RoleConfig roleConfig = new RoleConfig();
            if (roleConfig != null) {
                RoleTypeDefinition roleTypeDef = roleConfig.getRoleTypeDefinition(type);
                if (roleTypeDef != null) {
                    typeDisplayName = roleTypeDef.getDisplayableName();
                } else {
                    typeDisplayName = type;
                }
            } else {
                typeDisplayName = type;                    
            }
            this.roleType = WebUtil.escapeHTML(typeDisplayName, false);
            this.name = WebUtil.escapeHTML(role.getName(), false);
            //Getting sanitized in WebUtil
            this.description = WebUtil.localizeAttribute(role, Localizer.ATTR_DESCRIPTION);
            this.riskScore = String.valueOf(role.getRiskScoreWeight());
        }
        

        public String getRoleId() {
            return roleId;
        }
        
        public String getRoleType() {
            return roleType;
        }
        public String getName() {
            return name;
        }
        public String getDescription() {
            return description;
        }
        public String getRiskScore() {
            return riskScore;
        }
        
        public void setRiskScore(final String riskScore) {
            this.riskScore = riskScore;
        }
        
        public String toString() {
            StringBuffer businessRoleRiskScore = new StringBuffer();
            businessRoleRiskScore.append("RoleRiskScore:[id=")
                .append(roleId)
                .append(", roleType=")
                .append(roleType)
                .append(", name=")
                .append(name)
                .append(", description=")
                .append(description)
                .append(", riskScore=")
                .append(riskScore)
                .append("]");
            return businessRoleRiskScore.toString();
        }
    }
    
    private static Comparator<RoleRiskScore> ROLE_RISK_SCORE_NAME_COMPARATOR = 
        new Comparator<RoleRiskScore>() {
            public int compare(RoleRiskScore p1, RoleRiskScore p2) {
                String p1Name = p1.getName();
                if (null == p1Name)
                    p1Name = "";
                String p2Name = p2.getName();
                if (null == p2Name)
                    p2Name = "";
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(p1Name, p2Name);
            }
        };

    private static Comparator<RoleRiskScore> ROLE_RISK_SCORE_TYPE_COMPARATOR = 
        new Comparator<RoleRiskScore>() {
            public int compare(RoleRiskScore p1, RoleRiskScore p2) {
                String p1RoleType = p1.getRoleType();
                if (null == p1RoleType)
                    p1RoleType = "";
                String p2RoleType = p2.getRoleType();
                if (null == p2RoleType)
                    p2RoleType = "";
                
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(p1RoleType, p2RoleType);
            }
        };
        
    private static Comparator<RoleRiskScore> ROLE_RISK_SCORE_SCORE_COMPARATOR = 
        new Comparator<RoleRiskScore>() {
            public int compare(RoleRiskScore p1, RoleRiskScore p2) {
                int score1;
                try {
                    score1 = Integer.parseInt(p1.getRiskScore());
                } catch (Exception e) {
                    score1 = 0;
                }
                
                int score2;
                try {
                    score2 = Integer.parseInt(p2.getRiskScore());
                } catch (Exception e) {
                    score2 = 0;
                }
                
                return score1 - score2;
            }
        };

}
