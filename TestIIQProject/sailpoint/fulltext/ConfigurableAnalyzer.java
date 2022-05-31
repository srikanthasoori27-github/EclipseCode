/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.fulltext;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;

import sailpoint.object.Attributes;

/**
 * This class should be extended by Analyzers that support configuration parameters
 * @author Bernie Margolis
 */
public abstract class ConfigurableAnalyzer extends Analyzer{
    protected Attributes<String, Object> config;

    protected ConfigurableAnalyzer() {
        // Avoid NPE
        setConfiguration(new HashMap<>());
    }

    public void setConfiguration(Map<String, Object> config) {
        this.config = new Attributes<String, Object>(config);
    }
}
