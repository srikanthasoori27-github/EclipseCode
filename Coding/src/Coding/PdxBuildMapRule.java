package Coding;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.tools.Util;
import sailpoint.connector.DelimitedFileConnector;

public class PdxBuildMapRule {

	
	public Object customizationRule()
	{
		ResourceObject ro = new ResourceObject();
		ro.setIdentity(null);
		ro.setDisplayName(null);
	}
	
	public Object buildMapRule()
	{


		log.error("Starting the BuildMap Rule");
		Map map1 =new HashMap();

		Map map = DelimitedFileConnector.defaultBuildMap(cols, record);

		String pocdexID = (String) map.get("POCDEX UID");
		String agencyCode = (String) map.get("Present Agency Code");
		String positoinId = (String) map.get("Position ID");
		String positionEndDate="";
		
		log.error(" the pocdexID , agency Code, position Id are "+pocdexID+" , "+agencyCode+" , "+positoinId);
		if(pocdexID!=null)
		  {

		    QueryOptions qo = new QueryOptions();
		    qo.add(Filter.eq("iamPocdexUid",pocdexID));
		    Iterator identitySearchitr= context.search(Identity.class, qo);
		    if(identitySearchitr.hasNext())
		    {
		      Identity identity = (Identity) identitySearchitr.next();
		      String identityAgencyCode = (String) identity.getAttribute("iamAgencyCode");
			  if(identityAgencyCode!=null && identityAgencyCode.equals(agencyCode))
				{
				 
				log.error("Identity found with the agency code and pocdexid "+pocdexID+" , "+agencyCode+" , "+positoinId);
				List positionIdList = (List) identity.getAttribute("iamPositionId");
				if(!Util.isNullOrEmpty(positionEndDate))
						{
					
				        if(positoinId!=null && positionIdList!=null && positionIdList.size()>0 && positionIdList.contains(positoinId))
				        {
				        
				        log.error("positoinId list before removal "+positionIdList);
				        positionIdList.remove(positoinId);
				        log.error("positoinId list after removal "+positionIdList);
				        identity.setAttribute(iamPositionId, positionIdList);
				        
				        map.put("Position ID",null);
				        }
						}
				else
				{
					 if(positoinId!=null && positionIdList!=null && positionIdList.size()>0 && !positionIdList.contains(positoinId))
					 {
						 log.error("in the else block - positoinId list before addition "+positionIdList);
					        positionIdList.add(positoinId);
					        log.error("positoinId list after addition "+positionIdList); 
					 }
				}
				 return map;
				}
			  else
			  {
				  log.error(" No Identity found with the agency code and pocdexid "+pocdexID+" , "+agencyCode+" , "+positoinId);
				  return map1;
			  }
			
		  }
		    
		   return map;
		
		
	/*	 if(pocdexID!=null)
		  {

		    QueryOptions qo = new QueryOptions();
		    qo.add(Filter.eq("iamPocdexUid",pocdexID));
		    Iterator identitySearchitr= context.search(Identity.class, qo);
		    if(identitySearchitr.hasNext())
		    {
		      Identity identity = (Identity) identitySearchitr.next();
		      String identityAgencyCode = (String) identity.getAttribute("iamAgencyCode");
				if(identityAgencyCode!=null && identityAgencyCode.equals(agencyCode))
				{
				      log.error("identity found with the positionid and agencyCode "+positoinId+"  ,"+agencyCode);
				      return map;
				}
				 else
				{
				log.error("No identity found with the positionid and agencyCode "+positoinId+"  ,"+agencyCode);
				return map1;
		
				}
		  }
		  */
		    
		log.error("Ending the BuildMap Rule");
		return map;
		
	
	
	
}
	}
