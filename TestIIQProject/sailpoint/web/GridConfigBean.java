/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.ColumnConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.web.extjs.GridResponseMetaData;

/**
 * Backing bean useful for retrieving configuration data.
 * This is useful
 *
 * @author: jonathan.bryant@sailpoint.com
 */
public class GridConfigBean extends BaseListBean {

    private String loadUIConfigJson(String key) {
        
         try {
             Object cols = getUIConfig().getAttributes().get(key);
             if ((null != cols) && (cols instanceof List)) {
                 GridResponseMetaData metaData = new GridResponseMetaData((List<ColumnConfig>) cols, null);
                 metaData.localize(getLocale(), getUserTimeZone());
                 //metaData.setRoot("objects");
                 //metaData.setTotalProperty("count");
                 return JsonHelper.toJson(metaData);
             }
         } catch (GeneralException e) {
             throw new RuntimeException(e);
         }

        return "";
    }

    public Map getGridStateConfig() {
        return new ConfigProxy(ConfigProxy.GRID_STATE);
    }

    public Map getUiConfigJson() {
        return new ConfigProxy(ConfigProxy.UI_CONF);
    }

    private class ConfigProxy extends HashMap {

        public static final String UI_CONF = "uiconfig";
        public static final String GRID_STATE = "gridState";

        String type;

        private ConfigProxy(String type) {
            super();
            this.type = type;
        }

        @Override
        public Object get(Object key) {
            if (UI_CONF.equals(this.type)) {
                return loadUIConfigJson((String)key);
            } else if (GRID_STATE.equals(this.type)) {
                return loadGridState((String)key);
            } else {
                throw new RuntimeException("Unknown configuration type.");
            }
        }
    }

}
