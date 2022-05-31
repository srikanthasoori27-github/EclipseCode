/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRSection;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.design.JRDesignSubreport;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.engine.type.StretchTypeEnum;
import sailpoint.object.ReportColumnConfig;


/**
 * This class models a jasper report with columns that are added programtically at runtime.
 * In order to do this the page width must be adjusted depending on the number of columns
 * added.
 *
 * When the report is compiled, we resize the page design, attempting to make it fit a
 * standard paper size, either 8.5 x 11 or 8.5 x 14. Once a page size is chosen the
 * columns are stretched to fit the entire width of the page.
 *
 * User: jonathan.bryant
 * Created: 10:15:24 AM May 25, 2007
 */
public class DynamicColumnReport{

    // Standard paper 8.3 x 11 @ 72 DPI
    private static final int PAPER_STANDARD_WIDTH = 595;
    private static final int PAPER_STANDARD_LENGTH = 792;

    // 'Legal' paper 8.3 x 14 @ 72 DPI
    private static final int PAPER_LEGAL_WIDTH = 595;
    private static final int PAPER_LEGAL_LENGTH = 1008;

    private static final int MARGIN_DEFAULT = 30;

    private static final String ELEMENT_PAGE_TOTAL = "pageTotal";
    private static final String ELEMENT_PAGE_CURRENT = "currentPageNumber";
    private static final String ELEMENT_FOOTER_BAR = "footerBlackLine";

    private JasperDesign design;
    private String headerStyle;
    private String detailStyle;
    private List<ReportColumnConfig> columns;


    /**
     * Creates a new report with the given design.
     *
     * @param design The design for the new report. May not be null.
     */
    public DynamicColumnReport(JasperDesign design) {
        if (design==null)
            throw new IllegalArgumentException("Design may not be null");
        this.design =  design;
        columns = new ArrayList<ReportColumnConfig>();
    }

    /**
     * Adds a new column to the report with the given properties
     *
     * @param field Report column field name
     * @param header Report column header text
     * @param valueClass Value class for the column
     */
    public void addColumn(String field, String header, Class<?> valueClass){
        columns.add(new ReportColumnConfig(field, header, valueClass.getName()));
    }

    /**
     * Adds a new column to the report with the given properties
     *
     * @param field Report column field name
     * @param header Report column header text
     * @param valueClass Value class for the column
     * @param columnWidth Report column width
     */
    public void addColumn(String field, String header, Class<?> valueClass, int columnWidth){
        columns.add(new ReportColumnConfig(field, header, valueClass.getName(), columnWidth));
    }

    /**
     * Adds a new column to the report.
     *
     * @param col New column to add to the report
     */
    public void addColumn(ReportColumnConfig col){
        columns.add(col);
    }

    /**
     * Based on the size of the clumns, change the design so that the columns included
     * in this report will fit on a printed page.
     *
     * Basically we try and fit the report onto the smallest size paper possible
     * maintaining our margins. Here are the options in the order from smallest to
     * largest:
     *
     * 1. Standard paper portrait
     * 2. Standard paper landscape
     * 3. Legal paper landscape
     * 4. Too big for normal paper, just set the page width.
     *
     * Once the page size is selected, the columns' size is increased so that
     * they stretch to fill the entire page.
     *
     * Lastly, the footer is adjusted to it fits the new page size.
     */
    private void resizePage(){

        // Get the total width of our columns
        int width = 0;
        for(ReportColumnConfig col : columns)
            width += col.getWidth();
        
        // Based on the width choose a paper size and orientation
        if (width <= PAPER_STANDARD_WIDTH - (MARGIN_DEFAULT * 2) 
                && (!OrientationEnum.LANDSCAPE.equals(design.getOrientationValue()))){
            design.setOrientation(OrientationEnum.PORTRAIT);
            design.setPageWidth(PAPER_STANDARD_WIDTH);
            design.setPageHeight(PAPER_STANDARD_LENGTH);
            design.setColumnWidth(PAPER_STANDARD_WIDTH - (MARGIN_DEFAULT * 2));
        }else if (width <= PAPER_STANDARD_LENGTH - (MARGIN_DEFAULT * 2)){
            design.setOrientation(OrientationEnum.LANDSCAPE);
            design.setPageWidth(PAPER_STANDARD_LENGTH);
            design.setPageHeight(PAPER_STANDARD_WIDTH);
            design.setColumnWidth(PAPER_STANDARD_LENGTH - (MARGIN_DEFAULT * 2));
        }else if (width <= PAPER_LEGAL_LENGTH - (MARGIN_DEFAULT * 2)){
            design.setOrientation(OrientationEnum.LANDSCAPE);
            design.setPageWidth(PAPER_LEGAL_LENGTH);
            design.setPageHeight(PAPER_LEGAL_WIDTH);
            design.setColumnWidth(PAPER_LEGAL_LENGTH - (MARGIN_DEFAULT * 2));
        } else{
            // Report won't fit on a letter (8.5 x 11) or legal (8.5 x 14) paper,
            // so just set the width and let the user deal with it
            design.setOrientation(OrientationEnum.LANDSCAPE);
            design.setPageWidth(width + (MARGIN_DEFAULT * 2));
            design.setPageHeight(PAPER_LEGAL_WIDTH);
            design.setColumnWidth(width - (MARGIN_DEFAULT * 2));
        }

        // Make columns stretch to fit the whole page. Take any extra width and
        // add it as padding to each column
        if (width < design.getColumnWidth()){
            for(ReportColumnConfig col : columns) {
                col.setWidth(col.getWidth() + ((design.getColumnWidth() - width) / columns.size()));
            }
        }

        // Adjust the black footer bar so that it stretches across the whole page
        if(design.getPageFooter().getElementByKey(ELEMENT_FOOTER_BAR)!=null) {
            design.getPageFooter().getElementByKey(ELEMENT_FOOTER_BAR).setWidth(design.getColumnWidth());
        }

        // Adjust the 'Page 1 of 10' text so it is set correctly in the new page size
        if(design.getPageFooter().getElementByKey(ELEMENT_PAGE_CURRENT)!=null) {
            design.getPageFooter().getElementByKey(ELEMENT_PAGE_CURRENT).setX(design.getColumnWidth() - 150);
        }
        
        if(design.getPageFooter().getElementByKey(ELEMENT_PAGE_TOTAL)!=null) {
            design.getPageFooter().getElementByKey(ELEMENT_PAGE_TOTAL).setX(design.getColumnWidth() - 47);
        }
    }

    /**
     * For the given column, create a report field. Uses the column's field name and
     * value class properties.
     *
     * @param col The column
     * @return Resulting design field
     */
    private JRDesignField getColumnField(ReportColumnConfig col){
        JRDesignField field = new JRDesignField();
        field.setName(col.getField());
        try {
            Class clazz = col.getValueClass() != null ? Class.forName(col.getValueClass()) : String.class;
            field.setValueClass(clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot find class report column class '"+col.getValueClass()+"'", e);
        }
        return field;
    }

    /**
     * Gets column header object for the given ReportColumn.
     *
     * @param col The ReportColumnConfig
     * @param currentXPosition X position on the page for this column
     * @return Head object for the column.
     */
    private JRDesignStaticText getColumnHead(ReportColumnConfig col, int currentXPosition){

        JRDesignStaticText colHead = new JRDesignStaticText();
        colHead.setText(col.getHeader());
        colHead.setWidth(col.getWidth());
        colHead.setHeight(design.getColumnHeader().getHeight());
        colHead.setY(0);
        colHead.setX(currentXPosition);

        if(design.getStylesMap().get(getHeaderStyle())!=null)
            colHead.setStyle((JRDesignStyle)design.getStylesMap().get(getHeaderStyle()));
        return colHead;
    }

    /**
     * Gets column detail object for the given ReportColumn.
     *
     * @param col The ReportColumn
     * @param currentXPosition X position on the page for this column
     * @return  column detail object.
     */
    @SuppressWarnings("deprecation")
    private JRDesignElement getColumnDetail(ReportColumnConfig col, int currentXPosition){

        int height = 0;
        if (design.getDetailSection().getBands() != null){
            for(JRBand band : design.getDetailSection().getBands()){
                height += band.getHeight();
            }
        }

        JRDesignElement detailElement = null;


        if (col.getValueClass() != null && col.getValueClass().equals("java.util.List")){
            JRDesignSubreport subreport = new JRDesignSubreport(null);
            subreport.setStretchType(StretchTypeEnum.RELATIVE_TO_BAND_HEIGHT);
            subreport.setX(currentXPosition);
            subreport.setY(0);
            subreport.setWidth(col.getWidth());
            subreport.setPrintWhenDetailOverflows(true);
            subreport.setExpression(new JRDesignExpression("$P{GenericListSubReport}"));
            subreport.setDataSourceExpression(new JRDesignExpression("(JRRewindableDataSource)new JRBeanCollectionDataSource((java.util.List)$F{"+col.getField()+"})"));
            if(design.getStylesMap().get(getDetailStyle()) !=null)
                subreport.setStyle(design.getStylesMap().get(getDetailStyle()));

            detailElement = subreport;

        } else {
            JRDesignTextField colDetail = new JRDesignTextField();
            colDetail.setExpression(col.getJRDesignExpression());
            colDetail.setBlankWhenNull(true);
            colDetail.setWidth(col.getWidth());
            colDetail.setHeight(height);
            colDetail.setY(0);
            colDetail.setX(currentXPosition);
            colDetail.setStretchType(StretchTypeEnum.RELATIVE_TO_BAND_HEIGHT);
            colDetail.setStretchWithOverflow(true);
            if(design.getStylesMap().get(getDetailStyle()) !=null)
                colDetail.setStyle(design.getStylesMap().get(getDetailStyle()));

            detailElement = colDetail;
        }

        return detailElement;
    }

    /**
     * Creates compiled JasperReport with this report's design and columns.
     * Takes the initial JRDesign and adds the columns to it, resizing the
     * page to the correct width so all columns fit.
     *
     * @return Compiled report
     * @throws JRException
     */
    public JasperReport compile() throws JRException{

        return JasperCompileManager.compileReport(getDesign());
    }
    
    /**
     * Configures and returns the completed design
     *
     * @return Compiled report
     * @throws JRException
     */
    @SuppressWarnings("deprecation")
    public JasperDesign getDesign() throws JRException{

        resizePage();

        JRSection section = design.getDetailSection();
        JRDesignBand band = (JRDesignBand)section.getBands()[0];

        int currentXPosition = 0;
        for(ReportColumnConfig col : columns){

            design.addField(getColumnField(col));
            ((JRDesignBand)design.getColumnHeader()).addElement(getColumnHead(col, currentXPosition));
            band.addElement(getColumnDetail(col, currentXPosition));
            currentXPosition += col.getWidth();
        }

        return design;
    }

    /**
     * @return Style used by the report detail
     */
    public String getDetailStyle() {
        return detailStyle;
    }

    /**
     *
     * @param detailStyle Style used by the report detail
     */
    public void setDetailStyle(String detailStyle) {
        this.detailStyle = detailStyle;
    }

    /**
     * @return Style used by the report column header
     */
    public String getHeaderStyle() {
        return headerStyle;
    }

    /**
     * @param headerStyle Style used by the report column header
     */
    public void setHeaderStyle(String headerStyle) {
        this.headerStyle = headerStyle;
    }

}