package sailpoint.web.policy;

import java.util.ArrayList;
import java.util.List;

import javax.faces.model.SelectItem;

import sailpoint.object.ActivityConstraint;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Filter;
import sailpoint.object.SearchItem;
import sailpoint.object.TimePeriod;
import sailpoint.tools.GeneralException;
import sailpoint.web.search.AdvancedActivitySearchBean;
import sailpoint.web.search.AdvancedIdentitySearchBean;
import sailpoint.web.util.WebUtil;

public class ActivityConstraintDTO extends BaseConstraintDTO 
{
    private static final long serialVersionUID = 1L;
    
    private AdvancedIdentitySearchBean identitySearchBean;
    private SearchItem identitySearchItem;

    private AdvancedActivitySearchBean activitySearchBean;
    private SearchItem activitySearchItem;
    
    //Note: allTimePeriods is internal and should never be sent to web pages
    private List<TimePeriod> allTimePeriods;
    private List<SelectItem> timePeriodSelectList;
    private List<String> selectedTimePeriods;
    
    public ActivityConstraintDTO() 
    {
        this(new ActivityConstraint());
    }

    public ActivityConstraintDTO(ActivityConstraint src) 
    {
        super(src);
        
        this.identitySearchItem = new SearchItem(src.getIdentityFilters());
        loadIdentitySearchBean();
        
        this.activitySearchItem = new SearchItem(src.getActivityFilters());
        loadActivitySearchBean();
        
        loadAllTimePeriodStuff();
        loadSelectedTimePeriods(src);
    }

    public ActivityConstraintDTO(ActivityConstraintDTO src) 
    {
        super(src);
        
        this.identitySearchItem = src.identitySearchItem;
        loadIdentitySearchBean();
        
        this.activitySearchItem = src.activitySearchItem;
        loadActivitySearchBean();
        
        loadAllTimePeriodStuff();
        this.selectedTimePeriods = src.selectedTimePeriods;
    }
    
    // Start: Properties called by web pages =======================================>
    public AdvancedIdentitySearchBean getIdentitySearch() 
    {
        return this.identitySearchBean;
    }
    
    public AdvancedActivitySearchBean getActivitySearch()
    {
        return this.activitySearchBean;
    }

    public List<SelectItem> getTimePeriodSelectList() 
    {
        return this.timePeriodSelectList;
    }
    
    public List<String> getSelectedTimePeriods()
    {
        return this.selectedTimePeriods;
    }
    
    public void setSelectedTimePeriods(List<String> val)
    {
        this.selectedTimePeriods = val;
    }
    // End: Methods called by web pages ======================================================>

    @Override
    public ActivityConstraintDTO clone() 
    {
        return new ActivityConstraintDTO(this);
    }
    
    @Override
    public BaseConstraint newConstraint()
    {
        return new ActivityConstraint();
    }
    
    @Override
    public void commit(BaseConstraint src)
            throws GeneralException
    {
        super.commit(src);

        ActivityConstraint sourceConstraint = (ActivityConstraint) src;

        setIdentityFilters(sourceConstraint);
        
        setActivityFilters(sourceConstraint);
        
        setSelectedTimePeriodToConstraint(sourceConstraint);
    }

    
    private void loadAllTimePeriodStuff()
    {
        try
        {
            this.allTimePeriods = getContext().getObjects(TimePeriod.class);
            this.timePeriodSelectList = new ArrayList<SelectItem>();
            for (TimePeriod t : this.allTimePeriods)
            {
                this.timePeriodSelectList.add(new SelectItem(t.getId(), WebUtil.localizeMessage( t.getName() ) ));
            }
        }
        catch(GeneralException ex)
        {
            throw new IllegalStateException(ex);
        }
    }
    
    private void loadSelectedTimePeriods(ActivityConstraint source)
    {
        this.selectedTimePeriods = new ArrayList<String>();

        List<TimePeriod> timePeriods = source.getTimePeriods();
        if (timePeriods != null)
        {
            for (TimePeriod period : timePeriods)
            {
                this.selectedTimePeriods.add(period.getId());
            }
        }
    }
    
    private void loadIdentitySearchBean() 
    {
        this.identitySearchBean = new AdvancedIdentitySearchBean();
        this.identitySearchBean.setSearchItem(this.identitySearchItem);
        this.identitySearchBean.restoreSearchBean();
    }
    
    private void loadActivitySearchBean()
    {
        this.activitySearchBean = new AdvancedActivitySearchBean();
        this.activitySearchBean.setSearchItem(this.activitySearchItem);
        this.activitySearchBean.restoreSearchBean();
    }

    private void setIdentityFilters(ActivityConstraint sourceConstraint)
    {
        Filter filter = (this.identitySearchBean.getSearchBean().getFilter());
        sourceConstraint.setIdentityFilters(getFilters(filter));
    }

    private void setActivityFilters(ActivityConstraint sourceConstraint)
    {
        Filter filter = (this.activitySearchBean.getSearchBean().getFilter());
        sourceConstraint.setActivityFilters(getFilters(filter));
    }
    
    private void setSelectedTimePeriodToConstraint(ActivityConstraint sourceConstraint)
    {
        sourceConstraint.setTimePeriods(getSelectedTimePeriodsFromIds(getSelectedTimePeriods()));
    }

    private List<TimePeriod> getSelectedTimePeriodsFromIds(List<String> ids) 
    {
        List<TimePeriod> tps = null;
        if (ids != null && !ids.isEmpty() && this.allTimePeriods != null)
        {
            tps = new ArrayList<TimePeriod>();
            for (TimePeriod t : this.allTimePeriods)
            {
                for (String id : ids)
                {
                    if (id.equals(t.getId()))
                    {
                        tps.add(t);
                        break;
                    }
                }
            }
        }
        return tps;
    }
    
    private List<Filter> getFilters(Filter filter)
    {
        if (filter == null) 
        {
            return null;
        } 

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(filter);
        return filters;
    }
}
