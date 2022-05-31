/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.export;

import java.util.Map;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JasperPrint;

/**
 * Given the report and the parameters comming from the exporter
 * the job of this class s to compute the start and end page 
 * index.
 * NOTE:
 *  It was initally implemented in net.sf.jasperreports.engine.JRAbstractExporter
 *  and pushed here so it could be shared across the exporters.
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a
 */
public class PageRange {

    /**
     * Computed start page index.
     */
    int _startPageIndex;

    /**
     * Computed end page index.
     */
    int _endPageIndex;

    /**
     * The print object which comes from the PageHandler.
     */
    JasperPrint _print;

    /**
     * Parameters that were passed into the Exporter.  
     */
    Map _parameters;

    /**
     * PageHandler which gives us all of the reporting page information
     * and JRPage objects.
     */
    PageHandler _handler;

    public PageRange(Map parameters) {
        _handler = (PageHandler)parameters.get(SailPointExportParameter.PAGE_HANDLER);
        if ( _handler != null )
           _print = _handler.getPrint();
        _parameters = parameters;
    }

    /**
     *  This code is called by all of our custom exporters.  
     *  It was initally implemented in net.sf.jasperreports.engine.JRAbstractExporter
     *  and pushed here so it could be shared across the exporters.
     */  
    public void compute() throws JRException {

        if ( _handler == null ) 
            throw new JRException("Problem computing start/end page index because the PageHandler was null.");

        int lastPageIndex = -1;

        int pageCount = _handler.pageCount();
        if ( pageCount > 0 ) {
            lastPageIndex = pageCount - 1;
        }

        Integer start = (Integer)_parameters.get(JRExporterParameter.START_PAGE_INDEX);
        if (start == null) {
            _startPageIndex = 0;
        } else {
            _startPageIndex = start.intValue();
            if ( _startPageIndex < 0 || _startPageIndex > lastPageIndex ) {
                throw new JRException("Start page index out of range : " + _startPageIndex + " of " + lastPageIndex);
            }
        }

        Integer end = (Integer)_parameters.get(JRExporterParameter.END_PAGE_INDEX);
        if (end == null) {
            _endPageIndex = lastPageIndex;
        } else {
            _endPageIndex = end.intValue();
            if ( _endPageIndex < _startPageIndex || _endPageIndex > lastPageIndex) {
                throw new JRException("End page index out of range : " + _endPageIndex + " (" + _startPageIndex + " : " + lastPageIndex + ")");
            }
        }

        Integer index = (Integer)_parameters.get(JRExporterParameter.PAGE_INDEX);
        if (index != null) {
            int pageIndex = index.intValue();
            if (pageIndex < 0 || pageIndex > lastPageIndex) {
                throw new JRException("Page index out of range : " + pageIndex + " of " + lastPageIndex);
            }
            _startPageIndex = pageIndex;
            _endPageIndex = pageIndex;
        }

    }

    public int getEndPageIndex() {
       return _endPageIndex;
    }

    public int getStartPageIndex() {
       return _startPageIndex;
    }
}
