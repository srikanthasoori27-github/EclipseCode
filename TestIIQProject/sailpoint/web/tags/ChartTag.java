/*
 * Copyright 2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sailpoint.web.tags;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import javax.el.ValueExpression;
import javax.faces.component.UIGraphic;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.imagemap.StandardToolTipTagFragmentGenerator;

import sailpoint.api.SailPointFactory;
import sailpoint.charting.imagemap.JavascriptURLTagFragmentGenerator;
import sailpoint.object.Configuration;
import sailpoint.tools.FontHelper;
import sailpoint.tools.GeneralException;


public class ChartTag extends UIGraphic {

    private Object datasource;

    private Integer width;

    private Integer height;

    private Integer alpha;

    private Integer depth;

    private Integer startAngle;

    private Integer range;

    private String title;

    private String type;

    private String background;

    private String foreground;

    private String xlabel;

    private String ylabel;

    private String orientation;

    private String colors;
    
    private Boolean storeOnSession;

    private Boolean labels;

    private Boolean is3d;

    private Boolean legend;

    private Boolean antialias;

    private Boolean outline;    

    private String styleClass;

    private String alt;

    private String imgTitle;

    private String onclick;

    private String ondblclick;

    private String onmousedown;

    private String onmouseup;

    private String onmouseover;

    private String onmousemove;

    private String onmouseout;

    private String onkeypress;

    private String onkeydown;

    private String onkeyup;

    private String usemap;

    private String uselinks;
    
    private String size;
    
    private String path = "dashboard";
    
    private Configuration systemConfig;

    public ChartTag() {
        super();
    }
    
    /**
     * Loads the current system configuration.
     */
    private void loadSystemConfiguration() {
        if (systemConfig == null) {
            try {
                systemConfig = SailPointFactory.getCurrentContext().getConfiguration();
            } 
            catch (GeneralException e) {/* do nothing */}
        }
    }

    @Override
    public void encodeBegin(FacesContext context) throws IOException {
        setChartOnSession(context);

        ResponseWriter writer = context.getResponseWriter();
        writer.startElement("img", this);
        writer.writeAttribute("id", getClientId(context), null);
        writer.writeAttribute("width", String.valueOf(getWidth()), null);
        writer.writeAttribute("height", String.valueOf(getHeight()), null);
        writer.writeAttribute("src", context.getExternalContext().getRequestContextPath() + "/rest/image/chart/" + getPath() + "?ts=" + System.currentTimeMillis() + "&id=" + getClientId(context), null);

        ChartUtils.renderPassThruImgAttributes(writer, this);
        writer.endElement("img");

    }

    @Override
    public void encodeChildren(FacesContext context) throws IOException {
        super.encodeChildren(context);
    }

    @Override
    public void encodeEnd(FacesContext context){
        try{
            ResponseWriter writer = context.getResponseWriter();
            if(getUsemap()!=null){
                //Need to write the image map
                String mapString = (String)context.getExternalContext().getSessionMap().get(getClientId(context)+"map");                
                if(mapString!=null)
                {
                    writer.write(mapString);            
                }
            }
        } catch (Exception e) 
        {
            //ignore
        }
    }

    @Override
    public void decode(FacesContext context){
        try{
            super.decode(context);
        } catch (Exception e)
        {
            //ignore
        }
    }


    //creates and puts the chart data to session for this chart object
    private void setChartOnSession(FacesContext facesContext) {
        Map<String, Object> session = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();

        String clientId = getClientId(facesContext);
        ChartData data = new ChartData(this);
        session.put(clientId, data);
        
        //check to see if the chart has already been rendered and put on the session
        JFreeChart chart = null;
        
        String storedSize = (String)session.get(clientId + "size");
        if(storeOnSession!=null && storeOnSession.booleanValue()) {
            
            /** Only pull from the session if we are requesting a chart of the same size **/
            if(storedSize!=null && storedSize.equals(getSize()))
                chart = (JFreeChart)session.get(clientId + "chart");
        }
        
        if(chart==null) {
            Locale locale = getFacesContext().getViewRoot().getLocale();
            chart = ChartUtils.createChartWithType(data, locale);
            if(chart!=null){
                ChartUtils.setGeneralChartProperties(chart, data);
                
                loadSystemConfiguration();
                
                // If chart fonts are defined in init.xml, use them for the charts.
                if(systemConfig.getString(Configuration.CHART_TITLE_FONT_NAME) != null){
                    Font titleFont = FontHelper.getChartTitleFont(systemConfig);
                    chart.getTitle().setFont(titleFont);
                    chart.getLegend().setItemFont(titleFont);
                }

                ChartRenderingInfo chartInfo = new ChartRenderingInfo();
                BufferedImage image = chart.createBufferedImage(data.getWidth(), data.getHeight(), chartInfo);
    
                if(data.getImageMapName()!=null)
                {
                    session.put(clientId + "map", ChartUtilities.getImageMap(data.getImageMapName(), 
                            chartInfo, new StandardToolTipTagFragmentGenerator(), 
                            new JavascriptURLTagFragmentGenerator(data.getUselinks()))
                    );            
                }
                session.put(clientId + "image", image);
                
                if(storeOnSession!=null && storeOnSession.booleanValue()) {
                    session.put(clientId + "chart", chart);
                    session.put(clientId + "size", getSize());
                }
            }
        }
    }

    @Override
    public String getFamily() {
        return "sailpoint.ui.charts";
    }

    /**
     * Alpha attribute for pie charts
     */
    public int getAlpha() {
        if(alpha != null)
            return alpha.intValue();

        ValueExpression vb = getValueExpression("alpha");
        Integer v = vb != null ? (Integer)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.intValue(): 100;
    }

    public void setAlpha(int alpha) {
        this.alpha = new Integer(alpha);
    }

    /**
     * Antialias attribute
     */
    public boolean getAntialias() {
        if(antialias != null)
            return antialias.booleanValue();

        ValueExpression vb = getValueExpression("antialias");
        Boolean v = vb != null ? (Boolean)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.booleanValue(): false;
    }

    public void setAntialias(boolean antialias) {
        this.antialias = Boolean.valueOf(antialias);
    }

    /**
     * Background attribute
     */
    public String getBackground() {
        if(background != null)
            return background;

        ValueExpression vb = getValueExpression("background");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: "white";
    }

    public void setBackground(String background) {
        this.background = background;
    }

    /**
     * Foreground attribute
     */
    public String getForeground() {
        if(foreground != null)
            return foreground;

        ValueExpression vb = getValueExpression("foreground");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: "white";
    }

    public void setForeground(String foreground) {
        this.foreground = foreground;
    }

    /**
     * 3D attribute
     */
    public boolean getIs3d() {
        if(is3d != null)
            return is3d.booleanValue();

        ValueExpression vb = getValueExpression("is3d");
        Boolean v = vb != null ? (Boolean)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.booleanValue(): true;
    }

    public void setIs3d(boolean is3d) {
        this.is3d = Boolean.valueOf(is3d);
    }

    /**
     * Colors attributes for bar charts
     */
    public String getColors() {
        if(colors != null)
            return colors;

        ValueExpression vb = getValueExpression("colors");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setColors(String colors) {
        this.colors = colors;
    }

    /**
     * DataSource attribute
     */
    public Object getDatasource() {
        if(datasource != null)
            return datasource;

        ValueExpression vb = getValueExpression("datasource");
        Object v = vb != null ? vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setDatasource(Object datasource) {
        this.datasource = datasource;
    }

    /**
     * Depth attribute for pie charts
     */
    public int getDepth() {
        if(depth != null)
            return depth.intValue();

        ValueExpression vb = getValueExpression("depth");
        Integer v = vb != null ? (Integer)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.intValue(): 15;
    }

    public void setDepth(int depth) {
        this.depth = new Integer(depth);
    }

    /**
     * Range attribute
     */
    public int getRange() {
        if(range != null)
            return range.intValue();

        ValueExpression vb = getValueExpression("range");
        Integer v = vb != null ? (Integer)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.intValue(): -1;
    }

    public void setRange(int range) {
        this.range = new Integer(range);
    }

    /**
     * Width attribute
     */
    public int getWidth() {
        if(width != null)
            return width.intValue();

        ValueExpression vb = getValueExpression("width");
        Integer v = vb != null ? (Integer)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.intValue(): 400;
    }

    public void setWidth(int width) {
        this.width = new Integer(width);
    }

    /**
     * Height attribute
     */
    public int getHeight() {
        if(height != null)
            return height.intValue();

        ValueExpression vb = getValueExpression("height");
        Integer v = vb != null ? (Integer)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.intValue(): 300;
    }

    public void setHeight(int height) {
        this.height = new Integer(height);
    }

    /**
     * Legend attribute
     */
    public boolean getLegend() {
        if(legend != null)
            return legend.booleanValue();

        ValueExpression vb = getValueExpression("legend");
        Boolean v = vb != null ? (Boolean)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.booleanValue(): true;
    }

    public void setLegend(boolean legend) {
        this.legend = Boolean.valueOf(legend);
    }

    /**
     * Orientation attribute
     */
    public String getOrientation() {
        if(orientation != null)
            return orientation;

        ValueExpression vb = getValueExpression("orientation");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: "vertical";
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public boolean getLabels() {
        if(labels !=null)
            return labels.booleanValue();

        ValueExpression vb = getValueExpression("labels");
        Boolean v = vb != null ? (Boolean)vb.getValue(getFacesContext().getELContext()) : null;
        return v !=null ? v.booleanValue(): true;
    }

    public void setLabels(boolean labels) {
        this.labels = Boolean.valueOf(labels);
    }
    
    public boolean getStoreOnSession() {
        if(storeOnSession !=null)
            return storeOnSession.booleanValue();

        ValueExpression vb = getValueExpression("storeOnSession");
        Boolean v = vb != null ? (Boolean)vb.getValue(getFacesContext().getELContext()) : null;
        return v !=null ? v.booleanValue(): true;
    }

    public void setStoreOnSession(boolean storeOnSession) {
        this.storeOnSession = Boolean.valueOf(storeOnSession);
    }

    /**
     * Outline attribute
     */
    public boolean getOutline() {
        if(outline != null)
            return outline.booleanValue();

        ValueExpression vb = getValueExpression("outline");
        Boolean v = vb != null ? (Boolean)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.booleanValue(): true;
    }

    public void setOutline(boolean outline) {
        this.outline = Boolean.valueOf(outline);
    }

    /**
     * Start Angle attribute for pie charts
     */
    public int getStartAngle() {
        if(startAngle != null)
            return startAngle.intValue();

        ValueExpression vb = getValueExpression("startAngle");
        Integer v = vb != null ? (Integer)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v.intValue(): 0;
    }

    public void setStartAngle(int startAngle) {
        this.startAngle = new Integer(startAngle);
    }

    /**
     * Title attribute
     */
    public String getTitle() {
        if(title != null)
            return title;

        ValueExpression vb = getValueExpression("title");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUselinks() {
        if(uselinks !=null)
            return uselinks;

        ValueExpression vb = getValueExpression("uselinks");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v !=null ? v: null;
    }

    public void setUselinks(String uselinks) {
        this.uselinks = uselinks;
    }

    /**
     * Type attribute
     */
    public String getType() {
        if(type != null)
            return type;

        ValueExpression vb = getValueExpression("type");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * X-axis attribute
     */
    public String getXlabel() {
        if(xlabel != null)
            return xlabel;

        ValueExpression vb = getValueExpression("xlabel");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setXlabel(String xlabel) {
        this.xlabel = xlabel;
    }

    /**
     * Y-axis attribute
     */
    public String getYlabel() {
        if(ylabel != null)
            return ylabel;

        ValueExpression vb = getValueExpression("ylabel");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setYlabel(String ylabel) {
        this.ylabel = ylabel;
    }

    /**
     * StyleClass attribute
     */
    public String getStyleClass() {
        if(styleClass != null)
            return styleClass;

        ValueExpression vb = getValueExpression("styleClass");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setStyleClass(String styleClass) {
        this.styleClass = styleClass;
    }

    /**
     * Alt attribute
     */
    public String getAlt() {
        if(alt != null)
            return alt;

        ValueExpression vb = getValueExpression("alt");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setAlt(String alt) {
        this.alt = alt;
    }

    /**
     * ImgTitle attribute
     */
    public String getImgTitle() {
        if(imgTitle != null)
            return imgTitle;

        ValueExpression vb = getValueExpression("imgTitle");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setImgTitle(String imgTitle) {
        this.imgTitle = imgTitle;
    }

    /**
     * Onclick attribute
     */
    public String getOnclick() {
        if(onclick != null)
            return onclick;

        ValueExpression vb = getValueExpression("onclick");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOnclick(String onclick) {
        this.onclick = onclick;
    }

    /**
     * Ondblclick attribute
     */
    public String getOndblclick() {
        if(ondblclick != null)
            return ondblclick;

        ValueExpression vb = getValueExpression("ondblclick");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOndblclick(String ondblclick) {
        this.ondblclick = ondblclick;
    }

    /**
     * Onkeydown attribute
     */
    public String getOnkeydown() {
        if(onkeydown != null)
            return onkeydown;

        ValueExpression vb = getValueExpression("onkeydown");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOnkeydown(String onkeydown) {
        this.onkeydown = onkeydown;
    }

    /**
     * Onkeypress attribute
     */
    public String getOnkeypress() {
        if(onkeypress != null)
            return onkeypress;

        ValueExpression vb = getValueExpression("onkeypress");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOnkeypress(String onkeypress) {
        this.onkeypress = onkeypress;
    }

    /**
     * Onkeyup attribute
     */
    public String getOnkeyup() {
        if(onkeyup != null)
            return onkeyup;

        ValueExpression vb = getValueExpression("onkeyup");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOnkeyup(String onkeyup) {
        this.onkeyup = onkeyup;
    }

    /**
     * Onmousedown attribute
     */
    public String getOnmousedown() {
        if(onmousedown != null)
            return onmousedown;

        ValueExpression vb = getValueExpression("onmousedown");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOnmousedown(String onmousedown) {
        this.onmousedown = onmousedown;
    }

    /**
     * Onmousemove attribute
     */
    public String getOnmousemove() {
        if(onmousemove != null)
            return onmousemove;

        ValueExpression vb = getValueExpression("onmousemove");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOnmousemove(String onmousemove) {
        this.onmousemove = onmousemove;
    }

    /**
     * Onmouseout attribute
     */
    public String getOnmouseout() {
        if(onmouseout != null)
            return onmouseout;

        ValueExpression vb = getValueExpression("onmouseout");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOnmouseout(String onmouseout) {
        this.onmouseout = onmouseout;
    }

    /**
     * Onmouseover attribute
     */
    public String getOnmouseover() {
        if(onmouseover != null)
            return onmouseover;

        ValueExpression vb = getValueExpression("onmouseover");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOnmouseover(String onmouseover) {
        this.onmouseover = onmouseover;
    }

    /**
     * Onmouseup attribute
     */
    public String getOnmouseup() {
        if(onmouseup != null)
            return onmouseup;

        ValueExpression vb = getValueExpression("onmouseup");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setOnmouseup(String onmouseup) {
        this.onmouseup = onmouseup;
    }

    public String getUsemap() {
        if(usemap != null)
            return usemap;

        ValueExpression vb = getValueExpression("usemap");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setUsemap(String usemap) {
        this.usemap = usemap;
    }
    
    public String getSize() {
        if(size != null)
            return size;

        ValueExpression vb = getValueExpression("size");
        String v = vb != null ? (String)vb.getValue(getFacesContext().getELContext()) : null;
        return v != null ? v: null;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public Object saveState(FacesContext context) {
        Object values[] = new Object[39];
        values[0] = super.saveState(context);
        values[1] = datasource;
        values[2] = width;
        values[3] = height;
        values[4] = alpha; 
        values[5] = depth;
        values[6] =	startAngle;
        values[7] =	title;
        values[8] =	type;
        values[9] =	background;
        values[10] = foreground;
        values[11] = xlabel;
        values[12] = ylabel;
        values[13] = orientation;
        values[14] = colors;
        values[15] = is3d;
        values[16] = legend;
        values[17] = antialias;
        values[18] = outline;
        values[19] = styleClass;
        values[20] = alt;
        values[21] = imgTitle;
        values[22] = onclick;
        values[23] = ondblclick;
        values[24] = onmousedown;
        values[25] = onmouseup;
        values[26] = onmouseover;
        values[27] = onmousemove;
        values[28] = onmouseout;
        values[29] = onkeypress;
        values[30] = onkeydown;
        values[31] = onkeyup;
        values[32] = usemap;
        values[33] = uselinks;
        values[34] = labels;
        values[35] = range;
        values[36] = storeOnSession;
        values[37] = size;
        return values;
    }

    @Override
    public void restoreState(FacesContext context, Object state) {
        Object values[] = (Object[]) state;
        super.restoreState(context, values[0]);
        this.datasource = values [1];
        this.width = (Integer) values[2];
        this.height = (Integer) values[3];
        this.alpha = (Integer) values[4];
        this.depth = (Integer) values[5];
        this.startAngle = (Integer) values[6];
        this.title = (String) values[7];
        this.type = (String) values[8];
        this.background = (String) values[9];
        this.foreground = (String)values[10];
        this.xlabel = (String) values[11];
        this.ylabel = (String) values[12];
        this.orientation = (String) values[13];
        this.colors = (String) values[14];
        this.is3d = (Boolean )values[15];
        this.legend = (Boolean) values[16];
        this.antialias = (Boolean) values[17];
        this.outline = (Boolean) values[18];
        this.styleClass = (String) values[19];
        this.alt = (String) values[20];
        this.imgTitle = (String) values[21];
        this.onclick = (String) values[22];
        this.ondblclick = (String) values[23];
        this.onmousedown = (String) values[24];
        this.onmouseup = (String) values[25];
        this.onmouseover = (String) values[26];
        this.onmousemove = (String) values[27];
        this.onmouseout = (String) values[28];
        this.onkeypress = (String) values[29];
        this.onkeydown = (String) values[30];
        this.onkeyup = (String) values[31];
        this.usemap = (String) values[32];
        this.uselinks = (String) values[33];
        this.labels = (Boolean) values[34];
        this.range = (Integer) values[35];
        this.storeOnSession = (Boolean) values[36];
        this.size = (String) values[37];
    }

}


