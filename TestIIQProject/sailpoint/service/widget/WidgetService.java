package sailpoint.service.widget;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.Matchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.QueryOptions;
import sailpoint.object.UIPreferences;
import sailpoint.object.Widget;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A service class that manages widgets.
 *
 * @author patrick.jeong
 */
public class WidgetService {
    private static final Log LOG = LogFactory.getLog(WidgetService.class);

    private static final String EMPTY_WIDGETS_VALUE = "None";

    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Construct WidgetService for context and user.
     *
     * We pass in the session stored capabilities so we don't match using the updated capabilities.
     * User is required to log out and back in before having access to new widgets.
     *
     * @param context SailPointContext
     * @param user Current identity
     * @param capabilities session stored capabilities
     */
    public WidgetService(SailPointContext context, Identity user, List<Capability> capabilities) {
        this.context = context;
        this.user = user;
        this.capabilities = capabilities;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    private Identity user;
    private List<Capability> capabilities;

    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get all widgets available to user, sorted by name
     * @return List of WidgetDTO objects
     * @throws GeneralException
     */
    public List<WidgetDTO> getAllWidgets() throws GeneralException {
        List<WidgetDTO> widgetDTOs = new ArrayList<WidgetDTO>();
        QueryOptions ops = new QueryOptions();
        ops.addOrdering("name", true);
        IncrementalObjectIterator<Widget> iterator = new IncrementalObjectIterator<Widget>(this.context, Widget.class, ops);
        while (iterator.hasNext()) {
            Widget widget = iterator.next();
            // add if user has right
            if (canUserAccessWidget(widget)) {
                widgetDTOs.add(new WidgetDTO(widget));
            }
        }

        return widgetDTOs;
    }
    
    /**
     * Get the default configured list of widgets
     *
     * @return list of widgets
     * @throws GeneralException
     */
    public List<WidgetDTO> getConfiguredWidgets() throws GeneralException {
        String configuredWidgetNames = (String)user.getUIPreference(UIPreferences.PRF_HOME_WIDGETS);
        List<String> widgetNames;
        
        // If its not set, get the defaults
        if (Util.isNullOrEmpty(configuredWidgetNames)) {
            widgetNames = context.getConfiguration().getStringList(Configuration.DEFAULT_HOME_WIDGETS);
        } else {
            if (EMPTY_WIDGETS_VALUE.equals(configuredWidgetNames)) {
                widgetNames = new ArrayList<String>();
            } else {
                widgetNames = Util.csvToList(configuredWidgetNames); 
            }            
        }
        
        List<WidgetDTO> widgets = new ArrayList<WidgetDTO>();

        if (!Util.isEmpty(widgetNames)) {
            for (String widgetName : widgetNames) {
                Widget widget = context.getObjectByName(Widget.class, widgetName);
                if (widget == null) {
                    LOG.debug("Invalid widget " + widgetName + " configured for user " + user.getName());
                } else {
                    // add if user has right
                    if (canUserAccessWidget(widget)) {
                        widgets.add(new WidgetDTO(widget));
                    }
                }
            }
        }

        return widgets;
    }

    /**
     * Save the given widgets in the user preferences for logged in user
     * @param widgets List of widgets. Possibly empty.
     * @throws GeneralException
     */
    public void setConfiguredWidgets(List<WidgetDTO> widgets) throws GeneralException {
        String widgetNamesCsv;
        // If set to empty, use our special case
        if (Util.isEmpty(widgets)) {
            widgetNamesCsv = EMPTY_WIDGETS_VALUE;
        } else {
            List<String> widgetNames = new ArrayList<String>();
            for (WidgetDTO widget: widgets) {
                widgetNames.add(widget.getName());
            }
            widgetNamesCsv = Util.listToCsv(widgetNames);
        }

        this.user.setUIPreference(UIPreferences.PRF_HOME_WIDGETS, widgetNamesCsv);

        this.context.saveObject(this.user);
        this.context.commitTransaction();
    }

    /**
     * Check if user can access the widget
     *
     * @return true if user can access widget
     * @throws GeneralException 
     */
    private boolean canUserAccessWidget(Widget widget) throws GeneralException {

        // Check SysAdmin capability in session stored capabilities, which include both
        // directly assigned capabilities and capabilities granted through workgroup membership.
        if (Capability.hasSystemAdministrator(capabilities)) {
            return true;
        }

        IdentitySelector identitySelector = widget.getSelector();
        if (identitySelector == null) {
            return true;
        }
        else {
            // Save existing capabilities so it can be reset afterwards
            List<Capability> oldCapabilities = user.getCapabilities();

            // Apply session stored capabilities so that we don't use updated capabilities
            user.setCapabilities(capabilities);

            boolean isMatch = false;

            try {
                isMatch = new Matchmaker(this.context).isMatch(identitySelector, user);
            }
            finally {
                user.setCapabilities(oldCapabilities);
            }

            return isMatch;
        }
    }
}
