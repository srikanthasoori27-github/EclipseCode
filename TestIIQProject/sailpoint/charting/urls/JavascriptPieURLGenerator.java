/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.charting.urls;

import org.jfree.chart.urls.PieURLGenerator;
import org.jfree.data.general.PieDataset;

/**
 * @author peter.holcomb
 *
 */
public class JavascriptPieURLGenerator implements PieURLGenerator {

    /* (non-Javadoc)
     * @see org.jfree.chart.urls.PieURLGenerator#generateURL(org.jfree.data.general.PieDataset, java.lang.Comparable, int)
     */
    
    //Builds a url of type "functionName(category, pieIndex)" for use with javascript
    /** For serialization. */
    private static final long serialVersionUID = 1626966402065883419L;
    
    /** The prefix. */
    private String function = "javascript";
    
    public JavascriptPieURLGenerator()
    {
        super();
    }
    
    /**
     * Creates a new generator.
     *
     * @param prefix  the prefix.
     */
    public JavascriptPieURLGenerator(String function) {
        this.function = function;
    }
    
    
    public String generateURL(PieDataset dataset, Comparable key, int pieIndex) {
        String url = this.function;
        url += "('" + key.toString() + "\',\'" + String.valueOf(pieIndex) + "\')";
        //System.out.println(url);
        return url;
    }

}
