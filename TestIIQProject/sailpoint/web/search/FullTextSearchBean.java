package sailpoint.web.search;

import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 3/18/16.
 */
public abstract class FullTextSearchBean<E extends SailPointObject> extends SearchBean<E>{

    @Override
    public List<Map<String, Object>> getRows() throws GeneralException {

        if (_rows == null) {
            if (useLuceneForSearch()) {
                _rows = getRowsLucene();
            } else {
                _rows = super.getRows();
            }
        }

        return _rows;
    }

    public String getKeywordHelpMsg(String type) {
        final String msg = WebUtil.localizeMessage(MessageKeys.HELP_KEYWORD_SEARCH, WebUtil.localizeMessage(type));
        return msg;
    }

    public abstract boolean useLuceneForSearch() throws GeneralException;

    public abstract List<Map<String, Object>> getRowsLucene() throws GeneralException;

}
