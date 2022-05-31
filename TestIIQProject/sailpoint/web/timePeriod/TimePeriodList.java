/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.timePeriod;

import java.util.Arrays;
import java.util.List;

import sailpoint.object.TimePeriod;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseListBean;


public class TimePeriodList extends BaseListBean<TimePeriod> {
//    private static Log log = LogFactory.getLog(TimePeriodList.class);
    
    public TimePeriodList() {
        super();
        setScope(TimePeriod.class);
    }
    
    public List<TimePeriod> getObjects() throws GeneralException {
        List<TimePeriod> unsortedList = super.getObjects();
        TimePeriod [] tpArray = unsortedList.toArray(new TimePeriod[unsortedList.size()]);
        Arrays.sort(tpArray);
        return Arrays.asList(tpArray);
    }    
}
