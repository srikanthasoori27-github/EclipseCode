

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.ListResult;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItemArchive;
import sailpoint.rest.WorkItemArchiveResource;
import sailpoint.tools.GeneralException;

@Path("workitemarchiveextended")
public class WorkItemArchiveResourceExtended extends WorkItemArchiveResource{

	 private static final Log log = LogFactory.getLog(WorkItemArchiveResource.class);

	    @GET @Path("workitems")
	    public ListResult getWorkItems() throws GeneralException {
	        ListResult listresult= getListResult(UIConfig.WORKITEMS_ARCHIVE_TABLE_COLUMNS, WorkItemArchive.class, getQueryOptions(UIConfig.WORKITEMS_ARCHIVE_TABLE_COLUMNS));
	      Map map=(Map) listresult.getAttributes();
	      
	      log.debug("The map object is "+map);
	   return listresult;
	    }
	    
}
