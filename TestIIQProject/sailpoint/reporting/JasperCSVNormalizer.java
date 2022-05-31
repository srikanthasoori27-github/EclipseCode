/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRElementGroup;
import net.sf.jasperreports.engine.JRExpression;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.SplitTypeEnum;
import net.sf.jasperreports.engine.util.JRElementsVisitor;
import net.sf.jasperreports.engine.util.JRVisitorSupport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;

/** 
 *  A class we can use in the UI to render Jasper objects. Serves 
 *  two purposes, wraps the Jasper objects so we don't have them 
 *  riddled all over the place and additionally used to decrease
 *  the Jasper knowledge necesary to render to the various formats.
 *  <p>
 *  Curently support for PDF, HTML, CSV.
 *  NOTE : might think of just having these mehods on JasperResult?
 *  I kinda like this separation.
 */
public class JasperCSVNormalizer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(JasperCSVNormalizer.class);

    /**
     * The design object, so we can modify the report layout for csv.
     */
    private JasperDesign _design;

    //////////////////////////////////////////////////////////////////////
    //
    //  Constructors
    //
    //////////////////////////////////////////////////////////////////////
   
    public JasperCSVNormalizer(JasperDesign design) {
        _design  = design;
    }
 
    public JasperReport getReport() throws GeneralException {
        normalize();
        JasperReport report = null;
        try {
            report = JasperCompileManager.compileReport(_design);
        } catch(Exception e) {
            throw new GeneralException(e);
        }
        
        return report;
    }

    private void normalize() {
        disableBandSplitting();
        removePageHeaderAndFooter();
        addExpressionToColumnHeader();
    }
    
    /**
     * Sets the split type to "prevent" which is the non-deprecated equivalent of
     * setting "isSplitAllowed" to "false". This is invoked for all bands to avoid rows
     * being split during CSV export.
     */
    private void disableBandSplitting() 
    {
        JRElementsVisitor.visitReport(_design, new JRVisitorSupport() {
            @Override
            public void visitElementGroup(JRElementGroup elementGroup)
            {
                if (elementGroup instanceof JRBand) {
                    JRBand band = (JRBand)elementGroup;
                    band.setSplitType(SplitTypeEnum.PREVENT);
                }
            }
        });
    }

    private void removePageHeaderAndFooter() {
        JRDesignBand footer = (JRDesignBand)_design.getPageFooter();
        removeElementsFromBand(footer);
        footer.setHeight(0);
        _design.setPageFooter(footer);

        JRDesignBand header = (JRDesignBand)_design.getPageHeader();
        header.setHeight(0);
        removeElementsFromBand(header);
        _design.setPageHeader(header);   
    }

    private void addExpressionToColumnHeader() {
        JRDesignExpression expression = new JRDesignExpression();
        expression.setValueClass(java.lang.Boolean.class);
        expression.setText("new java.lang.Boolean($V{PAGE_NUMBER}.intValue()==1)");

        JRDesignBand columnHeaderBand = (JRDesignBand)_design.getColumnHeader();
        columnHeaderBand.setPrintWhenExpression((JRExpression)expression);
    }

    @SuppressWarnings("unchecked")
    private void removeElementsFromBand(JRDesignBand band) {
        JRElement[] elements = band.getElements();
        if ( elements != null ) {
            for(JRElement el : elements){
                if (JRDesignElement.class.isAssignableFrom(el.getClass())){
                    band.removeElement((JRDesignElement)el);
                }
            }
        }
    }

 
}
