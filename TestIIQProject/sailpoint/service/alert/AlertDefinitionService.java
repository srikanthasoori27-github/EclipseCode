/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.alert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.AlertDefinition;
import sailpoint.object.AlertMatchConfig;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Rule;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.service.MessageService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ryan.pickens on 9/9/16.
 */
public class AlertDefinitionService {

    private static Log _log = LogFactory.getLog(AlertDefinitionService.class);
    UserContext _context;
    private List<Message> errors;

    public AlertDefinitionService(UserContext context) {
        _context = context;
    }

    public AlertDefinitionDTO createAlertDefinition(AlertDefinitionDTO dto) throws GeneralException {

        validate(dto);

        AlertDefinition def = new AlertDefinition();
        // check if the alert definition name is already in use
        if (this._context.getContext().getObjectByName(AlertDefinition.class, dto.getName()) != null) {
            this.getErrors().add(new Message(Message.Type.Error, MessageKeys.UI_ALERT_DEFINITION_CREATE_NAME_ERROR));
            MessageService messageService = new MessageService(this._context);
            List<String> localizedMessages = messageService.getLocalizedMessages(this.getErrors());
            throw new CreateAlertException(localizedMessages);
        };
        merge(dto, def);

        _context.getContext().saveObject(def);
        _context.getContext().commitTransaction();

        return new AlertDefinitionDTO(def, _context);

    }

    public AlertDefinitionDTO updateAlertDefinition(AlertDefinitionDTO dto)
        throws GeneralException {

        validate(dto);

        SailPointContext ctx = _context.getContext();

        AlertDefinition def = ctx.getObjectById(AlertDefinition.class, dto.getId());
        if (def == null) {
            throw new ObjectNotFoundException(AlertDefinition.class, dto.getId());
        }

        merge(dto, def);

        _context.getContext().saveObject(def);
        _context.getContext().commitTransaction();

        return new AlertDefinitionDTO(def, _context);
    }

    protected void merge(AlertDefinitionDTO dto, AlertDefinition def)
        throws GeneralException {
        def.setName(dto.getName());
        def.setDisplayName(dto.getDisplayName());
        def.setDescription(dto.getDescription());
        def.setDisabled(dto.isDisabled());

        if (dto.getOwner() != null) {
            Identity owner = _context.getContext().getObjectById(Identity.class, dto.getOwner().getId());
            def.setOwner(owner);
        } else {
            throw new GeneralException("Owner must be specified");
        }

        AlertDefinition.ActionConfig previousCfg = def.getActionConfig();
        AlertDefinition.ActionConfig neuCfg = createActionConfig(dto.getActionConfig());
        mergeActionConfig(neuCfg, previousCfg);
        def.setActionConfig(neuCfg);
        def.setMatchConfig(createMatchConfig(dto.getMatchConfig()));
    }

    protected AlertDefinition.ActionConfig createActionConfig(AlertDefinitionDTO.ActionConfigDTO dto) throws GeneralException {
        AlertDefinition.ActionConfig action = null;
        if (dto != null) {
            action = new AlertDefinition.ActionConfig();

            if (dto.getActionType() != null) {
                action.setActionType(AlertDefinition.ActionType.valueOf(dto.getActionType().getName()));
                switch (action.getActionType()) {
                    case CERTIFICATION:
                        if (dto.getCertificationTrigger() != null) {
                            action.setCertificationTrigger(dto.getCertificationTrigger().getName());
                        }
                        break;

                    case WORKFLOW:
                        if (dto.getWorkflow() != null) {
                            action.setWorkflowName(dto.getWorkflow().getName());
                        }
                        break;

                    case NOTIFICATION:
                        if (dto.getEmailRecipients() != null) {
                            List<String> identNames = new ArrayList<String>();
                            for (IdentitySummaryDTO ident : Util.safeIterable(dto.getEmailRecipients())) {
                                identNames.add(ident.getName());
                            }
                            action.setEmailRecipients(identNames);
                        }

                        if (dto.getEmailTemplate() != null) {
                            action.setEmailTemplate(dto.getEmailTemplate().getName());
                        }
                        break;

                    default:
                        _log.warn("Unsupported actionType");
                }
            }

            //Set Notification Config

            if (dto.getEmailTemplate() != null) {
                action.setEmailTemplate(dto.getEmailTemplate().getName());
            }

            if (!Util.isEmpty(dto.getEmailRecipients())) {
                List<String> emails = new ArrayList<String>();
                for (IdentitySummaryDTO emailSugg : Util.safeIterable(dto.getEmailRecipients())) {
                    emails.add(emailSugg.getName());
                }
                action.setEmailRecipients(emails);
            }
        }
        return action;
    }

    //List of Args presented in the UI
    List<String> UI_ARGS = new ArrayList<String>(Arrays.asList(AlertDefinition.ActionConfig.ARG_CERT_TRIGGER,
            AlertDefinition.ActionConfig.ARG_EMAIL_TEMPLATE, AlertDefinition.ActionConfig.ARG_IDENTITY_EMAIL_RECIP,
            AlertDefinition.ActionConfig.ARG_WORKFLOW_NAME));

    //Create actionConfig from the DTO, but maintain the previous attributes not presented in the UI
    protected void mergeActionConfig(AlertDefinition.ActionConfig neu, AlertDefinition.ActionConfig previous) {
        if (previous != null && neu != null) {
            Attributes prevAtts = previous.getAttributes();
            Attributes neuAtts = neu.getAttributes();

            //Look for workflowArgs && emailVars. These are not present in the UI
            if (prevAtts != null && neuAtts != null) {
                for (Object key : Util.safeIterable(prevAtts.getKeys())) {
                    if (!Util.nullSafeContains(UI_ARGS, key)) {
                        neuAtts.put(key, prevAtts.get(key));
                    }
                }
            }
        }
    }

    protected AlertMatchConfig createMatchConfig(AlertDefinitionDTO.MatchConfigDTO dto)
        throws GeneralException {
        AlertMatchConfig matchConfig = null;
        if (dto != null) {
            matchConfig = new AlertMatchConfig();

            if (dto.getRule() != null) {
                Rule r = _context.getContext().getObjectByName(Rule.class, dto.getRule().getName());
                matchConfig.setMatchRule(r);
            }

            if (dto.getMatchExpression() != null) {
                matchConfig.setMatchExpression(createMatchExpression(dto.getMatchExpression()));
            }
        }

        return matchConfig;
    }

    protected AlertMatchConfig.AlertMatchExpression createMatchExpression(AlertDefinitionDTO.AlertMatchExpressionDTO dto)
        throws GeneralException {
        AlertMatchConfig.AlertMatchExpression exp = null;
        if (dto != null) {
            exp = new AlertMatchConfig.AlertMatchExpression();
            exp.setAnd(dto.isAnd());
            if (dto.getMatchTerms() != null) {
                for (AlertDefinitionDTO.AlertMatchTermDTO term : Util.safeIterable(dto.getMatchTerms())) {
                    exp.addMatchTerm(createMatchTerm(term));
                }
            }
        }

        return exp;
    }

    protected AlertMatchConfig.AlertMatchTerm createMatchTerm(AlertDefinitionDTO.AlertMatchTermDTO dto)
        throws GeneralException {
        AlertMatchConfig.AlertMatchTerm term = null;
        if (dto != null) {
            term = new AlertMatchConfig.AlertMatchTerm();
            if (Util.isNotNullOrEmpty(dto.getSource())) {
                Application app = _context.getContext().getObjectByName(Application.class, dto.getSource());
                term.setSource(app);
            }

            term.setName(dto.getName());
            term.setValue(dto.getValue());
            term.setContainer(dto.isContainer());
            term.setAnd(dto.isAnd());

            if (dto.getChildren() != null) {
                for (AlertDefinitionDTO.AlertMatchTermDTO child : Util.safeIterable(dto.getChildren())) {
                    term.addChild(createMatchTerm(child));
                }
            }
        }
        return term;
    }

    //Validate the dto. Throw exception if not valid
    public void validate(AlertDefinitionDTO dto) throws GeneralException {

        if (dto.getName() == null) {
            throw new GeneralException("Name required for AlertDefinition");
        }

        if (dto.getOwner() == null) {
            throw new GeneralException("Owner required for AlertDefinition");
        }

        if (dto.getMatchConfig() == null) {
            throw new GeneralException("Match Config required for AlertDefinition");
        } else {
            AlertDefinitionDTO.MatchConfigDTO alertMatchConfig = dto.getMatchConfig();
            if (alertMatchConfig.getRule() == null && alertMatchConfig.getMatchExpression() == null) {
                //Must have either rule or match expression configured
                throw new GeneralException("AlertDefinition Must have MatchRule or Match Terms configured.");
            } else {
                if (alertMatchConfig.getMatchExpression() != null) {
                    AlertDefinitionDTO.AlertMatchExpressionDTO expression = alertMatchConfig.getMatchExpression();
                    //Ensure Terms are configured
                    if (Util.isEmpty(expression.getMatchTerms())) {
                        throw new GeneralException("Alert Match Terms required");
                    } else {
                        for(AlertDefinitionDTO.AlertMatchTermDTO term : expression.getMatchTerms()) {
                            validateAlertMatchTerm(term);
                        }
                    }
                }
            }
        }

        //Validate Action Config
        if (dto.getActionConfig() == null) {
            throw new GeneralException("Action Config required for AlertDefinition");
        } else {
            AlertDefinitionDTO.ActionConfigDTO actionConfig = dto.getActionConfig();
            if (actionConfig.getActionType() == null) {
                throw new GeneralException("Action Type required for Action Config");
            } else {
                AlertDefinition.ActionType type = null;
                try {
                    type = AlertDefinition.ActionType.valueOf(actionConfig.getActionType().getName());
                } catch (IllegalArgumentException e) {
                    throw new GeneralException("Illegal value for ActionType");
                }
                switch (type) {
                    case WORKFLOW:
                        if (actionConfig.getWorkflow() == null) {
                            throw new GeneralException("Workflow required for ActionType WORKFLOW");
                        }
                        break;
                    case CERTIFICATION:
                        if (actionConfig.getCertificationTrigger() == null) {
                            throw new GeneralException("Certification required for ActionType CERTIFICATION");
                        }
                        break;
                    case NOTIFICATION:
                        if (Util.isEmpty(actionConfig.getEmailRecipients()) || actionConfig.getEmailTemplate() == null) {
                            throw new GeneralException("EmailTemplate and EmailRecipients required for ActionType NOTIFICATION");
                        }
                        break;
                    default:
                        //Can't get here
                        break;
                }
            }

        }
    }

    public void validateAlertMatchTerm(AlertDefinitionDTO.AlertMatchTermDTO term) throws GeneralException {
        if (Util.isNullOrEmpty(term.getName())) {
            throw new GeneralException("Match Term attribute required");
        }

        if (Util.isNullOrEmpty(term.getValue())) {
            throw new GeneralException("Match Term value required");
        }

        for (AlertDefinitionDTO.AlertMatchTermDTO child : Util.safeIterable(term.getChildren())) {
            validateAlertMatchTerm(child);
        }
    }

    public void deleteAlertDefinition(AlertDefinition def) throws GeneralException {
        Terminator terminator = new Terminator(_context.getContext());
        terminator.deleteObject(def);
    }

    private List<Message> getErrors() {
        if (this.errors == null) {
            this.errors = new ArrayList<Message>();
        }
        return this.errors;
    }
}
