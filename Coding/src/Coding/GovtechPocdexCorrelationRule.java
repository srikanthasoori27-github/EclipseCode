package Coding;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;

import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.Util;



public class GovtechPocdexCorrelationRule {
	SailPointContext context;
	public Map doCorrelation()
	{
		Map returnMap = new HashMap();
		String roUid = account.getStringAttribute("iamRoUid");
		String identityName;
		QueryOptions qo = new QueryOptions();
		qo.add(Filter.eq("iamPocdexUid",roUid));
		Iterator identitySearchitr= context.search(Identity.class, qo);
		if(identitySearchitr.hasNext())
		{
		identityName=identitySearchitr.next().getName();
		returnMap.put("identityName",identityName);
		}
		else
		{
			QueryOptions qo2 = new QueryOptions();
			qo2.add(Filter.eq("iamNric", qo2));
		}
		
		List wogADIDList =identity.getAttribute("");
		String wogADID = link.getAttribute("WOG AD ID"); 
		if(Util.isNotNullOrEmpty(wogADID))
		{
		if(wogADIDList!=null && !wogADIDList.contains(wogADID))
		{
			wogADIDList.add(wogADID);
		}
		else if(Util.isEmpty(wogADIDList))
		{
			wogADIDList= new ArrayList();
			wogADIDList.add(wogADID);			
		}
		
		}
		
		{
			
		}
					
					
		return returnMap;
	}

}
