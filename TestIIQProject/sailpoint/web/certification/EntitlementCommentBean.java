/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class EntitlementCommentBean extends BaseBean {

    private static final Log log = LogFactory.getLog(EntitlementCommentBean.class);

    private String itemId;
    private String text;

    public EntitlementCommentBean() {
        itemId = this.getRequestParameter("itemId");
    }

    public String addComment() throws GeneralException {

        // if they didn't enter anything just quit.
        if (text==null || "".equals(text))
            return "";

        CertificationItem item = getContext().getObjectById(CertificationItem.class, itemId);
      
        switch(item.getType()) {
            case BusinessRoleGrantedScope:
            case BusinessRoleGrantedCapability:
            case BusinessRolePermit:
            case BusinessRoleRequirement:
            case BusinessRoleProfile:
                throw new RuntimeException("Comments not allowed on role certifications.");
        }

        Identity identity = item.getIdentity(getContext());
        identity.addEntitlementComment(getContext(), item, getLoggedInUser().getDisplayName(), text);

        getContext().saveObject(identity);
        getContext().commitTransaction();

        return "";
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
