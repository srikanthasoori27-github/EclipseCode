package sailpoint.service.suggest;

import sailpoint.service.BaseListServiceContext;

/**
 * @author: pholcomb
 * A context that should be implemented by resources that want to use the SuggestService.
 */
public interface SuggestServiceContext extends BaseListServiceContext {
    String getSuggestClass();
    
    String getFilterString();
}
