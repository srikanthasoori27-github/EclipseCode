/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools.xml;

import java.io.Serializable;

public class TimeString implements Serializable, Comparable<String>, CharSequence {
    private static final long serialVersionUID = 1016203233534719043L;
    private String obj;
    
    public TimeString() {
        obj = new String();
    }
    
    public TimeString(String timeString) {
        obj = timeString;
    }

    public int compareTo(String anotherString) {
        return obj.compareTo(anotherString);
    }

    public char charAt(int index) {
        return obj.charAt(index);
    }

    public int length() {
        return obj.length();
    }

    public CharSequence subSequence(int beginIndex, int endIndex) {
        return obj.subSequence(beginIndex, endIndex);
    }
    
    public String toString() {
        return obj;
    }
}