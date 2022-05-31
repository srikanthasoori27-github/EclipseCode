package Coding;
import sailpoint.integration.JsonUtil;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import javax.net.ssl.HttpsURLConnection;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import sailpoint.tools.GeneralException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import sailpoint.tools.Util;


public class AfterOperationRule {

	Map processedResponseObject= new HashMap();
	public Map dosomething()
	{
		
	  
		import java.util.*;
		Integer fetchedRecordsCount = 0;
		if(null != processedResponseObject) {
		fetchedRecordsCount  = ((List) processedResponseObject).size();
		}
		Integer expectedCount = 4;
		Integer pageNo = 1;
		URL url = new URL(requestEndPoint.getFullUrl());
		System.out.println("AFTER RULE: Original Url ==> " + url);
		String[] params = url.getQuery().split("&");
		for (String param : params) {
		String name = param.split("=")[0];
		String value = param.split("=")[1];
		switch(name) {
		case "pageNo":
		pageNo = Integer.parseInt(value);
		break;
		default:
		}
		}
		System.out.println("processedResponseObject ==> " + processedResponseObject);
		System.out.println("AFTER RULE: Fetch Count ==> " + fetchedRecordsCount);
		System.out.println("AFTER RULE: Limit Count ==> " + expectedCount);
		System.out.println("AFTER RULE: Fetch pageNo ==> " + pageNo);
		 
		URL url = new URL(requestEndPoint.getFullUrl());
		 
		boolean hasMore = 
		System.out.println("AFTER RULE: Has More? ==> " + hasMore);
		Map transientValues = application.getAttributeValue("transientValues");
		if(transientValues == null) {
		transientValues = new HashMap();
		application.setAttribute("transientValues", transientValues);
		}
		transientValues.put("hasMore", hasMore);
		if (hasMore) {
		if(null != pageNo) {
		System.out.println("AFTER RULE: New pageNo ==> " + pageNo);
		transientValues.put("pageNo", String.valueOf(pageNo + 1));
		}
		}

		log.error(" requestEndPoint "+ requestEndPoint);
		log.error("processedResponseObject "+ processedResponseObject);

		return processedResponseObject;
}
	
	
	public Object paginationafterrule()
	{
		
		System.out.println("processedResponseObjectnew " + processedResponseObject.toString());
		JsonObject jsonObject = new JsonParser().parse(rawResponseObject).getAsJsonObject();
		int totalResults = Integer.parseInt(jsonObject.get("totalResults").getAsString());
		int startIndex= Integer.parseInt(jsonObject.get("startIndex").getAsString());
		int itemsPerPage=Integer.parseInt(jsonObject.get("startIndex").getAsString());
		System.out.println("processedResponseObject ==> " + processedResponseObject);
		System.out.println("AFTER RULE: totalResults ==> " + totalResults);
		System.out.println("AFTER RULE: startIndex ==> " + startIndex);
		System.out.println("AFTER RULE: itemsPerPage ==> " + itemsPerPage);
		 
		Map requestHeaderMap=requestEndPoint.getBody();
		for(Map.Entry entry : requestHeaderMap.entrySet())
		{
			System.out.println("key: "+entry.getKey()+"  value: "+entry.getValue());
			
			if(entry.getKey().equals("jsonBody"))
			{
				JSONObject jsonBody = (JSONObject) JsonUtil.parse((String) entry.getValue());
				startIndex=Integer.parseInt(jsonBody.get("startIndex").toString());
				System.out.println("the new start index is "+startIndex);
			}
		}
		System.out.println("AFTER RULE: requestHeaderMap ==> " + requestHeaderMap);
		System.out.println("AFTER RULE: requestEndPoint ==> " + requestEndPoint);
		 
		boolean hasMore = (startIndex+itemsPerPage>totalResults);
		
		System.out.println("AFTER RULE: Has More? ==> " + hasMore);
		Map transientValues = application.getAttributeValue("transientValues");
		if(transientValues == null) {
		transientValues = new HashMap();
		application.setAttribute("transientValues", transientValues);
		}
		transientValues.put("hasMore", hasMore);
		if (hasMore) {
		System.out.println("AFTER RULE: New startIndex ==> " + startIndex);
		transientValues.put("startIndex", startIndex+itemsPerPage);
		}

		return processedResponseObject;
		
	}
}
