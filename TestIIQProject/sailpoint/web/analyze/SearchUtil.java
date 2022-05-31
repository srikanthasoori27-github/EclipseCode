package sailpoint.web.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.reporting.SearchReport;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.SearchBean;

public class SearchUtil {
    private static final Log log = LogFactory.getLog(SearchUtil.class);
    
    /** Returns all of the search items that this user has stored on their identity **/
    @SuppressWarnings("unchecked")
    public static List<SearchItem> getAllMySearchItems(BaseBean baseBean) {
        List<SearchItem> savedSearches;
        
        try {
            Identity currentUser = baseBean.getLoggedInUser();
            savedSearches = currentUser.getSavedSearches();
        } catch (GeneralException ge) {
            log.error("GeneralException: [" + ge.getMessage() + "]");
            baseBean.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM, ge),
                        null);
            savedSearches = null;
        }
        
        return savedSearches;
    }
    
    public static void saveMyUser(BaseBean baseBean, Identity currentUser) throws GeneralException {
        try {
            baseBean.getContext().saveObject(currentUser);
            baseBean.getContext().commitTransaction();
        } catch (GeneralException ge) {
            log.info("Unable to save user due to exception: " + ge.getMessage());
            baseBean.getContext().rollbackTransaction();
        }
    }
    
    @SuppressWarnings("unchecked")
    public static Attributes buildReportAttributes(SearchBean searchBean, String searchType){

        List<String> columnNames = searchBean.getSelectedColumns();
        List<String> supplimentalCols = searchBean.getSupplimentalColumns();
        if (supplimentalCols != null)
            columnNames.addAll(supplimentalCols);            
        List<SearchInputDefinition> definitions = new ArrayList<SearchInputDefinition>();
        Map<String, SearchInputDefinition> inputs = searchBean.getInputs();
        for(String column : columnNames) { 
            // Remove the "id" column from the list 
            if(!"id".equals(column)){
                SearchInputDefinition input = inputs.get(column); 
                if(input != null) {
                    log.debug("Adding Input: " + input.getName());
                    definitions.add(input);
                }
            }
        }
        
        Attributes args = new Attributes();
        args.put(SearchReport.ARG_ADV_SEARCH_REPORT_TYPE, searchType);
        args.put(SearchReport.ARG_ADV_SEARCH_REPORT_DEFINITIONS, definitions);
        args.put(SearchReport.ARG_ADV_SEARCH_REPORT_FILTERS, searchBean.getFilter());
        return args;
    }
}
