/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.reporting.datasource.AccountReportDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * 
 * A ExtendedAccountAttributeReport class, used to generate a report for application
 * accounts queried by the defined extended account attributes.
 * 
 */
public class AccountReport extends JasperExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(AccountReport.class);

    private static final String GRID_REPORT = "ExtendedAccountAttributeReport";
    private static String TEMPLATE_FIELD = "extendedTemplateValue";
    private static String TEMPLATE_COLUMN = "ExtendedHeaderTemplate";
    private static String TEMPLATE_TEXT = "ExtendedTemplateTextField";

    private static String LINK_EXTERN_NAME = "LinkExternalAttribute.attributeName";
    private static String LINK_EXTERN_VALUE = "LinkExternalAttribute.value";
    private static String LINK_EXTERN_ID = "LinkExternalAttribute.objectId";


    @Override
    public String getJasperClass() {
        return GRID_REPORT;
    }
    
    public TopLevelDataSource getDataSource()
        throws GeneralException {

        Attributes<String,Object> args = getInputs();
        List<Filter> filters = buildFilters(args);
        return new AccountReportDataSource(filters, getLocale(), getTimeZone(), args);
    }

    protected List<Filter> buildFilters(Attributes<String,Object> inputs) throws GeneralException {
        List<Filter> filters = new ArrayList<Filter>();

        SailPointContext ctx = getContext();

        // 
        // Application Filter
        // 
        List<Application> apps = ObjectUtil.getObjects(ctx, Application.class, inputs.get(FILTER_APPLICATIONS));
        if ( (apps != null ) && ( apps.size() > 0 ) ) {
            filters.add(Filter.in("application", apps));
        }

        // 
        // Extended Attribute Filter
        // 

        ObjectConfig config = Link.getObjectConfig();
        List<ObjectAttribute> attrs = null;
        if ( config != null ) {
            attrs = config.getObjectAttributes();
        }
        if ( attrs == null ) {
            attrs = new ArrayList<ObjectAttribute>();
        }
        for ( ObjectAttribute attr : attrs ) {
            String name = attr.getName();    
            Object o = inputs.get(name);
            if ( o != null ) {
                if ( attr.isMulti() ) {
                    String operator = inputs.getString(OP_OPERATOR_PREFIX+name);
                    if ( operator != null ) {
                        List<String> vals = Util.delimToList("\n", o.toString(), true);
                        if ( ( vals != null ) && ( vals.size() > 0 ) ) {
                            if ( "OR".compareTo(operator) == 0  ) {
                                // NOTE: this case also requires a distinct query
                                Filter filter = Filter.and(Filter.join("id", LINK_EXTERN_ID),
                                                           Filter.eq(LINK_EXTERN_NAME, name),
                                                           Filter.in(LINK_EXTERN_VALUE, vals));
                                filters.add(filter); 
                            }
                            if ( "AND".compareTo(operator) == 0  ) {
                                List<Filter> ccFilters = new ArrayList<Filter>();
                                for ( int i=0; i<vals.size(); i++ ) {
                                    Filter filter = Filter.and(Filter.join("id", LINK_EXTERN_ID),
                                                              Filter.eq(LINK_EXTERN_NAME, name),
                                                              Filter.eq(LINK_EXTERN_VALUE, vals.get(i)));
                                    ccFilters.add(filter); 
                                }
                                if ( ( ccFilters != null ) && ( ccFilters.size() > 0 ) ) {
                                    filters.add(Filter.collectionCondition("LinkExternalAttribute", Filter.and(ccFilters)));
                                }
                            }
                        }
                    }
                } 
                else if ( o instanceof String ) {
                    filters.add(Filter.eq(name, o.toString()));
                } 
                else if ( o instanceof Date ) {
                    Date date = (Date)o;
                    String operator = inputs.getString(OP_OPERATOR_PREFIX+name);
                    if ( operator != null ) {                          
                        Filter.LogicalOperation op = Filter.LogicalOperation.valueOf(operator);
                        if ( op != null ) {
                            LeafFilter filter = new LeafFilter(op,name,date);
                            filters.add(filter);
                        }                           
                    }                         
                }
            }
        }
        return filters;
    }

    public JasperDesign updateDesign(JasperDesign design)
       throws GeneralException {

        AccountReportCustomizer customizer = new AccountReportCustomizer(design);
        return customizer.getDesign();
    }

    /**
     * Class in charge of modifying the design of the base report object to
     * include the configured account attributes.
     */
    public class AccountReportCustomizer {

        //////////////////////////////////////////////////////////////////////
        //
        // Fields
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * The design object, so we can modify the report layout.
         */
        JasperDesign _design;

        /**
         * List of extended attributes for Links.
         */
        List<ObjectAttribute> _extendedAttributes;
        
        /** 
         * Display names of the attributes, used for building the 
         * custom column names.
         */
        List<String> _displayNames;

        /** 
         * Names of the attributes, used for building the expression
         * and text fields.
         */
        List<String> _names;

        //////////////////////////////////////////////////////////////////////
        //
        //  Constructor
        //
        //////////////////////////////////////////////////////////////////////
   
        public AccountReportCustomizer(JasperDesign design) {
            _design  = design;          
            ObjectConfig config = Link.getObjectConfig();
            if ( config != null ) {
                _extendedAttributes = config.getObjectAttributes();
            }
            _displayNames = new ArrayList<String>();
            _names = new ArrayList<String>();
            if ( _extendedAttributes != null ) {
                for ( ObjectAttribute attr : _extendedAttributes ) {
                    _names.add(attr.getName());
                    _displayNames.add(attr.getDisplayName());
                }
            }
        }
 
        public JasperDesign getDesign() throws GeneralException {
            addExtendedAttributes();
            return _design;
        }

        private void addExtendedAttributes() throws GeneralException {
            addExtendedAttributesToColumnHeader();
            addExtendedAttributesToDetail();
        }

        private List<String> getAttributeDisplayNames() {
            return _displayNames;
        }

        private List<String> getAttributeNames() {
            return _names;
        }

        @SuppressWarnings("unchecked")
        private void addExtendedAttributesToColumnHeader() throws GeneralException {
            JRDesignBand band = (JRDesignBand)_design.getColumnHeader();

            JRDesignTextField baseElement = (JRDesignTextField)band.getElementByKey(TEMPLATE_COLUMN);
            if ( baseElement != null ) {
                int x = baseElement.getX();
                band.removeElement(baseElement);   
                List<String> names = getAttributeDisplayNames();
                for ( String name : names ) {
                    JRDesignTextField newElement = (JRDesignTextField)baseElement.clone();
                    newElement.setKey(name);
                    JRDesignExpression expression = new JRDesignExpression();
                    expression.setValueClass(java.lang.String.class);
                    expression.setText("$R{"+name+"}");
                    newElement.setExpression(expression);
                    newElement.setX(x); 
                    x += newElement.getWidth();
                    band.addElement(newElement);
                }
            }
            _design.setColumnHeader(band);
        }

        @SuppressWarnings("unchecked")
        private void addExtendedAttributesToDetail() throws GeneralException {
            JRDesignBand band = (JRDesignBand)_design.getDetailSection().getBands()[0];

            JRDesignTextField baseElement = (JRDesignTextField)band.getElementByKey(TEMPLATE_TEXT);
            if ( baseElement != null ) {
                int x = baseElement.getX();
                band.removeElement(baseElement);   
                List<String> names = getAttributeNames();
                for ( String name : names ) {
                    JRDesignTextField newElement = (JRDesignTextField)baseElement.clone();
                    newElement.setKey(name);

                    JRDesignExpression expression = new JRDesignExpression();
                    expression.setValueClass(java.lang.String.class);
                    expression.setText("$F{"+name+"}");                
                    newElement.setExpression(expression);
                    newElement.setX(x); 
                    x += newElement.getWidth();

                    JRDesignField field = new JRDesignField();
                    field.setName(name);
                    field.setValueClass(String.class);
                    try {
                        _design.addField(field);
                    } catch(Exception e) {
                        throw new GeneralException("Unable to add field " + name + " to design.");
                    }
                    band.addElement(newElement);
                }
            }
            _design.removeField(TEMPLATE_FIELD);

            if ( log.isDebugEnabled() ) {
                log.debug("\n" + JRXmlWriter.writeReport(_design, "UTF-8") + "\n");
            }
        }
    }
}
