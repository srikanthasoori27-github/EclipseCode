package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Differencer;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.SelectItemByLabelComparator;

/**
 * 
 * Identity Capabilities in View Identity -> User Rights tab
 *
 */
public class CapabilityHelper {

    private static final Log log = LogFactory.getLog(CapabilityHelper.class);
    
    private IdentityDTO parent;

    public CapabilityHelper(IdentityDTO parent) {
        
        if (log.isInfoEnabled()) {
            log.info("CapabilityHelper()");
        }
        this.parent = parent;
    }

    public SelectItem[] getAllCapabilities() throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        List<Capability> caps =
            this.parent.getContext().getObjects(Capability.class, qo);
        List<SelectItem> items = new ArrayList<SelectItem>();

        for (Capability cap : caps) {
            if(cap.getName()!=null) {
                String label = cap.getDisplayName();
                if(label==null) {
                    label = cap.getName();
                }
                items.add(new SelectItem(cap.getName(), this.parent.getMessage(label)));
            }
        }

        // Sort in memory since the labels are i18n'ized.
        Collections.sort(items, new SelectItemByLabelComparator(this.parent.getLocale()));
        
        SelectItem[] itemArray = items.toArray(new SelectItem[items.size()]);
        
        return itemArray;
    }

    /**
     * Return the capabilities of the selected identity as a String[]
     * that can be used by a selectManyListBox component.
     *
     */
    public List<String> getCapabilities() throws GeneralException {
        if (this.parent.getState().getCapabilities() != null) {
            return this.parent.getState().getCapabilities();
        }

        return getCurrentCapabilities();
    }

    private List<String> getCurrentCapabilities() throws GeneralException {
        Identity id = this.parent.getObject();
        if (null == id) {
            return null;
        } else {
            String[] capnames = getObjectNames(id.getCapabilities());
            return Arrays.asList(capnames);
        }
    }
 
    /**
     * Assign capabilities expressed as an array of strings
     * to the Identity after converting to a List of Capability.
     */
    public void setCapabilities(List<String> capnames) throws GeneralException {
        parent.getState().setCapabilities(capnames);
    }

    
    /**
     * Convert a list of objects to a String array, assuming that
     * the toString method is suitable for the array.
     * Could be a generic util.
     */
    private String[] getObjectNames(List<? extends SailPointObject> objects) {

        String[] strings = null;
        if (objects != null) {
            strings = new String[objects.size()];
            for (int i = 0 ; i < strings.length ; i++) {
                SailPointObject o = objects.get(i);
                strings[i] = (null != o) ? o.getName() : "";
            }
        }
        else {
            // Note that for list with JSF selector components,
            // the array must *not* be null
            strings = new String[0];
        }

        return strings;
    }

    void addCapabilitiesToRequest(AccountRequest account) throws GeneralException {
        // Only update the capabilities if the user updated them
        // If this is null in the IdentityEdit then the tab was never visited, so dont set capabilities at all.
        if (this.parent.getState().getCapabilities() != null &&
                !Differencer.equal(this.parent.getState().getCapabilities(), getCurrentCapabilities())) {
            AttributeRequest req = new AttributeRequest();
            req.setName(ProvisioningPlan.ATT_IIQ_CAPABILITIES);
            req.setOperation(ProvisioningPlan.Operation.Set);
            req.setValue(this.parent.getState().getCapabilities());
            account.add(req);
        }
    }

    
}

