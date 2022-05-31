/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * This object is used to represent advanced task statistics.  We could have used a map of maps instead
 * though that makes creating and accessing the data more cumbersome. Typical usage of this object would
 * be in Account Group Aggregation where the application, application schema object type and group details
 * are stored for each application aggregated. Serializing this into XML would look like:
 * <br/>
 * &lt;TaskStatistics name="application name"&gt;<br/>
 *   &lt;TaskStatistics name="schema name"&gt;<br/>
 *     &lt;TaskStatistic name="task_out_account_group_aggregation_application_object_total" data="45"/&gt;<br/>
 *     &lt;TaskStatistic name="task_out_account_group_aggregation_application_object_created" data="21"/&gt;<br/>
 *   &lt;/TaskStatistics&gt;<br/>
 * &lt;/TaskStatistics&gt;<br/>
 */
@XMLClass
public class TaskStatistics extends AbstractXmlObject {
    private String _name;
    private List<TaskStatistics> _taskStatistics = new ArrayList<TaskStatistics>();
    private List<TaskStatistic> _statistics = new ArrayList<TaskStatistic>();
    
    @XMLClass
    public static class TaskStatistic extends AbstractXmlObject {
        private String _name;
        private String _data;
        
        /**
         * @return message key value for the name of the statistic
         */
        @XMLProperty
        public String getName() {
            return _name;
        }
        
        public void setName(String name) {
            this._name = name;
        }

        /**
         * Note the value is a String object. No one is forcing anyone to use a Number to represent
         * the statistic, however, it might make more sense.
         * @return the data value of the statistic
         */
        @XMLProperty
        public String getData() {
            return _data;
        }

        public void setData(String data) {
            this._data = data;
        }
    }
    
    /**
     * @return message key value for the name of the statistic
     */
    @XMLProperty
    public String getName() {
        return _name;
    }
    
    public void setName(String name) {
        this._name = name;
    }

    /**
     * @return a list of TaskStatistic objects. Note this is the singular TaskStatistic and not
     * the container TaskStatistics.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<TaskStatistic> getStatistics() {
        return _statistics;
    }

    public void setStatistics(List<TaskStatistic> statistics) {
        this._statistics = statistics;
    }

    /**
     * @return a list of TaskStatistics container objects.
     */    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<TaskStatistics> getTaskStatistics() {
        return _taskStatistics;
    }

    public void setTaskStatistics(List<TaskStatistics> statistics) {
        this._taskStatistics = statistics;
    }

}
