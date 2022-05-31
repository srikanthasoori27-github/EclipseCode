/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.charting.urls;

import java.io.Serializable;

import org.jfree.chart.urls.CategoryURLGenerator;
import org.jfree.data.category.CategoryDataset;

/**
 * @author peter.holcomb
 *
 */
public class JavascriptCategoryURLGenerator implements CategoryURLGenerator, Serializable {

    /* (non-Javadoc)
     * @see org.jfree.chart.urls.CategoryURLGenerator#generateURL(org.jfree.data.category.CategoryDataset, int, int)
     */
    
    private static final long serialVersionUID = 1626966402065883419L;
    
    /** The prefix. */
    private String function = "javascript";
    private String chartName;
    
    public JavascriptCategoryURLGenerator()
    {
        
    }
    
    public JavascriptCategoryURLGenerator(String function, String chartName) {
        this.function = function;
        this.chartName = chartName;
    }
    
    
    public String generateURL(CategoryDataset dataset, int series, int category) {
        String url = this.function;
        //System.out.println(dataset.getColumnKey(category) + " " + dataset.getRowKey(series) + " " + chartName);
        url += "('" + dataset.getColumnKey(category) + "\',\'" + dataset.getRowKey(series) 
        + "\',\'" + chartName + "\')";
        //System.out.println(url);
        return url;
    }

}
