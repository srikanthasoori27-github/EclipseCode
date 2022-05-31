/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExpression;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JasperDesign;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.ApplicationScorecard;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.ScoreBandConfig;
import sailpoint.object.ScoreConfig;
import sailpoint.reporting.DynamicColumnReport;
import sailpoint.reporting.JasperExecutor;
import sailpoint.reporting.ReportingUtil;
import sailpoint.reporting.UncorrelatedIdentitiesReport;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author peter.holcomb
 *
 */
public class UncorrelatedIdentityDataSource extends SailPointDataSource<Application> {

	private static final String DESIGN_HEADER_STYLE = "spBlue";
	private static final String DESIGN_DETAIL_STYLE = "bandedText";

	/**
	 * @param context
	 * @param filters
	 */
	int counter;
	Attributes args;
	JasperReport report;
	boolean isDetailed;
	boolean isCsv;
    
    /** We only want to print the header once in the subreport that's being created.  
     * Since it's a sub-report, all attempts to use variables such as PAGE_NUMBER don't seem
     * to work, so we'll have to keep our own internal flag.  PH
     */
    boolean headerPrinted;
    
	List<Map<String,Object>> uncorrelatedIdentities;
	List<Map<String,Object>> summary;
	Float percentComplete;

	List<String> columns;

	private static final Log log = LogFactory.getLog(UncorrelatedIdentityDataSource.class);

	public UncorrelatedIdentityDataSource( List<Filter> filters, Locale locale, TimeZone timezone) {
		super(filters, locale, timezone);
	}

	public UncorrelatedIdentityDataSource(List<Filter> filters, Locale locale, TimeZone timezone,Attributes args) {
		this(filters, locale, timezone);
		/** We need to dynamically ascertain whether this is a detailed or grid report since
		 * it will affect which columns we add to the sub report. 
		 */
		String reportType = ((Attributes)args).getString(JasperExecutor.OP_REPORT_TYPE);
		if ( reportType != null )
			if ( JasperExecutor.OP_DETAILED_REPORT.compareTo(reportType) == 0 ) 
				isDetailed = true;

		Boolean isCsvBool = ((Attributes)args).getBoolean(JasperExecutor.OP_IS_CSV);
		if(isCsvBool!=null) {
			isCsv = isCsvBool;
		}
		this.args = args;
		setScope(Application.class);
	}

	@Override
	public void internalPrepare() throws GeneralException {
		qo.setOrderBy("name");
		_objects = getContext().search(Application.class, qo);
		summary = buildSummary();		
	}

	/* (non-Javadoc)
	 * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
	 */
	public Object getFieldValue(JRField jrField) throws JRException {
		Object returnValue = null;
		String fieldName = jrField.getName();
		if(fieldName.equals("applicationName")) {
			returnValue = _object.getName();
		}
		else if(fieldName.equals("uncorrelatedIdentitiesApplicationReport")) {
			returnValue = report;
		}
		else if(fieldName.equals("uncorrelatedIdentities")) {
			returnValue = getUncorrelatedIdentities();
		}
		else if(fieldName.equals("summary")) {
			returnValue = summary;
		}
		else if(fieldName.equals("percentComplete")) {
			returnValue = percentComplete;
		}
		return returnValue;

	}

	/* (non-Javadoc)
	 * @see net.sf.jasperreports.engine.JRDataSource#next()
	 */
	public boolean internalNext() throws JRException {
		boolean hasMore = false;
		if ( _objects != null ) {
			hasMore = _objects.hasNext();
			if ( hasMore ) {
				_object = _objects.next();
			} else {
				_object = null;
			}

			if ( _object != null ) {
				buildSubReport();
				updateProgress("Application", _object.getName());
			}
		}
		return hasMore;
	}

	@SuppressWarnings("unchecked")
	public void buildSubReport() {
		/** This is a bit of work...we have to do the following:
		 * a) Get the application schemas from the database so that we can build their columns
		 * b) dynamically create the columns
		 * c) send it back to the report that's calling it */
		Iterator<Object[]> keys = null;
		try {

			JasperDesign applicationReportDesign = 
				ReportingUtil.loadDesignFromDb(UncorrelatedIdentitiesReport.APP_SUB_REPORT, getContext(), true);

			QueryOptions qo = new QueryOptions();
			qo.add(Filter.ge("schemas.attributes.correlationKey",1));
			qo.add(Filter.eq("id", _object.getId()));
			qo.setDistinct(true);
			List<String> props = new ArrayList<String>();
			props.add("schemas.attributes.name");
			props.add("schemas.attributes.correlationKey");
			keys = getContext().search(Application.class, qo, props);

			columns = new ArrayList<String>();
			if(!isDetailed) {
				columns.add("applicationName");
			}
            columns.add("username");
			if(keys!=null && keys.hasNext()) {
				while(keys.hasNext()){
					Object[] next = keys.next();
					String col = (String)next[0];
					columns.add(col);
				}
			} else {
				log.debug("No correlation keys found");               
			}
			columns.add(1, "firstName");
			columns.add(2, "lastName");

			// If this is CSV, only show the header if this is the first application
			// with some uncorrelated identities. 
			if(isCsv && (headerPrinted || !areUncorrelatedIdentities(_object))) {
				JRDesignExpression expression = new JRDesignExpression();
				expression.setValueClass(java.lang.Boolean.class);
				expression.setText("new java.lang.Boolean(false)");
				JRDesignBand columnHeaderBand = (JRDesignBand)applicationReportDesign.getColumnHeader();
				columnHeaderBand.setPrintWhenExpression((JRExpression)expression);
			} else {
                JRDesignExpression expression = new JRDesignExpression();
                expression.setValueClass(java.lang.Boolean.class);
                expression.setText("new java.lang.Boolean($V{PAGE_NUMBER}.intValue()<2)");
                JRDesignBand columnHeaderBand = (JRDesignBand)applicationReportDesign.getColumnHeader();
                columnHeaderBand.setPrintWhenExpression((JRExpression)expression);
                headerPrinted = true;
            }

			DynamicColumnReport design = new DynamicColumnReport(applicationReportDesign);
			design.setHeaderStyle(DESIGN_HEADER_STYLE);
			design.setDetailStyle(DESIGN_DETAIL_STYLE);

            if(columns!=null) {
				for(String col : columns) {

                    Message colMsg =  null;
                    if ("username".equals(col))
                        colMsg = new Message(MessageKeys.REPT_UNCORRELATED_IDS_GRID_USERNAME);
                    else if ("firstName".equals(col))
                        colMsg = new Message(MessageKeys.REPT_UNCORRELATED_IDS_GRID_FIRSTNAME);
                    else if ("lastName".equals(col))
                        colMsg = new Message(MessageKeys.REPT_UNCORRELATED_IDS_GRID_LASTNAME);
                    else if ("applicationName".equals(col))
                        colMsg = new Message(MessageKeys.REPT_UNCORRELATED_IDS_GRID_APPNAME);

                    String colName = col;
                    if (colMsg != null)
                        colName = colMsg.getLocalizedMessage(getLocale(), null);

                    design.addColumn(col, colName, String.class);
				}
			}

			report = design.compile();
		} catch (GeneralException ge) {
			log.error("GeneralException caught while executing search: " + ge.getMessage());
		} catch (JRException jre) {
			log.error("GeneralException caught while executing search: " + jre.getMessage());
		}
	}


	private QueryOptions getUncorrelatedIdentitiesQueryOptions(Application application) {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.and(Filter.eq("application.id", application.getId()),
                Filter.eq("identity.correlated", false)));
        return qo;
	}
	
	private boolean areUncorrelatedIdentities(Application application) 
	    throws GeneralException {
	    return (getContext().countObjects(Link.class, getUncorrelatedIdentitiesQueryOptions(application)) > 0);
	}
	
	public List<Map<String,Object>> getUncorrelatedIdentities() {
		if(columns!=null) {
			try {
				uncorrelatedIdentities = new ArrayList<Map<String, Object>>();
				
				/** To prevent duplicate identities, keep a set of ids of idents we've seen **/
				Set<String> seenIds = new HashSet<String>();
				Iterator<Link> it = getContext().search(Link.class, getUncorrelatedIdentitiesQueryOptions(_object));
				while (it.hasNext()) {
					Link link = (Link)it.next();
					Identity identity = link.getIdentity();
					if(!seenIds.contains(identity.getId())) {
					
    					Map<String, Object> map = new HashMap<String, Object>();
    					if(columns!=null && !columns.isEmpty()) {
    						for(String column : columns) {
    							Object attr = link.getAttribute(column);
    							map.put(column, attr != null ? attr.toString() : null);
    						}
    					}
    
    					if(!isDetailed) {
    						map.put("applicationName", _object.getName());
    					}
    					map.put("username", identity.getName());
    					map.put("firstName", identity.getFirstname());
    					map.put("lastName", identity.getLastname());
    
    					uncorrelatedIdentities.add(map);
    					seenIds.add(identity.getId());
					}
					getContext().decache(link);
				}
			}catch (GeneralException ge) {
			    if (log.isErrorEnabled())
			        log.error("Unable to load uncorrelated identities. Exception: " + ge.getMessage(), ge);

				uncorrelatedIdentities = null;
			}
		}
		return uncorrelatedIdentities;
	}

	/** Builds the summary subreport fields by calculating the totals for each application **/
	public List<Map<String, Object>> buildSummary() {
		summary = new ArrayList<Map<String,Object>>();
		try {
			int totalCorrelated = 0;
			int totalTotal = 0;
			int totalUncorrelated = 0;

			Iterator<Application> uncorrelatedAppIter = getContext().search(Application.class, qo);
			while(uncorrelatedAppIter.hasNext()) {
				Map<String, Object> appSummary = new HashMap<String, Object>();
				Application next = uncorrelatedAppIter.next();

				appSummary.put("appName", next.getName());

				/** Count the identities with links to this app **/
				QueryOptions qo = new QueryOptions();
				qo.add(Filter.eq("links.application.id", next.getId()));
				qo.setDistinct(true);
				int totalIdents = getContext().countObjects(Identity.class, qo);
				totalTotal = totalTotal + totalIdents;

				qo.add(Filter.eq("correlated", false));
				qo.setDistinct(true);
				int uncorrelatedIdents = getContext().countObjects(Identity.class,qo);

				totalUncorrelated = totalUncorrelated + uncorrelatedIdents;

				int correlatedIdents = totalIdents - uncorrelatedIdents;
				totalCorrelated = totalCorrelated + correlatedIdents;

				float percentCorrelated = 100;
				if(totalIdents>0)
					percentCorrelated = Util.getPercentage(correlatedIdents, totalIdents);

				appSummary.put("totalIdents", new Integer(totalIdents));
				appSummary.put("uncorrelatedIdents", new Integer(uncorrelatedIdents));
				appSummary.put("correlatedIdents", new Integer(correlatedIdents));
				appSummary.put("percentCorrelated", new Float(percentCorrelated/100));

				/** Get the application scorecard object for this application and check its score
				 * against the score config. **/
				getRiskProfile(next.getId(), appSummary);

				log.debug("Application: [" + next.getName() + "]\tTotal: [" + totalIdents + "] Uncorrelated: [ " + uncorrelatedIdents +
						"] Correlated [" + correlatedIdents + "] Percent: " + percentCorrelated + "]");

				summary.add(appSummary);
			}

			/** I'm going to return this as a field since I've given up trying to calculate this
			 * as a variable in jasper **/
			percentComplete = (float)Util.getPercentage(totalCorrelated, totalTotal)/100;


		} catch (GeneralException ge) {
			log.error("Unable to build summary report. Exception: " + ge.getMessage());
		}

		return summary;
	}


	private void getRiskProfile(String id, Map<String,Object> map) throws GeneralException{
		String riskProfile = null;
		String riskColor = null;

		if(id!=null) {
			Application app = getContext().getObjectById(Application.class, id);
			if(app!=null && app.getScorecard()!=null) {
				ApplicationScorecard scorecard = app.getScorecard();
				int score = scorecard.getCompositeScore();
				ScoreBandConfig thisConfig = null;

				ScoreConfig scoreConfig = (ScoreConfig)getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
				List<ScoreBandConfig> configs = scoreConfig.getBands();
				for(ScoreBandConfig config : configs) {
					if(score >= config.getLowerBound() && score <= config.getUpperBound()) {
						thisConfig = config;
						break;
					}
				}

				if(thisConfig!=null) {
					riskProfile = thisConfig.getLabel();
					riskColor = thisConfig.getColor();
				}
			}
		}

		map.put("riskProfile", riskProfile);
		map.put("riskColor", riskColor);
	}

}
