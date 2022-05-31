package Coding;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentityArchive;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.api.SailPointContext;
import sailpoint.object.Resolver;
import sailpoint.object.Rule;
import sailpoint.tools.Message;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.task.TaskResultBean;

public class IdentityArch {
	
	public IdentityArchive getRecentArchive(String name) throws GeneralException
	{
		SailPointContext context = null;
		context.
		QueryOptions qo = new QueryOptions();
		Filter identityArchFilter = Filter.eq("name", name);
		qo.add(identityArchFilter);
		qo.addOrdering("modified", false);
		Iterator it = context.search(IdentityArchive.class,qo);
		return (IdentityArchive) it.next();
	}
	
	
	public String retrieveAzureADgroup(String displayName,String appName) throws GeneralException
	{
		SailPointContext context =null;
		String value=null;
		QueryOptions qo = new QueryOptions();
		qo.addFilter(Filter.eq("displayName", displayName));
		qo.addFilter(Filter.eq("application.name", appName));
		Iterator itr=(Iterator) context.search(ManagedAttribute.class, qo,"value");
		
		while(itr.hasNext())
		{
		Object obj[]=(Object[]) itr.next();
		obj[0].
		
		value=(String) obj[0];
		}
		
		return value;
		
		
	}

}
