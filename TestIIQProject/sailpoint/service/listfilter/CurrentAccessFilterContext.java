package sailpoint.service.listfilter;

import sailpoint.api.SailPointContext;
import sailpoint.service.CurrentAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CurrentAccessFilterContext extends BaseUserAccessFilterContext {

    public static final String FILTER_PROPERTY_STATUS = "status";
    
    /**
     * Constructor
     */
    public CurrentAccessFilterContext() {
        super(null, null, null, null, null);
    }
    
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filterDTOs = super.getDefaultFilters(context, locale);
        ListFilterDTO statusFilter = new ListFilterDTO(FILTER_PROPERTY_STATUS, MessageKeys.UI_SEARCH_FILTER_STATUS, false, ListFilterDTO.DataTypes.String, locale);
        List<ListFilterDTO.SelectItem> statusValues = new ArrayList<ListFilterDTO.SelectItem>();
        for (CurrentAccessService.CurrentAccessStatus status : CurrentAccessService.CurrentAccessStatus.values()) {
            String label = new Message(status.getMessageKey()).getLocalizedMessage(locale, null);
            statusValues.add(new ListFilterDTO.SelectItem(label, status.getStringValue()));
        }
        statusFilter.setAllowedValues(statusValues);
        filterDTOs.add(statusFilter);
        return filterDTOs;
    }
    
    //TODO: handle status filter when searching
}