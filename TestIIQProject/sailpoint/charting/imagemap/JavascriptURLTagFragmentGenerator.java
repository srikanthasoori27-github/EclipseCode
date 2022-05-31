/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.charting.imagemap;

import org.jfree.chart.imagemap.URLTagFragmentGenerator;

/**
 * @author peter.holcomb
 *
 */
public class JavascriptURLTagFragmentGenerator implements
        URLTagFragmentGenerator {
    
    boolean cursorPointer = false;

    /**
     * 
     */
    public JavascriptURLTagFragmentGenerator() {
    }
    
    public JavascriptURLTagFragmentGenerator(String useLinks) {
        if(useLinks!=null && !useLinks.equals(""))
            cursorPointer = true;
    }

    /* (non-Javadoc)
     * @see org.jfree.chart.imagemap.URLTagFragmentGenerator#generateURLFragment(java.lang.String)
     */
    public String generateURLFragment(String onclickText) {
        if(cursorPointer)
            return "style=\"cursor:pointer\" onclick=\"" + onclickText + "\"";
        else
            return "";
    }

}
