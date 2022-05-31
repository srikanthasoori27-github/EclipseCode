/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.object.ScoreBandConfig;
import sailpoint.object.ScoreConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class BandConfigBean extends BaseBean {
    private int maxNumberOfBands;
    private String numBands;
    private List<RiskScoreBand> bands;
    private List<SelectItem> bandOptions;
    private String maxScore;
    
    private static final RiskScoreBandComparator comparator = new RiskScoreBandComparator();
    private static Log log = LogFactory.getLog(BandConfigBean.class);
    
    public BandConfigBean() {
        super();
        
        try {
            ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
            maxNumberOfBands = config.getMaximumNumberOfBands();
            maxScore = String.valueOf(config.getMaximumScore());
            bands = createBandDTOs(config.getBands());
            numBands = String.valueOf(bands.size());
            createBandOptions();
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE), null);
            log.error("The database is not accessible right now.", e);
        }
    }
    
    public List<RiskScoreBand> getBands() {
        List<RiskScoreBand> sortedBands = new ArrayList<RiskScoreBand>();
        if (bands == null) {
            sortedBands = Arrays.asList(new RiskScoreBand [] { 
                new RiskScoreBand("333", "0", "risk_band_low", "#00ff00", "#FFFFFF"),
                new RiskScoreBand("667",  "334", "risk_band_med", "#ffff00", "#000000"),
                new RiskScoreBand("1000",  "668", "risk_band_high", "#00ff00", "#FFFFFF")
            });
        } else {
            sortedBands.addAll(bands);
        }
        
        if (sortedBands.size() < maxNumberOfBands) {
            // pad out the list if necessary
            final int deficit = maxNumberOfBands - sortedBands.size();
            
            for (int i = 0; i < deficit; ++i) {
                sortedBands.add(new RiskScoreBand());
            }
        }
        
        RiskScoreBand [] sortedBandArray = sortedBands.toArray(new RiskScoreBand[sortedBands.size()]); 
        Arrays.sort(sortedBandArray, comparator);
        
        // Populate the indecies
        for (int i = 0; i < sortedBandArray.length; ++i) {
            sortedBandArray[i].index = i;
        }
        
        return Arrays.asList(sortedBandArray);
    }
    
    public String getBandJson() {
        List<RiskScoreBand> currentBands = getBands();
        
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        
        try {
            jsonWriter.object();
            jsonWriter.key("colorBands");
            
            List<JSONObject> colorBands = new ArrayList<JSONObject>();
            
            for (RiskScoreBand band : currentBands) {
                Map<String, Object> bandInfo = new HashMap<String, Object>();
                bandInfo.put("id", band.getIndex());
                bandInfo.put("upper", band.getUpperBound());
                bandInfo.put("lower", band.getLowerBound());
                bandInfo.put("color", band.getColor());
                bandInfo.put("textColor", band.getTextColor());
                bandInfo.put("enabled", band.isInitialized());
                bandInfo.put("label", band.getLabel());
                colorBands.add(new JSONObject(bandInfo));
            }
            
            jsonWriter.value(new JSONArray(colorBands));
            jsonWriter.key("numColors");
            jsonWriter.value(currentBands.size());
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Failed to return a proper band store", e);
        }

        return jsonString.toString();
    }

    public String getNumBands() {
        return numBands;
    }

    public void setNumBands(String numBands) {
        this.numBands = numBands;
    }

    public String getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(String maxScore) {
        this.maxScore = maxScore;
    }

    public String saveBandChanges() {
        try {
            ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
            config.setBands(createBands());
            getContext().saveObject(config);
            getContext().commitTransaction();
        }  catch (NumberFormatException e) {
            // This should have been caught by the UI.  It's primarily here for debugging.
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_INVALID_NUMBER_ENTERED),
                    new Message(MessageKeys.MSG_PLAIN_TEXT, e));
            log.error("An invalid number was entered.", e);
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE), null);
            log.error("No changes can be saved right now because the system is offline.", e);
        }
        
        return "save";
    }
    
    private List <RiskScoreBand> createBandDTOs(List<ScoreBandConfig> bandList) {
        List <RiskScoreBand> retval = new ArrayList<RiskScoreBand>();
        
        for (ScoreBandConfig currentBand : bandList) {
            retval.add(new RiskScoreBand(String.valueOf(currentBand.getUpperBound()), String.valueOf(currentBand.getLowerBound()), currentBand.getLabel(), currentBand.getColor(), currentBand.getTextColor()));
        }
        
        return retval;
    }
    
    private List <ScoreBandConfig> createBands() {
        List <ScoreBandConfig> retval = new ArrayList<ScoreBandConfig>();
        int nextLower = 0;

        // Work around a JSF/a4j quirk -- Can't rely on the model... must use the inputs instead
        for (int i = 0; i < maxNumberOfBands; ++i) {
            String bandUpperBound = getRequestParameter("editForm:bandConfigTabl" + i + ":bandupper");

            if (Util.isNotNullOrEmpty(bandUpperBound)) {
                ScoreBandConfig usableBand = new ScoreBandConfig();
                usableBand.setLabel(getRequestParameter("editForm:bandConfigTabl" + i + ":bandLabel"));
                usableBand.setColor(getRequestParameter("editForm:bandConfigTabl" + i + ":bandColor"));
                usableBand.setTextColor(getRequestParameter("editForm:bandConfigTabl" + i + ":bandTextColor"));
                usableBand.setUpperBound(Integer.valueOf(bandUpperBound));
                usableBand.setLowerBound(nextLower);
                nextLower = usableBand.getUpperBound() + 1;
                
                log.debug("Creating band with the following info: " + usableBand.toString());
                
                retval.add(usableBand);
            }
        }
        
        return retval;
    }
    
    private void createBandOptions() {
        bandOptions = new ArrayList<SelectItem>();
        List<RiskScoreBand> sortedBands = getBands();
        
        for (RiskScoreBand band : sortedBands) {
            if (band.isInitialized()) {
                bandOptions.add(new SelectItem(String.valueOf(band.getIndex()), band.getLabel()));
            }
        }
    }
    
    public class RiskScoreBand {
        private String upperBound;
        private String lowerBound;
        private String label;
        private String color;
        private String textColor;
        private int index;
        
        public RiskScoreBand() {
            label = "";
            color = "";
            textColor = "";
            upperBound = "";
            lowerBound = "";
            index = -1;
        }
        
        public RiskScoreBand(final String anUpperBound, final String aLowerBound, final String aLabel, final String aColor, final String tColor) {
            setUpperBound(anUpperBound);
            setLowerBound(aLowerBound);
            setLabel(aLabel);
            setColor(aColor);
            setTextColor(tColor);
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getLowerBound() {
            return lowerBound;
        }

        public void setLowerBound(String lowerBound) {
            this.lowerBound = lowerBound;
        }

        public String getUpperBound() {
            return upperBound;
        }

        public void setUpperBound(String upperBound) {
            this.upperBound = upperBound;
        }
        
        public String getLabel() {
            return label;
        }
        
        public void setLabel(String label) {
            this.label = label;
        }
        
        public String getColor() {
            return color;
        }
        
        public void setColor(String color) {
            if(WebUtil.isSafeValue(color)){
                this.color = color;
            }else if (null == this.color){
                this.color = "#ffffff";
            }
        }

        public String getTextColor() {
            return textColor;
        }

        public void setTextColor(String textColor) {
            if(WebUtil.isSafeValue(textColor)){
                this.textColor = textColor;
            }else if (null == this.textColor){
                this.textColor = "#ffffff";
            }
        }
        
        public boolean isInitialized() {
            return !"".equals(label);
        }
    }
    
    public static class RiskScoreBandComparator implements Comparator<RiskScoreBand> {
        public int compare(RiskScoreBand o1, RiskScoreBand o2) {
            if ("".equals(o1.getLowerBound())) {
                if (o1.getLowerBound().equals(o2.getLowerBound())) {
                    return 0;
                } else {
                    // Shift uninitialized values to the end
                    return 1;
                }
            } else if ("".equals(o2.getLowerBound())) {
                // Shift uninitialized values to the end
                return -1;
            } else {
                // Initialized values are sorted normally
                return  Integer.valueOf(o1.getLowerBound()).intValue() - Integer.valueOf(o2.getLowerBound());
            }
        }
    }
}
