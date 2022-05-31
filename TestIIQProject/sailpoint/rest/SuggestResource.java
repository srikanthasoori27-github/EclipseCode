/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Rule;
import sailpoint.object.SPRight;
import sailpoint.object.Tag;
import sailpoint.service.LCMConfigService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestAuthorizer;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestService;
import sailpoint.tools.Util;
import sailpoint.object.Classification;
import sailpoint.object.QueryOptions;
import sailpoint.object.UIConfig;
import sailpoint.rest.ui.Paths;
import sailpoint.tools.GeneralException;
import sailpoint.tools.SimpleSelectItem;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.web.ClassicSuggestService;
import sailpoint.web.util.MapListComparator;
import sailpoint.web.util.WebUtil;

/**
 * Generic suggest component datasource for the major SailPoint object
 * classes.
 *
 * I created this because the new 'Presentation Hints' UI allows the
 * creation of Fields of any generic SailPoint object type.  
 *
 * @author: jonathan.bryant@sailpoint.com
 */
@Path(Paths.SUGGEST)
public class SuggestResource extends BaseListResource implements ClassicSuggestService.ClassicSuggestServiceContext {

    private static final Log log = LogFactory.getLog(SuggestResource.class);

    /**
     * Additional filter string which may be passed by the component.
     * This is used when dynamically generating forms using the Form class
     * and corresponds to the filterString property on the Field object.
     */
    protected String filterString;

    /**
     * Context used for Identity Suggest filters
     */
    protected String suggestContext;
    
    /**
     * Suggest ID used for Identity Suggest filters
     */
    protected String suggestId;

    protected String suggestClass;

    /**
     * Suggest authorizer context to use for auth
     */
    private SuggestAuthorizerContext suggestAuthorizerContext;

    public SuggestResource() {
        super();
    }

    public SuggestResource(BaseListResource baseResource, SuggestAuthorizerContext suggestAuthorizerContext) {
        this(baseResource, suggestAuthorizerContext, true);
    }

    /**
     * Constructor
     * @param baseResource BaseListResource
     * @param suggestAuthorizerContext Authorizer context to use for endpoint auth, if not provided default is set up
     * @param exclude true if currently assigned items are to be excluded from the list
     */
    public SuggestResource(BaseListResource baseResource, SuggestAuthorizerContext suggestAuthorizerContext, boolean exclude) {
        super(baseResource, exclude);
        this.suggestAuthorizerContext = suggestAuthorizerContext;
    }

    /**
     * @return the passed in authorizer context, or the default set up based on rights
     */
    private SuggestAuthorizerContext getSuggestAuthorizerContext() {
        if (this.suggestAuthorizerContext == null) {
            BaseSuggestAuthorizerContext baseSuggestAuthorizerContext = new BaseSuggestAuthorizerContext();

            // Tags are for certification group advanced search
            if (RightAuthorizer.isAuthorized(this, SPRight.FullAccessCertificationSchedule, SPRight.FullAccessCertifications, SPRight.ViewGroupCertification)) {
                baseSuggestAuthorizerContext.add(Tag.class.getSimpleName());
            }

            // Rules are for debug object page
            if (RightAuthorizer.isAuthorized(this, SPRight.FullAccessDebugPage)) {
                baseSuggestAuthorizerContext.add(Rule.class.getSimpleName());
            }

            //Classifications are for Role Editor/Search, Entitlement Editor/Search
            if (RightAuthorizer.isAuthorized(this, SPRight.ManageRole, SPRight.ViewRole, SPRight.FullAccessReport, 
                    SPRight.ManagedAttributePropertyAdministrator, SPRight.ManagedAttributeProvisioningAdministrator, 
                    SPRight.ViewAccountGroups)) {
                baseSuggestAuthorizerContext.add(Classification.class.getSimpleName());
            }
            
            this.suggestAuthorizerContext = baseSuggestAuthorizerContext;
        }

        return this.suggestAuthorizerContext;
    }

    @POST
    @Path("uiconfig")
    @Consumes("application/x-www-form-urlencoded")
    @SuppressWarnings("unchecked")
    public ListResult getUIConfigSuggest(@FormParam("start") int startRec, @FormParam("key") String key,
                                 @FormParam("limit") int limitRec, @FormParam("query") String queryText)
            throws GeneralException {
        authorize(new AllowAllAuthorizer());

        this.start = startRec;
        this.limit = WebUtil.getResultLimit(limitRec);
        this.query = queryText;
        
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        int total = 0;         
        if(key!=null && !key.equals("")) {
            UIConfig uiConfig = UIConfig.getUIConfig();
            List<SimpleSelectItem> selectItems = (List<SimpleSelectItem>)uiConfig.getObject(key);            
            
            for (SimpleSelectItem selectItem : Util.safeIterable(selectItems)) {
                String localizedLabel = selectItem.getLocalizedLabel(getLocale(), getUserTimeZone());
                if (localizedLabel == null) {
                    localizedLabel = selectItem.getValue();
                }
                if(localizedLabel != null && localizedLabel.toLowerCase().startsWith(this.query.toLowerCase())) {                        
                    Map<String,Object> row = new HashMap<String,Object>();
                    row.put("value", selectItem.getValue());
                    row.put("displayName", localizedLabel);
                    out.add(row);
                }
            }

            Collections.sort(out, new MapListComparator("displayName", true, getLocale()));
        }
        total = out.size();

        return new ListResult(out, total);
    }
    
    @POST
    @Path("column/{class}/{column}")
    @Consumes("application/x-www-form-urlencoded")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public ListResult getDistinctColumnSuggest(@PathParam("class") String className, @PathParam("column") String column,
                                               @FormParam("limit") int limitRec, @FormParam("query") String queryText,
                                               @FormParam("start") int startRec, @FormParam("isLCM") boolean isLCM,
                                               @FormParam("filterString") String filterString)
            throws GeneralException, ClassNotFoundException {
        authorize(new SuggestAuthorizer(getSuggestAuthorizerContext(), className, column));

        // todo need to look at updating BaseResource so that we can handle post as well as get
        this.start = startRec;
        this.limit = WebUtil.getResultLimit(limitRec);
        this.query = queryText;
        this.filterString = filterString;
        this.suggestClass = className;

        String quicklinkName = null;
        if (isLCM) {
            quicklinkName = (String) request.getSession().getAttribute(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);
        }

        ClassicSuggestService suggestService = new ClassicSuggestService(this);
        return suggestService.getColumnSuggestResult(column, isLCM, quicklinkName, exclude);
    }

    /**
     * Returns a json data feed for a suggest component for the given
     * class type.
     * @param className SailPoint object simple class name
     * @return
     * @throws GeneralException
     */
    @POST
    @Path("object/{class}")
    @Consumes("application/x-www-form-urlencoded")
    public ListResult getSuggest(@PathParam("class") String className, @FormParam("start") int startRec,
                                 @FormParam("limit") int limitRec, @FormParam("query") String queryText,
                                 @FormParam("sort") String sortField, @FormParam("dir") String sortDir,
                                 @FormParam("filter") String filter, @FormParam("context") String suggestContext,
                                 @FormParam("suggestId") String suggestId)
            throws GeneralException {
        authorize(new SuggestAuthorizer(getSuggestAuthorizerContext(), className));

        // todo need to look at updating BaseResource so that we can handle post as well as get
        this.filterString = filter;
        this.start = startRec;
        this.limit = WebUtil.getResultLimit(limitRec);
        this.sortBy = sortField;
        this.sortDirection = sortDir;
        this.query = queryText;
        this.suggestId = suggestId;
        this.suggestContext = suggestContext;
        this.suggestClass = className;

        ClassicSuggestService suggestService = new ClassicSuggestService(this);
        return suggestService.getObjectSuggestResult();
    }

    @GET
    @Path("object/{class}")
    public ListResult simpleList(@PathParam("class") String className)
            throws GeneralException {
        authorize(new SuggestAuthorizer(getSuggestAuthorizerContext(), className));

        this.filterString = "";
        this.sortBy = "name";
        this.suggestClass = className;

        SuggestService suggestService = new SuggestService(this);

        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        int total = 0;

        Class suggestClass = suggestService.getSuggestClass(className);

        if (suggestClass != null) {
            ClassicSuggestService classicSuggestService = new ClassicSuggestService(this);
            QueryOptions qo = classicSuggestService.getQueryOptions();
            //IIQETN-6317 :- adding forgotten line to fully implement pagination
            total = getContext().countObjects(suggestClass,qo);
            out = SuggestHelper.getSuggestResults(suggestClass, qo, getContext());
        } else {
            ListResult res = new ListResult(Collections.EMPTY_LIST, 0);
            res.setStatus("fail");
            res.addError("Unknown class:" + className);
            return res;
        }

        return new ListResult(out, total);
    }

    public String getSuggestClass() {
        return suggestClass;
    }
    
    public String getFilterString() {
        return filterString;
    }

    @Override
    public String getSuggestContext() {
        return suggestContext;
    }

    @Override
    public String getSuggestId() {
        return suggestId;
    }

    @Override
    public String getRequestParameter(String name) {
        return getRequest().getParameter(name);
    }
}
