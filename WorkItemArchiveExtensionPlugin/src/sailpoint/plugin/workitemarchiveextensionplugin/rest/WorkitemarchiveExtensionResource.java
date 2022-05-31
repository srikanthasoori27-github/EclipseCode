package sailpoint.plugin.workitemarchiveextensionplugin.rest;



import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.rest.BaseListResource;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.rest.plugin.RequiredRight;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.*;
import sailpoint.service.WorkItemService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.view.ViewBuilder;
import sailpoint.web.view.ViewEvaluationContext;

import javax.ws.rs.*;

import java.util.*;


@RequiredRight(value = "workitemarchiveExtensionRESTAllow")
@Path("workitemarchiveExtension")
public class WorkitemarchiveExtensionResource extends BasePluginResource {
	 private static final Log log = LogFactory.getLog(WorkitemarchiveExtensionResource.class);

	    @GET @Path("workitems")
	    public ListResult getWorkItems() throws GeneralException {
	    	
	    	System.out.println("called this method getWorkItems");
	    	log.error("called this method getWorkItems");
	        return getListResult(UIConfig.WORKITEMS_ARCHIVE_TABLE_COLUMNS, WorkItemArchive.class, getQueryOptions(UIConfig.WORKITEMS_ARCHIVE_TABLE_COLUMNS));
	    }

		@Override
		public String getPluginName() {
			// TODO Auto-generated method stub
			log.error("called this method getPluginName");
			return "workitemarchiveextensionplugin";
		}
	    
	   
	  
	}
