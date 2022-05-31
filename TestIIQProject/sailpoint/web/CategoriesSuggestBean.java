/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.List;

import javax.faces.model.SelectItem;

import sailpoint.object.Category;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;


/**
 * A JSF bean that returns categories for a suggest field.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis </a>
 */
public class CategoriesSuggestBean extends BaseBean
{
    /**
     * Default constructor.
     */
    public CategoriesSuggestBean() {}

    public List<SelectItem> getCategories() throws GeneralException {
//        Map request = super.getRequestParam();
//        String requestParam = (String) request.get("category");
        QueryOptions qo = new QueryOptions();
//      For now, we are just using this bean to fetch the categories for us.  If we ever
//      need it to be an actual suggets component, we can uncomment all this -- Bernie Margolis
//        if (null != requestParam) {
//            Filter filter = Filter.like("name", requestParam, Filter.MatchMode.START);
//            qo.add(filter);
//        }
//        qo.setResultLimit(8);

        qo.setOrderBy("name");

        // Not scoped - categories don't have scopes.
        
        List<Category> categories = getContext().getObjects(Category.class, qo);
        
        List<SelectItem> catSelectItems = new ArrayList<SelectItem>();
        catSelectItems.add(new SelectItem("", getMessage(MessageKeys.SELECT_CATEGORY)));
        
        for (Category cat : categories) {
            catSelectItems.add(new SelectItem(cat.getName(), cat.getName()));
        }
        
        return catSelectItems;

    }
}
