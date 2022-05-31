/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A specialization of the LinkAttributeScorer that does a more
 * complicated date comparison, not currently possible 
 * in LinkAttributeScorer.
 * 
 * Author: Jeff
 *
 */

package sailpoint.score;

import java.util.Calendar;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.GenericIndex;
import sailpoint.object.Link;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

public class DormantAccountScorer extends LinkAttributeScorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // not we share the same logging control
	private static Log log = LogFactory.getLog(DormantAccountScorer.class);

    public static final String ARG_DAYS_TILL_DORMANT = "daysTillDormant";
    public static final String TARGET = "Dormant Account";

    /**
     * Threshold date calculated from daysTillDormant relative
     * to the current time.
     */
    Date _threshold;

    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public DormantAccountScorer() {
    }

    /**
     * Reset the internal state of aggregate scorers.
     */
    public void prepare(SailPointContext context, 
                        ScoreConfig config,
                        ScoreDefinition def,
                        GenericIndex index)
        throws GeneralException {
        
        super.prepare(context, config, def, index);

        int days = def.getInt(ARG_DAYS_TILL_DORMANT);

        // this only makes sense if positive
        if (days > 0) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, -days);
            _threshold = c.getTime();
        }
    }

    /**
     * We can use the inherited score and finish 
     * methods, all we need to do is overload the
     * matcher.
     */
    @Override
    public ScoreItem isMatch(SailPointObject obj) {

        Link link = (Link)obj;
        ScoreItem item = null;
        boolean match = false;        
        
        if (_attribute != null && _threshold != null) {
            Attributes<String,Object> atts = link.getAttributes();
            if (atts != null) {
                Date lastLogin = atts.getDate(_attribute);
                // lastLogin is known and is less than or
                // equal to the threshold
                if(lastLogin != null &&
                         _threshold.compareTo(lastLogin) >= 0)
                {
                    item = new ScoreItem();
                    item.setTargetMessage(new Message(_target));
                    if (_suggestion != null)
                        item.setSuggestionMessage(new Message(_suggestion));
                }
            }
        }
        
        return item;
    }


}
