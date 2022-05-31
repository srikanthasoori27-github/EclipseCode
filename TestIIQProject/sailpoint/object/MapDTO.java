package sailpoint.object;

import java.util.Map;
import java.util.Set;

import sailpoint.tools.Util;
import sailpoint.web.BaseBean;

public class MapDTO extends BaseBean {

    private String id;
    private String key;
    private Object value;
    
    public MapDTO() {
        super();
        id = "D" + Util.uuid();
    }

    public MapDTO(Map objectMap) {
        super();
        id = "D" + Util.uuid();
        if(!objectMap.isEmpty()){
            
            Set<String> e = objectMap.keySet();
            for(String ee:e){
                this.key = ee;
                this.value = objectMap.get(ee);
            }
        }
    }

    public MapDTO(MapDTO source) {
        this.id = source.getId();
        this.key = source.getKey();
        this.value = source.getValue();
    }

   public String getKey() {
       return key;
   }

   public void setKey(String key) {
       this.key = key;
   }

   public Object getValue() {
       return value;
   }

   public void setValue(Object value) {
       this.value = value;
   }
   public void setId(String id){
       this.id = id;
   }
   
   public String getId(){
       return id;
   }
}

