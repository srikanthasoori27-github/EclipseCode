/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class RiskScoreConfigBean extends BaseBean {
    private static Log log = LogFactory.getLog(RiskScoreConfigBean.class);

    /**
     * Name of the HTTPSession attribute we use to hold the 
     * name of the ScoreDefinition we're editing when we drill
     * into the configuration page for a custom score.
     * Someday consider refactoring the hard-wired support
     * for the other score components into something more
     * extensible based on named ScoreWeights.
     */
    public static final String ATT_SCORE_DEFINITION = "ScoreDefinition";

    private Map<String, ScoreWeight> compensatedWeights;
    private Map<String, ScoreWeight> barWeights;
    private CertificationScoreConfig certificationConfig;
    private int numCategories;
    private int nextId;   
    private ScoreDefinition scoreDefinition;
    private Map<String, CompensationConfig> compensationConfigMap;
    private Map<String, CategoryUiProperties> uiPropsMap;

    @SuppressWarnings("unchecked")
    public RiskScoreConfigBean() {
        super();
        
        try {
            Map requestParams = getRequestParam();
            boolean forceLoad = Util.otob(requestParams.get(BaseObjectBean.FORCE_LOAD));
            
            // Clear out the tab config if necessary
            if (forceLoad) {
                FacesContext myFacesCtx = getFacesContext();
                EditedEntitlementAppBean entitlementAppBean = (EditedEntitlementAppBean) myFacesCtx.getApplication().createValueBinding("#{editedEntitlementApp}").getValue(myFacesCtx);
                entitlementAppBean.setCurrentRiskConfigTab("baselineAccessRiskPanel");
            }

            ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
            initializeUiPropsMap();
            initializeCompensations(config);
            
            initializeWeights(config);
            initializeCertifications(config);

        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE), null);
            log.error("The database is not accessible right now.", e);
        }
    }

    /**
     * Called from config pages for custom scores.
     * saveAndConfig must have recorded the name of the
     * ScoreDefinition we're editing in a session attribute.
     */
    @SuppressWarnings("unchecked")
    public ScoreDefinition getScoreDefinition() throws GeneralException {

        if (scoreDefinition == null) {
            Map session = getSessionScope();
            String defname = (String)session.get(ATT_SCORE_DEFINITION);
            if (defname == null)
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_UNABLE_LOCATE_SCORE_DEF_NAME_ON_SESSION), null);
            else {
                ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
                scoreDefinition = config.getIdentityScore(defname);
                if (scoreDefinition == null)
                    addMessage(new Message(Message.Type.Error,
                            MessageKeys.ERR_UNABLE_LOCATE_SCORE_DEF_NAME, defname), null);
            }
        }

        return scoreDefinition;
    }

    public List<ScoreWeight> getCompensatedWeights() {
        // Specify the order here
        String [] builtInCompensatedWeights = {
                ScoreConfig.SCORE_ROLE, 
                ScoreConfig.SCORE_ENTITLEMENT,
                ScoreConfig.SCORE_POLICY,
                ScoreConfig.SCORE_CERT
        };
        
        return orderWeights(builtInCompensatedWeights, compensatedWeights);
    }    
    
    public List<ScoreWeight> getBarWeights() {
        // Specify the order here
        String [] builtInBarWeights = {
                ScoreConfig.SCORE_RAW_ROLE, 
                ScoreConfig.SCORE_RAW_ENTITLEMENT,
                ScoreConfig.SCORE_RAW_POLICY
        };

        return orderWeights(builtInBarWeights, barWeights);
    }
    
    private List<ScoreWeight> orderWeights(String [] orderOfWeights, Map<String, ScoreWeight> weightsToOrder) {
        final List<ScoreWeight> retval = new ArrayList<ScoreWeight>();
        Map<String, ScoreWeight> remainingWeights = new HashMap<String, ScoreWeight>(weightsToOrder); 
        
        for (int i = 0; i < orderOfWeights.length; ++i) {
            ScoreWeight weight = weightsToOrder.get(orderOfWeights[i]);
            if (weight != null) {
                retval.add(weight);
                remainingWeights.remove(orderOfWeights[i]);
            }
        }
        
        if (!remainingWeights.isEmpty()) {
            retval.addAll(remainingWeights.values());
        }
        
        return retval;

    }
    
    
    public int getNumCategories() {
        return numCategories;
    }

    public void setNumCategories(int numCategories) {
        this.numCategories = numCategories;
    }
    
    public CertificationScoreConfig getCertificationConfig() {
        return certificationConfig;
    }

    public String getCertificationCompensationInstructions() {
        return certificationConfig.getInstructions();
    }

    public String getEntitlementCompensationInstructions() {
        return getCompensationInstructions(ScoreConfig.SCORE_ENTITLEMENT);
    }

    public String getBusinessRoleCompensationInstructions() {
        return getCompensationInstructions(ScoreConfig.SCORE_ROLE);
    }

    public String getSodViolationCompensationInstructions() {
        return getCompensationInstructions(ScoreConfig.SCORE_POLICY);
    } 
    
    private String getCompensationInstructions(String name) {
        final String instructions;        
        CompensationConfig compensationConfig = compensationConfigMap.get(name);

        if (compensationConfig != null)
            instructions = compensationConfig.getInstructions();
        else
            instructions = null;
        
        return instructions;        
    }
    
    public String saveChangesAction() {
        try {
            ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");

            List<ScoreDefinition> potentialCategories = config.getIdentityScores();
            saveCompositeScoreConfig(potentialCategories, config);
            config.setIdentityScores(potentialCategories);
            getContext().saveObject(config);
            getContext().commitTransaction();
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE), null);
            log.error("No changes can be saved right now because the system is offline.", e);
        }
        
        return "save";
    }
    
    private void saveCompositeScoreConfig(List<ScoreDefinition> scoreDefinitions, ScoreConfig config) throws GeneralException {        
        // Save the weights and compensating factors
        for (ScoreDefinition category : scoreDefinitions) {
            if (!category.isComposite()) {
                // jsl - formerly tested the isCompensated flag here, but
                // really we're interested in any "component" score, which
                // may or may not be compensated, I didn't change the names of
                // all the related fields though so you can think of compensated
                // as being synonymous with component in this file
                if (category.isComponent()) {
                    ScoreWeight weight = compensatedWeights.get(category.getName());
                    if (null != weight) {
                        category.setWeight(Integer.parseInt(weight.getWeight()));
                    } 
                    
                    // Save the business role, entitlement, and/or SodViolation compensations
                    List<Compensation> currentComps = getCompsForCategory(category);
                    
                    for (Compensation comp: currentComps) {
                        final String persistentFactor;
                        
                        float uiFactor = Float.parseFloat(comp.getFactor());
                        if (comp.isReducer()) {
                            persistentFactor = String.valueOf(1.0 - (uiFactor / 100.0));
                        } else {
                            persistentFactor = String.valueOf(uiFactor / 100.0 + 1.0);
                        }
                        
                        category.setArgument(comp.getType().toString(), persistentFactor);
                    }
                }
            }
        }
        
        // Save the certification stuff
        ScoreDefinition certCompensatedScoreDef = config.getIdentityScore(ScoreConfig.SCORE_CERT);
        certCompensatedScoreDef.setArgument("offset", certificationConfig.getOffset());
        certCompensatedScoreDef.setArgument("range", certificationConfig.getRange());
    }
        
    private List<Compensation> getCompsForCategory(ScoreDefinition category) {
        final List<Compensation> compList = new ArrayList<Compensation>();
        
        if (ScoreConfig.SCORE_ROLE.equals(category.getName())) {
            compList.addAll(getBusinessRoleCompensations());
        } else if (ScoreConfig.SCORE_POLICY.equals(category.getName())) {
            compList.addAll(getSodViolationCompensations());
        } else {
            compList.addAll(getEntitlementCompensations());
        }
        
        return compList;
    }
        
    public String cancelChangesAction() {
        return "cancel";
    }
    
    public List<Compensation> getBusinessRoleCompensations() {
        return getCompensations(ScoreConfig.SCORE_ROLE);
    }

    public void setBusinessRoleCompensations(List<Compensation> newCompensations) {
        setCompensations(ScoreConfig.SCORE_ROLE, newCompensations);
    }
    
    public List<Compensation> getEntitlementCompensations() {
        return getCompensations(ScoreConfig.SCORE_ENTITLEMENT);
    }

    public void setEntitlementCompensations(List<Compensation> newCompensations) {
        setCompensations(ScoreConfig.SCORE_ENTITLEMENT, newCompensations);
    }

    public List<Compensation> getSodViolationCompensations() {
        return getCompensations(ScoreConfig.SCORE_POLICY);
    }

    public void setSodViolationCompensations(List<Compensation> newCompensations) {
        setCompensations(ScoreConfig.SCORE_POLICY, newCompensations);
    }
    
    private List<Compensation> getCompensations(String name) {
        List<Compensation> retval;
        Map<CompensationType, Message> descriptionMap = getDescriptionMap(name);
        CompensationConfig compensationConfig = compensationConfigMap.get(name);
        
        if (compensationConfig != null) {
            retval = compensationConfig.getCompensations();
            for (Compensation comp : retval) {
                Message description = descriptionMap.get(comp.getType());
                comp.setDescription(description);
            }
        } else {
            retval = new ArrayList<Compensation>();
        }
        
        return retval;        
    }
    
    private void setCompensations(String name, List<Compensation> compensations) {
        CompensationConfig compensationConfig = compensationConfigMap.get(name);
        if (compensationConfig != null)
            compensationConfig.setCompensations(compensations);
    }
    
    /**
     * @return A response matching the nagivation rule for the config page of the component that
     * was clicked.  Note that the configLocation is specified in the ScoreDefinition object.
     */
    @SuppressWarnings("unchecked")
    public String saveAndConfig() {
        String category = 
            (String) getFacesContext().getExternalContext().getRequestParameterMap().get("editForm:category");
        
        saveChangesAction();

        // Have to remember this so extensible scores know what to edit.  
        Map session = getSessionScope();
        session.put(ATT_SCORE_DEFINITION, category);
        
        ScoreWeight weightInfo = barWeights.get(category);
        
        if (weightInfo == null) {
            weightInfo = compensatedWeights.get(category);
        }
        
        final String configLocation;
        
        if (weightInfo == null) {
            configLocation = null;
        } else {
            configLocation = weightInfo.getConfigLocation();
        }
        
        return configLocation;
    }
    
    @SuppressWarnings("unchecked")
    public String cancelAndConfig() {
        String category = 
            (String) getFacesContext().getExternalContext().getRequestParameterMap().get("editForm:category");
        
        // Have to remember this so extensible scores know what to edit.  
        Map session = getSessionScope();
        session.put(ATT_SCORE_DEFINITION, category);

        ScoreWeight weightInfo = barWeights.get(category);
        
        if (weightInfo == null) {
            weightInfo = compensatedWeights.get(category);
        }
        
        final String configLocation;
        
        if (weightInfo == null) {
            configLocation = null;
        } else {
            configLocation = weightInfo.getConfigLocation();
        }
        
        return configLocation;
    }

    private void initializeWeights(ScoreConfig config) throws GeneralException {
        nextId = 0;

        compensatedWeights = new HashMap<String, ScoreWeight>();
        barWeights = new HashMap<String, ScoreWeight>();
        
        List<ScoreDefinition> potentialCategories = config.getIdentityScores();
        
        if (potentialCategories != null) {
            for (ScoreDefinition category : potentialCategories) {
                if (!category.isComposite()) {
                    if (category.isComponent()) {
                        compensatedWeights.put(
                            category.getName(),
                            new ScoreWeight(category, true));
                    } else {
                        barWeights.put(category.getName(), new ScoreWeight(category, false));
                    }
                }
            }
        }
        
        numCategories = compensatedWeights.size();
    }
    
    private void initializeCompensations(ScoreConfig config) {
        compensationConfigMap = new HashMap<String, CompensationConfig>();
        List<ScoreDefinition> scoreDefinitions = config.getIdentityScores();
        if (scoreDefinitions != null) {
            for (ScoreDefinition scoreDef : scoreDefinitions) {
                String category = scoreDef.getName();
                CompensationConfig compensationInfo = new CompensationConfig(scoreDef);
                compensationConfigMap.put(category, compensationInfo);
            }
        }
    }
    
    private void initializeCertifications(ScoreConfig config) throws GeneralException {
        ScoreDefinition certCompensatedScoreDef = config.getIdentityScore(ScoreConfig.SCORE_CERT);
        if (certCompensatedScoreDef != null)
            certificationConfig = new CertificationScoreConfig(certCompensatedScoreDef.getArgument("offset").toString(), certCompensatedScoreDef.getArgument("range").toString(), certCompensatedScoreDef.getDescription());
    }
    
    public class ScoreWeight {
        private final String id;
        private String type;
        private String scoreName;
        private String weight;
        private String configLocation;
        private String description;
        
        public ScoreWeight(ScoreDefinition category, final boolean isCompensation) {
            this.type = category.getDisplayableName();
            this.description = category.getDescription();
            this.weight = String.valueOf(category.getWeight());
            this.configLocation = category.getConfigPage();
            this.scoreName = category.getName();
            
            if (isCompensation) {
                this.id = String.valueOf(getNextID());
            } else {
                this.id = "-1";
            }
        }
        
        public String getType() {
            return type;
        }
        
        public String getWeight() {
            return weight;
        }
        
        public void setWeight(final String weight) {
            this.weight = weight;
        }
        
        public void setScoreName(final String scoreName) {
            this.scoreName = scoreName;
        }
        
        public String getScoreName() {
            return scoreName;
        }
        
        public String getConfigLocation() {
            return configLocation;
        }
        
        public String getDescription() {
            return description;
        }
        
        private int getNextID() {
            return nextId++;
        }

        public String toString() {
            StringBuffer buf = new StringBuffer("ScoreWeight: type=[");
            buf.append(type);
            buf.append("], weight=[");
            buf.append(weight);
            buf.append("], configLocation=[");
            buf.append(configLocation);
            buf.append("]");
            
            return buf.toString();
        }

        public String getId() {
            return id;
        }
    }
    
    
    
    public enum CompensationType {
        uncertifiedFactor,
        certifiedFactor,
        mitigatedFactor,
        expiredFactor,
        remediatedFactor,
        activityMonitoringFactor
    }
    
    public class Compensation {
        private String id;
        private Message description;
        private String factor;
        private String minValue;
        private String maxValue;
        private String increment;
        private CompensationType type;
        private boolean isReducer;
        
        // Comments that will be displayed next to the percentage input box in the UI
        private String percentageComments;
        
        public Compensation(final CompensationType aType, final String anId, final String aFactor, final String anIncrement, final String aMinValue, final String aMaxValue, final String percentageComments, final boolean isReducer) {
            factor = aFactor;
            increment = anIncrement;
            minValue = aMinValue;
            maxValue = aMaxValue;
            id = anId;
            type = aType;
            this.percentageComments = percentageComments;
            this.isReducer = isReducer;
        }
        
        protected void setDescription(final Message description) {
            this.description = description;
        }
        
        public Message getDescription() {
            return description;
        }
        
        public String getFactor() {
            return factor;
        }
        
        public void setFactor(final String aFactor) {
            factor = aFactor;
        }
        
        public void setIncrement(final String anIncrement) {
            increment = anIncrement;
        }

        public String getIncrement() {
            return increment;
        }

        public void setMinValue(final String aMinValue) {
            minValue = aMinValue;
        }
        
        public String getMinValue() {
            return minValue;
        }
        
        public void setMaxValue(final String aMaxValue) {
            maxValue = aMaxValue;
        }
        
        public String getMaxValue() {
            return maxValue;
        }
        
        public String getId() {
            return id;
        }
                
        public String getPercentageComments() {
            return percentageComments;
        }
        
        public CompensationType getType() {
            return type;
        }
        
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            sb.append(Compensation.class.getName());
            sb.append(": [id=");
            sb.append(id);
            sb.append("], [factor=");
            sb.append(factor);
            sb.append("], [increment=");
            sb.append(increment);
            sb.append("], [minValue=");
            sb.append(minValue);
            sb.append("], [maxValue=");
            sb.append(maxValue);
            sb.append("], [type=");
            sb.append(type);
            sb.append("], [percentageComments=");
            sb.append(percentageComments);
            sb.append("]]");

            return sb.toString();
        }

        public boolean isReducer() {
            return isReducer;
        }
        
        public void setReducer(boolean isReducer) {
            this.isReducer = isReducer;
        }
    }
    
    static final Map<CompensationType, Message> JF_TYPE_TO_DESCRIPTION_MAP;

    static {
        final String businessRole = MessageKeys.BIZ_ROLE_LCASE;
        JF_TYPE_TO_DESCRIPTION_MAP = new HashMap<CompensationType, Message>();
        
        JF_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.certifiedFactor, new Message(MessageKeys.COMPENSATION_CERTIFIEDFACTOR, new Message(businessRole)));
        JF_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.uncertifiedFactor, new Message(MessageKeys.COMPENSATION_UNCERTIFIEDFACTOR, new Message(businessRole)));
        JF_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.mitigatedFactor, new Message(MessageKeys.COMPENSATION_MITIGATEDFACTOR, new Message(businessRole)));
        JF_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.expiredFactor, new Message(MessageKeys.COMPENSATION_EXPIREDFACTOR, new Message(businessRole)));
        JF_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.remediatedFactor, new Message(MessageKeys.COMPENSATION_REMEDIATEDFACTOR, new Message(businessRole)));
        JF_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.activityMonitoringFactor, new Message(MessageKeys.COMPENSATION_BIZ_ROLE_ACTIVITYMONITORINGFACTOR));
    }

    public class BusinessRoleCompensation extends Compensation {        

        public BusinessRoleCompensation(final CompensationType type, final String anId, final String aFactor, final String anIncrement, final String aMinValue, final String aMaxValue, final String percentageComments, final boolean isReducer) {
            super(type, anId, aFactor, anIncrement, aMinValue, aMaxValue, percentageComments, isReducer);
            setDescription(JF_TYPE_TO_DESCRIPTION_MAP.get(type));
        }

    }
    
    private void initializeUiPropsMap() {
        uiPropsMap = new HashMap<String, CategoryUiProperties>();
        uiPropsMap.put(ScoreConfig.SCORE_ROLE, new CategoryUiProperties("JF", "5"));
        uiPropsMap.put(ScoreConfig.SCORE_POLICY, new CategoryUiProperties("PV", "5"));
        uiPropsMap.put(ScoreConfig.SCORE_ENTITLEMENT, new CategoryUiProperties("ENT", "5"));
    }

    static final Map<CompensationType, Message> ENT_TYPE_TO_DESCRIPTION_MAP;
    
    static {
        final String entitlement = MessageKeys.ENTITLEMENT_LCASE;
        ENT_TYPE_TO_DESCRIPTION_MAP = new HashMap<CompensationType, Message>();
        
        ENT_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.certifiedFactor, new Message(MessageKeys.COMPENSATION_CERTIFIEDFACTOR, new Message(entitlement)));
        ENT_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.uncertifiedFactor, new Message(MessageKeys.COMPENSATION_UNCERTIFIEDFACTOR, new Message(entitlement)));
        ENT_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.mitigatedFactor, new Message(MessageKeys.COMPENSATION_MITIGATEDFACTOR, new Message(entitlement)));
        ENT_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.expiredFactor, new Message(MessageKeys.COMPENSATION_EXPIREDFACTOR, new Message(entitlement)));
        ENT_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.remediatedFactor, new Message(MessageKeys.COMPENSATION_REMEDIATEDFACTOR, new Message(entitlement)));
        ENT_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.activityMonitoringFactor, new Message(MessageKeys.COMPENSATION_ENTITLEMENT_ACTIVITYMONITORINGFACTOR));
    }

    public class EntitlementCompensation extends Compensation {        
        public EntitlementCompensation(final CompensationType type, final String anId, final String aFactor, final String anIncrement, final String aMinValue, final String aMaxValue, final String percentageComments, final boolean isReducer) {
            super(type, anId, aFactor, anIncrement, aMinValue, aMaxValue, percentageComments, isReducer);
            setDescription(ENT_TYPE_TO_DESCRIPTION_MAP.get(type));
        }        
    }

    static final Map<CompensationType, Message> PV_TYPE_TO_DESCRIPTION_MAP;
    
    static {
        final String policyViolation = MessageKeys.POLICY_VIOLATION_LCASE;
        PV_TYPE_TO_DESCRIPTION_MAP = new HashMap<CompensationType, Message>();

        PV_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.uncertifiedFactor,
                new Message(MessageKeys.COMPENSATION_UNCERTIFIEDFACTOR, new Message(policyViolation)));
        PV_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.mitigatedFactor,
                new Message(MessageKeys.COMPENSATION_SOD_MITIGATEDFACTOR));
        PV_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.expiredFactor,
                new Message(MessageKeys.COMPENSATION_EXPIREDFACTOR, new Message(policyViolation)));
        PV_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.remediatedFactor,
                new Message(MessageKeys.COMPENSATION_SOD_REMEDIATEDFACTOR));
        PV_TYPE_TO_DESCRIPTION_MAP.put(CompensationType.activityMonitoringFactor,
                new Message(MessageKeys.COMPENSATION_SOD_ACTIVITYMONITORINGFACTOR));

    }

    private static Map<CompensationType, Message> getDescriptionMap(String name) {
        final Map<CompensationType, Message> descriptionMap;
        
    	if (ScoreConfig.SCORE_ROLE.equals(name)) {
    		descriptionMap = JF_TYPE_TO_DESCRIPTION_MAP;
    	} else if (ScoreConfig.SCORE_POLICY.equals(name)) {
    	    descriptionMap = PV_TYPE_TO_DESCRIPTION_MAP;
    	} else if (ScoreConfig.SCORE_ENTITLEMENT.equals(name)) {
    	    descriptionMap = ENT_TYPE_TO_DESCRIPTION_MAP;
    	} else {
    	    descriptionMap =  new HashMap<CompensationType, Message>();
    	}
    	
    	return descriptionMap;
    }
    
    public class PolicyViolationCompensation extends Compensation {        
        public PolicyViolationCompensation(final CompensationType type, final String anId, final String aFactor, final String anIncrement, final String aMinValue, final String aMaxValue, final String percentageComments, final boolean isReducer) {
            super(type, anId, aFactor, anIncrement, aMinValue, aMaxValue, percentageComments, isReducer);
            setDescription(PV_TYPE_TO_DESCRIPTION_MAP.get(type));
        }        
    }

    public class CertificationScoreConfig {
        private String offset;
        private String range;
        private String instructions;
        
        public CertificationScoreConfig(final String offset, final String range, final String instructions) {
            this.offset = offset;
            this.range = range;
            this.instructions = instructions;
        }
        
        public void setOffset(final String offset) {
            this.offset = offset;
        }
        
        public String getOffset() {
            return offset;
        }
        
        public void setRange(final String range) {
            this.range = range;
        }
        
        public String getRange() {
            return range;
        }

        public String getInstructions() {
            return instructions;
        }

        public void setInstructions(String instructions) {
            this.instructions = instructions;
        }
    }
    
    private class CompensationConfig {
        @SuppressWarnings("unchecked")
        public CompensationConfig(final ScoreDefinition scoreDef) {
            compensations = new ArrayList<Compensation>();
            
            if (scoreDef != null) {
                instructions = scoreDef.getDescription();
    
                Map controls = scoreDef.getArguments();
                
                CompensationType [] types = CompensationType.values();
                CategoryUiProperties uiProps = uiPropsMap.get(scoreDef.getName());
                
                if (uiProps != null) {
                    for (int i = 0; i < types.length; ++i) {
                        CompensationType type = types[i];
                        
                        String typeName = type.toString();
                        
                        if (controls.get(typeName) != null) {
                            String factor = controls.get(typeName).toString();
                            String min = controls.get(typeName + "Min").toString();
                            String max = controls.get(typeName + "Max").toString();
                            boolean isReducer = Float.parseFloat(min) < 1;
                            
                            final String uiMin;
                            final String uiMax;
                            final String uiFactor;
                            
                            if (isReducer) {
                                // Adjust min and max to scale from 0-100%
                                uiMin = "0";
                                uiMax = "100";
                                uiFactor = String.valueOf(Math.round(new Float((1.0 - Float.parseFloat(factor)) * 100.0)));
                            } else {
                                // Adjust min and max to a percent increase
                                uiMin = String.valueOf(Math.round(new Float((Float.parseFloat(min) - 1.0) * 100.0)));
                                uiMax = String.valueOf(Math.round(new Float((Float.parseFloat(max) - 1.0) * 100.0)));
                                uiFactor = String.valueOf(Math.round(new Float((Float.parseFloat(factor) - 1.0) * 100.0)));
                            }
        
                            String percentageComments = isReducer ? getMessage(MessageKeys.DECREASES_RISK_BY) :
                                    getMessage(MessageKeys.INCREASES_RISK_BY);
            
                            Compensation newCompensation = 
                                new Compensation(type, uiProps.getPrefix() + type, uiFactor, uiProps.getIncrement(), uiMin, uiMax, percentageComments, isReducer);
                            
                           compensations.add(newCompensation);
                        } 
                    }
                }            
            }
        }
        
        private List<Compensation> compensations;
        private String instructions;
        
        public List<Compensation> getCompensations() {
            return compensations;
        }
        
        public void setCompensations(List<Compensation> compensations) {
            this.compensations = compensations;
        }

        public String getInstructions() {
            return instructions;
        }

        public void setInstructions(String instructions) {
            this.instructions = instructions;
        }
    }
    
    private class CategoryUiProperties {
        private String prefix;
        private String increment;

        public CategoryUiProperties(final String aPrefix, final String anIncrement) {
            prefix = aPrefix;
            increment = anIncrement;
        }
        
        public String getPrefix() {
            return prefix;
        }

        public String getIncrement() {
            return increment;
        }
    }
}
