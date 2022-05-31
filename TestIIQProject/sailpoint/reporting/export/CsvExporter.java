/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.export;

import java.io.IOException;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.CutsInfo;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRExporterGridCell;
import net.sf.jasperreports.engine.export.JRGridLayout;
import net.sf.jasperreports.engine.util.JRStyledText;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.reporting.ReportingUtil;
import sailpoint.tools.Rfc4180CsvBuilder;
import sailpoint.web.util.WebUtil;

/**
 * JRCsvExporter implementation that localizes text.
 * 
 * In addition to localizing text this exporter deals with LARGE
 * reports by using a PageHandler to compute the number of pages
 * and to fetch each page from a report.
 * 
 * In the case of large reports some pages may be stored in separate 
 * database objects (JasperPageBuckets). The PageHandler is the 
 * coupling between the JasperPrint object and its list of pages.
 *
 * For smaller reports that don't exceed the configured page size 
 * the entire report will be stored on a a single database object.
 *
 * The PageHandler object is passed in to the exporter via the parameter
 * map. It is set on the parameter map by the JasperRenderer 
 * and is required for this exporter to work property.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class CsvExporter extends JRCsvExporter {
    
    private static Log log = LogFactory.getLog(CsvExporter.class);

    private Locale locale;
    private TimeZone timezone;
    
    /** When we first run the report, we make a copy of the header and store it so that
     * later when we are trying to prevent the header from being printed, we can compare that
     * row against it.
     */
    private String header;
    
    /** The exporter behaves differently depending on if this is a completed jasper report or whether the 
     * exporter is being called directly.
     */
    private boolean isFreshExport;

    public CsvExporter(Locale locale, TimeZone timezone) {
        this.locale = locale;
        this.timezone = timezone;
    }
    
    public CsvExporter(Locale locale, TimeZone timezone, boolean isFreshExport) {
        this.locale = locale;
        this.timezone = timezone;
        this.isFreshExport = isFreshExport;
    }

    protected String prepareText(String source) {
        // IIQMAG-1559 IIQETN-4954 prevent csv text from excel formula injection
        source = Rfc4180CsvBuilder.escapeFormulaInjection(source);
        return WebUtil.stripHTML(super.prepareText(ExportUtil.localize(source, locale, timezone)), false);
    }

    @Override
    protected void exportReportToWriter() throws JRException, IOException
    {
        // djs: customization
        PageHandler handler = ReportingUtil.getPageHandler(parameters);
        for(int reportIndex = 0; reportIndex < jasperPrintList.size(); reportIndex++)
        {
            jasperPrint = (JasperPrint)jasperPrintList.get(reportIndex);
            // djs: customization
            int pageCount = handler.pageCount();
            if ( pageCount > 0 ) 
            {
                if (isModeBatch)
                {
                    startPageIndex = 0;
                    endPageIndex = pageCount - 1;
                }

                for(int i = startPageIndex; i <= endPageIndex; i++)
                {
                    if (Thread.currentThread().isInterrupted())
                    {
                        throw new JRException("Current thread interrupted.");
                    }
                    // djs: customization
                    JRPrintPage page = handler.getPage(i);
                    /*   */
                    exportPage(page, i);
                }
            }
        }                
        writer.flush();
    }
  
    protected void exportPage(JRPrintPage page, int pageNum) throws IOException
    {
        JRGridLayout layout = 
            new JRGridLayout(
                nature,
                page.getElements(), 
                jasperPrint.getPageWidth(), 
                jasperPrint.getPageHeight(), 
                globalOffsetX, 
                globalOffsetY,
                null //address
                );
        
        JRExporterGridCell[][] grid = layout.getGrid();

        CutsInfo xCuts = layout.getXCuts();
        CutsInfo yCuts = layout.getYCuts();

        StringBuffer rowbuffer = null;
        
        JRPrintElement element = null;
        String text = null;
        boolean isFirstColumn = true;
        for(int y = 0; y < grid.length; y++)
        {
            rowbuffer = new StringBuffer();
            
            if (yCuts.isCutNotEmpty(y))
            {
                isFirstColumn = true;
                for(int x = 0; x < grid[y].length; x++)
                {
                    if(grid[y][x].getWrapper() != null)
                    {
                        element = grid[y][x].getWrapper().getElement();
    
                        if (element instanceof JRPrintText)
                        {
                            JRStyledText styledText = getStyledText((JRPrintText)element);
                            if (styledText == null)
                            {
                                text = "";
                            }
                            else
                            {
                                text = styledText.getText();
                            }
                            
                            if (!isFirstColumn)
                            {
                                rowbuffer.append(delimiter);
                            }
                            rowbuffer.append(prepareText(text));
                            isFirstColumn = false;
                        }
                    }
                    else
                    {
                        if (xCuts.isCutNotEmpty(x))
                        {
                            if (!isFirstColumn)
                            {
                                rowbuffer.append(delimiter);
                            }
                            isFirstColumn = false;
                        }
                    }
                }
               
                if ( !isFreshExport) { 
                    if(y==1){
                        String currentRow = rowbuffer.toString();
                        // Store the header only if we are on the first row of the first page
                        if(pageNum==0){
                            header = currentRow;
                        } else if(header!=null && currentRow.equals(header)) {
                            continue;
                        }
                    }
                }
                
                if (rowbuffer.length() > 0 && !isEmpty(rowbuffer))
                {
                    //log.warn("Rowbuffer: " + rowbuffer.toString());
                    writer.write(rowbuffer.toString());
                    writer.write(recordDelimiter);
                }
            }
        }
        
        if (progressMonitor != null)
        {
            progressMonitor.afterPageExport();
        }
    }

    /** Determines whether an output row is all delimeters**/
    private boolean isEmpty(StringBuffer rowBuffer) {
        for(int i=0; i<rowBuffer.length(); i++) {
            char x = rowBuffer.charAt(i);
            if(!Character.toString(x).equals(delimiter)) 
                return false;
        }
        return true;
    }
    
    /**
     * This method was handled by the net.sf.jasperreports.engine.JRAbstractExporter
     * class but since it deals with the numberof pages we have to override this 
     * in each of our custom exporters.
     */
    @Override
    protected void setPageRange() throws JRException {
        PageRange ranger = new PageRange(parameters);
        ranger.compute();
        startPageIndex = ranger.getStartPageIndex();
        endPageIndex = ranger.getEndPageIndex();
    }
    
}
