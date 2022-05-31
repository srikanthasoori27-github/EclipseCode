/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.certification;

import sailpoint.web.BaseBean;
import sailpoint.web.util.NavigationHistory;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
public class CertificationGroupListBean extends BaseBean implements NavigationHistory.Page {

    public static final String SESSION_CERT_GRP="certGroupId";

    private String certificationGroupId;

    public String getCertificationGroupId() {
        return certificationGroupId;
    }

    public void setCertificationGroupId(String certificationGroupId) {
        this.certificationGroupId = certificationGroupId;
    }

    public String viewCertificationGroup(){
       getSessionScope().put(SESSION_CERT_GRP, certificationGroupId);
       NavigationHistory.getInstance().saveHistory(this);
       return "viewCertificationGroup";
    }


    public String getPageName() {
        return "Certification Group List";
    }

    public String getNavigationString() {
        return "viewCertificationGroupList";
    }

    public Object calculatePageState() {
        return null;
    }

    public void restorePageState(Object state) {

    }
}
