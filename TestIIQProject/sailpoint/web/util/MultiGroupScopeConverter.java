/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.ConverterException;

import org.json.JSONArray;
import org.json.JSONObject;

import sailpoint.tools.Util;



/**
 * A base implementation of an object converter. This converts a SailPointObject
 * to to its id.
 * 
 * This originally was written by Peter to convert from object to name, not id.
 * However, it wasn't in use, so I hijacked it for id purposes. DC
 * 
 * @author <a href="mailto:peter.holcomb@sailpoint.com">Peter Holcomb</a>
 */
public  class MultiGroupScopeConverter implements
        Converter{
    /* 
     * Converts String input from UI Map object
     * @see javax.faces.convert.Converter#getAsObject(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.String)
     */
    public Object getAsObject(FacesContext context, UIComponent component,
            String value) {
        
        value = Util.escapeJsonCharacter(value);
        List<Map<Object,Object>> data = new ArrayList<Map<Object,Object>>();

        if (null != value && value.trim() != "" && value.startsWith("[")) {
            try {
                JSONArray jArray = new JSONArray(value);
                
                for (int i=0; i< jArray.length(); i++ ) {
                    JSONObject object = null;
                    if (jArray.get(i) != null) {
                        object = new JSONObject(jArray.get(i).toString());
                        Map<Object,Object> innermap = new  HashMap<Object,Object>();
                        innermap.put("objectType", object.get("objectType") );
                        innermap.put("groupMemberFilterString",object.get("memberSearchFilter"));
                     
                        List searchDNList = null;
                        List searchDNs = null;
                        String memberSearchDN = (String)object.get("memberSearchDN");
                        if (Util.isNotNullOrEmpty(memberSearchDN) && ! "null".equalsIgnoreCase(memberSearchDN)) {
                            searchDNList = Arrays.asList(memberSearchDN.split("\\\\r\\\\n|\\\\n\\\\r"));
                            searchDNs = new ArrayList();
                            for (Object searchDN : searchDNList) {
                                String temp = (String)searchDN;
                                if (Util.isNotNullOrEmpty(temp)) {
                                    searchDNs.add(temp.replaceAll("\\\\n", "\\n"));
                                }
                            }
                            innermap.put("groupMembershipSearchDN",searchDNs);
                            data.add(innermap);
                        }
                    }
                }
            } catch (Exception ge) {
                throw new ConverterException(ge);
            }
        }
        return data;
    }
    
    /*
     * 
     * Converts Map object from application to String object
     * @see
     * javax.faces.convert.Converter#getAsString(javax.faces.context.FacesContext
     * , javax.faces.component.UIComponent, java.lang.Object)
     */
    @SuppressWarnings("unchecked")
    public String getAsString(FacesContext context, UIComponent component,
            Object value) {
        String objAsString = null;
        String arrObj = "[";

        if (null != value) {
            List<Map<Object, Object>> mapList = (List<Map<Object, Object>>)value;
            objAsString = "";
            for (int i=0; i<mapList.size(); i++) {
                Map<Object,Object> map = mapList.get(i);
                if (map != null) {
                    StringBuilder temp = new StringBuilder();
                    List<String> searchDNs = (List)map.get("groupMembershipSearchDN");
                    String searchDN = "";
                    if (searchDNs != null ) {
                        for (String dn : searchDNs) {
                            searchDN = searchDN + dn.replace( "\\n","\\\\n") + "\\r\\n";
                        }
                        if (searchDN.length() > 3) {
                            searchDN = searchDN.substring(0, searchDN.length()-4);
                        }
                    }
                    String objType = map.get("objectType") != null ? map.get("objectType").toString() : "";
                    String memberSearchFilter = map.get("groupMemberFilterString") != null ? map.get("groupMemberFilterString").toString() : "";
                    temp.append("{'objectType':'"+ objType +"','memberSearchFilter':'"+ memberSearchFilter + "','memberSearchDN':'"+ searchDN+ "'},");
                    arrObj = arrObj + temp.toString();
                    objAsString = arrObj.substring(0, arrObj.length()-1)+"]";
                }
            }
        }
        return objAsString;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.faces.component.StateHolder#restoreState(javax.faces.context.
     * FacesContext, java.lang.Object)
     */
    public void restoreState(FacesContext context, Object state) {
        // not needed
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.faces.component.StateHolder#saveState(javax.faces.context.FacesContext
     * )
     */
    public Object saveState(FacesContext context) {
        return null; // not needed
    }
}
