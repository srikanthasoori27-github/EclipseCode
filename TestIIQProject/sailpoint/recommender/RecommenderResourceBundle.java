package sailpoint.recommender;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.ResourceBundle;

public class RecommenderResourceBundle extends ResourceBundle {

    private volatile Map<String, String> data;

    static RecommenderResourceBundle loadBundle(Map<String, String> messages) {
        // non-null map means supported locale
        return messages != null ? new RecommenderResourceBundle(messages) : null;
    }

    private RecommenderResourceBundle(Map<String, String> data) {
        this.data = data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getKeys() {
        return Collections.enumeration(data.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object handleGetObject(String arg0) {
        return data.get(arg0);
    }
}
