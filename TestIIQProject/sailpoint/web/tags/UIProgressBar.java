/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

import java.io.IOException;
import java.util.Map;

import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.el.ValueBinding;

import sailpoint.tools.Util;

/**
 * <p> A custom tag for adding a ProgressBar to a page.  </p>
 * 
 * This can be used in two major ways:
 *  1) Specifying a total and completed and allowing the component to render
 *     the progress bar appropriately. Or, Specifying percentComplete only without specifying total and completed 
 *     allowing the component to render  the progress bar appropriately. 
 *     This allows to keep total and completed optional.  See method {@link #getPercentRemaining()}.
 *  2) In addition to the total and completed, specifying the greenNum,
 *     yellowNum, and redNum, which are used to determine the size of each
 *     portion of the bar.  This gives the caller full control over how the
 *     three sections are divided.
 * 
 * <p> Heres an Example use of the tag </p>
 * <pre>
 *    <sp:progressBar id="taskProgressBar" 
 *      width="250" 
 *      percentComplete="#{taskResult.object.percentComplete}" 
 *      updateMethodName="updateProgressBar"> 
 *    </sp:progressBar>
 * </pre>
 * <p>
 * NOTE:
 *    This example is using option 1 with percentComplete only without specifying total and completed. 
 *    This styles applied to the xStyle properties below should 
 *    NOT provide the width style, it will be calculated during 
 *    rendering.
 * </p>
 * <ul>
 *   <li>
 *     <b>width</b>: The value specifying the number of pixles the overall
 *              length of the progress bar. 
 *   </li>
 *   <li>
 *     <b>percentComplete</b>: A number from 0 to 100 either float or int
 *   </li>
 *   <li>
 *     <b>totalNum</b>: The number of total items.  If specified, the number
 *                      complete vs. total is displayed. (optional)
 *   </li>
 *   <li>
 *     <b>completeNum</b>: The number of completed items.  If specified, the
 *                         number complete vs. total is displayed. (optional)
 *   </li>
 *   <li>
 *     <b>detailsLink</b>: The URL to view the details for this progress bar.
 *                         (optional)
 *   </li>
 *   <li>
 *     <b>updateMethodName</b>: The name of the javascript function that will
 *        be generated and can be called to update the progress
 *        bar using the javascript/dom. If this attribute is not specifid
 *        then the method will not be generated.
 *   </li>
 *   <li>
 *     <b>topLevelStyle</b>: The styles to apply to the top level div which 
 *        provides the border that surrounds the entire progress bar.
 *   </li>
 *   <li>
 *     <b>topLevelClass</b>: The class for the toplevel div
 *   </li>
 *   <li>
 *     <b>percentStyle</b>:  The styles to apply to the overlay div which 
 *        provides the percentage complete in textual form.
 *   </li>
 *   <li>
 *     <b>percentClass</b>: The css class for the percentage div.
 *   </li>
 *   <li>
 *     <b>remainingStyle</b>: The styles to apply to the remaining div which 
 *        provides the percentage remaining image.
 *   </li>
 *   <li>
 *     <b>remainingClass</b>: The css class for the remaining div.
 *   </li>
 *   <li>
 *     <b>completeStyle</b>: The styles to apply to the complete div which 
 *        provides the percentage complete image.
 *   </li>
 *   <li>
 *     <b>completeStyle</b>: The css class for the complete div.
 *   </li>
 * </ul>
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class UIProgressBar extends UIOutput {

	private static final String DEFAULT_CLASS_PROGRESS_BAR_OVERDUE = "progressBarOverdue";

	private static final String DEFAULT_CLASS_PROGRESS_BAR_COMPLETE = "progressBarComplete";

	private static final String DEFAULT_CLASS_PROGRESS_BAR_REMAINING = "progressBarRemaining";
	
    private static final String DEFAULT_WIDTH = "150";
    private static final int DEFAULT_WIDTH_INT = 150;
    
    private int _defaultLeftMargin = 1;
    private int _defaultRightMargin = 1;

	private String _percentComplete;

    private String _completedNum;
    private String _totalNum;

    private String _greenNum;
    private String _yellowNum;
    private String _redNum;

    private String _detailsLink;

    private String _width;
    private String _updateMethodName;

    private String _topLevelStyle;
    private String _topLevelClass;

    private String _completeStyle;
    private String _completeClass;

    private String _remainingStyle;
    private String _remainingClass;
    
    private String _overdueStyle;
    private String _overdueClass;

    private String _percentStyle;
    private String _percentClass;


    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor. 
     */
    public UIProgressBar() 
    {
    	if (isIE6()) {
    		// IE6 needs different default margins
    		_defaultLeftMargin = 0;
			_defaultRightMargin = 2;
    	}
    }
    
    /**
     * Determines whether or not the request came from an IE6 browser.
     * @return True if the browser was determined to be IE6.
     */
    @SuppressWarnings("rawtypes")
	private boolean isIE6()
    {
    	Map headerValues = null;
    	
    	FacesContext context = getFacesContext();
    	if (context != null && context.getExternalContext() != null) {
    		headerValues = context.getExternalContext().getRequestHeaderMap();
    	}

    	if (headerValues != null) {
    		String userAgent = (String)headerValues.get("user-agent");    		
    		if (userAgent != null && userAgent.contains("MSIE 6.0")) {
    			return true;
    		}
    	}
    	
    	return false;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Properties - these are retrieved either from the instance variables or
    // are evaluated as value bindings.
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPercentComplete() {
        if ( null != _percentComplete )
            return _percentComplete;
        return evaluateValueBinding("percentComplete");
    }
    
    public void setPercentComplete (String complete) {
        _percentComplete = complete;
    }

    public int getPercentCompleteInt() {
        return Math.round( getPercentCompleted() );
    }

    public String getCompletedNum() {
        if ( null != _completedNum )
            return _completedNum;
        return evaluateValueBinding("completedNum");
    }

    public void setCompletedNum (String complete) {
        _completedNum = complete;
    }

    public String getTotalNum() {
        if ( null != _totalNum )
            return _totalNum;
        return evaluateValueBinding("totalNum");
    }

    public void setTotalNum (String total) {
        _totalNum = total;
    }
    

    public String getGreenNum() {
        if ( null != _greenNum )
            return _greenNum;
        return evaluateValueBinding("greenNum");
    }

    public void setGreenNum(String num) {
        _greenNum = num;
    }

    public String getYellowNum() {
        if ( null != _yellowNum )
            return _yellowNum;
        return evaluateValueBinding("yellowNum");
    }

    public void setYellowNum(String num) {
        _yellowNum = num;
    }

    public String getRedNum() {
        if ( null != _redNum )
            return _redNum;
        return evaluateValueBinding("redNum");
    }

    public void setRedNum(String num) {
        _redNum = num;
    }


    public String getDetailsLink() {
        if ( null != _detailsLink )
            return _detailsLink;
        return evaluateValueBinding("detailsLink");
    }

    public void setDetailsLink (String link) {
        _detailsLink = link;
    }

    public String getWidth() {
        if ( _width != null ) 
            return _width;

        String width = evaluateValueBinding("width");
        if ( width == null )  
            width = DEFAULT_WIDTH;

        return width;
    }

    public void setWidth(String width) {
        _width = width;
    }

    public String getUpdateMethodName() {
        if ( _updateMethodName != null ) 
            return _updateMethodName;
        return  evaluateValueBinding("updateMethodName");
    }

    public void setUpdateMethodName(String updateMethodName) {
        _updateMethodName = updateMethodName;
    }

    public String getCompleteClass() {
        if ( _completeClass != null ) 
            return _completeClass;
        return  evaluateValueBinding("completeClass");
    }

    public void setCompleteClass(String completeClass) {
        _completeClass = completeClass;
    }
    
    public String getOverdueClass() {
    	if ( _overdueClass != null ) 
            return _overdueClass;
        return  evaluateValueBinding("overdueClass");
	}

	public void setOverdueClass(String class1) {
		_overdueClass = class1;
	}

	public String getOverdueStyle() {
		if ( _overdueStyle != null ) 
            return _overdueStyle;
        return  evaluateValueBinding("overdueStyle");
	}

	public void setOverdueStyle(String style) {
		_overdueStyle = style;
	}
	
    /** 
     * Sets the style for the complete div, this must not include
     * the width property, since that is calculated by this class
     * and appended to any passed style.
     */
    public String getCompleteStyle() {
        if ( _completeStyle != null ) 
            return _completeStyle;
        return  evaluateValueBinding("completeStyle");
    }

    public void setCompleteStyle(String completeStyle) {
        _completeStyle = completeStyle;
    }

    public String getRemainingClass() {
        if ( _remainingClass != null ) 
            return _remainingClass;
        return  evaluateValueBinding("remainingClass");
    }

    public void setRemainingClass(String completeClass) {
        _remainingClass = completeClass;
    }

    public String getRemainingStyle() {
        if ( _remainingStyle != null ) 
            return _remainingStyle;
        return evaluateValueBinding("remainingStyle");
    }

    /** 
     * Sets the style for the remaining div, this must not include
     * the width property, since that is calculated by this class
     * and appended to any passed style.
     */
    public void setRemainingStyle(String completeStyle) {
        _remainingStyle = completeStyle;
    }

    /** 
     * Sets the style for the remaining div, this must not include
     * the width property, since that is calculated by this class
     * and appended to any passed style.
     */
    public void setPercentStyle(String percentStyle) {
       _percentStyle = percentStyle;
    }

    public String getPercentStyle() {
        if ( _percentStyle != null ) 
            return _percentStyle;
        return evaluateValueBinding("percentStyle");
    }

    public String getPercentClass() {
        if ( _percentClass != null ) 
            return _percentClass;
        return  evaluateValueBinding("percentClass");
    }

    public void setPercentClass(String percentClass) {
        _percentClass = percentClass;
    }

    /** 
     * Sets the style for the remaining div, this must not include
     * the width property, since that is calculated by this class
     * and appended to any passed style.
     */
    public void setTopLevelStyle(String percentStyle) {
       _topLevelStyle = percentStyle;
    }

    public String getTopLevelStyle() {
        if ( _topLevelStyle != null ) 
            return _topLevelStyle;
        return evaluateValueBinding("topLevelStyle");
    }

    public String getTopLevelClass() {
        if ( _topLevelClass != null ) 
            return _topLevelClass;
        return  evaluateValueBinding("topLevelClass");
    }

    public void setTopLevelClass(String topDivClass) {
        _topLevelClass = topDivClass;
    }

    private String evaluateValueBinding(String bindingName) {
        ValueBinding vb = getValueBinding(bindingName);
        return (vb != null) ? (String) vb.getValue(FacesContext.getCurrentInstance()) : null;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Helper methods
    //
    ////////////////////////////////////////////////////////////////////////////

    //TODO: can we just tokenize this string to make sure the 
    // width property doesn't get put into the style twice?
    private String appendToStyle(String existingStyle, String widthStyle) {
        StringBuffer styleBuf = new StringBuffer();
        if ( existingStyle != null  ) {
            styleBuf.append(existingStyle);
            if ( ( existingStyle.length() > 0 )  && 
                 ( !existingStyle.endsWith(";") ) ) {
                styleBuf.append(";");
            }
        }
        styleBuf.append(widthStyle);
        return styleBuf.toString();
    }

    private int getPercentInt(String percentStr) {

        if (percentStr == null || percentStr.trim().length() == 0) 
            percentStr = "0";

        // assume a float, which will also handle ints
        Float percentFloat = Float.parseFloat(percentStr);
        float percent = percentFloat.floatValue();
        return Math.round( percent );
    }

    private float getCompleteWidth() {
        return getWidth( getPercentCompleteInt() );
    }
    
    /**
     * Calculate the remaining width by subtracting the completed from the
     * total.  This is only used when green/yellow/red are not specified.
     */
    private float getRemainingWidth() {
    	float result = getWidth(getPercentRemaining());
    	
    	if (isIE6()) {
    		result += 1;
    	}
    	
        return result;
    }
    
    private float getGreenWidth() {
        float width = Math.round(getWidth(getPercentGreen()));
        return width;
    }

    private float getYellowWidth() {
        float width = Math.round(getWidth(getPercentYellow()));
        
        
        /** Trim the width back if the green+yellow is greater than total width**/
        if(getPercentRed()==0 && (width+getGreenWidth()>getInternalWidth())) {
            width = getInternalWidth() - getGreenWidth();
        }        
        return width;
    }

    private float getRedWidth() {
        float width = Math.round(getWidth(getPercentRed()));
        
        if((width+getGreenWidth()+getYellowWidth())>getInternalWidth()) {
            width = getInternalWidth() - getGreenWidth()-getYellowWidth();
        }
        return width;
    }

    private float getWidth(float percent) {
        if(percent < 0)
            return 0;
        float width = percent * getProgressMultiplier();
        
        /** don't let it be longer than the total allowed width **/
        if(width>getInternalWidth()) {
            width=getInternalWidth();
            
            if (isIE6()) {
            	width -=1;
            }
        }
        return width;
    }

    private float getCompleteLeft() {
        return _defaultLeftMargin;
    }

    /**
     * Calculate the left offset for the "remaining" section by subtracting the
     * completed from the total.  This is only used when green/yellow/red are
     * not specified.
     */
    private float getRemainingLeft() {
        float remainingLeft = getCompleteLeft() + getCompleteWidth();
        return remainingLeft;
    }
    
    private float getGreenLeft() {
        return _defaultLeftMargin;
    }

    private float getYellowLeft() {
    	float yellowLeft = getGreenLeft() + getGreenWidth();
        return yellowLeft;
    }

    private float getRedLeft() {
        float redLeft = getYellowLeft() + getYellowWidth();
        return redLeft;
    }

    private float getPercentCompleted() {
        float percentCompleted = 0;
        final int total = safeParseInt(getTotalNum());            
        final int completed = safeParseInt(getCompletedNum());
         
        if (total > 0){
            percentCompleted = Util.getPercentage( completed, total );
        } else if (completed == 0 && total == 0) {
            // 0/0 is 100%
            percentCompleted = 100;
        } else if (null != Util.getString(getPercentComplete()) 
                  && null == Util.getString(getCompletedNum()) 
                  && null == Util.getString(getTotalNum())) {
            //this is used if the totalNum and completeNum are NOT specified
            //AND the percentComplete is specified.
            percentCompleted = Util.atof(getPercentComplete());
        }

        return percentCompleted;
    }
    
    /**
     * Calculate the percent of the bar used to display the remaining by
     * subtracting the completed from the total.  or, if the total and completed are
     * not specified look for percentComplete and use that to calculate percentRemaining.
     * This is only used when
     * green/yellow/red are not specified.
     */
    private float getPercentRemaining() {
        float percentRemaining = 0;
        percentRemaining = 100 - getPercentCompleted();
        return percentRemaining;
    }

    private float getPercentGreen() {
        int percentGreen = 0;
        final int totalNum = safeParseInt(getTotalNum());
        if (totalNum > 0)
            percentGreen = Util.getPercentage(safeParseInt(getGreenNum()), totalNum);
        return percentGreen;
    }

    private float getPercentYellow() {
        int percentYellow = 0;
        final int totalNum = safeParseInt(getTotalNum());
        if (totalNum > 0)
            percentYellow = Util.getPercentage(safeParseInt(getYellowNum()), totalNum);
        return percentYellow;
    }

    private float getPercentRed() {
        int percentRed = 0;
        final int totalNum = safeParseInt(getTotalNum());
        if (totalNum > 0)
            percentRed = Util.getPercentage(Integer.parseInt(getRedNum()), totalNum);
        
        return percentRed;
    }
    

    private float getProgressMultiplier() {
        return ( (float) getInternalWidth() ) / 100f;
    }

    private int getInternalWidth() {
        return DEFAULT_WIDTH_INT - _defaultLeftMargin - _defaultRightMargin;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Component methods
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getRendererType() {
        // Don't use a renderer.
        return null;
    }

    @Override
    public void encodeBegin(FacesContext context) throws IOException {

        ResponseWriter writer = context.getResponseWriter();
        String updateMethod = getUpdateMethodName();

        int percentComplete = getPercentCompleteInt();

        // Top level div which provides a border around the  
        // progress bar
        String existingTopDivStyle = getTopLevelStyle();
        String topDivStyle = appendToStyle(existingTopDivStyle, 
                                           "width: " +getWidth()+ "px");
        String topDivClass = getTopLevelClass();
        if ( topDivClass == null ) topDivClass = "progressBar";
        writer.startElement("div", this);
        writer.writeAttribute("class", topDivClass, null);
        writer.writeAttribute("style", topDivStyle, null);

        // If the subsection numbers are not specified, render the complete and
        // remaining divs.
        if (!subsectionsSpecified()) {

            // Complete div which gives us the percent complete graphic
            writeSection(writer, getCompleteStyle(), getCompleteClass(),
                         DEFAULT_CLASS_PROGRESS_BAR_COMPLETE, getCompleteWidth(), getCompleteLeft() );

            // Remaining div which gives us the percent remaining graphic
            writeSection(writer, getRemainingStyle(), getRemainingClass(),
                         DEFAULT_CLASS_PROGRESS_BAR_REMAINING, getRemainingWidth(),
                         getRemainingLeft());
        }
        else {
            // Sanity check - the javascript that we generate for the updateMethod
            // doesn't support three sections yet.  Complain loudly if someone
            // tries to use this.  Eventually need to get this working or maybe
            // just get rid of the component.
            if (null != updateMethod) {
                throw new RuntimeException("UIProgressBar does not currently support updateMethodName for continuous certifications.");
            }

            // Green div which gives us the percent green graphic
            if(getGreenNum()!=null  && Integer.parseInt(getGreenNum())>0) {
                writeSection(writer, getCompleteStyle(), getCompleteClass(),
                             DEFAULT_CLASS_PROGRESS_BAR_COMPLETE, getGreenWidth(), getGreenLeft() );
            }

            // Yellow div which gives us the percent yellow graphic
            if(getYellowNum()!=null  && Integer.parseInt(getYellowNum())>0) {
                writeSection(writer, getRemainingStyle(), getRemainingClass(),
                             DEFAULT_CLASS_PROGRESS_BAR_REMAINING, getYellowWidth(),
                             getYellowLeft());
            }
            
            // Red div which gives us the percent red graphic
            if(getRedNum()!=null  && Integer.parseInt(getRedNum())>0) {
                writeSection(writer, getOverdueStyle(), getOverdueClass(),
                             DEFAULT_CLASS_PROGRESS_BAR_OVERDUE, getRedWidth(), getRedLeft());
            }
        }
        
        writer.endElement("div");

        //
        // Num completed vs. num total and details link (only rendered if
        // properties are found).
        //
        if (null != Util.getString(getPercentComplete())) {
        
            writer.startElement("span", this);
            writer.writeAttribute("class", "progressBarNumComplete", null);
            writer.writeAttribute("style", "", null);

            if ((null != Util.getString(getCompletedNum()) &&
                (null != Util.getString(getTotalNum())))) {
                writer.write("<span id='progressBarTotals'>"+ getCompletedNum() + "/" + getTotalNum()+"</span>");
            }
            
            writer.write(" (<span id='progressBarPercentage'>"+ Util.itoa(percentComplete) + "%</span>) ");

            if (null != Util.getString(getDetailsLink()) && Util.atoi(getTotalNum())>0) {
                // Use a relative position to avoid overflow issues (absolute is inherited
                // from progressBarPercentage).
                writer.write("&nbsp;<a style=\"position: relative\" href=\"" + getDetailsLink() + "\">[Details]</a>");
            }

            writer.endElement("span");
        }

        

        // if method supplied generated javascript that can be called to update the progress
        // Note that this currently doesn't support continuous cert progress bars and that
        // trying to use this with a continuous cert will throw an exception (see above).
        // We either need to fix this function to work for all types of progress bars or
        // get a new progress bar component.
        if ( updateMethod != null ) {
            writer.write("\n\n");
            writer.startElement("script", this);
            writer.writeAttribute("defer", "defer", null);

            writer.write("\nfunction " + updateMethod +"(percentComplete, completed, total) { \n");
            //writer.write("  alert('Updating progress bar with percentComplete = ' + percentComplete + ', completed = ' + completed + ', and total = ' + total);");
            writer.write("  if(!completed) { completed = 0; }\n");
            writer.write("  if(!total) { total= 0; }\n");
            writer.write("  var percent = percentComplete + \"%\";\n") ;
            writer.write("  $('progressBarPercentage').update(percent);\n");
            writer.write("  if($('progressBarTotals')) {\n");
            writer.write("    $('progressBarTotals').update(completed + \"/\"+total);\n");
            writer.write("  }\n");
            writer.write("  var remaining = $('"+ getClazzValue(getRemainingClass(), DEFAULT_CLASS_PROGRESS_BAR_REMAINING) + "');\n");
            writer.write("  if (new Number(percentComplete).toFixed() === 0 && Ext.isIE6) {\n");
            writer.write("    remaining.style.left = '0px';\n");
            writer.write("  } else {\n");
            writer.write("    remaining.style.width = (" + getWidth() + " - (percentComplete*" + getProgressMultiplier() + "))+ \"px\";\n");
            writer.write("    remaining.style.left = (percentComplete*" + getProgressMultiplier() + ")+ \"px\";\n");
            writer.write("    var completed = $('" + getClazzValue(getCompleteClass(), DEFAULT_CLASS_PROGRESS_BAR_COMPLETE) + "');\n");
            writer.write("    if (completed) {\n");
            writer.write("      var completedWidth = percentComplete*" + getProgressMultiplier() + ";\n");
            writer.write("      var margin = " + _defaultLeftMargin + " + " + _defaultRightMargin + ";\n");
            writer.write("      if (Ext.isIE6 && completedWidth >= margin) { completedWidth -= margin; }\n");
            writer.write("      completed.style.width = completedWidth +\"px\";\n");
            writer.write("    }\n");            
            writer.write("  }\n");
            writer.write("}\n");
            writer.endElement("script");
            
        }
    }

    /**
     * Return whether or not the number of green/yellow/red are specified for
     * this component.
     */
    private boolean subsectionsSpecified() {
        return ((null != getGreenNum()) && (null != getYellowNum()) &&
                (null != getRedNum())) &&
                ((0 != Integer.parseInt(getGreenNum())) ||
                 (0 != Integer.parseInt(getYellowNum())) ||
                 (0 != Integer.parseInt(getRedNum())));
    }
    
    /**
     * Render one part of the bar.
     */
    private void writeSection(ResponseWriter writer, String style, String clazz,
                              String defaultClass, float width, float left)
        throws IOException {

        String styleWithPosition =
            appendToStyle(style, " width: " + width + "px;" +
                                 ((left > 0) ? " left: " + left + "px" : ""));

        clazz = getClazzValue(clazz, defaultClass);
        writer.startElement("span", this);

        // need id if we are generating a method to update progress
        String updateMethod = getUpdateMethodName();
        if ( updateMethod != null ) 
            writer.writeAttribute("id", clazz, null);

        writer.writeAttribute("class", clazz, null);
        writer.writeAttribute("style", styleWithPosition, null);
        writer.endElement("span");
    }
    

    private String getClazzValue(String clazz, 
    		                     String defaultClass)
        throws IOException {
    	String clazzValue = (null == clazz) ? defaultClass : clazz;
    	return clazzValue;
    }
    
    private int safeParseInt(String intString) {
        final int result;
        
        if (intString == null || intString.trim().length() == 0)
            result = 0;
        else
            result = Integer.parseInt(intString);
        
        return result;
    }
}
