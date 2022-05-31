package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import net.sf.jasperreports.engine.design.JasperDesign;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class PrivilegedAccountReport extends AccountReport {

    public TopLevelDataSource getDataSource()
    throws GeneralException {

        Attributes<String,Object> args = getInputs();

        /** Make sure the user entered at least one attribute **/
        if(!checkRequiredInput(args)){
            throw new GeneralException("No privileged attribute specified.  Please specify a value for at least one attribute.");
        }

        return super.getDataSource();
    }

    protected List<Filter> buildFilters(Attributes<String,Object> inputs) throws GeneralException {
        List<Filter> filters = super.buildFilters(inputs);
        addEQFilter(filters, inputs, "managers", "identity.manager.id", null);
        addLikeFilter(filters, inputs, "lastname", "identity.lastname", null);
        addLikeFilter(filters, inputs, "firstname", "identity.firstname", null);
        addLikeFilter(filters, inputs, "email", "identity.email", null);
        addIdentityAttributes(filters, inputs);
        return filters;
    }

    /** Override the super -- we don't need to customize the report **/
    public JasperDesign updateDesign(JasperDesign design) throws GeneralException {
        return design;
    }

    /** In order for this report to find privileged users, it requires that the user
     * enter at least one attribute.  If there is no attribute, we throw.
     * @param inputs
     * @return
     */
    private boolean checkRequiredInput(Attributes<String,Object> inputs) {
        ObjectConfig config = Link.getObjectConfig();
        boolean isValid = false;
        List<ObjectAttribute> attrs = null;
        if ( config != null ) {
            attrs = config.getObjectAttributes();
        }
        if ( attrs == null ) {
            attrs = new ArrayList<ObjectAttribute>();
        }
        for ( ObjectAttribute attr : attrs ) {
            String name = attr.getName(); 
            String type = attr.getType();
            Object o = inputs.get(name);
            if ( o != null ) {
                /** If the input is a date, it will have a value by default so we need to check to see if they
                 * specified how to treat the date (equals, greater than, etc...).  If that is null, we
                 * haven't found a valid input
                 */
                if(type.equals("date")) {
                    Object o2 = _inputs.get("operator."+attr.getName());
                    if(o2==null)
                        continue;
                }
                isValid = true;
                break;
            }
        }

        return isValid;
    }

    private void addIdentityAttributes(List<Filter> filters, 
            Attributes<String,Object> inputs) {

        ObjectConfig config = Identity.getObjectConfig();
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
            if ( o == null ) continue;

            /** Handle Multi-valued Attributes **/
            if ( attr.isMulti() ) {
                String operator = inputs.getString(OP_OPERATOR_PREFIX+name);
                if ( operator != null ) {
                    List<String> vals = Util.delimToList("\n", o.toString(), true);
                    if ( ( vals != null ) && ( vals.size() > 0 ) ) {
                        if ( "OR".compareTo(operator) == 0  ) {
                            Filter filter = Filter.and(Filter.join("identity.id", UserReport.IDENTITY_EXTERN_ID),
                                    Filter.eq(UserReport.IDENTITY_EXTERN_NAME, name),
                                    Filter.in(UserReport.IDENTITY_EXTERN_VALUE, vals));
                            filters.add(filter); 
                        } else
                            if ( "AND".compareTo(operator) == 0  ) {
                                List<Filter> ccFilters = new ArrayList<Filter>();
                                for ( int i=0; i<vals.size(); i++ ) {
                                    Filter filter = Filter.and(Filter.join("identity.id", UserReport.IDENTITY_EXTERN_ID),
                                            Filter.eq(UserReport.IDENTITY_EXTERN_NAME, name),
                                            Filter.eq(UserReport.IDENTITY_EXTERN_VALUE, vals.get(i)));
                                    ccFilters.add(filter); 
                                }
                                if ( ( ccFilters != null ) && ( ccFilters.size() > 0 ) ) {
                                    filters.add(Filter.collectionCondition("IdentityExternalAttribute", Filter.and(ccFilters)));
                                }
                            }
                    }
                }
            } else if (attr.getExtendedNumber() > 0){
                addLikeFilter(filters, inputs, attr.getName(), "identity."+attr.getName(), null);
            }
        } 
    }

}
