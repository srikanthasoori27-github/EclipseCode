/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web.group;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.GroupFactory;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Rule;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class GroupFactoryBean extends BaseObjectBean<GroupFactory> {

    private static Log log = LogFactory.getLog(GroupFactoryBean.class);

    private List<SelectItem> groupOwnerRules;

    ////////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Cached values for the group factory attribute selector.
     */
    Map<String, String> _factoryAttributes;


    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     *
     */
    public GroupFactoryBean() {
        super();
        setScope(GroupFactory.class);
        restoreObjectId();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Helpers/Setup
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Overload from BaseObjectBean called to create a new object.
     */
    public GroupFactory createObject() {
        GroupFactory gf = new GroupFactory();

        return gf;
    }

    public void restoreObjectId() {

        Map session = getSessionScope();

        // always restore fresh object
        // this takes care of the duplicate session object problem
        // when hitting back from the edit sub group page
        session.remove(FORCE_LOAD);
        
        clearHttpSession();
            
        // list page will set this initially, thereafter we keep it
        // refreshed as we transition among our pages
        String id = (String)session.get(GroupFactoryListBean.ATT_OBJECT_ID);
        if (id == null) {
            // the other convention on some pages
            Map map = getRequestParam();
            id = (String) map.get("selectedId");
            if (id == null)
                id = (String) map.get("editForm:selectedId");
        }

        setObjectId(id);
    }

    public Map<String, String> getFactoryAttributeOptions ()
        throws GeneralException {

        if (_factoryAttributes == null) {

            Map<String, String> map = new HashMap<String, String>();

            ObjectConfig config = getContext().getObjectByName(ObjectConfig.class,
                                                         ObjectConfig.IDENTITY);
            if (config != null) {
                List<ObjectAttribute> atts = config.getObjectAttributes();
                if (atts != null) {
                    for (ObjectAttribute att : atts) {
                        // attribute must be marked as being factoriable
                        if (att.isGroupFactory())
                            map.put(att.getDisplayableName(getLocale()), att.getName());
                    }
                }
            }

            _factoryAttributes = map;
        }

        return _factoryAttributes;
    }
    
    public List<SelectItem> getGroupOwnerRules() throws GeneralException {
        if (groupOwnerRules == null) {
            groupOwnerRules = WebUtil.getRulesByType(getContext(), Rule.Type.GroupOwner, true);
        }
        return groupOwnerRules;
    }
    
    /**
     * Convert rule name to rule before setting on GroupFactory
     * 
     * @param ruleName
     * @throws GeneralException
     */
    public void setGroupOwnerRule(String ruleName) throws GeneralException {
        Rule ownerRule = getContext().getObjectByName(Rule.class, ruleName);
        
        if (getObject() != null) {
            getObject().setGroupOwnerRule(ownerRule);
        }
    }
    
    /**
     * Convert rule name to rule before setting on GroupFactory
     * 
     * @throws GeneralException
     */
    public String getGroupOwnerRule() throws GeneralException {
        String ruleName = "";
        if (getObject() != null) {
            Rule rule =  getObject().getGroupOwnerRule();
            if (rule != null) {
                ruleName = rule.getName();
            }
        }
        return ruleName;
    }

    private void setCurrentTab() {
        Object bean = super.resolveExpression("#{groupNavigation}");
        if ( bean != null )
            ((GroupNavigationBean)bean).setCurrentTab(0);
    }

    @Override
    public String saveAction() throws GeneralException {
        if (getObject() != null && Util.isNullOrEmpty(getObject().getName())) {
            addErrorMessage("name",
                    new Message(Message.Type.Error, MessageKeys.VALUE_IS_REQUIRED, MessageKeys.LABEL_NAME),
                    new Message(Message.Type.Error, MessageKeys.ERR_REQUIRED));
            return null;
        }

        setCurrentTab();
        authorize(new RightAuthorizer(SPRight.FullAccessGroup));
        return super.saveAction();
    }

    @Override
    public String cancelAction() throws GeneralException {
        setCurrentTab();
        return super.cancelAction();
    }

}
