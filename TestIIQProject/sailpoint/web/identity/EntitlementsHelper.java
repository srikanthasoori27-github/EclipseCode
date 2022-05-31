package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Configuration;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Identity;
import sailpoint.object.UIPreferences;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Bacing bean for View Identity => Entitlements Tab => Entitlements
 * This is initialized by IdentityDTO 
 *
 */
public class EntitlementsHelper {

    private static final Log log = LogFactory.getLog(EntitlementsHelper.class);
    
    private Boolean displayEntitlementDescription;
    private IdentityDTO parent;
    
    public EntitlementsHelper(IdentityDTO parent) {
        if (log.isInfoEnabled()) {
            log.info("EntitlementsHelper()");
        }
        this.parent = parent;
    }
    
    public boolean isDisplayEntitlementDescriptions() throws GeneralException {

        if (this.displayEntitlementDescription == null) {
            Identity user = this.parent.getLoggedInUser();
            Object pref = user.getUIPreference(UIPreferences.PRF_DISPLAY_ENTITLEMENT_DESC);
            if (null != pref) {
                this.displayEntitlementDescription = Util.otob(pref);
            } else{
                this.displayEntitlementDescription =
                        Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC);
            }
        }
        
        return displayEntitlementDescription;
    }
    
    public List<ExceptionBean> getExceptions() throws GeneralException {

        List<ExceptionBean> beans = this.parent.getState().getExceptions();

        if (beans == null) {
            beans = new ArrayList<ExceptionBean>();

            Identity id = this.parent.getObject();
            if (id != null) {
                List<EntitlementGroup> exceptions = id.getExceptions();
                if (exceptions != null) {
                    for (EntitlementGroup eg : exceptions)
                        beans.add(new ExceptionBean(eg, isDisplayEntitlementDescriptions(), this.parent.getLocale(), this.parent.getContext()));
                }

                Collections.sort(beans, new Comparator<ExceptionBean>() {
                    public int compare(ExceptionBean e1, ExceptionBean e2) {
                        return e1.getApplication().getName()
                                .compareToIgnoreCase(
                                        e2.getApplication().getName());
                    }
                });
                this.parent.getState().setExceptions(beans);
            }
        }

        return beans;
    }

}
