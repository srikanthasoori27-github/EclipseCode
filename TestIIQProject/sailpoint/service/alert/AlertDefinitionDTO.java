/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.alert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.integration.Util;
import sailpoint.object.AlertDefinition;
import sailpoint.object.AlertMatchConfig;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.SailPointObject;
import sailpoint.object.Workflow;
import sailpoint.service.BaseDTO;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.service.suggest.SuggestDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 9/1/16.
 */
public class AlertDefinitionDTO extends BaseDTO{

    private static Log _log = LogFactory.getLog(AlertDefinitionDTO.class);

    String _name;

    String _displayName;

    String _description;

    boolean _disabled;

    IdentitySummaryDTO _owner;

    Date _created;

    ActionConfigDTO _actionConfig;

    MatchConfigDTO _matchConfig;

    public AlertDefinitionDTO() { }

    public AlertDefinitionDTO(Map<String, Object> props, UserContext context) {
        super((String) props.get("id"));
        _name = (String) props.get("name");
        _displayName = (String) props.get("displayName");
        _description = (String) props.get("description");
        _disabled = (Boolean)props.get("disabled");
        _owner = getIdentitySummaryDto((String) props.get("owner"), context);
        _created = (Date) props.get("created");

    }

    public AlertDefinitionDTO(AlertDefinition def, UserContext context) {
        super(def.getId());
        _name = def.getName();
        _displayName = def.getDisplayName();
        _description = def.getDescription();
        _disabled = def.isDisabled();
        _owner = def.getOwner() != null ? new IdentitySummaryDTO(def.getOwner()) : null;
        _created = def.getCreated();

        _actionConfig = getActionConfigDto(def.getActionConfig(), context);
        _matchConfig = getMatchConfigDto(def.getMatchConfig(), context);

    }

    public String getName() { return _name; }
    public void setName(String s) { _name = s; }

    public String getDisplayName() { return _displayName; }
    public void setDisplayName(String s) { _displayName = s; }

    public String getDescription() { return _description; }
    public void setDescription(String s) { _description = s; }

    public boolean isDisabled() { return _disabled; }
    public void setDisabled(boolean b) { _disabled = b; }

    public IdentitySummaryDTO getOwner() { return _owner; }
    public void setOwner(IdentitySummaryDTO s) { _owner = s; }

    public Date getCreated() { return _created; }
    public void setCreated(Date created) { _created = created; }

    public MatchConfigDTO getMatchConfig() { return _matchConfig; }
    public void setMatchConfig(MatchConfigDTO dto) { _matchConfig = dto; }

    public ActionConfigDTO getActionConfig() { return _actionConfig; }
    public void setActionConfig(ActionConfigDTO dto) { _actionConfig = dto; }

    public MatchConfigDTO getMatchConfigDto(AlertMatchConfig config, UserContext context) {
        MatchConfigDTO dto = null;
        if (config != null) {
            dto = new MatchConfigDTO(config, context);
        }
        return dto;
    }

    public ActionConfigDTO getActionConfigDto(AlertDefinition.ActionConfig config, UserContext context) {
        ActionConfigDTO dto = null;
        if (config != null) {
            dto = new ActionConfigDTO(config, context);
        }
        return dto;
    }

    public static IdentitySummaryDTO getIdentitySummaryDto(String identName, UserContext context) {
        IdentitySummaryDTO dto = null;
        try {
            if (Util.isNotNullOrEmpty(identName)) {
                Identity ident = context.getContext().getObjectByName(Identity.class, identName);
                if (ident != null) {
                    dto = new IdentitySummaryDTO(ident);
                }
            }
        } catch (GeneralException ge) {
            _log.error("Error getting IdentitySummaryDto for ident[" + identName + "]", ge);
        }

        return dto;
    }

    static class ActionConfigDTO {

        SuggestDTO _actionType;

        SuggestDTO _workflow;

        SuggestDTO _certificationTrigger;

        SuggestDTO _emailTemplate;

        List<IdentitySummaryDTO> _emailRecipients;

        public ActionConfigDTO() { }

        public ActionConfigDTO(AlertDefinition.ActionConfig config, UserContext context) {
            _actionType = getActionTypeDTO(config.getActionType(), context);
            _workflow = getSuggestDto(config.getWorkflowName(), Workflow.class, context);
            _certificationTrigger = getSuggestDto(config.getCertificationTrigger(),
                    IdentityTrigger.class, context);
            _emailTemplate = getSuggestDto(config.getEmailTemplate(), EmailTemplate.class, context);
            _emailRecipients = getEmailRecipientDtos(config.getEmailRecipients(), context);
        }

        public SuggestDTO getActionType() { return _actionType; }
        public void setActionType(SuggestDTO s) { _actionType = s; }

        public SuggestDTO getWorkflow() { return _workflow; }
        public void setWorkflow(SuggestDTO s) { _workflow = s; }

        public SuggestDTO getCertificationTrigger() { return _certificationTrigger; }
        public void setCertificationTrigger(SuggestDTO s) { _certificationTrigger = s; }

        public SuggestDTO getEmailTemplate() { return _emailTemplate; }
        public void setEmailTemplate(SuggestDTO s) { _emailTemplate = s; }

        public List<IdentitySummaryDTO> getEmailRecipients() { return _emailRecipients; }
        public void setEmailRecipients(List<IdentitySummaryDTO> s) { _emailRecipients = s; }

        protected List<IdentitySummaryDTO> getEmailRecipientDtos(List<String> identNames, UserContext context) {
            List<IdentitySummaryDTO> dtos = null;

            for(String s : sailpoint.tools.Util.safeIterable(identNames)) {
                IdentitySummaryDTO d = getIdentitySummaryDto(s, context);
                if (d != null) {
                    if (dtos == null) {
                        dtos = new ArrayList<IdentitySummaryDTO>();
                    }
                    dtos.add(d);
                }
            }
            return dtos;
        }

        protected SuggestDTO getSuggestDto(String name, Class clazz, UserContext context) {
            SuggestDTO dto = null;
            if (Util.isNotNullOrEmpty(name)) {
                try {
                    SailPointObject t = context.getContext().getObjectByName(clazz, name);
                    if (t != null) {
                        dto = new SuggestDTO(t);
                    }
                } catch(GeneralException ge) {
                    _log.error("Failed to get " + clazz.getSimpleName() + "[" + name +"]", ge);
                }
            }

            return dto;
        }

        protected SuggestDTO getActionTypeDTO(AlertDefinition.ActionType type, UserContext context) {
            SuggestDTO dto = null;
            if (type != null) {
                dto = new SuggestDTO();
                dto.setName(type.name());
                dto.setDisplayName(new Message(type.getMessageKey()).getLocalizedMessage(context.getLocale(), context.getUserTimeZone()));
                dto.setId(type.name());
            }
            return dto;
        }
    }

    static class MatchConfigDTO {
        SuggestDTO _rule;

        AlertMatchExpressionDTO _matchExpression;

        public MatchConfigDTO() { }

        public MatchConfigDTO(AlertMatchConfig config, UserContext context) {
            _rule = config.getMatchRule() != null ? new SuggestDTO(config.getMatchRule()) : null;
            _matchExpression = getMatchExpressionDTO(config, context);
        }

        public SuggestDTO getRule() { return _rule; }
        public void setRule(SuggestDTO s) { _rule = s; }

        public AlertMatchExpressionDTO getMatchExpression() { return _matchExpression; }
        public void setMatchExpression(AlertMatchExpressionDTO dto) { _matchExpression = dto; }

        AlertMatchExpressionDTO getMatchExpressionDTO(AlertMatchConfig config, UserContext context) {
            AlertMatchExpressionDTO dto = null;
            if (config != null && config.getMatchExpression() != null) {
                dto = new AlertMatchExpressionDTO(config.getMatchExpression(), context);
            }
            return dto;
        }
    }

    static class AlertMatchExpressionDTO {

        boolean _and;
        List<AlertMatchTermDTO> _matchTerms;

        public AlertMatchExpressionDTO() { }

        public AlertMatchExpressionDTO(AlertMatchConfig.AlertMatchExpression expression, UserContext context) {
            _and = expression.isAnd();
            _matchTerms = getMatchTermDtos(expression, context);
        }

        public boolean isAnd() { return _and; }
        public void setAnd(boolean b) { _and = b; }

        public List<AlertMatchTermDTO> getMatchTerms() { return _matchTerms; }
        public void setMatchTerms(List<AlertMatchTermDTO> dto) { _matchTerms = dto; }

        List<AlertMatchTermDTO> getMatchTermDtos(AlertMatchConfig.AlertMatchExpression expression, UserContext context) {
            List<AlertMatchTermDTO> matchTerms = null;
            if (expression != null && !Util.isEmpty(expression.getMatchTerms())) {
                matchTerms = new ArrayList<AlertMatchTermDTO>();
                for (AlertMatchConfig.AlertMatchTerm term : sailpoint.tools.Util.safeIterable(expression.getMatchTerms())) {
                    matchTerms.add(new AlertMatchTermDTO(term, context));
                }

            }
            return matchTerms;
        }

    }

    static class AlertMatchTermDTO {
        String _source;
        String _name;
        String _value;
        boolean _container;
        boolean _and;
        List<AlertMatchTermDTO> _children;

        public AlertMatchTermDTO() { }

        public AlertMatchTermDTO(AlertMatchConfig.AlertMatchTerm term, UserContext context) {
            _source = term.getSource() != null ? term.getSource().getName() : null;
            _name = term.getName();
            _value = term.getValue();
            _container = term.isContainer();
            _and = term.isAnd();
            _children = getChildrenDtos(term, context);
        }

        public String getSource() { return _source; }
        public void setSource(String s) { _source = s; }

        public String getName() { return _name; }
        public void setName(String s) { _name = s; }

        public String getValue() { return _value; }
        public void setValue(String s) { _value = s; }

        public boolean isContainer() { return _container; }
        public void setContainer(boolean b) { _container = b; }

        public boolean isAnd() { return _and; }
        public void setAnd(boolean b) { _and = b; }

        public List<AlertMatchTermDTO> getChildren() { return _children; }
        public void setChildren(List<AlertMatchTermDTO> c) { _children = c; }

        List<AlertMatchTermDTO> getChildrenDtos(AlertMatchConfig.AlertMatchTerm term, UserContext context) {
            List<AlertMatchTermDTO> children = null;
            if (term != null && !Util.isEmpty(term.getChildren())) {
                children = new ArrayList<AlertMatchTermDTO>();
                for (AlertMatchConfig.AlertMatchTerm child : sailpoint.tools.Util.safeIterable(term.getChildren())) {
                    children.add(new AlertMatchTermDTO(child, context));
                }
            }
            return children;
        }

    }

}
