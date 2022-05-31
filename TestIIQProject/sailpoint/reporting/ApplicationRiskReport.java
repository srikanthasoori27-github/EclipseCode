/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JasperDesign;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;


public class ApplicationRiskReport extends ApplicationReport {
	
	private static Log log = LogFactory.getLog(ApplicationRiskReport.class);
	
	private static final String GRID_REPORT = "ApplicationRiskGridReport";
	private static final String DESIGN_HEADER_STYLE = "spBlue";
	private static final String DESIGN_DETAIL_STYLE = "bandedText";

    @Override	
    public String getJasperClass() {
        return GRID_REPORT;
    }
	
	/**
     * We have to build the report columns dynamically based on how the application score
     * card is configured.
     */
    public JasperDesign updateDesign(JasperDesign design) 
        throws GeneralException {
        //nothing by default
    	
    	DynamicColumnReport dynamicReport = new DynamicColumnReport(design);
    	dynamicReport.setHeaderStyle(DESIGN_HEADER_STYLE);
    	dynamicReport.setDetailStyle(DESIGN_DETAIL_STYLE);

    	List<String> columns = new ArrayList<String>();
    	
    	/** Add "Application" and "Composite Score" columns **/
    	dynamicReport.addColumn("name", "Application", String.class);    	
    	dynamicReport.addColumn("scorecard.compositeScore", "Composite Score", Integer.class);
    	/** Load the score components and make them into columns **/
    	
    	ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, ScoreConfig.OBJ_NAME);
        List<ScoreDefinition> scores = config.getApplicationScores();
        if (scores != null) {
            for (ScoreDefinition score : scores) {
                // unlike identity scores these aren't explicitly
                // marked since we don't have the raw/compensated dichotomy
                if (!score.isDisabled()  && score.getName()!=null) {
                    String name = Internationalizer.getMessage(score.getDisplayableName(), getLocale());
                    dynamicReport.addColumn("score."+score.getName(),
                            name != null ? name : score.getDisplayableName(), Integer.class);
                }
            }
        }
		
    	JasperDesign newDesign = null;
    	try {
    		newDesign = dynamicReport.getDesign();
    	} catch (JRException jre) {
    		log.warn("Unable to get Report Design: " + jre.getMessage());
    	}
		return newDesign;
    }
}
