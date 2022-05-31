package sailpoint.rest.ui;

import sailpoint.object.Comment;
import sailpoint.web.util.WebUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApprovalResourceUtil {

    /**
     * Turn the given list of comments into a list of maps with the comments.
     */
    static List<Map<String,Object>> generateComments(List<Comment> comments) {
        List<Map<String,Object>> list = new ArrayList<Map<String,Object>>();

        if (null != comments) {
            for (Comment comment : comments) {
                list.add(generateComment(comment));
            }
        }

        return list;
    }

    /**
     * Turn the given comment into a map version of the comment.
     */
    static Map<String,Object> generateComment(Comment comment) {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("author", comment.getAuthor());
        map.put("comment", WebUtil.escapeComment(comment.getComment()));
        if (null != comment.getDate()) {
            map.put("date", comment.getDate().getTime());
        }
        return map;
    }

    /**
     * Parses and holds information for a request to add comments.
     */
    protected static class CommentRequest {
        private static final String ATT_COMMENT = "comment";

        private String comment;

        public CommentRequest(Map<String,String> map) {
            this.comment = map.get(ATT_COMMENT);
        }

        public String getComment() {
            return this.comment;
        }
    }
}
