/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sailpoint.connector.ConnectorException;
import sailpoint.object.Schema;

/**
  * This class is used by only VMSConnector, hence moved to connectors repository.
  * Marking this class as deprecated and needs to be removed in future releases.
  */
@Deprecated
public class SysUafParser {

    public static final String PRIV = "Privileges";
    public static final String AUTH_PRIV = "Authorized Privileges";
    public static final String UNAME = "Username";
    public static final String OWNER = "Owner";
    public static final String ACCOUNT = "Account";
    public static final String UIC = "UIC";
    public static final String DEF_PRIV = "Default Privileges";
    public static final String IDENTIFIER = "Identifier";
    public static final String CLI = "CLI";
    public static final String TABLES = "Tables";
    public static final String DEFAULT = "Default";
    public static final String FLAGS = "Flags";
    public static final String NETWORK = "Network";
    public static final String BATCH = "Batch";
    public static final String LOCAL = "Local";
    public static final String DIALUP = "Dialup";
    public static final String REMOTE = "Remote";
    public static final String END_RECORD = "END OF RECORD";
    
    private static final String LINE_SEPERATOR = System.getProperty("line.separator", "\n");
    public static final String NETWORK_PRIMARY = "Network Primary";
    public static final String NETWORK_SECONDARY = "Network Secondary";
    public static final String LOCAL_PRIMARY = "Local Primary";
    public static final String LOCAL_SECONDARY = "Local Secondary";
    public static final String REMOTE_PRIMARY = "Remote Primary";
    public static final String REMOTE_SECONDARY = "Remote Secondary";
    public static final String BATCH_PRIMARY = "Batch Primary";
    public static final String BATCH_SECONDARY = "Batch Secondary";
    public static final String DIALUP_PRIMARY = "Dialup Primary";
    public static final String DIALUP_SECONDARY = "Dialup Secondary";

    private static final CharSequence ACCESS_SQ = "access";
    
    /**
     * 
     * @param rec the record Name
     * @param fieldName the field Name
     * @param endMarker the end marker
     * @param required whether it is required.
     * @throws ConnectorException
     */
    private static String getField(String rec, String fieldName, String endMarker, boolean required) 
            throws ConnectorException {
        int beginIndex = rec.indexOf(fieldName);
        int endIndex = -1;
        String val = "";

        if (beginIndex < 0) {
            // Not found, bad
            if (required)
                throw new ConnectorException("Attribute required: " + fieldName);
        }
        else {
            endIndex = rec.indexOf(endMarker, beginIndex);
            
            // If we don't find the end marker
            // assume the rest of the record 
            // This should only be the case for "Default Privileges" where sometimes
            // the "Identifier" attribute does not exist
            if (endMarker.equals(END_RECORD)) {
                endIndex = rec.length();
            }
            
            if (endIndex < 0) {
                if (endMarker.equals(IDENTIFIER)) {
                    // The IDENTIFIER end marker sometimes doesn't exist use the end of record.
                    endIndex = rec.length();
                }
                else {
                    throw new ConnectorException("End marker not found: " + endMarker);
                }
            }
            
            val = rec.substring(beginIndex + fieldName.length()+1, endIndex);
        }
        return val.trim();
    }
    
    /**
     * Each record should contain some attribute/value pairs
     * Combine the Authorized and Default privileges
     * 
     * @param rec the record name
     * @param schema the schema
     * @return attribute/value pairs
     */
    public static Map<String, Object> parseRecord(String rec, Schema schema) throws ConnectorException {
        
        if (rec == null || rec.length() == 0) {
            throw new ConnectorException("Nothing to parse, record is null or empty.");
        }
        List<String> configuredAttributes = schema.getAttributeNames();
        
        Map<String, Object> recordMap = new HashMap<String, Object>();
        
        if (configuredAttributes.contains(UNAME)) {
            recordMap.put(UNAME,  getField(rec, UNAME+":", OWNER+":",  true));
        }
        
        if (configuredAttributes.contains(OWNER)) {
            recordMap.put(OWNER, getField(rec, OWNER+":", LINE_SEPERATOR,  false));
        }

        if (configuredAttributes.contains(ACCOUNT)) {
            recordMap.put(ACCOUNT, getField(rec, ACCOUNT+":", UIC+":",  false));
        }
        
        if (configuredAttributes.contains(UIC)) {
            recordMap.put(UIC, getField(rec, UIC+":", LINE_SEPERATOR,  false));
        }
        
        if (configuredAttributes.contains(CLI)) {
            recordMap.put(CLI, getField(rec, CLI+":", TABLES+":",  false));
        }
        
        if (configuredAttributes.contains(TABLES)) {
            recordMap.put(TABLES, getField(rec, TABLES+":", LINE_SEPERATOR,  false));
        }
        
        if (configuredAttributes.contains(DEFAULT)) {
            recordMap.put(DEFAULT, getField(rec, DEFAULT+":", LINE_SEPERATOR,  false));
        }
        
        if (configuredAttributes.contains(FLAGS)) {
            recordMap.put(FLAGS, getField(rec, FLAGS+":", LINE_SEPERATOR,  false));
        }

        String authPrivs = getField(rec, AUTH_PRIV+":", DEF_PRIV+":",  false);
        
        if (configuredAttributes.contains(AUTH_PRIV)) {
            recordMap.put(AUTH_PRIV, authPrivs);
        }

        String defPrivs = getField(rec, DEF_PRIV+":", IDENTIFIER,  false);
        
        if (configuredAttributes.contains(DEF_PRIV)) {
            recordMap.put(DEF_PRIV, defPrivs);
        }
        
        if (configuredAttributes.contains(NETWORK_PRIMARY) || configuredAttributes.contains(NETWORK_SECONDARY)) {
            String networkStr = getField(rec, NETWORK+":" , LINE_SEPERATOR, false);
            recordMap.put(NETWORK_PRIMARY, getPrimary(networkStr));
            recordMap.put(NETWORK_SECONDARY, getSecondary(networkStr));
        }
        
        if (configuredAttributes.contains(LOCAL_PRIMARY) || configuredAttributes.contains(LOCAL_SECONDARY)) {
            String localStr = getField(rec, LOCAL+":" , LINE_SEPERATOR, false);
            recordMap.put(LOCAL_PRIMARY, getPrimary(localStr));
            recordMap.put(LOCAL_SECONDARY, getSecondary(localStr));
        }
        
        if (configuredAttributes.contains(REMOTE_PRIMARY) || configuredAttributes.contains(REMOTE_SECONDARY)) {
            String remoteStr = getField(rec, REMOTE+":" , LINE_SEPERATOR, false);
            recordMap.put(REMOTE_PRIMARY, getPrimary(remoteStr));
            recordMap.put(REMOTE_SECONDARY, getSecondary(remoteStr));
        }
        
        if (configuredAttributes.contains(BATCH_PRIMARY) || configuredAttributes.contains(BATCH_SECONDARY)) {
            String batchStr = getField(rec, BATCH+":" , LINE_SEPERATOR, false);
            recordMap.put(BATCH_PRIMARY, getPrimary(batchStr));
            recordMap.put(BATCH_SECONDARY, getSecondary(batchStr));
        }
        
        if (configuredAttributes.contains(DIALUP_PRIMARY) || configuredAttributes.contains(DIALUP_SECONDARY)) {
            String dialupStr = getField(rec, DIALUP+":" , LINE_SEPERATOR, false);
            recordMap.put(DIALUP_PRIMARY, getPrimary(dialupStr));
            recordMap.put(DIALUP_SECONDARY, getSecondary(dialupStr));
        }
        
        if (configuredAttributes.contains(PRIV)) {
            Set<String> privs = new HashSet<String>();
            String[] authPrivArray = authPrivs.split("\\s+"); 
            String[] defPrivArray = defPrivs.split("\\s+");
            
            for (int i=0; i< authPrivArray.length; ++i) {
                privs.add(authPrivArray[i]);
            }
            for (int i=0; i< defPrivArray.length; ++i) {
                privs.add(defPrivArray[i]);
            }
            recordMap.put(PRIV, privs);
        }
        
        if (configuredAttributes.contains(IDENTIFIER)) {
            // The Identifier field is a little different and needs special processing
            String idFieldVal  = getField(rec, IDENTIFIER, END_RECORD,  false);
            recordMap.put(IDENTIFIER, parseIdentifier(idFieldVal));
        }
        
        return recordMap;
    }

    private static String getSecondary(String remoteStr) {
        if (remoteStr.length() < 60)
            return "invalid input";
        String ss = remoteStr.substring(36, 60);
        if (ss.contains(ACCESS_SQ)) {
            // trim off begin end pounds/dashes
            ss = ss.replaceAll("#", "").replaceAll("-", "");
        }
        return ss.trim();
    }

    private static String getPrimary(String remoteStr) {
        if (remoteStr.length() < 24)
            return "invalid input";
        String ss = remoteStr.substring(0, 24);
        if (ss.contains(ACCESS_SQ)) {
            // trim off begin end pounds/dashes
            ss = ss.replaceAll("#", "").replaceAll("-", "");
        }
        return ss.trim();
    }

    /**
     *  Identifier                         Value           Attributes
     *  SCRIP_CLIENT                     %X80010008
     *  
     *  Need to get just the first column from this multiline field
     *  
     * @param idFieldVal
     * @return
     */
    private static String parseIdentifier(String idFieldVal) {
        String[] vals = idFieldVal.split("\\n");
        
        StringBuilder sb = new StringBuilder();
        
        String[] columns;
        // Ignore the first line,  "Value", "Attributes"
        for (int i=1; i<vals.length; ++i) {
            columns = vals[i].trim().split("\\s+");
            // only need the first column
            sb.append(columns[0]).append(" ");
        }
        
        return sb.toString();
    }
}
