/**
 * 
 */
package sailpoint.web.group;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;
import sailpoint.web.SelectOptionBean;

/**
 * @author peter.holcomb
 *
 */
public class GroupFilterBean extends BaseBean {
	
	private static final Log log = LogFactory.getLog(GroupFilterBean.class);
	
	public class GroupFilter {
		private String id;
		private String factory;
		private String definition;
		private Filter filter;
		private List<SelectOptionBean> definitionChoices;
		
		public GroupFilter(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getFactory() {
			return factory;
		}

		public void setFactory(String factory) {
			this.factory = factory;
		}

		public String getDefinition() {
			return definition;
		}

		public void setDefinition(String definition) {
			this.definition = definition;
		}
		
		public List<SelectOptionBean> getDefinitionChoices() throws GeneralException{
	        GroupDefinitionListBean defBean = new GroupDefinitionListBean();
	        definitionChoices = defBean.getDefinitionSelectOptionList(this.factory);
	        
	        /** Remove the "Show All" options **/
	        if(definitionChoices!=null) {
		        for(Iterator<SelectOptionBean> choices = definitionChoices.iterator(); choices.hasNext();) {
		        	SelectOptionBean choice = choices.next();
		        	if(choice.getValue().equals(GroupDefinitionListBean.ATT_SHOW_ALL)) {
		        		choices.remove();
		        	}
		        }
		        if(definitionChoices.size()>1) {
		        	definitionChoices.add(0, new SelectOptionBean("", "", false));
		        }
	        }
	        
	        return definitionChoices;
	    }
		
	}
	
	
	
	int filterCounter = 0;
	String selectedFilterId;
	private BaseBean baseBean;
	private Filter filter;
	private String sessionString;
	List<GroupFilter> filters = new ArrayList<GroupFilter>();
	
	
	public GroupFilterBean(BaseBean baseBean, String sessionString) {
		this.attachContext(baseBean);
		this.sessionString = sessionString;
	}
	
	public GroupFilterBean(BaseBean baseBean, String sessionString, String valueString) {
		this.attachContext(baseBean);
		this.sessionString = sessionString;
		
		if(valueString!=null) {
			this.fromString(valueString);
		}
	}
	
	public void attachContext(BaseBean baseBean) {
        this.baseBean = baseBean;
    }
	
	public List<GroupFilter> getFilters() {
		if(filters==null) 
			filters = new ArrayList<GroupFilter>();
		if(filters.isEmpty()) {
			filters.add(new GroupFilter("0"));
			this.filterCounter++;
		}
		return filters;
	}

	public Filter getFilter() {
		return filter;
	}
	
	public String updateFilter() {
		return null;
	}
	
	public String reset() {
		this.filter = null;
		this.filters = new ArrayList<GroupFilter>();
		return null;
	}
	
	public String buildFilter() {
		if(getFilters()!=null && !getFilters().isEmpty()) {
			List<Filter> filters = new ArrayList<Filter>();
			
			for(GroupFilter filterBean : getFilters()) {
				if(filterBean.getDefinition()!=null) {
					try {
						GroupDefinition def = baseBean.getContext().getObjectById(GroupDefinition.class, filterBean.getDefinition());
						if(def!=null) {
							filters.add(def.getFilter());
						}
					} catch(Exception e) {
						log.warn("Unable to load group definition for id: " + filterBean.getDefinition() + " Exception: " + e.getMessage());
					}
				}
			}
			
			if(filters.size()>1) {
				filter = Filter.and(filters);
			} else if(!filters.isEmpty()){
				filter = filters.get(0);
			}
		}
		if(baseBean!=null && sessionString!=null)
			baseBean.getSessionScope().put(sessionString, this);
		return null;
	}
	
	public String addFilter() {
		this.filters.add(new GroupFilter(String.valueOf(this.filterCounter++)));
		
		baseBean.getSessionScope().put(sessionString, this);
		return null;
	}
	
	public String removeFilter() {
		if (null != this.selectedFilterId)
        {
            for (Iterator<GroupFilter> it=this.filters.iterator(); it.hasNext(); )
            {
                if (this.selectedFilterId.equals(it.next().getId()))
                {
                    it.remove();
                    this.filterCounter--;
                    break;
                }
            }
        }
		
        this.selectedFilterId = null;
        
        return null;
	}

	public void setFilters(List<GroupFilter> filters) {
		this.filters = filters;
	}

	public String getSelectedFilterId() {
		return selectedFilterId;
	}

	public void setSelectedFilterId(String selectedFilterId) {
		this.selectedFilterId = selectedFilterId;
	}

	public int getFilterCounter() {
		return filterCounter;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		for(int i=0; i<filters.size(); i++) {
			if(i>0 && i<filters.size()) 
				sb.append(";");
			
			GroupFilter filter = filters.get(i);
			if(filter.getDefinition()!=null && !filter.getDefinition().equals("")) {
				sb.append(filter.getFactory() + ":" + filter.getDefinition());
			}
		}
		
		return sb.toString();
	}
	
	private void fromString(String fromString) {
		String[] parts = fromString.split(";");
		this.filters = new ArrayList<GroupFilter>();
		for(int i=0; i<parts.length; i++) {
			String filterString = parts[i];
			String[] vals = filterString.split(":");
			
			if(vals.length==2) {
			    GroupFilter f = new GroupFilter(Integer.toString(i));
			    f.setDefinition(vals[1]);
			    f.setFactory(vals[0]);
			    this.filterCounter = i;
			    filters.add(f);
			}
		}		
	}

}
