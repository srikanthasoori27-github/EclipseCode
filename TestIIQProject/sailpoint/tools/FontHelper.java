/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import java.awt.Font;

import sailpoint.object.Configuration;

/**
 * Helper class to create appropriate font for JFreeChart classes.
 * 
 * @author <a href="mailto:michael.hide@sailpoint.com">Michael Hide</a>
 */
public class FontHelper {
    
    public final static String SANS_SERIF_NAME = "SansSerif"; // Might be "Sans-Serif"?
    
    /**
     * Takes the key names of the system config properties defined in init.xml
     * to generate a new Font.
     * 
     * @param name
     * @param style
     * @param size
     * @return
     */
    private static Font getChartFont(Configuration sysConfig, String name, String style, String size) {

        String fontName = SANS_SERIF_NAME;
        int fontSize = 12;
        int fontStyleInt = Font.PLAIN;

        if (sysConfig != null) {
            fontName = sysConfig.getString(name);
            String fontStyle = sysConfig.getString(style);
            fontSize = sysConfig.getInt(size);

            if (fontStyle != null) {
                if (fontStyle.equalsIgnoreCase("bold")) {
                    fontStyleInt = Font.BOLD;
                } 
                else if (fontStyle.equalsIgnoreCase("italic")) {
                    fontStyleInt = Font.ITALIC;
                }
            }
        }

        return new Font(fontName, fontStyleInt, fontSize);
    }

    /**
     * Uses values defined in init.xml to create a unicode title font.
     * 
     * @return
     */
    public static Font getChartTitleFont(Configuration sysConfig) {
        return getChartFont(sysConfig, Configuration.CHART_TITLE_FONT_NAME,
                Configuration.CHART_TITLE_FONT_STYLE,
                Configuration.CHART_TITLE_FONT_SIZE);
    }

    /**
     * Uses the system configuration value for the font name, but allows
     * a different size and style.
     * 
     * @param style (e.g. java.awt.Font.BOLD)
     * @param size The size of the font.
     * @return
     */
    public static Font getChartTitleFont(Configuration sysConfig, int style, int size) {
        String fontName = SANS_SERIF_NAME;
        if (sysConfig != null) {
            fontName = sysConfig.getString(Configuration.CHART_TITLE_FONT_NAME);
        }
        return new Font(fontName, style, size);
    }

    /**
     * Uses values defined in init.xml to create a unicode body font.
     * 
     * @return
     */
    public static Font getChartBodyFont(Configuration sysConfig) {
        return getChartFont(sysConfig, Configuration.CHART_BODY_FONT_NAME,
                Configuration.CHART_BODY_FONT_STYLE,
                Configuration.CHART_BODY_FONT_SIZE);
    }

    /**
     * Uses the system configuration value for the font name, but allows
     * a different size and style.
     * 
     * @param style (e.g. java.awt.Font.ITALIC)
     * @param size The size of the font.
     * @return
     */
    public static Font getChartBodyFont(Configuration sysConfig, int style, int size) {
        String fontName = SANS_SERIF_NAME;
        if (sysConfig != null) {
            fontName = sysConfig.getString(Configuration.CHART_BODY_FONT_NAME);
        }
        return new Font(fontName, style, size);
    }
    
    /**
     * Uses values defined in init.xml to create a unicode body font.
     * 
     * @return
     */
    public static Font getChartLabelFont(Configuration sysConfig) {
        return getChartFont(sysConfig, Configuration.CHART_LABEL_FONT_NAME,
                Configuration.CHART_LABEL_FONT_STYLE,
                Configuration.CHART_LABEL_FONT_SIZE);
    }

    /**
     * Uses the system configuration value for the font name, but allows
     * a different size and style.
     * 
     * @param style (e.g. java.awt.Font.PLAIN)
     * @param size The size of the font.
     * @return
     */
    public static Font getChartLabelFont(Configuration sysConfig, int style, int size) {
        String fontName = SANS_SERIF_NAME;
        if (sysConfig != null) {
            fontName = sysConfig.getString(Configuration.CHART_LABEL_FONT_NAME);
        }
        return new Font(fontName, style, size);
    }
}
