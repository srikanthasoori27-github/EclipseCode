/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.List;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;


/**
 * This is a basic JRDatasource implementation using a List as the dataset. 
 * You can also pass in '_THIS' as a field name and the datasource will return the current
 * bean rather than just a field. This can be helpful in a number of situations and I believe
 * it's being added to the next japser release.
 *
 * User: jonathan.bryant
 * Created: 11:43:08 PM Jun 25, 2007
 *
 * @ignore TQM: I don't see this class being used anywhere.
 */
@Deprecated
public class SailPointListDataSource extends SailPointDataSource{

    private static final Log log = LogFactory.getLog(SailPointListDataSource.class);

    // Index of the currentBean.
    private int count = -1;

    // The data set for this datasource
    private List data = null;

    /**
     * Creates a datasource for with the given context and list.
     *
     * @param data    List to use for this datasource.
     */
    public SailPointListDataSource( List data){
        super();
        this.data = data;
    }

    /**
     * Initializes data set.
     */
    public void internalPrepare() throws GeneralException {
        if (data == null)
            data = new ArrayList();
        moveFirst();
    }

    /**
     * @return True if the data for this source has been loaded, ie data != null.
     */
    protected boolean isInitialized() {
        return data != null;
    }


    /**
     * @return This datasource's data set
     */
    protected List getData() {
        return data;
    }

    /**
     * @param data This datasource's data set
     */
    protected void setData(List data) {
        this.data = data;
    }


    /**
     * @return Next item from list.
     * @throws JRException
     */
    public boolean internalNext() throws JRException {
        count++;
        return !data.isEmpty() && count < data.size();
    }


    /**
     * Resets datasource count to before first item on list.
     */
    public void moveFirst() {
        count = -1;
    }

    /**
     * Gets current item for the datasource. This is helpful in
     * cases where you need to get the current item after
     * Jasper has called next.
     *
     * @return The current item in the datasource
     * @throws JRException
     */
    public Object getCurrentItem() throws JRException{
        return data.get(count);
    }

    public Object getFieldValue(JRField field) throws JRException {
        return DataSourceUtil.getFieldValue(field, this.getCurrentItem());
    }
}
